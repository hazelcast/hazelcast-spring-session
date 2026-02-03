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
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.spring.session.HazelcastIndexedSessionRepository.HazelcastSession;
import org.assertj.core.api.ObjectAssert;
import org.example.CustomPojo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.hazelcast.spring.session.HazelcastIndexedSessionRepository.DEFAULT_SESSION_MAP_NAME;
import static com.hazelcast.spring.session.HazelcastIndexedSessionRepository.PRINCIPAL_NAME_ATTRIBUTE;
import static com.hazelcast.spring.session.TestUtils.getConfig;
import static com.hazelcast.spring.session.TestUtils.getConfigWithoutSerialization;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.session.FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME;
import static org.springframework.session.FlushMode.IMMEDIATE;
import static org.springframework.session.SaveMode.ALWAYS;

// IDE doesn't catch that nullability is checked in sub methods
@SuppressWarnings("DataFlowIssue")
@ParameterizedClass
@CsvSource(delimiter = '|', useHeadersInDisplayName = true, textBlock = """
        Code Deployed | Use Client
        true          | true
        false         | true
        true          | false
        false         | false
        """)
public class AttributeHandlingTest extends TestWithHazelcast {
    private static final Logger LOGGER = LoggerFactory.getLogger(AttributeHandlingTest.class);

    @Parameter(0)
    boolean codeDeployed;
    @Parameter(1)
    boolean useClient;

    private HazelcastIndexedSessionRepository repository;
    private HazelcastIndexedSessionRepository otherMemberRepository;
    private IMap<String, BackingMapSession> sessionsMap;
    private HazelcastInstance hazelcastInstance;

    @BeforeEach
    void setUp() {
        Config config = codeDeployed ? getConfig() : getConfigWithoutSerialization();

        FACTORY.newHazelcastInstance(config);
        FACTORY.newHazelcastInstance(config);

        ClientConfig clientConfig = new ClientConfig();
        clientConfig.setProperty("hazelcast.partition.count", "11");
        this.hazelcastInstance = useClient
                ? FACTORY.newHazelcastClient(HazelcastSessionConfiguration.applySerializationConfig(clientConfig))
                : FACTORY.newHazelcastInstance(getConfig());


        this.repository = new HazelcastIndexedSessionRepository(hazelcastInstance)
                .setApplicationEventPublisher(e -> LOGGER.info("Event: {}", e))
                .setDeployedOnAllMembers(codeDeployed)
                .setSaveMode(ALWAYS)
                .setFlushMode(IMMEDIATE);
        this.repository.afterPropertiesSet();

        var newMember = FACTORY.newHazelcastInstance(getConfig());
        this.otherMemberRepository = new HazelcastIndexedSessionRepository(newMember);
        this.otherMemberRepository.setDeployedOnAllMembers(codeDeployed);
        this.otherMemberRepository.afterPropertiesSet();

        this.sessionsMap = hazelcastInstance.getMap(DEFAULT_SESSION_MAP_NAME);
    }

    @AfterEach
    void clean() {
        FACTORY.shutdownAll();
    }

    @Test
    void handleAttributeChange() {
        HazelcastSession session = repository.createSession();

        session.setAttribute("keyString", "value123");
        session.setAttribute("keyInteger", 1);
        session.setAttribute("keyLong", 11L);
        session.setAttribute("keyPojo", new CustomPojo(1, "1"));

        repository.save(session);

        session.setAttribute("keyString", "value");
        session.setAttribute("keyPojo", new CustomPojo(2, "2"));
        repository.save(session);

        HazelcastSession sessionFound = repository.findById(session.getId());
        assertAttribute(sessionFound, "keyPojo")
                .isEqualTo(new CustomPojo(2, "2"));


        var newMember = FACTORY.newHazelcastInstance(getConfig());
        var repository = new HazelcastIndexedSessionRepository(newMember);
        repository.setDeployedOnAllMembers(codeDeployed);
        repository.afterPropertiesSet();
        HazelcastSession sessionFromSecondMember = repository.findById(session.getId());
        assertAttribute(sessionFromSecondMember, "keyPojo")
                .isInstanceOf(CustomPojo.class)
                .isEqualTo(new CustomPojo(2, "2"));

        sessionFromSecondMember.setAttribute("keyPojo", new CustomPojo(3, "3"));
        repository.save(sessionFromSecondMember);

        assertAttribute(sessionFromSecondMember, "keyPojo")
                .isInstanceOf(CustomPojo.class)
                .isEqualTo(new CustomPojo(3, "3"));
    }

