package com.whattoeat.room.application;

/**
 * 한 참여자가 받는 방 화면 정보. 공통 방 상태(room)와 요청한 본인에 대한 정보(me)로 나뉜다.
 * 방장 여부는 본인 것만 알려주므로, 다른 사람에게 누가 방장인지 드러나지 않는다.
 */
public record RoomView(RoomState room, Me me) {

	public record Me(boolean host) {
	}

}
