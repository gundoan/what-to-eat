package com.whattoeat.room.application;

import java.time.Instant;
import java.util.List;
import java.util.random.RandomGenerator;

import com.whattoeat.room.domain.MergeGroup;
import com.whattoeat.room.domain.MergeGroupRepository;
import com.whattoeat.room.domain.MergeGroupStatus;
import com.whattoeat.room.domain.Menu;
import com.whattoeat.room.domain.MenuRepository;
import com.whattoeat.room.domain.Room;
import org.springframework.stereotype.Component;

/**
 * 합치기 단계가 열릴 때와 닫힐 때 하는 일, 그리고 그룹의 결과를 내는 일. 방을 잠근 트랜잭션 안에서 불러야 한다.
 */
@Component
class MergePhase {

	private final MenuRepository menuRepository;

	private final MergeGroupRepository mergeGroupRepository;

	private final MenuGrouper grouper;

	private final RandomGenerator random;

	MergePhase(MenuRepository menuRepository, MergeGroupRepository mergeGroupRepository, MenuGrouper grouper,
			RandomGenerator random) {
		this.menuRepository = menuRepository;
		this.mergeGroupRepository = mergeGroupRepository;
		this.grouper = grouper;
		this.random = random;
	}

	/** 열릴 때: 같은 메뉴로 보이는 것끼리 자동 그룹을 만든다. 그룹에 든 제안자들은 "합칠까요?" 질문을 받는다. */
	void open(Room room, Instant now) {
		List<Menu> menus = menuRepository.findSortedByRoom(room);
		List<String> names = menus.stream().map(Menu::getName).toList();
		for (List<Integer> positions : grouper.group(names)) {
			List<Menu> similar = positions.stream().map(menus::get).toList();
			mergeGroupRepository.save(MergeGroup.auto(room, similar, now));
		}
	}

	/** 닫힐 때: 결과가 안 나온 그룹은 답하지 않은 사람을 "합치지 않기"로 보고 결과를 낸다. */
	void close(Room room, Instant now) {
		for (MergeGroup group : mergeGroupRepository.findByRoomAndStatus(room, MergeGroupStatus.PENDING)) {
			group.resolve(random, now);
		}
	}

	/** 그룹 안의 모두가 답했으면 단계가 끝나기를 기다리지 않고 바로 결과를 낸다. */
	void resolveIfEveryoneAnswered(MergeGroup group, Instant now) {
		if (group.isEveryoneAnswered()) {
			group.resolve(random, now);
		}
	}

}