    @Test
    void attributeExternalAccessWorks() {
        HazelcastSession session = repository.createSession();

        session.setAttribute("keyString", "value123");
        session.setAttribute("keyPojo", new CustomPojo(1, "1"));
        repository.save(session);

        BackingMapSession backingMapSession = sessionsMap.get(session.getId());
        assertThat(backingMapSession).isNotNull();
        assertThat((String) backingMapSession.getAttribute("keyString").deserialize(hazelcastInstance))
                .isEqualTo("value123");
        assertThat((CustomPojo) backingMapSession.getAttribute("keyPojo").deserialize(hazelcastInstance))
                .isEqualTo(new CustomPojo(1, "1"));
    }

    @Test
    void handleAttributeRemoval() {
        HazelcastSession session = repository.createSession();
        session.setAttribute("keyPojo", new CustomPojo(1, "1"));
        repository.save(session);
        session.setAttribute("keyString", "value");
        session.setAttribute("keyPojo", new CustomPojo(2, "2"));
        repository.save(session);

        var newMember = FACTORY.newHazelcastInstance(getConfig());
        var repository = new HazelcastIndexedSessionRepository(newMember);
        repository.afterPropertiesSet();
        HazelcastSession sessionFromSecondMember = repository.findById(session.getId());
        assertAttribute(sessionFromSecondMember, "keyPojo")
                .isInstanceOf(CustomPojo.class)
                .isEqualTo(new CustomPojo(2, "2"));

        sessionFromSecondMember.removeAttribute("keyPojo");
        repository.save(sessionFromSecondMember);

        assertAttribute(sessionFromSecondMember, "keyPojo").isNull();
        assertAttribute(sessionFromSecondMember, "keyString").isEqualTo("value");
    }

    @Test
    void handleAttributeVariousTypes() {
        HazelcastSession session = repository.createSession();

        session.setAttribute("keyString", "value");
        session.setAttribute("keyInteger", 1);
        session.setAttribute("keyLong", 11L);
        session.setAttribute("keyPojo", new CustomPojo(1, "1"));

        repository.save(session);

        HazelcastSession sessionFound = repository.findById(session.getId());
        assertAttribute(sessionFound, "keyString")
                .isInstanceOf(String.class)
                .isEqualTo("value");
        assertAttribute(sessionFound, "keyInteger")
                .isInstanceOf(Integer.class)
                .isEqualTo(1);
        assertAttribute(sessionFound, "keyLong")
                .isInstanceOf(Long.class)
                .isEqualTo(11L);
        assertAttribute(sessionFound, "keyPojo")
                .isInstanceOf(CustomPojo.class)
                .isEqualTo(new CustomPojo(1, "1"));
    }

    @Test
    void handlePrincipalAttribute() {
        // given
        var session = repository.createSession();
        session.setAttribute(PRINCIPAL_NAME_ATTRIBUTE, "MasterChief");

        // when
        repository.save(session);

        // then
        var sessionFound = repository.findById(session.getId());
        assertAttribute(sessionFound, PRINCIPAL_NAME_ATTRIBUTE)
                .isEqualTo("MasterChief");
        assertAttribute(sessionFound, PRINCIPAL_NAME_INDEX_NAME)
                .isEqualTo("MasterChief");

        HazelcastSession sessionFromSecondMember = otherMemberRepository.findById(session.getId());
        assertAttribute(sessionFromSecondMember, PRINCIPAL_NAME_ATTRIBUTE)
                .isEqualTo("MasterChief");
        sessionFromSecondMember.setAttribute(PRINCIPAL_NAME_ATTRIBUTE, "Yoda");
        otherMemberRepository.save(sessionFromSecondMember);

        assertAttribute(repository.findById(session.getId()), PRINCIPAL_NAME_ATTRIBUTE)
                .isEqualTo("Yoda");
    }

