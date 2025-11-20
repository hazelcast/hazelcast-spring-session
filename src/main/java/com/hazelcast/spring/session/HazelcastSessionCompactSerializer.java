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

import com.hazelcast.nio.serialization.compact.CompactReader;
import com.hazelcast.nio.serialization.compact.CompactSerializer;
import com.hazelcast.nio.serialization.compact.CompactWriter;
import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link com.hazelcast.nio.serialization.compact.CompactSerializer} implementation that handles the
 * (de)serialization of {@link BackingMapSession} stored on {@link com.hazelcast.map.IMap}.
 *
 * <p>
 * The use of this serializer is <strong>mandatory</strong> on the instance(s) that will be used by {@link HazelcastIndexedSessionRepository}.
 * <p>
 * An example of how to register the serializer on instance can be seen below:
 *
 * <pre class="code">
 * Config config = new Config();
 *
 * // ... other configurations for Hazelcast ...
 * HazelcastSessionConfiguration.applySerializationConfig(config);
 *
 * HazelcastInstance hazelcastInstance = Hazelcast.newHazelcastInstance(config);
 * </pre>
 *
 * Below is the example of how to register the serializer on client instance:
 *
 * <pre class="code">
 * ClientConfig clientConfig = new ClientConfig();
 *
 * // ... other configurations for Hazelcast Client ...
 * HazelcastSessionConfiguration.applySerializationConfig(config);
 *
 * HazelcastInstance hazelcastClient = HazelcastClient.newHazelcastClient(clientConfig);
 * </pre>
 *
 * @since 4.0.0
 */
@SuppressWarnings("ClassEscapesDefinedScope")
public class HazelcastSessionCompactSerializer implements CompactSerializer<BackingMapSession> {

    @Override
    @NonNull
    public BackingMapSession read(CompactReader reader) {
        String originalId = reader.readString("originalId");
        BackingMapSession cached = new BackingMapSession(originalId);
        cached.setId(reader.readString("id"));
        cached.setPrincipalName(reader.readString("principalName"));
        cached.setCreationTime(readInstant(reader, "creationTime"));
        cached.setLastAccessedTime(readInstant(reader, "lastAccessedTime"));
        cached.setMaxInactiveInterval(readDuration(reader, "maxInactiveInterval"));
        String[] attributeNames = reader.readArrayOfString("attributeNames");
        AttributeValue[] attributeValues = reader.readArrayOfCompact("attributeValues", AttributeValue.class);

        assert attributeNames != null : "Attribute names should not be null";
        assert attributeValues != null : "Attribute values should not be null";
        for (int i = 0; i < attributeNames.length; i++) {
            cached.setAttribute(attributeNames[i], attributeValues[i]);
        }
        return cached;
    }

    @Override
    public void write(@NonNull CompactWriter writer, BackingMapSession session) {
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
    public Class<BackingMapSession> getCompactClass() {
        return BackingMapSession.class;
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
