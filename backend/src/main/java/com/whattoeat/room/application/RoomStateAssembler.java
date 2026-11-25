package com.whattoeat.room.application;

import java.time.Clock;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.whattoeat.room.application.RoomState.MenuItem;
import com.whattoeat.room.application.RoomState.MergeGroupItem;
import com.whattoeat.room.application.RoomView.Me;
import com.whattoeat.room.domain.Menu;
import com.whattoeat.room.domain.MenuRepository;
import com.whattoeat.room.domain.MergeGroup;
import com.whattoeat.room.domain.MergeGroupRepository;
import com.whattoeat.room.domain.Participant;
import com.whattoeat.room.domain.ParticipantRepository;
import com.whattoeat.room.domain.Room;
import com.whattoeat.room.domain.RoomStatus;
import org.springframework.stereotype.Component;

/**
 * DB에서 필요한 것을 모아 화면에 보낼 방 상태(RoomState)와 참여자별 "나" 정보(Me)를 만든다.
 * 트랜잭션 안에서 불러야 한다.
 */
@Component
class RoomStateAssembler {

	private final ParticipantRepository participantRepository;

	private final MenuRepository menuRepository;

	private final MergeGroupRepository mergeGroupRepository;

	private final Clock clock;

	RoomStateAssembler(ParticipantRepository participantRepository, MenuRepository menuRepository,
			MergeGroupRepository mergeGroupRepository, Clock clock) {
		this.participantRepository = participantRepository;
		this.menuRepository = menuRepository;
		this.mergeGroupRepository = mergeGroupRepository;
		this.clock = clock;
	}

	/** 한 참여자가 받을 화면 정보. 요청에 대한 응답으로 쓴다. */
	RoomView view(Room room, Participant me) {
		return views(room).viewOf(me.getId()).orElseThrow();
	}

	/**
	 * 방 상태와 참여자 모두의 "나" 정보를 한 번에 만든다. 참여자가 몇 명이든 참여자, 메뉴, 합치기 그룹을 한 번씩만 읽는다.
	 */
	RoomViews views(Room room) {
		List<Participant> participants = participantRepository.findByRoom(room);
		List<Menu> menus = menuRepository.findSortedByRoom(room);
		List<MergeGroup> groups = mergeGroupRepository.findWithMenusByRoom(room);
		Map<Long, Menu> menuByParticipantId = Menu.byParticipantId(menus);
		RoomStatus status = room.getStatus();

		long doneCount = participants.stream()
			.filter(participant -> participant.hasFinished(status, menuByParticipantId.get(participant.getId())))
			.count();
		RoomState state = new RoomState(room.getCode(), status, room.getPhaseDurations(), participants.size(),
				room.getPhaseEndsAt(), clock.instant(), remainingMenus(menus), doneCount, groupItems(groups, menus));

		Map<Long, String> waitingGroupIdByMenuId = waitingGroupIdByMenuId(groups);
		Map<Long, Me> meByParticipantId = new HashMap<>();
		for (Participant participant : participants) {
			Menu myMenu = menuByParticipantId.get(participant.getId());
			meByParticipantId.put(participant.getId(), me(participant, myMenu, status, waitingGroupIdByMenuId));
		}
		return new RoomViews(state, meByParticipantId);
	}

	private static Me me(Participant participant, Menu myMenu, RoomStatus status,
			Map<Long, String> waitingGroupIdByMenuId) {
		boolean proposed = myMenu != null;
		boolean merged = proposed && myMenu.isMerged();
		return new Me(participant.isHost(), proposed ? myMenu.getName() : null, participant.isProposalAllGood(),
				proposed && !merged ? myMenu.getPublicId() : null, merged ? myMenu.getMergedInto().getName() : null,
				proposed ? waitingGroupIdByMenuId.get(myMenu.getId()) : null, participant.hasFinished(status, myMenu));
	}

	/** 다른 메뉴에 합쳐지지 않고 남은 메뉴. menus가 이미 가나다순이라 순서는 그대로 둔다. */
	private static List<MenuItem> remainingMenus(List<Menu> sortedMenus) {
		return sortedMenus.stream().filter(menu -> !menu.isMerged()).map(RoomStateAssembler::item).toList();
	}

	/**
	 * 그룹 안의 메뉴는 목록과 같은 가나다순으로, 그룹끼리는 첫 메뉴의 가나다순으로 놓는다.
	 * 그룹이 만들어진 순서는 드러내지 않는다.
	 */
	private static List<MergeGroupItem> groupItems(List<MergeGroup> groups, List<Menu> sortedMenus) {
		Map<Long, Integer> displayOrder = new HashMap<>();
		for (int i = 0; i < sortedMenus.size(); i++) {
			displayOrder.put(sortedMenus.get(i).getId(), i);
		}
		Comparator<Menu> inDisplayOrder = Comparator.comparing(menu -> displayOrder.get(menu.getId()));

		record SortedGroup(MergeGroup group, List<Menu> menus) {
		}
		return groups.stream()
			.map(group -> new SortedGroup(group, group.getMenus().stream().sorted(inDisplayOrder).toList()))
			.sorted(Comparator.comparing(sorted -> displayOrder.get(sorted.menus().getFirst().getId())))
			.map(sorted -> {
				Menu representative = sorted.group().getRepresentative();
				return new MergeGroupItem(sorted.group().getPublicId(), sorted.group().getStatus(),
						sorted.menus().stream().map(RoomStateAssembler::item).toList(),
						representative == null ? null : representative.getName(), sorted.group().getAnsweredCount());
			})
			.toList();
	}

	/** 메뉴 번호 → 그 메뉴를 낸 사람의 답을 기다리는 그룹 번호. 한 메뉴는 답을 기다리는 그룹에 많아야 하나 들어 있다. */
	private static Map<Long, String> waitingGroupIdByMenuId(List<MergeGroup> groups) {
		Map<Long, String> result = new HashMap<>();
		for (MergeGroup group : groups) {
			for (Menu menu : group.getWaitingMenus()) {
				result.put(menu.getId(), group.getPublicId());
			}
		}
		return result;
	}

	private static MenuItem item(Menu menu) {
		return new MenuItem(menu.getPublicId(), menu.getName());
	}

}
