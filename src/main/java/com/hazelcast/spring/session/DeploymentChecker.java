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

import com.hazelcast.cluster.Member;
import com.hazelcast.core.HazelcastInstance;
import org.jspecify.annotations.NonNull;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class DeploymentChecker {
    private final HazelcastInstance hazelcastInstance;

    public DeploymentChecker(@NonNull HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    boolean checkDeployedOnAllMembers() {
        Map<Member, Future<Boolean>> probeResults = hazelcastInstance.getExecutorService("default")
                                                                     .submitToAllMembers(new ClassAvailabilityProbe());
        for (Map.Entry<Member, Future<Boolean>> memberFutureEntry : probeResults.entrySet()) {
            try {
                memberFutureEntry.getValue().get();
            } catch (InterruptedException | ExecutionException e) {
                return false;
            }
        }
        return true;
    }

    private static class ClassAvailabilityProbe implements Callable<Boolean>, Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        @Override
        public Boolean call() {
            try {
                new SessionUpdateEntryProcessor();
                return true;
            } catch (Throwable e) {
                return false;
            }
        }
    }
}
