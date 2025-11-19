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

import com.hazelcast.map.EntryProcessor;
import com.hazelcast.map.ExtendedMapEntry;
import com.hazelcast.nio.serialization.genericrecord.GenericRecord;
import com.hazelcast.nio.serialization.genericrecord.GenericRecordBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Hazelcast {@link EntryProcessor} responsible for handling updates to session.
 *
 * @author Vedran Pavic
 * @author Eleftheria Stein
 * @since 1.3.4
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class SessionUpdateEntryProcessor implements EntryProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionUpdateEntryProcessor.class);

    private Instant lastAccessedTime;

    private Duration maxInactiveInterval;

    private Map<String, AttributeValue> delta;

    public SessionUpdateEntryProcessor() {
    }

    public SessionUpdateEntryProcessor(HazelcastIndexedSessionRepository.HazelcastSession session) {
        if (session.lastAccessedTimeChanged) {
            setLastAccessedTime(session.getLastAccessedTime());
        }
        if (session.maxInactiveIntervalChanged) {
            setMaxInactiveInterval(session.getMaxInactiveInterval());
        }
        if (!session.delta.isEmpty()) {
            setDelta(new HashMap<>(session.delta));
        }
    }

    @Override
    public Object process(Map.Entry entry) {
        if (entry.getValue() instanceof GenericRecord gr) {
            // case where the schema of the object was registered by a client, but server does not have CompactSerializer
            // instances registered. In such cases, object will be represented as GenericRecord
            return processGenericRecord(entry, gr);
        }
        BackingMapSession value = (BackingMapSession) entry.getValue();
        if (value == null) {
            return Boolean.FALSE;
        }
        processMapSession(value);
        var extendedEntry = (ExtendedMapEntry<String, BackingMapSession>) entry;
        extendedEntry.setValue(value, value.getMaxInactiveInterval().getSeconds(), TimeUnit.SECONDS);
        return Boolean.TRUE;
    }

    private Boolean processGenericRecord(Map.Entry entry, GenericRecord gr) {
        GenericRecordBuilder builder = gr.newBuilderWithClone();
        long ttl = gr.getInt64("maxInactiveInterval_seconds");
        if (this.lastAccessedTime != null) {
            builder.setInt64("lastAccessedTime_seconds", this.lastAccessedTime.getEpochSecond());
            builder.setInt32("lastAccessedTime_nanos", this.lastAccessedTime.getNano());
        }
        if (this.maxInactiveInterval != null) {
            ttl = this.maxInactiveInterval.getSeconds();
            builder.setInt64("maxInactiveInterval_seconds", this.maxInactiveInterval.getSeconds());
            builder.setInt32("maxInactiveInterval_nanos", this.maxInactiveInterval.getNano());
        }
        if (this.delta != null) {
            List<String> attributeNames = toList(gr.getArrayOfString("attributeNames"));
            List<GenericRecord> attributeValues = toList(gr.getArrayOfGenericRecord("attributeValues"));

            for (final Map.Entry<String, AttributeValue> attribute : this.delta.entrySet()) {
                int index = findIndex(attribute.getKey(), attributeNames);
                if (attribute.getValue() != null) {
                    GenericRecord valueAsRecord = AttributeValue.toGenericRecord(attribute.getValue());
                    if (index != -1) {
                        attributeValues.set(index, valueAsRecord);
                    } else {
                        attributeNames.add(attribute.getKey());
                        attributeValues.add(valueAsRecord);
                    }
                } else if (index != -1) {
                    attributeNames.remove(index);
                    attributeValues.remove(index);
                }
            }
            builder.setArrayOfString("attributeNames", attributeNames.toArray(new String[0]));
            builder.setArrayOfGenericRecord("attributeValues", attributeValues.toArray(new GenericRecord[0]));
        }

        ((ExtendedMapEntry) entry).setValue(builder.build(), ttl, TimeUnit.SECONDS);
        return Boolean.TRUE;
    }

    private <T> List<T> toList(T[] array) {
        if (array == null) {
            return new ArrayList<>();
        }
        ArrayList<T> list = new ArrayList<>(array.length);
        list.addAll(Arrays.asList(array));
        return list;
    }

    private int findIndex(String key, List<String> attributeNames) {
        for (int i = 0; i < attributeNames.size(); i++) {
            if (attributeNames.get(i).equals(key)) {
                return i;
            }
        }
        return -1;
    }

    void processMapSession(BackingMapSession value) {
        if (this.lastAccessedTime != null) {
            value.setLastAccessedTime(this.lastAccessedTime);
        }
        if (this.maxInactiveInterval != null) {
            value.setMaxInactiveInterval(this.maxInactiveInterval);
        }
        if (this.delta != null) {
            for (final Map.Entry<String, AttributeValue> attribute : this.delta.entrySet()) {
                if (attribute.getValue() != null) {
                    value.setAttribute(attribute.getKey(), attribute.getValue());
                } else {
                    value.removeAttribute(attribute.getKey());
                }
            }
        }
    }

    void setLastAccessedTime(Instant lastAccessedTime) {
        this.lastAccessedTime = lastAccessedTime;
    }

    void setMaxInactiveInterval(Duration maxInactiveInterval) {
        this.maxInactiveInterval = maxInactiveInterval;
    }

    void setDelta(Map<String, AttributeValue> delta) {
        this.delta = delta;
    }
}
