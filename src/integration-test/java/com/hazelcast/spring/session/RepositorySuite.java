package com.hazelcast.spring.session;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
@SuiteDisplayName("HazelcastIndexedSessionRepository ITs")
@SelectClasses({
        ClientServerHazelcastIndexedSessionRepositoryIT.class,
        ClientServerWithSerializerHazelcastIndexedSessionRepositoryIT.class,
        ClientServerNoClasspathHazelcastIndexedSessionRepositoryIT.class,
        EmbeddedHazelcastIndexedSessionRepositoryIT.class
})
public class RepositorySuite {
}
