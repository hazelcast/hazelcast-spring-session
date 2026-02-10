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

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.CompactSerializationConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.SerializationConfig;
import com.hazelcast.internal.serialization.SerializationService;
import com.hazelcast.internal.serialization.impl.DefaultSerializationServiceBuilder;
import org.jspecify.annotations.NonNull;

import static com.hazelcast.spring.session.HazelcastSessionConfiguration.applySerializationConfig;
import com.hazelcast.internal.serialization.impl.compact.Schema;
import com.hazelcast.internal.serialization.impl.compact.SchemaService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class TestUtils {
    private TestUtils() {
    }

    static SerializationService defaultSerializationService() {
        CompactSerializationConfig compactSerializationConfig = new CompactSerializationConfig();
        compactSerializationConfig.addSerializer(AttributeValueCompactSerializer.INSTANCE);
        compactSerializationConfig.addSerializer(HazelcastSessionCompactSerializer.INSTANCE);
        return new DefaultSerializationServiceBuilder()
                .setConfig(new SerializationConfig().setCompactSerializationConfig(compactSerializationConfig))
                .setSchemaService(new InMemorySchemaService())
                .build();
    }

    @NonNull
    static Config getConfig() {
        Config config = getConfigWithoutSerialization();
        applySerializationConfig(config);
        return config;
    }

    @NonNull
    static Config getConfigWithoutSerialization() {
        Config config = new Config();
        // reduce number of partitions for faster startup
        config.setProperty("hazelcast.partition.count", "11");
        return config;
    }

    @NonNull
    static ClientConfig getClientConfig() {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.setProperty("hazelcast.partition.count", "11");
        return applySerializationConfig(clientConfig);
    }

    /**
     * Equivalent to class in Hazelcast core, but it's not available in 5.4, so we need to copy for now.
     */
    public static class InMemorySchemaService implements SchemaService {
        private final Map<Long, Schema> schemas = new ConcurrentHashMap<>();

        public InMemorySchemaService() {
        }

        @Override
        public Schema get(long schemaId) {
            return schemas.get(schemaId);
        }

        @Override
        public void put(Schema schema) {
            long schemaId = schema.getSchemaId();
            Schema existingSchema = schemas.putIfAbsent(schemaId, schema);
            if (existingSchema != null && !schema.equals(existingSchema)) {
                throw new IllegalStateException("Schema with schemaId " + schemaId + " already exists. "
                                                        + "existing schema " + existingSchema
                                                        + "new schema " + schema);
            }
        }

        @Override
        public void putLocal(Schema schema) {
            put(schema);
        }
    }

}
