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
import com.hazelcast.spring.session.serialization.DurationSerializer;
import com.hazelcast.spring.session.serialization.InstantSerializer;
import org.jspecify.annotations.NonNull;

import java.util.Set;

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
public class HazelcastSessionCompactSerializer implements CompactSerializer<BackingMapSession> {

    public static final HazelcastSessionCompactSerializer INSTANCE = new HazelcastSessionCompactSerializer();

    @Override
    @NonNull
    public BackingMapSession read(CompactReader reader) {
        String originalId = reader.readString("originalId");
        BackingMapSession cached = new BackingMapSession(originalId);
        cached.setId(reader.readString("id"));
        cached.setPrincipalName(reader.readString("principalName"));
        cached.setCreationTime(InstantSerializer.read(reader, "creationTime"));
        cached.setLastAccessedTime(InstantSerializer.read(reader, "lastAccessedTime"));
        cached.setMaxInactiveInterval(DurationSerializer.read(reader, "maxInactiveInterval"));
        String[] attributeNames = reader.readArrayOfString("attributeNames");
        AttributeValue[] attributeValues = reader.readArrayOfCompact("attributeValues", AttributeValue.class);

        assert attributeNames != null : "Attribute names should not be null";
        assert attributeValues != null : "Attribute values should not be null";
        for (int i = 0; i < attributeNames.length; i++) {
            cached.setSerializedAttribute(attributeNames[i], attributeValues[i]);
        }
        return cached;
    }

    @Override
    public void write(@NonNull CompactWriter writer, BackingMapSession session) {
        writer.writeString("originalId", session.getOriginalId());
        writer.writeString("id", session.getId());
        writer.writeString("principalName", session.getPrincipalName());
        InstantSerializer.write(writer, "creationTime", session.getCreationTime());
        InstantSerializer.write(writer, "lastAccessedTime", session.getLastAccessedTime());
        DurationSerializer.write(writer, "maxInactiveInterval", session.getMaxInactiveInterval());
        Set<String> attributeNames = session.getAttributeNameWithoutPrincipal();
        AttributeValue[] attributeValues = attributeNames.stream().map(session::getAttribute).toArray(AttributeValue[]::new);

        writer.writeArrayOfString("attributeNames", attributeNames.toArray(new String[0]));
        writer.writeArrayOfCompact("attributeValues", attributeValues);
    }

    @Override
    @NonNull
    public String getTypeName() {
        return "BackingMapSession";
    }

    @Override
    @NonNull
    public Class<BackingMapSession> getCompactClass() {
        return BackingMapSession.class;
    }

}
