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

import com.hazelcast.config.MapConfig;
import com.hazelcast.function.ConsumerEx;
import org.jspecify.annotations.NonNull;
import org.springframework.util.Assert;

/**
 * A functional interface used to customize session map used by {@link HazelcastIndexedSessionRepository}.
 *
 * @since 4.0.0
 */
@FunctionalInterface
public interface SessionMapCustomizer {

    SessionMapCustomizer NOOP = conf -> {
    };

    static @NonNull SessionMapCustomizer noop() {
        return NOOP;
    }

    /**
     * Configures given {@link MapConfig}.
     */
    void configure(@NonNull MapConfig config);

    static SessionMapCustomizer wrap(@NonNull ConsumerEx<MapConfig> sessionMapConfigCustomizer) {
        Assert.notNull(sessionMapConfigCustomizer, "sessionMapConfigCustomizer can't be null");
        return sessionMapConfigCustomizer::accept;
    }

    @NonNull
    default SessionMapCustomizer andThen(@NonNull final SessionMapCustomizer otherSessionMapCustomizer) {
        var thisCustomizer = this;
        return mapConf -> {
            thisCustomizer.configure(mapConf);
            otherSessionMapCustomizer.configure(mapConf);
        };
    }

}
