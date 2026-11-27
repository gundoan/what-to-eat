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
	@DisplayName("시간을 정하지 않은 단계는 기본값이다: 제안, 합치기, 투표 5분, 랜덤 선택 1분")
	void usesDefaultsForMissingPhases() {
		PhaseDurations durations = PhaseDurations.of(null, 90, null, null);

		assertThat(durations.proposalSeconds()).isEqualTo(300);
		assertThat(durations.mergeSeconds()).as("정한 단계는 정한 대로").isEqualTo(90);
		assertThat(durations.voteSeconds()).isEqualTo(300);
		assertThat(durations.randomPickSeconds()).isEqualTo(60);
	}

	@ParameterizedTest(name = "{0}초는 허용한다")
	@ValueSource(ints = { 30, 60, 90, 300, 600, 1800, 3600 })
	@DisplayName("30초부터 60분까지 30초 단위는 허용한다")
	void acceptsValidSeconds(int seconds) {
		PhaseDurations durations = PhaseDurations.of(seconds, seconds, seconds, seconds);

		assertThat(durations.proposalSeconds()).isEqualTo(seconds);
	}

	@Test
	@DisplayName("0초는 \"제한 없음\"이고, 그 단계의 시간은 없다(null)")
	void zeroMeansNoLimit() {
		PhaseDurations durations = PhaseDurations.of(PhaseDurations.NO_LIMIT, 90, 0, 0);

		assertThat(durations.proposalSeconds()).isZero();
		assertThat(durations.proposalLimit()).isNull();
		assertThat(durations.mergeLimit()).as("다른 단계는 정한 대로").isEqualTo(90);
		assertThat(durations.voteLimit()).isNull();
		assertThat(durations.randomPickLimit()).isNull();
	}

	@ParameterizedTest(name = "{0}초는 거절한다")
	@ValueSource(ints = { 15, 29, 45, 3601, 3630, -30 })
	@DisplayName("0초(제한 없음)가 아니면서 30초보다 짧거나, 60분보다 길거나, 30초 단위가 아니면 거절한다")
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
