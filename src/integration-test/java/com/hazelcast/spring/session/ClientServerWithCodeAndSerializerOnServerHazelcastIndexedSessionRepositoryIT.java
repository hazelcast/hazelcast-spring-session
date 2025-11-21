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

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.spring.session.config.annotation.SpringSessionHazelcastInstance;
import com.hazelcast.spring.session.config.annotation.web.http.EnableHazelcastHttpSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Integration tests for {@link HazelcastIndexedSessionRepository} using client-server
 * topology with code deployed on server node and serializers configured there.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ClientServerWithCodeAndSerializerOnServerHazelcastIndexedSessionRepositoryIT.HazelcastSessionConfig.class)
@SuppressWarnings("resource")
class ClientServerWithCodeAndSerializerOnServerHazelcastIndexedSessionRepositoryIT
        extends AbstractHazelcastIndexedSessionRepositoryIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            ClientServerWithCodeAndSerializerOnServerHazelcastIndexedSessionRepositoryIT.class);
	private static GenericContainer<?> container;

	@BeforeAll
	static void setUpClass() throws IOException {
        var jarResource = ClientServerWithCodeAndSerializerOnServerHazelcastIndexedSessionRepositoryIT.class.getResource("../../../../HSS.jar");
        assert jarResource != null;
        var path = new File(jarResource.getFile()).getParentFile();

        container = new GenericContainer<>(DockerImageName.parse("hazelcast/hazelcast:5.6.0-slim"))
                .withExposedPorts(5701)
                .withCopyFileToContainer(MountableFile.forClasspathResource("/hazelcast-server-with-serializer.xml"),
                                         "/opt/hazelcast/hazelcast.xml")
                .withEnv("HAZELCAST_CONFIG", "hazelcast.xml")
                .withLogConsumer(new Slf4jLogConsumer(LOGGER).withPrefix("hz>"));
        Files.list(path.toPath()).forEach(file ->  container.withCopyFileToContainer(
                MountableFile.forHostPath(file),
                "/opt/hazelcast/lib/" + file.getFileName().toString()));
		container.start();
	}

	@AfterAll
	static void tearDownClass() {
		container.stop();
	}

	@EnableHazelcastHttpSession
	@Configuration(proxyBeanMethods = false)
    @WebAppConfiguration
	static class HazelcastSessionConfig {
		@Bean @SpringSessionHazelcastInstance
        HazelcastInstance hazelcastInstance() {
            ClientConfig clientConfig = new ClientConfig();
            clientConfig.getNetworkConfig().addAddress(container.getHost() + ":" + container.getFirstMappedPort());
            return HazelcastClient.newHazelcastClient(HazelcastSessionConfiguration.applySerializationConfig(clientConfig));
        }
	}

}