    @Test
    void findPrincipalByIndex() {
        // given
        var session = repository.createSession();
        session.setAttribute(PRINCIPAL_NAME_ATTRIBUTE, "MasterChief");

        // when
        repository.save(session);

        // then
        assertAttributes(repository.findById(session.getId()),
                         Map.of(PRINCIPAL_NAME_ATTRIBUTE, "MasterChief",
                                PRINCIPAL_NAME_INDEX_NAME, "MasterChief")
                        );

        var sessionsByIndex = repository.findByIndexNameAndIndexValue(PRINCIPAL_NAME_INDEX_NAME, "MasterChief");
        assertThat(sessionsByIndex).containsOnlyKeys(session.getId());
        assertAttributes(sessionsByIndex.get(session.getId()),
                         Map.of(PRINCIPAL_NAME_ATTRIBUTE, "MasterChief",
                                PRINCIPAL_NAME_INDEX_NAME, "MasterChief")
                        );

        var sessionFromOtherMemberMap = otherMemberRepository.findByIndexNameAndIndexValue(PRINCIPAL_NAME_INDEX_NAME, "MasterChief");
        assertAttributes(sessionFromOtherMemberMap.get(session.getId()),
                         Map.of(PRINCIPAL_NAME_ATTRIBUTE, "MasterChief",
                                PRINCIPAL_NAME_INDEX_NAME, "MasterChief")
                        );
    }

    @Test
    void handlePrincipalAttributeRemoval() {
        // given
        var session = repository.createSession();
        session.setAttribute(PRINCIPAL_NAME_ATTRIBUTE, "MasterChief");

        // when
        repository.save(session);

        // then
        assertAttributes(repository.findById(session.getId()),
                         Map.of(PRINCIPAL_NAME_ATTRIBUTE, "MasterChief",
                                PRINCIPAL_NAME_INDEX_NAME, "MasterChief")
                        );

        session.removeAttribute(PRINCIPAL_NAME_ATTRIBUTE);
        repository.save(session);

        var sessionFromSecondMember = otherMemberRepository.findById(session.getId());
        assertAttribute(sessionFromSecondMember, PRINCIPAL_NAME_ATTRIBUTE).isNull();
        assertAttribute(sessionFromSecondMember, PRINCIPAL_NAME_INDEX_NAME).isNull();

        session.setAttribute(PRINCIPAL_NAME_ATTRIBUTE, "MasterChief");
        repository.save(session);
        assertAttributes(repository.findById(session.getId()),
                         Map.of(PRINCIPAL_NAME_ATTRIBUTE, "MasterChief",
                                PRINCIPAL_NAME_INDEX_NAME, "MasterChief")
                        );
        session.removeAttribute(PRINCIPAL_NAME_INDEX_NAME);
        repository.save(session);
        assertThat(repository.findById(session.getId()).getAttributeNames()).isEmpty();
    }

    private static ObjectAssert<Object> assertAttribute(HazelcastSession session,
                                                        String attributeName) {
        assertThat(session).isNotNull();
        return assertThat((Object) session.getAttribute(attributeName));
    }

    private static void assertAttributes(HazelcastSession session,
                                         Map<String, Object> attributeMap) {
        assertThat(session).isNotNull();
        assertThat(attributeMap).isNotNull();
        attributeMap.forEach((name, value) -> assertAttribute(session, name).isEqualTo(value));
    }
}
