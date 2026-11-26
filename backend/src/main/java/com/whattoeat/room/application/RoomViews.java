package com.whattoeat.room.application;

import java.util.Map;
import java.util.Optional;

import com.whattoeat.room.application.RoomView.Me;

/**
 * 방 상태 하나와 참여자마다의 "나" 정보. SSE로 보낼 때 한 번만 읽어서, 연결마다 그 연결의 주인 정보만 붙여 보낸다.
 *
 * @param meByParticipantId 참여자 번호 → 그 사람의 "나" 정보. 참여자 번호는 서버 안에서만 쓰고 화면에 내보내지 않는다
 */
public record RoomViews(RoomState room, Map<Long, Me> meByParticipantId) {

	public Optional<RoomView> viewOf(long participantId) {
		return Optional.ofNullable(meByParticipantId.get(participantId)).map(me -> new RoomView(room, me));
	}

}
