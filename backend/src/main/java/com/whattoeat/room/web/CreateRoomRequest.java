package com.whattoeat.room.web;

import com.whattoeat.room.domain.PhaseDurations;

/**
 * 방 만들기 요청. 보내지 않은 단계 시간(null)은 기본값이 된다(제안, 합치기, 투표 5분, 랜덤 선택 1분).
 */
public record CreateRoomRequest(Integer proposalSeconds, Integer mergeSeconds, Integer voteSeconds,
		Integer randomPickSeconds) {

	public PhaseDurations toPhaseDurations() {
		return PhaseDurations.of(proposalSeconds, mergeSeconds, voteSeconds, randomPickSeconds);
	}

}
