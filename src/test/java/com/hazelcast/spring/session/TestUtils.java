package com.hazelcast.spring.session;

import com.hazelcast.internal.serialization.SerializationService;
import com.hazelcast.internal.serialization.impl.DefaultSerializationServiceBuilder;
import com.hazelcast.internal.serialization.impl.compact.schema.MemberSchemaService;

final class TestUtils {
    private TestUtils() {
    }

    static SerializationService getSerializationService() {
        return new DefaultSerializationServiceBuilder()
                .setSchemaService(new MemberSchemaService())
                .build();
    }
}
