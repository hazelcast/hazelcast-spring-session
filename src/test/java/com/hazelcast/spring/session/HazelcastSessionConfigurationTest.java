package com.hazelcast.spring.session;

import com.hazelcast.config.Config;
import org.junit.jupiter.api.Test;

import static com.hazelcast.spring.session.HazelcastSessionConfiguration.applySerializationConfig;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

class HazelcastSessionConfigurationTest {

    /**
     * Reproducer for <a href="https://hazelcast.atlassian.net/browse/SUP-1104">SUP-1104 ticket</a>.
     */
    @Test
    void serializersAreReused() {
        Config c1 = new Config();
        applySerializationConfig(c1);
        Config c2 = new Config();
        applySerializationConfig(c2);

        assertThat(c1.getSerializationConfig()).isEqualTo(c2.getSerializationConfig());
    }
}