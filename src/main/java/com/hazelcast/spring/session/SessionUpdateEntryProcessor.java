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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hazelcast.spring.session.HazelcastIndexedSessionRepository.PRINCIPAL_NAME_ATTRIBUTE;
import static org.springframework.session.FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME;

/**
 * Hazelcast {@link EntryProcessor} responsible for handling updates to session.
 *
 * @author Vedran Pavic
 * @author Eleftheria Stein
 * @since 1.3.4
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class SessionUpdateEntryProcessor implements EntryProcessor {
    Instant lastAccessedTime;

    Duration maxInactiveInterval;

    /**
     * Mapping of {@code attribute name} -> {@code serialized data} to be added, modified or removed.
     */
    Map<String, byte[]> delta;

    String principalName;

    public SessionUpdateEntryProcessor() {
    }

    SessionUpdateEntryProcessor(Instant lastAccessedTime, Duration maxInactiveInterval, Map<String, byte[]> delta, String principalName) {
        this.lastAccessedTime = lastAccessedTime;
        this.maxInactiveInterval = maxInactiveInterval;
        this.delta = delta;
        this.principalName = principalName;
    }

    SessionUpdateEntryProcessor(HazelcastIndexedSessionRepository.HazelcastSession session) {
        if (session.lastAccessedTimeChanged) {
            setLastAccessedTime(session.getLastAccessedTime());
        }
        if (session.maxInactiveIntervalChanged) {
            setMaxInactiveInterval(session.getMaxInactiveInterval());
        }
        if (!session.delta.isEmpty()) {
            delta = new HashMap<>(session.delta.size());
            session.delta.forEach((k, v) -> delta.put(k, v == null ? null : v.objectBytes()));
        }
        if (session.principalNameChanged()) {
            this.principalName = session.getDelegate().getPrincipalName();
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

            for (final Map.Entry<String, byte[]> attribute : this.delta.entrySet()) {
                byte[] value = attribute.getValue();
                if (attribute.getValue() != null) {
                    addValue(value, attribute.getKey(), attributeNames, attributeValues);

                    if (attribute.getKey().equals(PRINCIPAL_NAME_ATTRIBUTE) || attribute.getKey().equals(PRINCIPAL_NAME_INDEX_NAME)) {
                        addValue(value, PRINCIPAL_NAME_ATTRIBUTE, attributeNames, attributeValues);
                        addValue(value, PRINCIPAL_NAME_INDEX_NAME, attributeNames, attributeValues);

                        builder.setString("principalName", principalName);
                    }
                } else {
                    int index = findIndex(attribute.getKey(), attributeNames);
                    if (index != -1) {
                        attributeNames.remove(index);
                        attributeValues.remove(index);
                    }
                }
            }
            builder.setArrayOfString("attributeNames", attributeNames.toArray(new String[0]));
            builder.setArrayOfGenericRecord("attributeValues", attributeValues.toArray(new GenericRecord[0]));
        }

        ((ExtendedMapEntry) entry).setValue(builder.build(), ttl, TimeUnit.SECONDS);
        return Boolean.TRUE;
    }

    private void addValue(byte[] valueBytes,
                          String attributeName,
                          List<String> attributeNames,
                          List<GenericRecord> attributeValues) {
        int index = findIndex(attributeName, attributeNames);
        if (index != -1) {
            attributeValues.set(index, AttributeValue.serializedGenericRecord(valueBytes));
        } else {
            attributeNames.add(attributeName);
            attributeValues.add(AttributeValue.serializedGenericRecord(valueBytes));
        }
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
        return attributeNames.indexOf(key);
    }

    void processMapSession(BackingMapSession value) {
        if (this.lastAccessedTime != null) {
            value.setLastAccessedTime(this.lastAccessedTime);
        }
        if (this.maxInactiveInterval != null) {
            value.setMaxInactiveInterval(this.maxInactiveInterval);
        }
        if (this.delta != null) {
            for (final Map.Entry<String, byte[]> attribute : this.delta.entrySet()) {
                if (attribute.getValue() != null) {
                    value.setSerializedAttribute(attribute.getKey(), AttributeValue.serialized(attribute.getValue()));
                } else {
                    value.removeAttribute(attribute.getKey());
                }
            }
        }
        if (principalName != null) {
            value.setPrincipalName(principalName);
        }
    }

    void setLastAccessedTime(Instant lastAccessedTime) {
        this.lastAccessedTime = lastAccessedTime;
    }

    void setMaxInactiveInterval(Duration maxInactiveInterval) {
        this.maxInactiveInterval = maxInactiveInterval;
    }

    void setDelta(Map<String, AttributeValue> delta) {
        Map<String, byte[]> onlyByte = new HashMap<>(delta.size());
        for (Map.Entry<String, AttributeValue> entry : delta.entrySet()) {
            AttributeValue value = entry.getValue();
            onlyByte.put(entry.getKey(), value == null ? null : value.objectBytes());
        }
        this.delta = onlyByte;
    }
}
