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

module com.hazelcast.spring.session {
    requires com.hazelcast.core;
    requires org.apache.commons.logging;
    requires spring.beans;
    requires spring.context;
    requires spring.core;
    requires spring.session.core;
    requires org.jspecify;
    requires org.slf4j;

    exports com.hazelcast.spring.session;
    exports com.hazelcast.spring.session.config.annotation;
    exports com.hazelcast.spring.session.config.annotation.web.http;
}
