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

import com.hazelcast.nio.serialization.genericrecord.GenericRecord;
import com.hazelcast.nio.serialization.genericrecord.GenericRecordBuilder;

import java.io.Serial;
import java.io.Serializable;

/**
 * Class used by {@link BackingMapSession} to store attribute values with its corresponding type.
 * <p>
 * In client-server architecture we don't want to hold users' Java objects to avoid the need to upload user code to server.
 * However, for speed and simplicity, few types are stored as-is: String, Integer, Long.
 */
record AttributeValue(Object object, AttributeValueDataType dataType) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    static GenericRecord toGenericRecord(Object value) {
        if (value instanceof AttributeValue av) {
            var builder = GenericRecordBuilder.compact("AttributeValue");
            builder.setString("value", (String) av.object());
            builder.setInt8("dataType", (byte) av.dataType.ordinal());
            return builder.build();
        }
        return null;
    }

    static AttributeValue string(Object value) {
        return value == null ? null : new AttributeValue(value, AttributeValueDataType.STRING);
    }

    static AttributeValue data(Object value) {
        return value == null ? null : new AttributeValue(value, AttributeValueDataType.DATA);
    }

    enum AttributeValueDataType implements Serializable {
        STRING,
        INTEGER,
        LONG,
        DATA;

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
}
