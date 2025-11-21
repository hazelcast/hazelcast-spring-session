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
import com.hazelcast.client.test.TestHazelcastFactory;
import com.hazelcast.config.Config;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.spring.session.HazelcastIndexedSessionRepository.HazelcastSession;
import org.assertj.core.api.ObjectAssert;
import org.example.CustomPojo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static com.hazelcast.spring.session.HazelcastIndexedSessionRepository.PRINCIPAL_NAME_ATTRIBUTE;
import static com.hazelcast.spring.session.HazelcastSessionConfiguration.applySerializationConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.session.FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME;

@ParameterizedClass
@CsvSource(delimiter = '|', useHeadersInDisplayName = true, textBlock = """
        Code Deployed | Use Client
        true  | true
        false | true
        true  | false
        false | false
        """)
public class AttributeHandlingTest {

    @Parameter(0)
    boolean jarOnEveryMember;
    @Parameter(1)
    boolean useClient;

    private HazelcastIndexedSessionRepository repository;
    private HazelcastIndexedSessionRepository otherMemberRepository;
    private final TestHazelcastFactory factory = new TestHazelcastFactory();

    @BeforeEach
    void setUp() {
        Config config = jarOnEveryMember ? getConfig() : getConfigWithoutSerialization();

        factory.newHazelcastInstance(config);
        factory.newHazelcastInstance(config);

        ClientConfig clientConfig = new ClientConfig();
        clientConfig.setProperty("hazelcast.partition.count", "11");
        HazelcastInstance hazelcastInstance = useClient
                ? factory.newHazelcastClient(HazelcastSessionConfiguration.applySerializationConfig(clientConfig))
                : factory.newHazelcastInstance(getConfig());

        this.repository = new HazelcastIndexedSessionRepository(hazelcastInstance);
        this.repository.setJarOnEveryMember(jarOnEveryMember);
        this.repository.afterPropertiesSet();

        var newMember = factory.newHazelcastInstance(getConfig());
        this.otherMemberRepository = new HazelcastIndexedSessionRepository(newMember);
        this.otherMemberRepository.setJarOnEveryMember(jarOnEveryMember);
        this.otherMemberRepository.afterPropertiesSet();
    }

    @AfterEach
    void clean() {
        factory.shutdownAll();
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


        var newMember = factory.newHazelcastInstance(getConfig());
        var repository = new HazelcastIndexedSessionRepository(newMember);
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
    void handleAttributeRemoval() {
        HazelcastSession session = repository.createSession();
        session.setAttribute("keyPojo", new CustomPojo(1, "1"));
        repository.save(session);
        session.setAttribute("keyString", "value");
        session.setAttribute("keyPojo", new CustomPojo(2, "2"));
        repository.save(session);

        var newMember = factory.newHazelcastInstance(getConfig());
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

    private Config getConfig() {
        Config config = getConfigWithoutSerialization();
        applySerializationConfig(config);
        return config;
    }

    private Config getConfigWithoutSerialization() {
        Config config = new Config();
        config.setProperty("hazelcast.partition.count", "11");
        return config;
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
    }

    private static ObjectAssert<Object> assertAttribute(HazelcastSession session,
                                                        String attributeName) {
        return assertThat((Object) session.getAttribute(attributeName));
    }

    private static void assertAttributes(HazelcastSession session,
                                         Map<String, Object> attributeMap) {

        attributeMap.forEach((name, value) -> assertAttribute(session, name).isEqualTo(value));
    }
}
