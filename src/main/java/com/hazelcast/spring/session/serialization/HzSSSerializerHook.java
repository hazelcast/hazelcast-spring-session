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

package com.hazelcast.spring.session.serialization;

import com.hazelcast.internal.serialization.DataSerializerHook;
import com.hazelcast.internal.serialization.impl.FactoryIdHelper;
import com.hazelcast.nio.serialization.DataSerializableFactory;
import com.hazelcast.spring.session.SessionUpdateEntryProcessor;

public class HzSSSerializerHook implements DataSerializerHook {
    // TODO reference FactoryIdHelper once 5.7 will be minimum supported version
    public static final int F_ID_OFFSET_HZ_SPRING_SESSION = -3000;
    public static final String HZ_SS_DS_FACTORY = "hazelcast.serialization.ds.hazelcast.spring.session";

    public static final int F_ID = FactoryIdHelper.getFactoryId(HZ_SS_DS_FACTORY, F_ID_OFFSET_HZ_SPRING_SESSION);

    public static final int SESSION_UPDATE_ENTRY_PROCESSOR = 1;

    @Override
    public int getFactoryId() {
        return F_ID;
    }

    @Override
    public DataSerializableFactory createFactory() {
        return type -> switch (type) {
                case SESSION_UPDATE_ENTRY_PROCESSOR -> new SessionUpdateEntryProcessor();
                default -> null;
            };
    }
}
