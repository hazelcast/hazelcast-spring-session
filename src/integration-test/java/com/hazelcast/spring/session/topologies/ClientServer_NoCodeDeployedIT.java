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

package com.hazelcast.spring.session.topologies;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.IndexConfig;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.instance.impl.HazelcastInstanceFactory;
import com.hazelcast.spring.session.AbstractHazelcastIndexedSessionRepositoryIT;
import com.hazelcast.spring.session.HazelcastIndexedSessionRepository;
import com.hazelcast.spring.session.config.annotation.SpringSessionHazelcastInstance;
import com.hazelcast.spring.session.config.annotation.web.http.EnableHazelcastHttpSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.FlushMode;
import org.springframework.session.SaveMode;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.MountableFile;

import static com.hazelcast.config.IndexType.HASH;
import static com.hazelcast.spring.session.BuildContext.HAZELCAST_DOCKER_VERSION;
import static com.hazelcast.spring.session.HazelcastIndexedSessionRepository.DEFAULT_SESSION_MAP_NAME;
import static com.hazelcast.spring.session.HazelcastIndexedSessionRepository.PRINCIPAL_NAME_ATTRIBUTE;
import static com.hazelcast.spring.session.HazelcastSessionConfiguration.applySerializationConfig;
import static com.hazelcast.test.HazelcastTestSupport.assertTrueEventually;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link HazelcastIndexedSessionRepository} using client-server
 * topology with no code deployed on server side.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ClientServer_NoCodeDeployedIT.HazelcastSessionConfig.class)
@SuppressWarnings("resource")
class ClientServer_NoCodeDeployedIT extends AbstractHazelcastIndexedSessionRepositoryIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientServer_NoCodeDeployedIT.class);
	private static GenericContainer<?> container;

	@BeforeAll
	static void setUpClass() {
        container = new GenericContainer<>(HAZELCAST_DOCKER_VERSION)
                .withExposedPorts(5701)
                .withCopyFileToContainer(MountableFile.forClasspathResource("/hazelcast-server-plain.xml"),
                                         "/opt/hazelcast/hazelcast.xml")
                .withCopyFileToContainer(MountableFile.forClasspathResource("/log4j2.xml"),
                                         "/opt/hazelcast/config/log4j2.xml")
                .withEnv("HAZELCAST_CONFIG", "hazelcast.xml")
                .withEnv("LOGGING_CONFIG", "/opt/hazelcast/config/log4j2.xml")
                .withLogConsumer(new Slf4jLogConsumer(LOGGER).withPrefix("hz>"));
		container.start();
	}

    @Test
    void hasIndexConfigured() {
        // given
        Config config = new Config();
        NetworkConfig network = config.getNetworkConfig().setPort(0);
        network.getJoin().getAutoDetectionConfig().setEnabled(false);
        network.getJoin().getTcpIpConfig()
               .setEnabled(true)
               .addMember(container.getHost() + ":" + container.getFirstMappedPort());
        var hz = HazelcastInstanceFactory.newHazelcastInstance(applySerializationConfig(config));

        // when
        try {
            MapConfig mapConfig = hz.getConfig().getMapConfig(DEFAULT_SESSION_MAP_NAME);

            // then
            assertTrueEventually(() -> assertThat(mapConfig.getIndexConfigs()).hasSize(1));

            IndexConfig idxConf = mapConfig.getIndexConfigs().get(0);
            assertThat(idxConf.getType()).isEqualTo(HASH);
            assertThat(idxConf.getAttributes()).containsExactly(PRINCIPAL_NAME_ATTRIBUTE);
        } finally {
            hz.shutdown();
        }
    }

	@AfterAll
	static void tearDownClass() {
		container.stop();
	}

    @Configuration(proxyBeanMethods = false)
	@EnableHazelcastHttpSession
    @WebAppConfiguration
	static class HazelcastSessionConfig {
		@Bean @SpringSessionHazelcastInstance
        HazelcastInstance hazelcastInstance() {
            ClientConfig clientConfig = new ClientConfig();
            clientConfig.getNetworkConfig().addAddress(container.getHost() + ":" + container.getFirstMappedPort());
            return HazelcastClient.newHazelcastClient(applySerializationConfig(clientConfig));
        }

        @Bean
        public SessionRepositoryCustomizer<HazelcastIndexedSessionRepository> customizeSessionRepo() {
            return (sessionRepository) -> {
                sessionRepository.setFlushMode(FlushMode.IMMEDIATE);
                sessionRepository.setSaveMode(SaveMode.ALWAYS);
                sessionRepository.setDeployedOnAllMembers(false);
            };
        }
    }
}
