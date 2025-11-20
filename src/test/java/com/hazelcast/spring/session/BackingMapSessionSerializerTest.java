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

import com.hazelcast.client.test.TestHazelcastFactory;
import com.hazelcast.config.Config;
import com.hazelcast.internal.serialization.Data;
import com.hazelcast.internal.serialization.SerializationService;
import com.hazelcast.spi.impl.SerializationServiceSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

public class BackingMapSessionSerializerTest {

    private static final TestHazelcastFactory FACTORY = new TestHazelcastFactory();
    private static SerializationService serializationService;

    @BeforeAll
    static void beforeAll() {
        var conf = new Config();
        conf.getSerializationConfig().getCompactSerializationConfig()
            .addSerializer(new HazelcastSessionCompactSerializer())
            .addSerializer(new AttributeValueCompactSerializer());

        var hz = FACTORY.newHazelcastInstance(conf);
        serializationService = ((SerializationServiceSupport) hz).getSerializationService();
    }

    @AfterAll
    static void cleanup() {
        FACTORY.terminateAll();
    }

    @Test
    void basicSerialization() {
        // given
        BackingMapSession backingMapSession = new BackingMapSession("originalId");
        backingMapSession.setId("test");
        backingMapSession.setCreationTime(Instant.now());
        backingMapSession.setLastAccessedTime(Instant.now());
        backingMapSession.setMaxInactiveInterval(Duration.ofDays(1));
        backingMapSession.setPrincipalName("principal");

        // when
        Data serialized = serializationService.toData(backingMapSession);
        BackingMapSession deserializedBackingMapSession = serializationService.toObject(serialized);

        // then
        assertThat(deserializedBackingMapSession).isEqualTo(backingMapSession);
    }
}
