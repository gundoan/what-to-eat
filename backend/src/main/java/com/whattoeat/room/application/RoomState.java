package com.whattoeat.room.application;

import java.time.Instant;
import java.util.List;

import com.whattoeat.room.domain.MergeGroupKind;
import com.whattoeat.room.domain.MergeGroupStatus;
import com.whattoeat.room.domain.PhaseDurations;
import com.whattoeat.room.domain.ResultReason;
import com.whattoeat.room.domain.RoomStatus;

/**
 * 방 참여자 모두에게 똑같이 보이는 방 상태.
 * 누가 참여했는지, 누가 방장인지, 누가 어떤 메뉴를 냈는지, 누가 어떻게 답했는지 같은 개인 정보는 절대 넣지 않는다.
 *
 * @param phaseEndsAt 지금 단계가 끝나는 시각. 시간 제한이 없는 단계에서는 null. 랜덤 선택에서 모두 한 번씩 뽑혀
 * 자동 다시 뽑기를 멈췄을 때도 null이다
 * @param serverTime 이 상태를 만든 순간의 서버 시각. 화면이 휴대폰 시계와 서버 시계의 차이를 보정해 남은 시간을 계산한다
 * @param menus 남아 있는 메뉴(다른 메뉴에 합쳐진 것은 빠진다), 가나다순. 누가 냈는지는 없다
 * @param doneCount 지금 단계(제안, 합치기, 투표, 재투표)를 끝낸 사람 수. 그 밖의 단계에서는 0
 * @param mergeGroups 합치기 그룹들(합치기 단계부터 생긴다)
 * @param voteCandidates 지금 표를 줄 수 있는 메뉴(투표면 남은 메뉴 전부, 재투표면 동점이었던 메뉴). 투표 중이 아니면 비어 있다
 * @param voteScores 첫 투표의 메뉴별 점수(총점이 높은 순). 투표가 끝난 뒤에만 채운다. 투표 중에 보여주면, 누가 표를 낸 순간
 * 바뀐 점수로 그 사람이 어디에 표를 줬는지 짐작할 수 있어서다
 * @param revoteScores 재투표의 메뉴별 점수. 재투표가 끝난 뒤에만 채운다
 * @param pickTurn 랜덤 선택에서 지금 몇 번째 뽑기인지. 누가 뽑혔는지는 없다. 랜덤 선택이 아니면 null
 * @param result 결정된 메뉴와 결정 이유. 끝나기 전에는 null
 */
public record RoomState(String code, RoomStatus status, PhaseDurations phaseDurations, long participantCount,
		Instant phaseEndsAt, Instant serverTime, List<MenuItem> menus, long doneCount,
		List<MergeGroupItem> mergeGroups, List<MenuItem> voteCandidates, List<MenuScoreItem> voteScores,
		List<MenuScoreItem> revoteScores, Integer pickTurn, ResultItem result) {

	/**
	 * @param name 결정된 메뉴 이름. 랜덤 선택에서 뽑힌 사람이 정한 이름이면 메뉴 목록에 없을 수 있다
	 * @param reason 어떻게 결정됐는지
	 */
	public record ResultItem(String name, ResultReason reason) {
	}

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
