package com.whattoeat.room.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.whattoeat.common.error.BusinessException;
import com.whattoeat.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PhaseDurationsTest {

	@Test
	@DisplayName("시간을 정하지 않은 단계는 1분이다")
	void defaultsToOneMinute() {
		PhaseDurations durations = PhaseDurations.of(null, 90, null, null);

		assertThat(durations.proposalSeconds()).isEqualTo(60);
		assertThat(durations.mergeSeconds()).isEqualTo(90);
		assertThat(durations.voteSeconds()).isEqualTo(60);
		assertThat(durations.randomPickSeconds()).isEqualTo(60);
	}

	@ParameterizedTest(name = "{0}초는 허용한다")
	@ValueSource(ints = { 30, 60, 90, 300, 600 })
	@DisplayName("30초부터 10분까지 30초 단위는 허용한다")
	void acceptsValidSeconds(int seconds) {
		PhaseDurations durations = PhaseDurations.of(seconds, seconds, seconds, seconds);

		assertThat(durations.proposalSeconds()).isEqualTo(seconds);
	}

	@ParameterizedTest(name = "{0}초는 거절한다")
	@ValueSource(ints = { 0, 29, 45, 601, 630, -30 })
	@DisplayName("30초보다 짧거나, 10분보다 길거나, 30초 단위가 아니면 거절한다")
	void rejectsInvalidSeconds(int seconds) {
		assertThatThrownBy(() -> PhaseDurations.of(seconds, null, null, null))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).errorCode())
			.isEqualTo(ErrorCode.INVALID_PHASE_DURATION);
	}

	@Test
	@DisplayName("네 단계 중 하나만 틀려도 거절한다")
	void rejectsWhenAnyPhaseIsInvalid() {
		assertThatThrownBy(() -> PhaseDurations.of(60, 60, 60, 45)).isInstanceOf(BusinessException.class);
	}

}
