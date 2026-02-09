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
import com.hazelcast.config.Config;
import org.jspecify.annotations.NonNull;

/**
 * Utility methods used to make configuration much easier.
 * @since 4.0.0
 */
public final class HazelcastSessionConfiguration {

    /**
     * Applies required serialization configuration,
     * adds {@link AttributeValueCompactSerializer} and {@link HazelcastSessionCompactSerializer}
     * to {@link com.hazelcast.config.CompactSerializationConfig}.
     *
     * @return config provided by user (for fluent API)
     */
    @NonNull
    public static ClientConfig applySerializationConfig(@NonNull ClientConfig clientConfig) {
        clientConfig.getSerializationConfig().getCompactSerializationConfig()
                    .addSerializer(AttributeValueCompactSerializer.INSTANCE)
                    .addSerializer(HazelcastSessionCompactSerializer.INSTANCE);
        return clientConfig;
    }

    /**
     * Applies required serialization configuration,
     * adds {@link AttributeValueCompactSerializer} and {@link HazelcastSessionCompactSerializer}
     * to {@link com.hazelcast.config.CompactSerializationConfig}.
     *
     * @return config provided by user (for fluent API)
     */
    @NonNull
    public static Config applySerializationConfig(@NonNull Config instanceConfig) {
        instanceConfig.getSerializationConfig().getCompactSerializationConfig()
                      .addSerializer(AttributeValueCompactSerializer.INSTANCE)
                      .addSerializer(HazelcastSessionCompactSerializer.INSTANCE);
        return instanceConfig;
    }
}
