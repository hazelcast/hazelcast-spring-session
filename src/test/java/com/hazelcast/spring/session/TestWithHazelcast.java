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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

abstract class TestWithHazelcast {

    protected static final TestHazelcastFactory FACTORY = new TestHazelcastFactory();

    @BeforeAll
    static void beforeAll() {
        var conf = new Config();
        conf.setProperty("hazelcast.partition.count", "11");
        conf.getSerializationConfig().getCompactSerializationConfig()
            .addSerializer(new HazelcastSessionCompactSerializer())
            .addSerializer(new AttributeValueCompactSerializer());
    }

    @AfterAll
    static void cleanup() {
        FACTORY.terminateAll();
    }
}
