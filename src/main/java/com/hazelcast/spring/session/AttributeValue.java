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

import com.hazelcast.internal.serialization.Data;
import com.hazelcast.internal.serialization.SerializationService;
import com.hazelcast.internal.serialization.impl.HeapData;
import com.hazelcast.nio.serialization.genericrecord.GenericRecord;
import com.hazelcast.nio.serialization.genericrecord.GenericRecordBuilder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

/**
 * Class used by {@link BackingMapSession} to store attribute values with its corresponding type.
 * <p>
 * In client-server architecture we don't want to hold users' Java objects as pure, deserialized objects to avoid the need
 * to upload user code to server. Therefore, most of the user objects will be first serialized to
 * {@link Data} and kept in {@link AttributeValue} as a {@link Data#toByteArray() byte array representation}.
 * <p>
 * For the speed we are caching the in-memory, deserialized object representation.
 *
 * @since 4.0.0
 */
final class AttributeValue {
    private transient Object object;
    private byte[] objectBytes;

    public static AttributeValue serialized(byte[] value) {
        if (value == null) {
            return null;
        }
        AttributeValue attributeValue = new AttributeValue();
        attributeValue.objectBytes = value;
        return attributeValue;
    }

    public static GenericRecord serializedGenericRecord(byte[] value) {
        if (value == null) {
            return null;
        }
        var builder = GenericRecordBuilder.compact("AttributeValue");
        builder.setArrayOfInt8("objectBytes", value);
        return builder.build();
    }

    public static AttributeValue deserialized(Object value) {
        if (value == null) {
            return null;
        }
        AttributeValue attributeValue = new AttributeValue();
        attributeValue.object = value;
        return attributeValue;
    }

    void deserialize(SerializationService serializationService) {
        if (object == null) {
            object = serializationService.toObject(new HeapData(objectBytes));
        }
    }

    @Nullable
    static AttributeValue string(Object value) {
        return value == null ? null : AttributeValue.deserialized(value);
    }

    @Nullable
    static AttributeValue data(byte[] value) {
        return value == null ? null : AttributeValue.deserialized(value);
    }

    public Object object() {
        return object;
    }

    AttributeValue serialize(SerializationService serializationService) {
        if (objectBytes == null) {
            objectBytes = serializationService.toData(object).toByteArray();
        }
        return this;
    }
    public byte[] objectBytes() {
        return objectBytes;
    }

    @Override
    public String toString() {
        return "AttributeValue["
                + "object=" + object
                + "objectBytes=" + Arrays.toString(objectBytes)
                + ']';
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AttributeValue that)) {
            return false;
        }
        return Objects.equals(object, that.object);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(object);
    }

    public void assertDeserialized() {
        if (object == null) {
            throw new IllegalStateException("Object is null, should have been deserialized before");
        }
    }
}
