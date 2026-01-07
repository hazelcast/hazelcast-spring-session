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

package com.hazelcast.spring.session.serialization;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.compact.CompactReader;
import com.hazelcast.nio.serialization.compact.CompactWriter;
import com.hazelcast.spi.annotation.PrivateApi;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.time.Instant;

@PrivateApi
public final class InstantSerializer {

    private InstantSerializer() {
    }

    public static void write(ObjectDataOutput out, Instant instant) throws IOException {
        out.writeBoolean(instant == null);
        if (instant != null) {
            out.writeLong(instant.getEpochSecond());
            out.writeInt(instant.getNano());
        }
    }

    public static void write(@NonNull CompactWriter writer, @NonNull String fieldName, @Nullable Instant instant) {
        writer.writeNullableInt64(fieldName + "_seconds", instant == null ? null : instant.getEpochSecond());
        writer.writeNullableInt32(fieldName + "_nanos", instant == null ? null : instant.getNano());
    }

    @Nullable
    public static Instant read(@NonNull ObjectDataInput in) throws IOException {
        boolean isNullable = in.readBoolean();
        if (isNullable) {
            return null;
        }
        long seconds = in.readLong();
        int nanos = in.readInt();
        return Instant.ofEpochSecond(seconds, nanos);
    }

    @Nullable
    public static Instant read(@NonNull CompactReader reader, String fieldName) {
        Long seconds = reader.readNullableInt64(fieldName + "_seconds");
        Integer nanos = reader.readNullableInt32(fieldName + "_nanos");
        if (seconds == null || nanos == null) {
            return null;
        }
        return Instant.ofEpochSecond(seconds, nanos);
    }
}
