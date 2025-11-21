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

import org.springframework.session.Session;
import org.springframework.session.SessionIdGenerator;
import org.springframework.session.UuidSessionIdGenerator;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.hazelcast.spring.session.HazelcastIndexedSessionRepository.PRINCIPAL_NAME_ATTRIBUTE;
import static org.springframework.session.FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME;

/**
 * {@link Session} implementation holding basic session data and attributes in dataType of a {@link Map}.
 * <p>Differs from {@link org.springframework.session.MapSession} in one detail - {@code principalName} is also a field.
 * This makes dealing with it easier, no need for extractors.
 *
 * @since 4.0.0
 */
class BackingMapSession {

    /**
     * Default {@link #setMaxInactiveInterval(Duration)} (30 minutes) in seconds.
     */
    public static final int DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS = 1800;

    /**
     * Default {@link #setMaxInactiveInterval(Duration)} (30 minutes).
     */
    public static final Duration DEFAULT_MAX_INACTIVE_INTERVAL = Duration
            .ofSeconds(DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS);

    private String id;

    private final String originalId;

    private final Map<String, AttributeValue> sessionAttrs = new HashMap<>();

    private Instant creationTime = Instant.now();

    private Instant lastAccessedTime = this.creationTime;

    private String principalName;

    /**
     * Defaults to 30 minutes.
     */
    private Duration maxInactiveInterval = DEFAULT_MAX_INACTIVE_INTERVAL;

    private transient SessionIdGenerator sessionIdGenerator = UuidSessionIdGenerator.getInstance();

    /**
     * Creates a new instance with a secure randomly generated identifier.
     */
    BackingMapSession() {
        this(generateId());
    }

    /**
     * Creates a new instance using the specified {@link SessionIdGenerator} to generate
     * the session id.
     * @param sessionIdGenerator the {@link SessionIdGenerator} to use.
     */
    BackingMapSession(SessionIdGenerator sessionIdGenerator) {
        this(sessionIdGenerator.generate());
        this.sessionIdGenerator = sessionIdGenerator;
    }

    /**
     * Creates a new instance with the specified id. This is preferred to the default
     * constructor when the id is known to prevent unnecessary consumption on entropy
     * which can be slow.
     * @param id the identifier to use
     */
    BackingMapSession(String id) {
        this.id = id;
        this.originalId = id;
    }

    public void setLastAccessedTime(Instant lastAccessedTime) {
        this.lastAccessedTime = lastAccessedTime;
    }

    public String getPrincipalName() {
        return principalName;
    }

    public void setPrincipalName(String principalName) {
        this.principalName = principalName;
        if (principalName == null) {
            sessionAttrs.remove(PRINCIPAL_NAME_ATTRIBUTE);
            sessionAttrs.remove(PRINCIPAL_NAME_INDEX_NAME);
        } else {
            this.sessionAttrs.put(PRINCIPAL_NAME_ATTRIBUTE, AttributeValue.string(principalName));
            this.sessionAttrs.put(PRINCIPAL_NAME_INDEX_NAME, AttributeValue.string(principalName));
        }
    }

    public Instant getCreationTime() {
        return this.creationTime;
    }

    public String getId() {
        return this.id;
    }

    /**
     * Get the original session id.
     * @return the original session id
     * @see com.hazelcast.spring.session.HazelcastIndexedSessionRepository.HazelcastSession#changeSessionId()
     */
    public String getOriginalId() {
        return this.originalId;
    }

    public Instant getLastAccessedTime() {
        return this.lastAccessedTime;
    }

    public void setMaxInactiveInterval(Duration interval) {
        this.maxInactiveInterval = interval;
    }

    public Duration getMaxInactiveInterval() {
        return this.maxInactiveInterval;
    }

    public boolean isExpired() {
        return isExpired(Instant.now());
    }

    boolean isExpired(Instant now) {
        if (this.maxInactiveInterval.isNegative()) {
            return false;
        }
        return now.minus(this.maxInactiveInterval).compareTo(this.lastAccessedTime) >= 0;
    }

    public AttributeValue getAttribute(String attributeName) {
        if (attributeName.equals(PRINCIPAL_NAME_ATTRIBUTE)) {
            return AttributeValue.string(principalName);
        }
        if (attributeName.equals(PRINCIPAL_NAME_INDEX_NAME)) {
            return AttributeValue.string(principalName);
        }
        return this.sessionAttrs.get(attributeName);
    }

    public Set<String> getAttributeNames() {
        return new HashSet<>(this.sessionAttrs.keySet());
    }

    public void setAttribute(String attributeName, AttributeValue attributeValue) {
        if (attributeValue == null) {
            removeAttribute(attributeName);
        } else if (attributeName.equals(PRINCIPAL_NAME_ATTRIBUTE) || attributeName.equals(PRINCIPAL_NAME_INDEX_NAME)) {
            setPrincipalName((String) attributeValue.object());
        } else {
            this.sessionAttrs.put(attributeName, attributeValue);
        }
    }

    public void removeAttribute(String attributeName) {
        this.sessionAttrs.remove(attributeName);
        if (attributeName.equals(PRINCIPAL_NAME_ATTRIBUTE)) {
            principalName = null;
            this.sessionAttrs.remove(PRINCIPAL_NAME_ATTRIBUTE);
            this.sessionAttrs.remove(PRINCIPAL_NAME_INDEX_NAME);
        }
    }

    /**
     * Sets the time that this {@link Session} was created. The default is when the
     * {@link Session} was instantiated.
     * @param creationTime the time that this {@link Session} was created.
     */
    public void setCreationTime(Instant creationTime) {
        this.creationTime = creationTime;
    }

    /**
     * Sets the identifier for this {@link Session}. The id should be a secure randomly
     * generated value to prevent malicious users from guessing this value. The default is
     * a secure randomly generated identifier.
     * @param id the identifier for this session.
     */
    public void setId(String id) {
        this.id = id;
    }

    private static String generateId() {
        return UUID.randomUUID().toString();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BackingMapSession that)) {
            return false;
        }
        return Objects.equals(id, that.id)
                && Objects.equals(originalId, that.originalId)
                && Objects.equals(sessionAttrs, that.sessionAttrs)
                && Objects.equals(creationTime,  that.creationTime)
                && Objects.equals(lastAccessedTime, that.lastAccessedTime)
                && Objects.equals(principalName, that.principalName)
                && Objects.equals(maxInactiveInterval, that.maxInactiveInterval)
                && Objects.equals(sessionIdGenerator, that.sessionIdGenerator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, originalId, sessionAttrs, creationTime, lastAccessedTime, principalName, maxInactiveInterval,
                            sessionIdGenerator);
    }
}
