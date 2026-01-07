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

import com.hazelcast.config.IndexConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.spring.session.AbstractHazelcastIndexedSessionRepositoryIT;
import com.hazelcast.spring.session.HazelcastIndexedSessionRepository;
import com.hazelcast.spring.session.config.annotation.web.http.EnableHazelcastHttpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;

import static com.hazelcast.spring.session.HazelcastITUtils.embeddedHazelcastServer;
import static com.hazelcast.spring.session.HazelcastIndexedSessionRepository.DEFAULT_SESSION_MAP_NAME;
import static com.hazelcast.spring.session.HazelcastIndexedSessionRepository.PRINCIPAL_NAME_ATTRIBUTE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link HazelcastIndexedSessionRepository} using embedded
 * topology.
 *
 * @author Tommy Ludwig
 * @author Vedran Pavic
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = EmbeddedHazelcastIT.HazelcastSessionConfig.class)
class EmbeddedHazelcastIT extends AbstractHazelcastIndexedSessionRepositoryIT {

	@Test
	void hasIndexConfigured() {
		assertThat(hazelcastInstance.getConfig().getMapConfig(DEFAULT_SESSION_MAP_NAME)
									.getIndexConfigs())
				.flatExtracting(IndexConfig::getAttributes)
				.containsExactly(PRINCIPAL_NAME_ATTRIBUTE);
	}

	@EnableHazelcastHttpSession
    @Configuration(proxyBeanMethods = false)
    @WebAppConfiguration
	static class HazelcastSessionConfig {

		@Bean
		HazelcastInstance hazelcastInstance() {
			return embeddedHazelcastServer();
		}

	}

}
