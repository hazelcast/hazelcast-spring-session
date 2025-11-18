package com.hazelcast.spring.session;

import org.springframework.session.Session;
import org.springframework.session.SessionIdGenerator;
import org.springframework.session.UuidSessionIdGenerator;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.hazelcast.spring.session.HazelcastIndexedSessionRepository.PRINCIPAL_NAME_ATTRIBUTE;
import static org.springframework.session.FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME;

/**
 * {@link Session} implementation holding basic session data and attributes in form of a {@link Map}.
 * <p>Differs from {@link org.springframework.session.MapSession} in one detail - {@code principalName} is also a field.
 * This makes dealing with it easier, no need for extractors.
 */
public class ExtendedMapSession implements Session {

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

    private Map<String, Object> sessionAttrs = new HashMap<>();

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
    public ExtendedMapSession() {
        this(generateId());
    }

    /**
     * Creates a new instance using the specified {@link SessionIdGenerator} to generate
     * the session id.
     * @param sessionIdGenerator the {@link SessionIdGenerator} to use.
     * @since 3.2
     */
    public ExtendedMapSession(SessionIdGenerator sessionIdGenerator) {
        this(sessionIdGenerator.generate());
        this.sessionIdGenerator = sessionIdGenerator;
    }

    /**
     * Creates a new instance with the specified id. This is preferred to the default
     * constructor when the id is known to prevent unnecessary consumption on entropy
     * which can be slow.
     * @param id the identifier to use
     */
    public ExtendedMapSession(String id) {
        this.id = id;
        this.originalId = id;
    }

    @Override
    public void setLastAccessedTime(Instant lastAccessedTime) {
        this.lastAccessedTime = lastAccessedTime;
    }

    public String getPrincipalName() {
        return principalName;
    }

    public void setPrincipalName(String principalName) {
        if (principalName == null) {
            sessionAttrs.remove(PRINCIPAL_NAME_ATTRIBUTE);
            sessionAttrs.remove(PRINCIPAL_NAME_INDEX_NAME);
        } else {
            this.sessionAttrs.put(PRINCIPAL_NAME_ATTRIBUTE, AttributeValue.string(principalName));
            this.sessionAttrs.put(PRINCIPAL_NAME_INDEX_NAME, AttributeValue.string(principalName));
        }
        this.principalName = principalName;
    }

    @Override
    public Instant getCreationTime() {
        return this.creationTime;
    }

    @Override
    public String getId() {
        return this.id;
    }

    /**
     * Get the original session id.
     * @return the original session id
     * @see #changeSessionId()
     */
    public String getOriginalId() {
        return this.originalId;
    }

    @Override
    public String changeSessionId() {
        String changedId = this.sessionIdGenerator.generate();
        setId(changedId);
        return changedId;
    }

    @Override
    public Instant getLastAccessedTime() {
        return this.lastAccessedTime;
    }

    @Override
    public void setMaxInactiveInterval(Duration interval) {
        this.maxInactiveInterval = interval;
    }

    @Override
    public Duration getMaxInactiveInterval() {
        return this.maxInactiveInterval;
    }

    @Override
    public boolean isExpired() {
        return isExpired(Instant.now());
    }

    boolean isExpired(Instant now) {
        if (this.maxInactiveInterval.isNegative()) {
            return false;
        }
        return now.minus(this.maxInactiveInterval).compareTo(this.lastAccessedTime) >= 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String attributeName) {
        if (attributeName.equals(PRINCIPAL_NAME_ATTRIBUTE)) {
            return (T) AttributeValue.string(principalName);
        }
        if (attributeName.equals(PRINCIPAL_NAME_INDEX_NAME)) {
            return (T) AttributeValue.string(principalName);
        }
        return (T) this.sessionAttrs.get(attributeName);
    }

    @Override
    public Set<String> getAttributeNames() {
        return new HashSet<>(this.sessionAttrs.keySet());
    }

    @Override
    public void setAttribute(String attributeName, Object attributeValue) {
        if (attributeValue == null) {
            removeAttribute(attributeName);
        } else if (attributeName.equals(PRINCIPAL_NAME_ATTRIBUTE) || attributeName.equals(PRINCIPAL_NAME_INDEX_NAME)) {
            principalName = (String) ((AttributeValue) attributeValue).object();
            setPrincipalName(principalName);
        }
        else {
            this.sessionAttrs.put(attributeName, attributeValue);
        }
    }

    @Override
    public void removeAttribute(String attributeName) {
        this.sessionAttrs.remove(attributeName);
        if (attributeName.equals(PRINCIPAL_NAME_ATTRIBUTE)) {
            principalName =  null;
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

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Session && this.id.equals(((Session) obj).getId());
    }

    @Override
    public int hashCode() {
        return this.id.hashCode();
    }

    private static String generateId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Sets the {@link SessionIdGenerator} to use when generating a new session id.
     * @param sessionIdGenerator the {@link SessionIdGenerator} to use.
     * @since 3.2
     */
    public void setSessionIdGenerator(SessionIdGenerator sessionIdGenerator) {
        this.sessionIdGenerator = sessionIdGenerator;
    }

}
