/*
 * Copyright 2014-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.spring.session;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.internal.serialization.SerializationService;
import com.hazelcast.map.IMap;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.map.listener.EntryEvictedListener;
import com.hazelcast.map.listener.EntryExpiredListener;
import com.hazelcast.map.listener.EntryRemovedListener;
import com.hazelcast.nio.serialization.HazelcastSerializationException;
import com.hazelcast.query.Predicates;
import com.hazelcast.spi.impl.SerializationServiceSupport;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.session.DelegatingIndexResolver;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.FlushMode;
import org.springframework.session.IndexResolver;
import org.springframework.session.PrincipalNameIndexResolver;
import org.springframework.session.SaveMode;
import org.springframework.session.Session;
import org.springframework.session.SessionIdGenerator;
import org.springframework.session.UuidSessionIdGenerator;
import org.springframework.session.events.AbstractSessionEvent;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.util.Assert;

import static com.hazelcast.spring.session.BackingMapSession.PRINCIPAL_NAME_ATTRIBUTES;

/**
 * A {@link org.springframework.session.SessionRepository} implementation that stores
 * sessions in Hazelcast's distributed {@link IMap}.
 *
 * <p>
 * An example of how to create a new instance can be seen below:
 *
 * <pre>{@code
 * Config config = new Config();
 *
 * // ... configure Hazelcast ...
 * HazelcastSessionConfiguration.applySerializationConfig(config);
 *
 * HazelcastInstance hazelcastInstance = Hazelcast.newHazelcastInstance(config);
 *
 * HazelcastIndexedSessionRepository sessionRepository =
 *         new HazelcastIndexedSessionRepository(hazelcastInstance);
 * }</pre>
 *
 * In order to support finding sessions by principal name using
 * {@link #findByIndexNameAndIndexValue(String, String)} method, custom configuration of
 * {@code IMap} supplied to this implementation is recommended for performance reasons.
 *
 * The following snippet demonstrates how to define recommended configuration using
 * programmatic Hazelcast Configuration:
 *
 * <pre>{@code
 * Config config = new Config();
 *
 * config.getMapConfig(HazelcastIndexedSessionRepository.DEFAULT_SESSION_MAP_NAME)
 *         .addIndexConfig(new IndexConfig(
 *                 IndexType.HASH,
 *                 HazelcastIndexedSessionRepository.PRINCIPAL_NAME_ATTRIBUTE));
 *
 * Hazelcast.newHazelcastInstance(config);
 * }</pre>
 *
 * This implementation listens for events on the Hazelcast-backed SessionRepository and
 * translates those events into the corresponding Spring Session events. Publish the
 * Spring Session events with the given {@link ApplicationEventPublisher}.
 *
 * <ul>
 * <li>entryAdded - {@link SessionCreatedEvent}</li>
 * <li>entryEvicted - {@link SessionExpiredEvent}</li>
 * <li>entryRemoved - {@link SessionDeletedEvent}</li>
 * </ul>
 *
 * @author Vedran Pavic
 * @author Tommy Ludwig
 * @author Mark Anderson
 * @author Aleksandar Stojsavljevic
 * @author Eleftheria Stein
 * @since 2.2.0
 */
