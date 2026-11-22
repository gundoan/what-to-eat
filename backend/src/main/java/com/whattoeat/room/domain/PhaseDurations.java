package com.whattoeat.room.domain;

import com.whattoeat.common.error.BusinessException;
import com.whattoeat.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * 방장이 방을 만들 때 정하는 단계별 시간(초). 재투표는 투표 시간을 같이 쓴다.
 * 생성자에서 규칙을 검사하므로 규칙을 어긴 값으로는 객체를 만들 수 없다.
 */
@Embeddable
public record PhaseDurations(
		@Column(name = "proposal_seconds", nullable = false) int proposalSeconds,
		@Column(name = "merge_seconds", nullable = false) int mergeSeconds,
		@Column(name = "vote_seconds", nullable = false) int voteSeconds,
		@Column(name = "random_pick_seconds", nullable = false) int randomPickSeconds) {

	public static final int MIN_SECONDS = 30;
	public static final int MAX_SECONDS = 600;
	public static final int STEP_SECONDS = 30;
	public static final int DEFAULT_SECONDS = 60;

	public PhaseDurations {
		validate(proposalSeconds);
		validate(mergeSeconds);
		validate(voteSeconds);
		validate(randomPickSeconds);
	}

	/** 값을 보내지 않은(null) 단계는 기본값 1분으로 정한다. */
	public static PhaseDurations of(Integer proposalSeconds, Integer mergeSeconds, Integer voteSeconds,
			Integer randomPickSeconds) {
		return new PhaseDurations(orDefault(proposalSeconds), orDefault(mergeSeconds), orDefault(voteSeconds),
				orDefault(randomPickSeconds));
	}

	public static PhaseDurations defaults() {
		return of(null, null, null, null);
	}

	private static int orDefault(Integer seconds) {
		return seconds == null ? DEFAULT_SECONDS : seconds;
	}

	private static void validate(int seconds) {
		if (seconds < MIN_SECONDS || seconds > MAX_SECONDS || seconds % STEP_SECONDS != 0) {
			throw new BusinessException(ErrorCode.INVALID_PHASE_DURATION);
		}
	}

}
