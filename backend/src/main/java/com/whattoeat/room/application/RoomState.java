package com.whattoeat.room.application;

import java.time.Instant;
import java.util.List;

import com.whattoeat.room.domain.MergeGroupKind;
import com.whattoeat.room.domain.MergeGroupStatus;
import com.whattoeat.room.domain.PhaseDurations;
import com.whattoeat.room.domain.RoomStatus;

/**
 * 방 참여자 모두에게 똑같이 보이는 방 상태.
 * 누가 참여했는지, 누가 방장인지, 누가 어떤 메뉴를 냈는지, 누가 어떻게 답했는지 같은 개인 정보는 절대 넣지 않는다.
 *
 * @param phaseEndsAt 지금 단계가 끝나는 시각. 시간 제한이 없는 단계에서는 null
 * @param serverTime 이 상태를 만든 순간의 서버 시각. 화면이 휴대폰 시계와 서버 시계의 차이를 보정해 남은 시간을 계산한다
 * @param menus 남아 있는 메뉴(다른 메뉴에 합쳐진 것은 빠진다), 가나다순. 누가 냈는지는 없다
 * @param doneCount 지금 단계(제안, 합치기, 투표)를 끝낸 사람 수. 그 밖의 단계에서는 0
 * @param mergeGroups 합치기 그룹들(합치기 단계부터 생긴다)
 * @param resultMenu 결정된 메뉴. 끝나기 전에는 null
 * @param voteScores 메뉴별 점수(총점이 높은 순). 투표가 끝난 뒤에만 채운다. 투표 중에 보여주면, 누가 표를 낸 순간
 * 바뀐 점수로 그 사람이 어디에 표를 줬는지 짐작할 수 있어서다
 */
public record RoomState(String code, RoomStatus status, PhaseDurations phaseDurations, long participantCount,
		Instant phaseEndsAt, Instant serverTime, List<MenuItem> menus, long doneCount,
		List<MergeGroupItem> mergeGroups, MenuItem resultMenu, List<MenuScoreItem> voteScores) {

	/**
	 * @param id 공개 번호(무작위). DB 번호와 달리 올라온 순서가 드러나지 않는다
	 */
	public record MenuItem(String id, String name) {
	}

	/**
	 * @param kind AUTO(자동으로 묶은 그룹) 또는 DIRECT(직접 합치기 요청)
	 * @param menus 그룹에 든 메뉴(합쳐져 사라진 메뉴 포함), 가나다순
	 * @param targetMenuId 직접 요청에서 요청받은 메뉴의 번호. 나머지 하나가 요청한 메뉴다. 자동 그룹이면 null
	 * @param representative 합쳐졌을 때 남은 대표 메뉴 이름. 아니면 null
	 * @param answeredCount 답한 사람 수. 누가 어떻게 답했는지는 없다
	 */
	public record MergeGroupItem(String id, MergeGroupKind kind, MergeGroupStatus status, List<MenuItem> menus,
			String targetMenuId, String representative, long answeredCount) {
	}

	/**
	 * 한 메뉴가 받은 점수. 누가 어떻게 찍었는지는 없다.
	 *
	 * @param plus +1을 받은 수
	 * @param minus -1을 받은 수
	 * @param total 총점(plus - minus)
	 */
	public record MenuScoreItem(MenuItem menu, int plus, int minus, int total) {
	}

}
