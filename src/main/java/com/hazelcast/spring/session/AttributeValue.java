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

import com.hazelcast.internal.serialization.SerializationService;
import com.hazelcast.internal.serialization.impl.HeapData;
import com.hazelcast.nio.serialization.genericrecord.GenericRecord;
import com.hazelcast.nio.serialization.genericrecord.GenericRecordBuilder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

/**
 * Class used by {@link BackingMapSession} to store attribute values with its corresponding type.
 * <p>
 * In client-server architecture we don't want to hold users' Java objects to avoid the need to upload user code to server.
 * However, for speed and simplicity, few types are stored as-is: String, Integer, Long.
 *
 * @param object actual value of the attribute. String, Integer, Long or any other data type serialized as {@code byte[]}.
 *
 * @since 4.0.0
 */
record AttributeValue(@NonNull Object object, @NonNull AttributeValueDataType dataType) {
    @NonNull
    static GenericRecord toGenericRecord(@NonNull Object value) {
        if (value instanceof AttributeValue av) {
            var builder = GenericRecordBuilder.compact("AttributeValue");
            builder.setArrayOfInt8("value", av.convertObjectToValueBytes());
            builder.setInt8("dataType", (byte) av.dataType.ordinal());
            return builder.build();
        }
        throw new IllegalArgumentException(value.getClass().getName());
    }

    @Nullable
    public static AttributeValue toAttributeValue(@Nullable Object rawValue, @NonNull SerializationService serializationService) {
        if (rawValue == null) {
            return null;
        } else if (rawValue instanceof String s) {
            return new AttributeValue(s, AttributeValue.AttributeValueDataType.STRING);
        } else if (rawValue instanceof Integer s) {
            return new AttributeValue(s, AttributeValue.AttributeValueDataType.INTEGER);
        } else if (rawValue instanceof Long s) {
            return new AttributeValue(s, AttributeValue.AttributeValueDataType.LONG);
        } else {
            byte[] serializedValue = serializationService.toData(rawValue).toByteArray();
            return new AttributeValue(serializedValue, AttributeValue.AttributeValueDataType.DATA);
        }
    }

    static Object convertSerializedValueToObject(byte[] value, AttributeValueDataType formAsEnum) {
        return switch (formAsEnum) {
            case STRING -> new String(value);
            case DATA -> value;
            case LONG ->  Long.parseLong(new String(value));
            case INTEGER ->  Integer.parseInt(new String(value));
        };
    }

    byte[] convertObjectToValueBytes() {
        return switch (dataType) {
            case STRING -> ((String) object).getBytes();
            case DATA -> (byte[]) object;
            case LONG, INTEGER ->  object.toString().getBytes();
        };
    }

    @NonNull
    Object getDeserializedValue(SerializationService serializationService) {
        return switch (dataType()) {
            case DATA -> serializationService.toObject(new HeapData((byte[]) object()));
            case STRING, LONG, INTEGER -> object();
        };
    }

    @Nullable
    static AttributeValue string(Object value) {
        return value == null ? null : new AttributeValue(value, AttributeValueDataType.STRING);
    }

    @Nullable
    static AttributeValue data(byte[] value) {
        return value == null ? null : new AttributeValue(value, AttributeValueDataType.DATA);
    }

    enum AttributeValueDataType implements Serializable {
        STRING,
        INTEGER,
        LONG,
        DATA;

        /**
         * Gets {@link AttributeValueDataType} of given ordinal.
         * <p>
         * Uses switch instead of {@link AttributeValueDataType#values()} for better performance - calling {@code values()}
         * create new array each time, making a pressure on GC.
         */
        static AttributeValueDataType from(byte ord) {
            return switch(ord) {
                case (byte) 0 -> STRING;
                case (byte) 1 -> INTEGER;
                case (byte) 2 -> LONG;
                case (byte) 3 -> DATA;
                default -> throw new IllegalArgumentException();
            };
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AttributeValue that)) {
            return false;
        }
        if (dataType != that.dataType) {
            return false;
        }
        if (dataType == AttributeValue.AttributeValueDataType.DATA) {
            return Arrays.equals((byte[]) object, (byte[]) that.object);
        }
        return Objects.equals(object, that.object);
    }

    @Override
    public int hashCode() {
        int objectHash = object instanceof byte[] ? Arrays.hashCode((byte[]) object) : object.hashCode();
        return Objects.hash(objectHash, dataType);
    }
}
