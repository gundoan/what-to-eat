package com.whattoeat.room.application;

import java.time.Instant;
import java.util.List;

import com.whattoeat.room.domain.PhaseDurations;
import com.whattoeat.room.domain.RoomStatus;

/**
 * 방 참여자 모두에게 똑같이 보이는 방 상태. SSE로도 이 모양 그대로 보낸다.
 * 누가 참여했는지, 누가 방장인지, 누가 어떤 메뉴를 냈는지 같은 개인 정보는 절대 넣지 않는다.
 *
 * @param phaseEndsAt 지금 단계가 끝나는 시각. 시간 제한이 없는 단계에서는 null
 * @param serverTime 이 상태를 만든 순간의 서버 시각. 화면이 휴대폰 시계와 서버 시계의 차이를 보정해 남은 시간을 계산한다
 * @param menus 제안된 메뉴 이름(가나다순). 누가 냈는지는 없다
 * @param doneCount 제안 단계에서 메뉴를 냈거나 "다 좋음"을 누른 사람 수. 제안 단계가 아니면 0
 */
public record RoomState(String code, RoomStatus status, PhaseDurations phaseDurations, long participantCount,
		Instant phaseEndsAt, Instant serverTime, List<String> menus, long doneCount) {
}
