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
import com.hazelcast.nio.serialization.Serializer;
import com.hazelcast.nio.serialization.SerializerHook;
import com.hazelcast.nio.serialization.StreamSerializer;
import com.hazelcast.nio.serialization.compact.CompactReader;
import com.hazelcast.nio.serialization.compact.CompactSerializer;
import com.hazelcast.nio.serialization.compact.CompactWriter;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.time.Instant;

public class InstantSerializer implements CompactSerializer<Instant>, StreamSerializer<Instant> {

    public static final String HZ_SPRING_SESSION_INSTANT = "HzSpringSessionInstant";

    @Override
    public void write(ObjectDataOutput out, Instant instant) throws IOException {
        out.writeLong(instant.getEpochSecond());
        out.writeInt(instant.getNano());
    }

    @Override
    public void write(@NonNull CompactWriter writer, @NonNull Instant instant) {
        writer.writeInt64("seconds", instant.getEpochSecond());
        writer.writeInt32("nanos", instant.getNano());
    }

    @Override @NonNull
    public Instant read(@NonNull ObjectDataInput in) throws IOException {
        long seconds = in.readLong();
        int nanos = in.readInt();
        return Instant.ofEpochSecond(seconds, nanos);
    }

    @Override
    @NonNull
    public Instant read(@NonNull CompactReader reader) {
        long seconds = reader.readInt64("seconds");
        int nanos = reader.readInt32("nanos");
        return Instant.ofEpochSecond(seconds, nanos);
    }

    @Override
    public int getTypeId() {
        return -3002;
    }

    @Override
    @NonNull
    public String getTypeName() {
        return HZ_SPRING_SESSION_INSTANT;
    }

    @Override
    @NonNull
    public Class<Instant> getCompactClass() {
        return Instant.class;
    }

    public static class InstantSerializerHook implements SerializerHook<Instant> {

        @Override
        public Class<Instant> getSerializationType() {
            return Instant.class;
        }

        @Override
        public boolean isOverwritable() {
            return false;
        }

        @Override
        public Serializer createSerializer() {
            return new InstantSerializer();
        }
    }
}
