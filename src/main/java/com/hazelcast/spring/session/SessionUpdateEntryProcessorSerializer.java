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

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.Serializer;
import com.hazelcast.nio.serialization.StreamSerializer;
import com.hazelcast.nio.serialization.genericrecord.GenericRecord;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Serializer used to serialize {@link SessionUpdateEntryProcessor}.
 * Since the processor will be used only when the Hazelcast Spring Session's code is deployed on server, we can safely use
 * {@link StreamSerializer} plus {@link com.hazelcast.nio.serialization.SerializerHook}.
 */
public class SessionUpdateEntryProcessorSerializer implements StreamSerializer<SessionUpdateEntryProcessor> {

    public void write(@NonNull ObjectDataOutput out, @NonNull SessionUpdateEntryProcessor object) throws IOException {
        writeInstant(out, object.lastAccessedTime);
        writeDuration(out, object.maxInactiveInterval);

        if (object.delta == null) {
             out.writeInt(-1);
        } else {
            // this part will be done on client side, so we don't need to worry that the value may be a GenericRecord
            Set<Map.Entry<String, AttributeValue>> entries = object.delta.entrySet();
            out.writeInt(entries.size());

            for (Map.Entry<String, AttributeValue> entry : entries) {
                out.writeString(entry.getKey());
                out.writeObject(entry.getValue());
            }
        }
    }

    @Override
    @NonNull
    public SessionUpdateEntryProcessor read(@NonNull ObjectDataInput in) throws IOException {
        Instant lastAccessedTime = readInstant(in);
        Duration maxInactiveInterval = readDuration(in);

        int count = in.readInt();
        HashMap<String, AttributeValue> map = null;
        if (count != -1) {
            map = new HashMap<>(count);
            for (int i = 0; i < count; i++) {
                String key = in.readString();
                AttributeValue value = objectAsValue(in.readObject());

                map.put(key, value);
            }
        }
        return new SessionUpdateEntryProcessor(lastAccessedTime, maxInactiveInterval, map);
    }

    private AttributeValue objectAsValue(Object o) {
        if (o instanceof AttributeValue av) {
            return  av;
        }
        // client-server with no serializer configured on server side - in this case object in the map will be deserialized
        // as GenericRecord, schema will be taken from clients who distribute it automatically.
        if (o instanceof GenericRecord gr) {
            var dataType = AttributeValue.AttributeValueDataType.from(gr.getInt8("dataType"));
            byte[] values = gr.getArrayOfInt8("value");
            Object valueObject = AttributeValue.convertSerializedValueToObject(values, dataType);
            return new AttributeValue(valueObject, dataType);
        }
        return null;
    }

    @Override
    public int getTypeId() {
        return 1337;
    }
    
    private void writeInstant(ObjectDataOutput writer, Instant instant) throws IOException {
        if (instant == null) {
            writer.writeLong(-1);
            return;
        }
        writer.writeLong(instant.getEpochSecond());
        writer.writeInt(instant.getNano());
    }

    private void writeDuration(ObjectDataOutput writer, Duration duration) throws IOException {
        if (duration == null) {
            writer.writeLong(-1);
            return;
        }
        writer.writeLong(duration.getSeconds());
        writer.writeInt(duration.getNano());
    }

    private Instant readInstant(ObjectDataInput reader) throws IOException {
        long seconds = reader.readLong();
        if (seconds < 0L) {
            return null;
        }
        int nanos = reader.readInt();
        return Instant.ofEpochSecond(seconds, nanos);
    }

    private Duration readDuration(ObjectDataInput reader) throws IOException {
        long seconds = reader.readLong();
        if (seconds < 0L) {
            return null;
        }
        int nanos = reader.readInt();
        return Duration.ofSeconds(seconds, nanos);
    }

    public static class HzSSSerializerHook implements com.hazelcast.nio.serialization.SerializerHook<SessionUpdateEntryProcessor> {

        @Override
        public Class<SessionUpdateEntryProcessor> getSerializationType() {
            return SessionUpdateEntryProcessor.class;
        }

        @Override
        public Serializer createSerializer() {
            return new SessionUpdateEntryProcessorSerializer();
        }

        @Override
        public boolean isOverwritable() {
            return true;
        }
    }
}
