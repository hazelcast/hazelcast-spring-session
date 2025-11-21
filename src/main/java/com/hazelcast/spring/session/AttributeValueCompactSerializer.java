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

/**
 * A {@link com.hazelcast.nio.serialization.compact.CompactSerializer} implementation that handles the
 * (de)serialization of {@link AttributeValue} stored on {@link com.hazelcast.map.IMap} as the wrapper for user session attributes.
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
public class AttributeValueCompactSerializer implements CompactSerializer<AttributeValue> {
    @Override
    @NonNull
    public AttributeValue read(CompactReader reader) {
        byte[] value = reader.readArrayOfInt8("value");
        byte form = reader.readInt8("dataType");
        AttributeValue.AttributeValueDataType formAsEnum = AttributeValue.AttributeValueDataType.from(form);
        Object finalValue = AttributeValue.convertSerializedValueToObject(value, formAsEnum);
        return new AttributeValue(finalValue, formAsEnum);
    }

    @Override
    public void write(@NonNull CompactWriter writer, @NonNull AttributeValue object) {
        byte[] finalValue = object.convertObjectToValueBytes();
        writer.writeArrayOfInt8("value",  finalValue);
        writer.writeInt8("dataType", (byte) object.dataType().ordinal());
    }

    @Override
    @NonNull
    public String getTypeName() {
        return "AttributeValue";
    }

    @Override
    @NonNull
    public Class<AttributeValue> getCompactClass() {
        return AttributeValue.class;
    }
}
