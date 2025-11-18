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

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import com.hazelcast.map.ExtendedMapEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SessionUpdateEntryProcessorTests {

	private SessionUpdateEntryProcessor processor;

	@BeforeEach
	void setUp() {
		this.processor = new SessionUpdateEntryProcessor();
	}

	@Test
	void shouldReturnFalseIfNoSessionExistsInHazelcastMapEntry() {
		@SuppressWarnings("unchecked")
		ExtendedMapEntry<String, BackingMapSession> mapEntry = mock(ExtendedMapEntry.class);

		Object result = this.processor.process(mapEntry);

		assertThat(result).isEqualTo(Boolean.FALSE);
	}

	@Test
	void shouldUpdateMaxInactiveIntervalOnSessionAndSetMapEntryValueWithNewTimeToLive() {
		Duration newMaxInactiveInterval = Duration.ofSeconds(123L);
        BackingMapSession mapSession = new BackingMapSession();
		@SuppressWarnings("unchecked")
		ExtendedMapEntry<String, BackingMapSession> mapEntry = mock(ExtendedMapEntry.class);
		given(mapEntry.getValue()).willReturn(mapSession);

		this.processor.setMaxInactiveInterval(newMaxInactiveInterval);
		Object result = this.processor.process(mapEntry);

		assertThat(result).isEqualTo(Boolean.TRUE);
		assertThat(mapSession.getMaxInactiveInterval()).isEqualTo(newMaxInactiveInterval);
		verify(mapEntry).setValue(mapSession, newMaxInactiveInterval.getSeconds(), TimeUnit.SECONDS);
	}

	@Test
	void shouldSetMapEntryValueWithOldTimeToLiveIfNoChangeToMaxInactiveIntervalIsRegistered() {
		Duration maxInactiveInterval = Duration.ofSeconds(123L);
        BackingMapSession mapSession = new BackingMapSession();
		mapSession.setMaxInactiveInterval(maxInactiveInterval);
		@SuppressWarnings("unchecked")
		ExtendedMapEntry<String, BackingMapSession> mapEntry = mock(ExtendedMapEntry.class);
		given(mapEntry.getValue()).willReturn(mapSession);

		Object result = this.processor.process(mapEntry);

		assertThat(result).isEqualTo(Boolean.TRUE);
		assertThat(mapSession.getMaxInactiveInterval()).isEqualTo(maxInactiveInterval);
		verify(mapEntry).setValue(mapSession, maxInactiveInterval.getSeconds(), TimeUnit.SECONDS);
	}

	@Test
	void shouldUpdateLastAccessTimeOnSessionAndSetMapEntryValueWithOldTimeToLive() {
		Instant lastAccessTime = Instant.ofEpochSecond(1234L);
        BackingMapSession mapSession = new BackingMapSession();
		@SuppressWarnings("unchecked")
		ExtendedMapEntry<String, BackingMapSession> mapEntry = mock(ExtendedMapEntry.class);
		given(mapEntry.getValue()).willReturn(mapSession);

		this.processor.setLastAccessedTime(lastAccessTime);
		Object result = this.processor.process(mapEntry);

		assertThat(result).isEqualTo(Boolean.TRUE);
		assertThat(mapSession.getLastAccessedTime()).isEqualTo(lastAccessTime);
		verify(mapEntry).setValue(mapSession, mapSession.getMaxInactiveInterval().getSeconds(), TimeUnit.SECONDS);
	}

	@Test
	void shouldUpdateSessionAttributesFromDeltaAndSetMapEntryValueWithOldTimeToLive() {
        BackingMapSession mapSession = new BackingMapSession();
		mapSession.setAttribute("changed", AttributeValue.string("oldValue"));
		mapSession.setAttribute("removed", AttributeValue.string("existingValue"));
		@SuppressWarnings("unchecked")
		ExtendedMapEntry<String, BackingMapSession> mapEntry = mock(ExtendedMapEntry.class);
		given(mapEntry.getValue()).willReturn(mapSession);

		HashMap<String, AttributeValue> delta = new HashMap<>();
		delta.put("added", AttributeValue.string("addedValue"));
		delta.put("changed", AttributeValue.string("newValue"));
		delta.put("removed", null);
		this.processor.setDelta(delta);

		Object result = this.processor.process(mapEntry);

		assertThat(result).isEqualTo(Boolean.TRUE);
		assertThat(mapSession.getAttribute("added").object()).isEqualTo("addedValue");
		assertThat(mapSession.getAttribute("changed").object()).isEqualTo("newValue");
		assertThat(mapSession.getAttribute("removed")).isNull();
		verify(mapEntry).setValue(mapSession, mapSession.getMaxInactiveInterval().getSeconds(), TimeUnit.SECONDS);
	}

}
