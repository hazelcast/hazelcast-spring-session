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
import com.hazelcast.query.Predicates;
import com.hazelcast.spi.impl.SerializationServiceSupport;

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

/**
 * A {@link org.springframework.session.SessionRepository} implementation that stores
 * sessions in Hazelcast's distributed {@link IMap}.
 *
 * <p>
 * An example of how to create a new instance can be seen below:
 *
 * <pre class="code">
 * Config config = new Config();
 *
 * // ... configure Hazelcast ...
 * HazelcastSessionConfiguration.applySerializationConfig(config);
 *
 * HazelcastInstance hazelcastInstance = Hazelcast.newHazelcastInstance(config);
 *
 * HazelcastIndexedSessionRepository sessionRepository =
 *         new HazelcastIndexedSessionRepository(hazelcastInstance);
 * </pre>
 *
 * In order to support finding sessions by principal name using
 * {@link #findByIndexNameAndIndexValue(String, String)} method, custom configuration of
 * {@code IMap} supplied to this implementation is recommended due to performance reasons.
 *
 * The following snippet demonstrates how to define recommended configuration using
 * programmatic Hazelcast Configuration:
 *
 * <pre class="code">
 * Config config = new Config();
 *
 * config.getMapConfig(HazelcastIndexedSessionRepository.DEFAULT_SESSION_MAP_NAME)
 *         .addIndexConfig(new IndexConfig(
 *                 IndexType.HASH,
 *                 HazelcastIndexedSessionRepository.PRINCIPAL_NAME_ATTRIBUTE));
 *
 * Hazelcast.newHazelcastInstance(config);
 * </pre>
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

	private Duration defaultMaxInactiveInterval = Duration.ofSeconds(BackingMapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS);

	private IndexResolver<Session> indexResolver = new DelegatingIndexResolver<>(new PrincipalNameIndexResolver<>());

	private String sessionMapName = DEFAULT_SESSION_MAP_NAME;

	private FlushMode flushMode = FlushMode.ON_SAVE;

	private SaveMode saveMode = SaveMode.ON_SET_ATTRIBUTE;

    private boolean jarOnEveryMember = true;

	private IMap<String, BackingMapSession> sessions;

	private UUID sessionListenerId;

    private SerializationService serializationService;

	private SessionIdGenerator sessionIdGenerator = UuidSessionIdGenerator.getInstance();

	/**
	 * Create a new {@link HazelcastIndexedSessionRepository} instance.
	 * @param hazelcastInstance the {@link HazelcastInstance} to use for managing sessions
	 */
	public HazelcastIndexedSessionRepository(HazelcastInstance hazelcastInstance) {
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
	public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		Assert.notNull(applicationEventPublisher, "ApplicationEventPublisher cannot be null");
		this.eventPublisher = applicationEventPublisher;
	}

	/**
	 * Set the maximum inactive interval in seconds between requests before newly created
	 * sessions will be invalidated. A negative time indicates that the session will never
	 * time out. The default is 30 minutes.
	 * @param defaultMaxInactiveInterval the default maxInactiveInterval
	 */
	public void setDefaultMaxInactiveInterval(Duration defaultMaxInactiveInterval) {
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
	public void setIndexResolver(IndexResolver<Session> indexResolver) {
		Assert.notNull(indexResolver, "indexResolver cannot be null");
		this.indexResolver = indexResolver;
	}

	/**
	 * Set the name of map used to store sessions.
	 * @param sessionMapName the session map name
	 */
	public void setSessionMapName(String sessionMapName) {
		Assert.hasText(sessionMapName, "Map name must not be empty");
		this.sessionMapName = sessionMapName;
	}

	/**
	 * Sets the Hazelcast flush mode. Default flush mode is {@link FlushMode#ON_SAVE}.
	 * @param flushMode the new Hazelcast flush mode
	 */
	public void setFlushMode(FlushMode flushMode) {
		Assert.notNull(flushMode, "flushMode cannot be null");
		this.flushMode = flushMode;
	}

	/**
	 * Set the save mode.
	 * @param saveMode the save mode
	 */
	public void setSaveMode(SaveMode saveMode) {
		Assert.notNull(saveMode, "saveMode must not be null");
		this.saveMode = saveMode;
	}

    /**
     * If true, this repository will assume that class instances are present on all members and we can use faster
     * {@link com.hazelcast.map.EntryProcessor} to process sessions in-place, instead of a combination of
     * {@link IMap#get} + {@link IMap#set}.
     */
    public void setJarOnEveryMember(boolean jarOnEveryMember) {
        this.jarOnEveryMember = jarOnEveryMember;
    }

    /**
     * Replaces {@link SerializationService} that we got from {@link HazelcastInstance}.
     */
    void setSerializationService(SerializationService serializationService) {
        Assert.notNull(serializationService, "serializationService must not be null");
        this.serializationService = serializationService;
    }

    @Override
	public HazelcastSession createSession() {
		BackingMapSession cached = new BackingMapSession(this.sessionIdGenerator);
		cached.setMaxInactiveInterval(this.defaultMaxInactiveInterval);
		HazelcastSession session = new HazelcastSession(cached, true);
		session.flushImmediateIfNecessary();
		return session;
	}

	@Override
	public void save(HazelcastSession session) {
        final String sessionId = session.getId();
		if (session.isNew) {
			this.sessions.set(session.getId(), session.getDelegate(), session.getMaxInactiveInterval().getSeconds(),
					TimeUnit.SECONDS);
		} else {
            if (session.sessionIdChanged) {
                this.sessions.delete(session.originalId);
                session.originalId = sessionId;
                this.sessions.set(sessionId, session.getDelegate(), session.getMaxInactiveInterval().getSeconds(),
                                  TimeUnit.SECONDS);
            }
            else if (session.hasChanges()) {
                SessionUpdateEntryProcessor entryProcessor = new SessionUpdateEntryProcessor(session);

                if (jarOnEveryMember) {
                    //noinspection unchecked
                    this.sessions.executeOnKey(sessionId, entryProcessor);
                } else {
                    sessions.lock(sessionId);
                    try {
                        BackingMapSession mapSession = sessions.get(sessionId);
                        entryProcessor.processMapSession(mapSession);
                        sessions.set(sessionId, mapSession, session.getMaxInactiveInterval().getSeconds(), TimeUnit.SECONDS);
                    } finally {
                        sessions.unlock(sessionId);
                    }
                }
            }
        }
        session.clearChangeFlags();
    }

    @Override
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
	public void deleteById(String id) {
		this.sessions.remove(id);
	}

	@Override
	public Map<String, HazelcastSession> findByIndexNameAndIndexValue(String indexName, String indexValue) {
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
	public void entryAdded(EntryEvent<String, BackingMapSession> event) {
		BackingMapSession session = event.getValue();
		if (session.getId().equals(session.getOriginalId())) {
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("Session created with id: " + session.getId());
			}
			this.eventPublisher.publishEvent(new SessionCreatedEvent(this, new HazelcastSession(session)));
		}
	}

	@Override
	public void entryEvicted(EntryEvent<String, BackingMapSession> event) {
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Session expired with id: " + event.getOldValue().getId());
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
			LOGGER.debug("Session expired with id: " + event.getOldValue().getId());
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

		HazelcastSession(BackingMapSession cached, boolean isNew) {
			this.delegate = cached;
			this.isNew = isNew;
			this.originalId = cached.getId();
			if (this.isNew || (saveMode == SaveMode.ALWAYS)) {
				getAttributeNames()
					.forEach((attributeName) -> this.delta.put(attributeName, cached.getAttribute(attributeName)));
			}
		}

		HazelcastSession(BackingMapSession cached) {
			this(cached, false);
		}

		@Override
		public void setLastAccessedTime(Instant lastAccessedTime) {
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
		public <T> T getAttribute(String attributeName) {
            AttributeValue attributeValue = this.delegate.getAttribute(attributeName);
			if (attributeValue != null && saveMode.equals(SaveMode.ON_GET_ATTRIBUTE)) {
				this.delta.put(attributeName, attributeValue);
			}
            return attributeValue == null ? null : (T) attributeValue.getDeserializedValue(serializationService);
		}

		@Override
		public Set<String> getAttributeNames() {
			return this.delegate.getAttributeNames();
		}

		@Override
		public void setAttribute(String attributeName, Object attributeValue) {
            AttributeValue value = AttributeValue.toAttributeValue(attributeValue, serializationService);

            this.delegate.setAttribute(attributeName, value);
			this.delta.put(attributeName, value);
			if (SPRING_SECURITY_CONTEXT.equals(attributeName)) {
				Map<String, String> indexes = indexResolver.resolveIndexesFor(this);
				String principal = (attributeValue != null) ? indexes.get(PRINCIPAL_NAME_INDEX_NAME) : null;
                AttributeValue val = AttributeValue.toAttributeValue(principal, serializationService);
				this.delegate.setPrincipalName(principal);
                this.delta.put(PRINCIPAL_NAME_INDEX_NAME, val);
			}
			flushImmediateIfNecessary();
		}

        @Override
		public void removeAttribute(String attributeName) {
			setAttribute(attributeName, null);
		}

		BackingMapSession getDelegate() {
			return this.delegate;
		}

		boolean hasChanges() {
			return (this.lastAccessedTimeChanged || this.maxInactiveIntervalChanged || !this.delta.isEmpty());
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
