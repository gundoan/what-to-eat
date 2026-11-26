package com.whattoeat.room.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.whattoeat.common.error.ErrorCode;

/**
 * 직접 합치기를 요청할 수 있는지 판단하는 규칙. 요청 API의 검사와 화면의 "이 메뉴에 합치기" 버튼이 이 규칙 하나를 같이 쓴다.
 * 그래서 화면에서 켜진 버튼을 눌렀는데 서버가 거절하는 식으로 둘이 어긋나지 않는다.
 * 방의 참여자와 합치기 그룹을 한 번 읽어서 만들고, 그 뒤로는 DB를 읽지 않는다.
 */
public final class MergeRequestRules {

	/** 결과가 안 나온 그룹(자동 그룹, 직접 요청)에 든 메뉴. 한 메뉴는 한 번에 하나에만 걸릴 수 있다. */
	private final Set<Long> busyMenuIds;

	/** "합치기 끝냄"을 누른 참여자 */
	private final Set<Long> finishedParticipantIds;

	/** 거절된 직접 요청의 두 메뉴. 방향과 상관없이 같은 두 메뉴 사이에는 다시 요청할 수 없다. */
	private final Set<Set<Long>> declinedPairs;

	private MergeRequestRules(Set<Long> busyMenuIds, Set<Long> finishedParticipantIds, Set<Set<Long>> declinedPairs) {
		this.busyMenuIds = busyMenuIds;
		this.finishedParticipantIds = finishedParticipantIds;
		this.declinedPairs = declinedPairs;
	}

	public static MergeRequestRules of(List<Participant> participants, List<MergeGroup> groups) {
		Set<Long> busy = new HashSet<>();
		Set<Set<Long>> declined = new HashSet<>();
		for (MergeGroup group : groups) {
			Set<Long> menuIds = group.getMenus().stream().map(Menu::getId).collect(Collectors.toSet());
			if (group.isPending()) {
				busy.addAll(menuIds);
			}
			// 자동 그룹에서 안 합쳐진 것은 막지 않는다. 대표가 무작위라 거절했을 수 있는데, 직접 요청은 요청받은 메뉴가 남는다.
			else if (group.getKind() == MergeGroupKind.DIRECT && group.getStatus() == MergeGroupStatus.NOT_MERGED) {
				declined.add(menuIds);
			}
		}
		Set<Long> finished = participants.stream()
			.filter(Participant::isMergeDone)
			.map(Participant::getId)
			.collect(Collectors.toSet());
		return new MergeRequestRules(busy, finished, declined);
	}

	/**
	 * me가 자기 메뉴(myMenu)를 target에 합치자고 요청할 수 있는지.
	 *
	 * @param myMenu me가 낸 메뉴. 내지 않았으면 null
	 * @param target 요청받을 메뉴. 이 방에 없으면 null
	 * @return 요청할 수 없는 이유. 요청할 수 있으면 비어 있다
	 */
	public Optional<ErrorCode> check(Participant me, Menu myMenu, Menu target) {
		if (myMenu == null || myMenu.isMerged()) {
			return Optional.of(ErrorCode.NO_MENU_TO_MERGE);
		}
		if (isFinished(me)) {
			return Optional.of(ErrorCode.MERGE_ALREADY_FINISHED);
		}
		if (target == null || target.isMerged()) {
			return Optional.of(ErrorCode.MENU_NOT_FOUND);
		}
		if (target.getId().equals(myMenu.getId())) {
			return Optional.of(ErrorCode.CANNOT_MERGE_WITH_OWN_MENU);
		}
		if (isFinished(target.getParticipant())) {
			return Optional.of(ErrorCode.TARGET_FINISHED_MERGING);
		}
		if (busyMenuIds.contains(myMenu.getId()) || busyMenuIds.contains(target.getId())) {
			return Optional.of(ErrorCode.MENU_BUSY);
		}
		if (declinedPairs.contains(Set.of(myMenu.getId(), target.getId()))) {
			return Optional.of(ErrorCode.ALREADY_DECLINED);
		}
		return Optional.empty();
	}

	/** menus 중에서 me가 지금 합치기를 요청할 수 있는 메뉴들. 화면의 버튼을 켤 메뉴다. */
	public List<Menu> requestableTargets(Participant me, Menu myMenu, List<Menu> menus) {
		return menus.stream().filter(target -> check(me, myMenu, target).isEmpty()).toList();
	}

	private boolean isFinished(Participant participant) {
		return finishedParticipantIds.contains(participant.getId());
	}

}