@SuppressWarnings("ClassEscapesDefinedScope")
public class HazelcastIndexedSessionRepository
		implements FindByIndexNameSessionRepository<HazelcastIndexedSessionRepository.HazelcastSession>,
                   EntryAddedListener<String, BackingMapSession>, EntryEvictedListener<String, BackingMapSession>,
                   EntryRemovedListener<String, BackingMapSession>, EntryExpiredListener<String, BackingMapSession>, InitializingBean,
                   DisposableBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(HazelcastIndexedSessionRepository.class);

	/**
	 * The default name of map used by Spring Session to store sessions.
	 */
	public static final String DEFAULT_SESSION_MAP_NAME = "spring:session:sessions";

	/**
	 * The principal name custom attribute name.
	 */
	public static final String PRINCIPAL_NAME_ATTRIBUTE = "principalName";

	private static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

	private final HazelcastInstance hazelcastInstance;

	private ApplicationEventPublisher eventPublisher = (event) -> {
	};

	private Duration defaultMaxInactiveInterval = BackingMapSession.DEFAULT_MAX_INACTIVE_INTERVAL;

	private IndexResolver<Session> indexResolver = new DelegatingIndexResolver<>(new PrincipalNameIndexResolver<>());

	private String sessionMapName = DEFAULT_SESSION_MAP_NAME;

	private FlushMode flushMode = FlushMode.ON_SAVE;

	private SaveMode saveMode = SaveMode.ON_SET_ATTRIBUTE;

    /**
     * If false, the {@link #save(HazelcastSession)} will fall back to simple algorithm:
     * <ol>
     *     <li> lock entry with session
     *     <li> get current value from cluster
     *     <li> process changes
     *     <li> {@link IMap#set} the value
     *     <li> unlock entry
     * </ol>
     * In this case {@link SessionUpdateEntryProcessor} will never be serialized and sent. We still need to serialize/deserialize
     * user objects, but since processing is done on client and client is required to register serializers, we have no problem with this.
     * <p>
     * If the value is true, the {@link SessionUpdateEntryProcessor} will be sent to a cluster to process the changes. Processor
     * must be present on all members, will be serialized using custom serializer, that will register itself automatically via ServiceLoader.
     *
     * User data will be handled in {@link SessionUpdateEntryProcessor} as pure Java objects <strong>if</strong> CompactSerializers
     * were configured on the server side or as {@link com.hazelcast.nio.serialization.genericrecord.GenericRecord} if serializers
     * were configured only on clients.
     */
    private volatile boolean deployedOnAllMembers = true;

	private IMap<String, BackingMapSession> sessions;

	private UUID sessionListenerId;

    private SerializationService serializationService;

	private SessionIdGenerator sessionIdGenerator = UuidSessionIdGenerator.getInstance();

	/**
	 * Create a new {@link HazelcastIndexedSessionRepository} instance.
	 * @param hazelcastInstance the {@link HazelcastInstance} to use for managing sessions
	 */
	public HazelcastIndexedSessionRepository(@NonNull HazelcastInstance hazelcastInstance) {
		Assert.notNull(hazelcastInstance, "HazelcastInstance must not be null");
		this.hazelcastInstance = hazelcastInstance;
        if (hazelcastInstance instanceof SerializationServiceSupport sss) {
            // can be a mock for tests
            this.serializationService = sss.getSerializationService();
        }
        LOGGER.info("HazelcastIndexedSessionRepository initialized");
	}

    @Override
	public void afterPropertiesSet() {
		this.sessions = this.hazelcastInstance.getMap(this.sessionMapName);
		this.sessionListenerId = this.sessions.addEntryListener(this, true);
	}

	@Override
	public void destroy() {
		this.sessions.removeEntryListener(this.sessionListenerId);
	}

    /**
	 * Sets the {@link ApplicationEventPublisher} that is used to publish
	 * {@link AbstractSessionEvent session events}. The default is to not publish session
	 * events.
	 * @param applicationEventPublisher the {@link ApplicationEventPublisher} that is used
	 * to publish session events. Cannot be null.
	 */
	public void setApplicationEventPublisher(@NonNull ApplicationEventPublisher applicationEventPublisher) {
		Assert.notNull(applicationEventPublisher, "ApplicationEventPublisher cannot be null");
		this.eventPublisher = applicationEventPublisher;
	}

	/**
	 * Set the maximum inactive interval in seconds between requests before newly created
	 * sessions will be invalidated. A negative time indicates that the session will never
	 * time out. The default is 30 minutes.
	 * @param defaultMaxInactiveInterval the default maxInactiveInterval
	 */
	public void setDefaultMaxInactiveInterval(@NonNull Duration defaultMaxInactiveInterval) {
		Assert.notNull(defaultMaxInactiveInterval, "defaultMaxInactiveInterval must not be null");
		this.defaultMaxInactiveInterval = defaultMaxInactiveInterval;
	}

	/**
	 * Set the maximum inactive interval in seconds between requests before newly created
	 * sessions will be invalidated. A negative time indicates that the session will never
	 * time out. The default is 1800 (30 minutes).
	 * @param defaultMaxInactiveInterval the default maxInactiveInterval in seconds
	 * @deprecated since 3.0.0, in favor of
	 * {@link #setDefaultMaxInactiveInterval(Duration)}
	 */
	@Deprecated(since = "3.0.0")
	public void setDefaultMaxInactiveInterval(Integer defaultMaxInactiveInterval) {
		setDefaultMaxInactiveInterval(Duration.ofSeconds(defaultMaxInactiveInterval));
	}

	/**
	 * Set the {@link IndexResolver} to use.
	 * @param indexResolver the index resolver
	 */
	public void setIndexResolver(@NonNull IndexResolver<Session> indexResolver) {
		Assert.notNull(indexResolver, "indexResolver cannot be null");
		this.indexResolver = indexResolver;
	}

	/**
	 * Set the name of map used to store sessions.
	 * @param sessionMapName the session map name
	 */
	public void setSessionMapName(@NonNull String sessionMapName) {
		Assert.hasText(sessionMapName, "Map name must not be empty");
		this.sessionMapName = sessionMapName;
	}

	/**
	 * Sets the Hazelcast flush mode. Default flush mode is {@link FlushMode#ON_SAVE}.
	 * @param flushMode the new Hazelcast flush mode
	 */
	public void setFlushMode(@NonNull FlushMode flushMode) {
		Assert.notNull(flushMode, "flushMode cannot be null");
		this.flushMode = flushMode;
	}

	/**
	 * Set the save mode.
	 * @param saveMode the save mode
	 */
	public void setSaveMode(@NonNull SaveMode saveMode) {
		Assert.notNull(saveMode, "saveMode must not be null");
		this.saveMode = saveMode;
	}

    /**
     * If true, this repository will assume that class instances are present on all members, and we can use faster
     * {@link com.hazelcast.map.EntryProcessor} to process sessions in-place, instead of a combination of
     * {@link IMap#get} + {@link IMap#set}.
     */
    public void setDeployedOnAllMembers(boolean deployedOnAllMembers) {
        this.deployedOnAllMembers = deployedOnAllMembers;
    }

    /**
     * Replaces {@link SerializationService} that we got from {@link HazelcastInstance}.
     */
    void setSerializationService(@NonNull SerializationService serializationService) {
        Assert.notNull(serializationService, "serializationService must not be null");
        this.serializationService = serializationService;
    }

    @Override
    @NonNull
    public HazelcastSession createSession() {
		BackingMapSession cached = new BackingMapSession(this.sessionIdGenerator);
		cached.setMaxInactiveInterval(this.defaultMaxInactiveInterval);
		HazelcastSession session = new HazelcastSession(cached, true);
		session.flushImmediateIfNecessary();
		return session;
	}

	@Override
	public void save(@NonNull HazelcastSession session) {
        final String sessionId = session.getId();
        session.prepareAttributesSerializedForm(serializationService);
		if (session.isNew) {
			this.sessions.set(session.getId(), session.getDelegate(), session.getMaxInactiveInterval().getSeconds(),
					TimeUnit.SECONDS);
        } else if (session.sessionIdChanged) {
            this.sessions.delete(session.originalId);
            session.originalId = sessionId;
            this.sessions.set(sessionId, session.getDelegate(), session.getMaxInactiveInterval().getSeconds(),
                              TimeUnit.SECONDS);
        } else if (session.hasChanges()) {
            SessionUpdateEntryProcessor entryProcessor = new SessionUpdateEntryProcessor(session);

			if (deployedOnAllMembers) {
				try {
                    //noinspection unchecked
                    this.sessions.executeOnKey(sessionId, entryProcessor);
				} catch (HazelcastSerializationException e) {
					deployedOnAllMembers = true;
				}
			}

            if (!deployedOnAllMembers) {
                sessions.lock(sessionId);
                try {
                    BackingMapSession mapSession = sessions.get(sessionId);
                    if (mapSession != null) {
                        entryProcessor.processMapSession(mapSession);
                        sessions.set(sessionId, mapSession, session.getMaxInactiveInterval().getSeconds(), TimeUnit.SECONDS);
                    }
                } finally {
                    sessions.unlock(sessionId);
                }
            }
        }

        session.clearChangeFlags();
    }

    @Override
    @Nullable
    public HazelcastSession findById(String id) {
		BackingMapSession saved = this.sessions.get(id);
		if (saved == null) {
			return null;
		}
		if (saved.isExpired()) {
			deleteById(saved.getId());
			return null;
		}
		return new HazelcastSession(saved);
	}

	@Override
	public void deleteById(@NonNull String id) {
		this.sessions.remove(id);
	}

	@Override
    @NonNull
	public Map<String, HazelcastSession> findByIndexNameAndIndexValue(@NonNull String indexName, @Nullable String indexValue) {
		if (!PRINCIPAL_NAME_INDEX_NAME.equals(indexName)) {
			return Collections.emptyMap();
		}
		Collection<BackingMapSession> sessions = this.sessions.values(Predicates.equal(PRINCIPAL_NAME_ATTRIBUTE, indexValue));
		Map<String, HazelcastSession> sessionMap = new HashMap<>(sessions.size());
		for (BackingMapSession session : sessions) {
			sessionMap.put(session.getId(), new HazelcastSession(session));
		}
		return sessionMap;
	}

	@Override
	public void entryAdded(@NonNull EntryEvent<String, BackingMapSession> event) {
		BackingMapSession session = event.getValue();
		if (session.getId().equals(session.getOriginalId())) {
			if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Session created with id: {}", session.getId());
			}
			this.eventPublisher.publishEvent(new SessionCreatedEvent(this, new HazelcastSession(session)));
		}
	}

	@Override
	public void entryEvicted(@NonNull EntryEvent<String, BackingMapSession> event) {
		if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Session evicted with id: {}", event.getOldValue().getId());
		}
		this.eventPublisher.publishEvent(new SessionExpiredEvent(this, new HazelcastSession(event.getOldValue())));
	}

	@Override
	public void entryRemoved(EntryEvent<String, BackingMapSession> event) {
		BackingMapSession session = event.getOldValue();
		if (session != null) {
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("Session deleted with id: " + session.getId());
			}
			this.eventPublisher.publishEvent(new SessionDeletedEvent(this, new HazelcastSession(session)));
		}
	}

	@Override
	public void entryExpired(EntryEvent<String, BackingMapSession> event) {
		if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Session expired with id: {}", event.getOldValue().getId());
		}
		this.eventPublisher.publishEvent(new SessionExpiredEvent(this, new HazelcastSession(event.getOldValue())));
	}

	/**
	 * Set the {@link SessionIdGenerator} to use to generate session ids.
	 * @param sessionIdGenerator the {@link SessionIdGenerator} to use
	 * @since 3.2
	 */
	public void setSessionIdGenerator(SessionIdGenerator sessionIdGenerator) {
		Assert.notNull(sessionIdGenerator, "sessionIdGenerator cannot be null");
		this.sessionIdGenerator = sessionIdGenerator;
	}

    /**
	 * A custom implementation of {@link Session} that uses a {@link BackingMapSession} as the
	 * basis for its mapping. It keeps track if changes have been made since last save.
	 *
	 * @author Aleksandar Stojsavljevic
	 */
	public final class HazelcastSession implements Session {

		private final BackingMapSession delegate;

		private boolean isNew;

		boolean sessionIdChanged;

		boolean lastAccessedTimeChanged;

		boolean maxInactiveIntervalChanged;

		private String originalId;

		final Map<String, AttributeValue> delta = new HashMap<>();
        boolean principalNameChanged;

		HazelcastSession(@NonNull BackingMapSession cached, boolean isNew) {
			this.delegate = cached;
			this.isNew = isNew;
			this.originalId = cached.getId();
			if (this.isNew || (saveMode == SaveMode.ALWAYS)) {
				delegate.getAttributeNames()
					.forEach((attributeName) -> registerDelta(attributeName, cached.getAttribute(attributeName)));
			}
		}
        /**
         *  New principalName will be registered in {@link BackingMapSession#setAttribute}, so in case of these attributes
         *  we only mark that there was a change.
         *  <p>
         *  Otherwise changed attribute will be added to {@link #delta}.
         */
        private void registerDelta(String attributeName, @Nullable AttributeValue attribute) {
            if (PRINCIPAL_NAME_ATTRIBUTES.contains(attributeName)) {
                principalNameChanged = true;
                return;
            }
            this.delta.put(attributeName, attribute);
        }

        boolean principalNameChanged() {
            return principalNameChanged;
        }

		HazelcastSession(@NonNull BackingMapSession cached) {
			this(cached, false);
		}

		@Override
		public void setLastAccessedTime(@NonNull Instant lastAccessedTime) {
			this.delegate.setLastAccessedTime(lastAccessedTime);
			this.lastAccessedTimeChanged = true;
			flushImmediateIfNecessary();
		}

		@Override
		public boolean isExpired() {
			return this.delegate.isExpired();
		}

		@Override
		public Instant getCreationTime() {
			return this.delegate.getCreationTime();
		}

		@Override
        @NonNull
        public String getId() {
			return this.delegate.getId();
		}

		@Override
		public String changeSessionId() {
			String newSessionId = sessionIdGenerator.generate();
			this.delegate.setId(newSessionId);
			this.sessionIdChanged = true;
			return newSessionId;
		}

		@Override
		public Instant getLastAccessedTime() {
			return this.delegate.getLastAccessedTime();
		}

		@Override
		public void setMaxInactiveInterval(Duration interval) {
			this.delegate.setMaxInactiveInterval(interval);
			this.maxInactiveIntervalChanged = true;
			flushImmediateIfNecessary();
		}

		@Override
		public Duration getMaxInactiveInterval() {
			return this.delegate.getMaxInactiveInterval();
		}

        @Override
		@SuppressWarnings("unchecked")
        @Nullable
		public <T> T getAttribute(String attributeName) {
            AttributeValue attributeValue = this.delegate.getAttribute(attributeName);
			if (attributeValue == null) {
                return null;
            }
            attributeValue.deserialize(serializationService);
            if (saveMode.equals(SaveMode.ON_GET_ATTRIBUTE)) {
                registerDelta(attributeName, attributeValue);
			}
            return (T) attributeValue.object();
		}

		@Override
        @NonNull
        public Set<String> getAttributeNames() {
			return this.delegate.getAttributeNames();
		}

		@Override
		public void setAttribute(@NonNull String attributeName, @Nullable Object attributeValue) {
            if (attributeValue == null) {
                this.delegate.removeAttribute(attributeName);
                this.delta.put(attributeName, null);
            } else {
                AttributeValue value = AttributeValue.deserialized(attributeValue);

                this.delegate.setAttribute(attributeName, value);
                registerDelta(attributeName, value);
            }
			if (SPRING_SECURITY_CONTEXT.equals(attributeName)) {
				Map<String, String> indexes = indexResolver.resolveIndexesFor(this);
				String principal = (attributeValue != null) ? indexes.get(PRINCIPAL_NAME_INDEX_NAME) : null;
				this.delegate.setPrincipalName(principal);
                this.principalNameChanged = true;
			}
			flushImmediateIfNecessary();
		}

        void prepareAttributesSerializedForm(SerializationService serializationService) {
            this.delegate.prepareAttributesSerializedForm(serializationService);
            delta.forEach((attributeName, attributeValue) -> {
                if (attributeValue != null) {
                    attributeValue.serialize(serializationService);
                }
            });
        }

        @Override
		public void removeAttribute(@NonNull String attributeName) {
			setAttribute(attributeName, null);
		}

		BackingMapSession getDelegate() {
			return this.delegate;
		}

		boolean hasChanges() {
			return (this.lastAccessedTimeChanged || this.maxInactiveIntervalChanged || !this.delta.isEmpty() || principalNameChanged);
		}

		void clearChangeFlags() {
			this.isNew = false;
			this.lastAccessedTimeChanged = false;
			this.sessionIdChanged = false;
			this.maxInactiveIntervalChanged = false;
			this.delta.clear();
		}

		private void flushImmediateIfNecessary() {
			if (flushMode == FlushMode.IMMEDIATE) {
				save(this);
			}
		}
	}


}
