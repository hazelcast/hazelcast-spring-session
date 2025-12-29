package com.hazelcast.spring.session;

import com.hazelcast.config.Config;
import com.hazelcast.internal.serialization.Data;
import com.hazelcast.internal.serialization.SerializationService;
import com.hazelcast.spi.impl.SerializationServiceSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class SessionUpdateEntryProcessorSerializationTest extends TestWithHazelcast {
    private static SerializationService serializationService;

    @BeforeAll
    static void setup() {
        var hz = FACTORY.newHazelcastInstance(HazelcastSessionConfiguration.applySerializationConfig(new Config()));
        serializationService = ((SerializationServiceSupport) hz).getSerializationService();
    }

    @ParameterizedTest
    @MethodSource("attributes")
    void serializeTest(SessionUpdateEntryProcessor entryProcessor) {
        Data serialized = serializationService.toData(entryProcessor);
        SessionUpdateEntryProcessor deserialized = serializationService.toObject(serialized);

        assertThat(deserialized.principalName).isEqualTo(entryProcessor.principalName);
        assertThat(deserialized.lastAccessedTime).isEqualTo(entryProcessor.lastAccessedTime);
        assertThat(deserialized.maxInactiveInterval).isEqualTo(entryProcessor.maxInactiveInterval);
        assertThat(deserialized.delta).usingRecursiveComparison().isEqualTo(entryProcessor.delta);
    }

    public static List<SessionUpdateEntryProcessor> attributes() {
        List<SessionUpdateEntryProcessor> processorList = new ArrayList<>();
        for (Map<String, byte[]> delta : deltas()) {
            for (Duration maxInactiveInterval : asList(null, Duration.ofSeconds(20))) {
                for (Instant lastAccessedTime : asList(null, Instant.now())) {
                    for (String principal : asList(null, "principal1")) {
                        var ep = new SessionUpdateEntryProcessor();
                        ep.delta = delta;
                        ep.lastAccessedTime = lastAccessedTime;
                        ep.maxInactiveInterval = maxInactiveInterval;
                        ep.principalName = principal;
                        processorList.add(ep);
                    }
                }
            }
        }

        return processorList;
    }

    private static List<Map<String, byte[]>> deltas() {
        var nonNull = new HashMap<String, byte[]>();
        var nonEmpty = new HashMap<String, byte[]>();
        nonEmpty.put("key1", serializationService.toData("value1").toByteArray());
        return asList(null, nonNull, nonEmpty);
    }

}
