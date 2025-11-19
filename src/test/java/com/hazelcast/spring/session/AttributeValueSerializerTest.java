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
import org.example.CustomPojo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AttributeValueSerializerTest {


    private static final TestHazelcastFactory hazelcastFactory = new TestHazelcastFactory();

    @AfterEach
    void cleanup() {
        hazelcastFactory.terminateAll();
    }

    @ParameterizedTest
    @MethodSource("attributes")
    void handleAttributeVariousTypes(AttributeValue attributeValue) {

        SerializationService serializationService = getSerializationService();

        Data data = serializationService.toData(attributeValue);
        Object object = serializationService.toObject(data);

        assertThat(object)
                .isNotNull()
                .isInstanceOf(AttributeValue.class)
                .isEqualTo(attributeValue);
    }

    private static SerializationService getSerializationService() {
        var conf = new Config();
        conf.getSerializationConfig().getCompactSerializationConfig()
            .addSerializer(new HazelcastSessionCompactSerializer())
            .addSerializer(new AttributeValueCompactSerializer());

        var hz = hazelcastFactory.newHazelcastInstance(conf);
        return ((SerializationServiceSupport) hz).getSerializationService();
    }

    static List<AttributeValue> attributes() {
        return List.of(
                AttributeValue.string("value1"),
                new AttributeValue(1, AttributeValue.AttributeValueDataType.INTEGER),
                new AttributeValue(1L, AttributeValue.AttributeValueDataType.LONG),
                AttributeValue.data(getSerializationService().toData(new CustomPojo(1, "Luke")).toByteArray())
                      );
    }
}
