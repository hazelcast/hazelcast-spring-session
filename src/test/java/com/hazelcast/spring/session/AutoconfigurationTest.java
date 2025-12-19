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
import com.hazelcast.config.IndexConfig;
import com.hazelcast.config.IndexType;
import com.hazelcast.config.MapConfig;
import com.hazelcast.core.HazelcastInstance;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.session.FlushMode;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.hazelcast.spring.session.HazelcastIndexedSessionRepository.PRINCIPAL_NAME_ATTRIBUTE;
import static com.hazelcast.spring.session.HazelcastSessionConfiguration.applySerializationConfig;
import static org.assertj.core.api.Assertions.assertThat;

@ParameterizedClass
@CsvSource(useHeadersInDisplayName = true, textBlock = """
        Client Server
        true
        false
        """)
public class AutoconfigurationTest extends TestWithHazelcast {

    @Parameter(0)
    boolean clientServer;

    @BeforeEach
    void setUp() {
        Config config = getConfig();

        FACTORY.newHazelcastInstance(config);
        FACTORY.newHazelcastInstance(config);
    }

    private @NonNull ClientConfig getClientConfig() {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.setProperty("hazelcast.partition.count", "11");
        return applySerializationConfig(clientConfig);
    }

    private @NonNull Config getConfig() {
        Config config = new Config();
        config.setProperty("hazelcast.partition.count", "11");
        applySerializationConfig(config);
        return config;
    }

    @AfterEach
    void clean() {
        FACTORY.shutdownAll();
    }

    @Test
    void indexIsConfigured() {
        HazelcastInstance hazelcastInstance = clientServer
            ? FACTORY.newHazelcastClient(getClientConfig())
            : FACTORY.newHazelcastInstance(getConfig());

        final String mapName = "SessionMap_indexIsConfigured";
        var repository = new HazelcastIndexedSessionRepository(hazelcastInstance);
        repository.setDeployedOnAllMembers(true);
        repository.setSessionMapName(mapName);
        repository.afterPropertiesSet();

        HazelcastInstance normalMember = clientServer
                ? FACTORY.newHazelcastInstance(getConfig())
                : hazelcastInstance;
        MapConfig mapConfig = normalMember.getConfig().getMapConfigOrNull(mapName);
        assertThat(mapConfig).isNotNull();

        assertThat(mapConfig.getIndexConfigs())
                .hasSize(1)
                .containsExactly(new IndexConfig(IndexType.HASH, PRINCIPAL_NAME_ATTRIBUTE));
    }

    @Test
    void mapPreconfigured() {
        final String mapName = "SessionMap_mapPreconfigured";
        HazelcastInstance hazelcastInstance = clientServer
            ? FACTORY.newHazelcastClient(getClientConfig())
            : FACTORY.newHazelcastInstance(getConfig());
        HazelcastInstance normalMember = clientServer
                ? FACTORY.newHazelcastInstance(getConfig())
                : hazelcastInstance;

        MapConfig preconfiguredConfig = new MapConfig(mapName);
        preconfiguredConfig.setIndexConfigs(List.of(new IndexConfig(IndexType.SORTED, PRINCIPAL_NAME_ATTRIBUTE)));
        preconfiguredConfig.setBackupCount(4);
        normalMember.getConfig().addMapConfig(preconfiguredConfig);

        var repository = new HazelcastIndexedSessionRepository(hazelcastInstance);
        repository.setDeployedOnAllMembers(true);
        repository.setSessionMapName(mapName);
        repository.afterPropertiesSet();

        MapConfig mapConfig = normalMember.getConfig().getMapConfigOrNull(mapName);
        assertThat(mapConfig).isNotNull();

        assertThat(mapConfig).isEqualTo(preconfiguredConfig);
    }

    @Test
    void notConfigurationIfRequested() {
        final String mapName = "notConfigurationIfRequested";
        HazelcastInstance hazelcastInstance = clientServer
            ? FACTORY.newHazelcastClient(getClientConfig())
            : FACTORY.newHazelcastInstance(getConfig());
        HazelcastInstance normalMember = clientServer
                ? FACTORY.newHazelcastInstance(getConfig())
                : hazelcastInstance;

        var repository = new HazelcastIndexedSessionRepository(hazelcastInstance)
                .setDeployedOnAllMembers(true)
                .setSessionMapName(mapName)
                .setFlushMode(FlushMode.IMMEDIATE)
                .setDefaultMaxInactiveInterval(Duration.ofMinutes(1))
                .setIndexResolver(session -> Map.of())
                .disableSessionMapAutoConfiguration();
        repository.afterPropertiesSet();

        MapConfig mapConfig = normalMember.getConfig().getMapConfig(mapName);
        assertThat(mapConfig.getIndexConfigs()).isEmpty();
    }

}
