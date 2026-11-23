package com.whattoeat.room.application;

/**
 * 한 참여자가 받는 방 화면 정보. 공통 방 상태(room)와 요청한 본인에 대한 정보(me)로 나뉜다.
 * 방장 여부와 제안한 메뉴는 본인 것만 알려주므로, 다른 사람에게 드러나지 않는다.
 */
public record RoomView(RoomState room, Me me) {

	/**
	 * @param host 내가 방장인지
	 * @param proposal 내가 제안한 메뉴 이름. 제안하지 않았으면 null
	 * @param allGood 제안 단계에서 내가 "다 좋음"을 눌렀는지
	 */
	public record Me(boolean host, String proposal, boolean allGood) {
	}

}
