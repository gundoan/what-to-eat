package com.whattoeat.room.domain;

import com.whattoeat.common.error.BusinessException;
import com.whattoeat.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * 방장이 방을 만들 때 정하는 단계별 시간(초). 재투표는 투표 시간을 같이 쓴다.
 * 30초~60분(30초 단위)이거나, 0이면 "제한 없음"이다.
 * 화면은 이 안에서 몇 가지(30초, 1분, ... 30분)를 목록으로 보여주고, 나머지는 "직접 설정"으로 분 단위로 받는다.
 * 생성자에서 규칙을 검사하므로 규칙을 어긴 값으로는 객체를 만들 수 없다.
 */
@Embeddable
public record PhaseDurations(
		@Column(name = "proposal_seconds", nullable = false) int proposalSeconds,
		@Column(name = "merge_seconds", nullable = false) int mergeSeconds,
		@Column(name = "vote_seconds", nullable = false) int voteSeconds,
		@Column(name = "random_pick_seconds", nullable = false) int randomPickSeconds) {

	public static final int MIN_SECONDS = 30;
	public static final int MAX_SECONDS = 3600;
	public static final int STEP_SECONDS = 30;

	/**
	 * "제한 없음". 이 값으로 정한 단계는 끝나는 시각이 없어서, 모두 끝내거나 방장이 넘길 때만 넘어간다.
	 * 0초짜리 단계는 원래 있을 수 없어서 다른 뜻과 헷갈리지 않는다. API와 DB도 같은 값(0)을 쓴다.
	 */
	public static final int NO_LIMIT = 0;

	/** 시간을 정하지 않은 단계의 기본값. 제안, 합치기, 투표(재투표도)는 5분이고 랜덤 선택은 1분이다. */
	public static final int DEFAULT_PROPOSAL_SECONDS = 300;
	public static final int DEFAULT_MERGE_SECONDS = 300;
	public static final int DEFAULT_VOTE_SECONDS = 300;
	public static final int DEFAULT_RANDOM_PICK_SECONDS = 60;

	public PhaseDurations {
		validate(proposalSeconds);
		validate(mergeSeconds);
		validate(voteSeconds);
		validate(randomPickSeconds);
	}

	/** 값을 보내지 않은(null) 단계는 그 단계의 기본값으로 정한다. */
	public static PhaseDurations of(Integer proposalSeconds, Integer mergeSeconds, Integer voteSeconds,
			Integer randomPickSeconds) {
		return new PhaseDurations(orDefault(proposalSeconds, DEFAULT_PROPOSAL_SECONDS),
				orDefault(mergeSeconds, DEFAULT_MERGE_SECONDS), orDefault(voteSeconds, DEFAULT_VOTE_SECONDS),
				orDefault(randomPickSeconds, DEFAULT_RANDOM_PICK_SECONDS));
	}

	public static PhaseDurations defaults() {
		return of(null, null, null, null);
	}

	/** 제안 시간(초). 제한 없음이면 null */
	public Integer proposalLimit() {
		return limitOf(proposalSeconds);
	}

	/** 합치기 시간(초). 제한 없음이면 null */
	public Integer mergeLimit() {
		return limitOf(mergeSeconds);
	}

	/** 투표 시간(초). 재투표도 이 시간을 쓴다. 제한 없음이면 null */
	public Integer voteLimit() {
		return limitOf(voteSeconds);
	}

	/** 랜덤 선택에서 뽑힌 사람이 정할 시간(초). 제한 없음이면 null이고, 그러면 자동으로 다시 뽑지 않는다 */
	public Integer randomPickLimit() {
		return limitOf(randomPickSeconds);
	}

	private static Integer limitOf(int seconds) {
		return seconds == NO_LIMIT ? null : seconds;
	}

	private static int orDefault(Integer seconds, int defaultSeconds) {
		return seconds == null ? defaultSeconds : seconds;
	}

	private static void validate(int seconds) {
		if (seconds == NO_LIMIT) {
			return;
		}
		if (seconds < MIN_SECONDS || seconds > MAX_SECONDS || seconds % STEP_SECONDS != 0) {
			throw new BusinessException(ErrorCode.INVALID_PHASE_DURATION);
		}
	}

}
