package com.whattoeat.room.application;

import com.whattoeat.room.domain.PhaseDurations;
import com.whattoeat.room.domain.Room;
import com.whattoeat.room.domain.RoomStatus;

/**
 * 방 참여자 모두에게 똑같이 보이는 방 상태. SSE로도 이 모양 그대로 보낸다.
 * 누가 참여했는지, 누가 방장인지 같은 개인 정보는 절대 넣지 않는다.
 */
public record RoomState(String code, RoomStatus status, PhaseDurations phaseDurations, long participantCount) {

	public static RoomState of(Room room, long participantCount) {
		return new RoomState(room.getCode(), room.getStatus(), room.getPhaseDurations(), participantCount);
	}

}
