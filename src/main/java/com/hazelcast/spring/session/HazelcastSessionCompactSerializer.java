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

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.nio.serialization.compact.CompactReader;
import com.hazelcast.nio.serialization.compact.CompactSerializer;
import com.hazelcast.nio.serialization.compact.CompactWriter;
import org.jspecify.annotations.NonNull;
import org.springframework.session.MapSession;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link com.hazelcast.nio.serialization.compact.CompactSerializer} implementation that handles the
 * (de)serialization of {@link MapSession} stored on {@link com.hazelcast.map.IMap}.
 *
 * <p>
 * The use of this serializer is optional and provides faster serialization of sessions.
 * If not configured to be used, Hazelcast will serialize sessions via
 * {@link java.io.Serializable} by default.
 *
 * <p>
 * If multiple instances of a Spring application is run, then all of them need to use the
 * same serialization method. If this serializer is registered on one instance and not
 * another one, then it will end up with HazelcastSerializationException. The same applies
 * when clients are configured to use this serializer but not the members, and vice versa.
 * Also note that, if a new instance is created with this serialization but the existing
 * Hazelcast cluster contains the values not serialized by this but instead the default
 * one, this will result in incompatibility again.
 *
 * <p>
 * An example of how to register the serializer on embedded instance can be seen below:
 *
 * <pre class="code">
 * Config config = new Config();
 *
 * // ... other configurations for Hazelcast ...
 *
 * SerializerConfig serializerConfig = new SerializerConfig();
 * serializerConfig.setImplementation(new HazelcastSessionSerializer()).setTypeClass(MapSession.class);
 * config.getSerializationConfig().addSerializerConfig(serializerConfig);
 *
 * HazelcastInstance hazelcastInstance = Hazelcast.newHazelcastInstance(config);
 * </pre>
 *
 * Below is the example of how to register the serializer on client instance. Note that,
 * to use the serializer in client/server mode, the serializer - and hence
 * {@link MapSession}, must exist on the server's classpath and must be registered via
 * {@link com.hazelcast.config.SerializerConfig} with the configuration above for each
 * server.
 *
 * <pre class="code">
 * ClientConfig clientConfig = new ClientConfig();
 *
 * // ... other configurations for Hazelcast Client ...
 *
 * SerializerConfig serializerConfig = new SerializerConfig();
 * serializerConfig.setImplementation(new HazelcastSessionSerializer()).setTypeClass(MapSession.class);
 * clientConfig.getSerializationConfig().addSerializerConfig(serializerConfig);
 *
 * HazelcastInstance hazelcastClient = HazelcastClient.newHazelcastClient(clientConfig);
 * </pre>
 *
 * @since 4.0.0
 */
public class HazelcastSessionCompactSerializer implements CompactSerializer<ExtendedMapSession> {

    @Override
    @NonNull
    public ExtendedMapSession read(CompactReader reader) {
        String originalId = reader.readString("originalId");
        ExtendedMapSession cached = new ExtendedMapSession(originalId);
        cached.setId(reader.readString("id"));
        cached.setPrincipalName(reader.readString("principalName"));
        cached.setCreationTime(readInstant(reader, "creationTime"));
        cached.setLastAccessedTime(readInstant(reader, "lastAccessedTime"));
        cached.setMaxInactiveInterval(readDuration(reader, "maxInactiveInterval"));
        String[] attributeNames = reader.readArrayOfString("attributeNames");
        AttributeValue[] attributeValues = reader.readArrayOfCompact("attributeValues", AttributeValue.class);
        for (int i = 0; i < attributeNames.length; i++) {
            cached.setAttribute(attributeNames[i], attributeValues[i]);
        }
        return cached;
    }

    @Override
    public void write(@NonNull CompactWriter writer, ExtendedMapSession session) {
        writer.writeString("originalId", session.getOriginalId());
        writer.writeString("id", session.getId());
        writer.writeString("principalName", session.getPrincipalName());
        writeInstant(writer, session.getCreationTime(), "creationTime");
        writeInstant(writer, session.getLastAccessedTime(), "lastAccessedTime");
        writeDuration(writer, session.getMaxInactiveInterval(),  "maxInactiveInterval");
        List<String> attributeNames = new ArrayList<>(session.getAttributeNames());
        AttributeValue[] attributeValues = new AttributeValue[attributeNames.size()];
        for (int i = 0; i < attributeNames.size(); i++) {
            AttributeValue value = session.getAttribute(attributeNames.get(i));
            attributeValues[i] = value;
        }

        writer.writeArrayOfString("attributeNames", attributeNames.toArray(new String[0]));
        writer.writeArrayOfCompact("attributeValues", attributeValues);
    }

    @Override
    @NonNull
    public String getTypeName() {
        return "ExtendedMapSession";
    }

    @Override
    @NonNull
    public Class<ExtendedMapSession> getCompactClass() {
        return ExtendedMapSession.class;
    }
    private void writeInstant(CompactWriter writer, Instant instant, String prefix) {
        writer.writeInt64(prefix + "_seconds", instant.getEpochSecond());
        writer.writeInt32(prefix + "_nanos", instant.getNano());
    }

    @SuppressWarnings("SameParameterValue")
    private void writeDuration(CompactWriter writer, Duration duration, String prefix) {
        writer.writeInt64(prefix + "_seconds", duration.getSeconds());
        writer.writeInt32(prefix + "_nanos", duration.getNano());
    }

    private Instant readInstant(CompactReader reader, String prefix) {
        long seconds = reader.readInt64(prefix + "_seconds");
        int nanos = reader.readInt32(prefix + "_nanos");
        return Instant.ofEpochSecond(seconds, nanos);
    }

    @SuppressWarnings("SameParameterValue")
    private Duration readDuration(CompactReader reader, String prefix) {
        long seconds = reader.readInt64(prefix + "_seconds");
        int nanos = reader.readInt32(prefix + "_nanos");
        return Duration.ofSeconds(seconds, nanos);
    }
}
