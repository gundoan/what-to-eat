package com.whattoeat.room.application;

import java.util.List;

/**
 * 한 참여자가 받는 방 화면 정보. 공통 방 상태(room)와 받는 사람 본인에 대한 정보(me)로 나뉜다.
 * me에는 본인 것만 들어가므로, 다른 사람이 방장인지 무엇을 냈는지는 드러나지 않는다.
 */
public record RoomView(RoomState room, Me me) {

	/**
	 * @param host 내가 방장인지
	 * @param proposal 내가 제안한 메뉴 이름(합쳐졌어도 원래 이름). 제안하지 않았으면 null
	 * @param allGood 제안 단계에서 내가 "다 좋음"을 눌렀는지
	 * @param menuId 남아 있는 내 메뉴의 공개 번호. 메뉴가 없거나 다른 메뉴에 합쳐졌으면 null
	 * @param mergedInto 내 메뉴가 최종적으로 합쳐진 메뉴 이름. 합쳐지지 않았으면 null
	 * @param pendingGroupId 내 답을 기다리는 합치기 그룹(자동 그룹이나 내가 받은 직접 요청)의 번호. 없으면 null
	 * @param sentGroupId 내가 보내고 상대의 답을 기다리는 직접 요청의 번호. 없으면 null
	 * @param requestableMenuIds 지금 내가 직접 합치기를 요청할 수 있는 메뉴들의 번호
	 * @param done 내가 지금 단계(제안, 합치기)에서 할 일을 끝냈는지
	 */
	public record Me(boolean host, String proposal, boolean allGood, String menuId, String mergedInto,
			String pendingGroupId, String sentGroupId, List<String> requestableMenuIds, boolean done) {
	}

}
