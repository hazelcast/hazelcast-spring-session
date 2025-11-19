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

import com.hazelcast.config.CompactSerializationConfig;
import com.hazelcast.config.SerializationConfig;
import com.hazelcast.internal.serialization.SerializationService;
import com.hazelcast.internal.serialization.impl.DefaultSerializationServiceBuilder;
import com.hazelcast.internal.serialization.impl.compact.InMemorySchemaService;
import com.hazelcast.internal.serialization.impl.compact.schema.MemberSchemaService;

final class TestUtils {
    private TestUtils() {
    }

    static SerializationService defaultSerializationService() {
        CompactSerializationConfig compactSerializationConfig = new CompactSerializationConfig();
        compactSerializationConfig.addSerializer(new AttributeValueCompactSerializer());
        compactSerializationConfig.addSerializer(new HazelcastSessionCompactSerializer());
        return new DefaultSerializationServiceBuilder()
                .setConfig(new SerializationConfig().setCompactSerializationConfig(compactSerializationConfig))
                .setSchemaService(new InMemorySchemaService())
                .build();
    }
}
