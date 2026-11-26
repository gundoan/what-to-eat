package com.whattoeat.room.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.whattoeat.IntegrationTest;
import com.whattoeat.room.application.RoomResults.CreatedRoom;
import com.whattoeat.room.application.RoomState.MergeGroupItem;
import com.whattoeat.room.domain.MergeGroupStatus;
import com.whattoeat.room.domain.PhaseDurations;
import com.whattoeat.room.domain.Room;
import com.whattoeat.room.domain.RoomRepository;
import com.whattoeat.room.domain.RoomStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 시간 초과로 단계가 넘어가는지 확인한다. 실제로 1분을 기다리지 않고, "지금 시각"을 직접 넣어서 시간이 다 된 뒤를 만든다.
 */
@IntegrationTest
class PhaseTimeoutTest {

	@Autowired
	RoomService roomService;

	@Autowired
	RoomPhaseService phaseService;

	@Autowired
	ProposalService proposalService;

	@Autowired
	MergeService mergeService;

	@Autowired
	RoomRepository roomRepository;

	@Test
	@DisplayName("제안 시간이 다 되기 전에는 그대로이고, 다 되면 다음 단계로 넘어간다")
	void movesOnOnlyAfterDeadline() {
		StartedRoom room = startRoom(0);
		Instant deadline = room(room).getPhaseEndsAt();

		phaseService.advanceExpiredPhases(deadline.minusMillis(1));
		assertThat(room(room).getStatus()).isEqualTo(RoomStatus.PROPOSING);

		phaseService.advanceExpiredPhases(deadline);
		// 아무도 제안하지 않았으므로 랜덤 선택으로 간다.
		assertThat(room(room).getStatus()).isEqualTo(RoomStatus.RANDOM_PICK);
	}

	@Test
	@DisplayName("제안 시간이 다 됐을 때 메뉴가 2개 이상이면 합치기 단계로 간다 (안 끝낸 사람이 있어도)")
	void movesToMergingWhenTimeIsUp() {
		StartedRoom room = startRoom(2);
		proposalService.propose(room.code(), room.tokens().get(0), "제육");
		proposalService.propose(room.code(), room.tokens().get(1), "김밥");
		Instant deadline = room(room).getPhaseEndsAt();

		phaseService.advanceExpiredPhases(deadline.plusSeconds(1));

		assertThat(room(room).getStatus()).isEqualTo(RoomStatus.MERGING);
	}

	@Test
	@DisplayName("이미 다른 이유로 넘어간 방은 시간 초과 처리에서 다시 넘기지 않는다")
	void doesNotAdvanceTwice() {
		StartedRoom room = startRoom(0);
		Instant deadline = room(room).getPhaseEndsAt();
		// 혼자라서 제안하자마자 넘어가고, 메뉴가 1개라 합치기 없이 투표 단계다.
		proposalService.propose(room.code(), room.host(), "제육");

		phaseService.advanceExpiredPhases(deadline.plusSeconds(1));

		assertThat(room(room).getStatus()).isEqualTo(RoomStatus.VOTING);
	}

	@Test
	@DisplayName("합치기 시간이 다 되면 투표 단계로 가고, 결과가 안 나온 그룹은 답하지 않은 사람을 '합치지 않기'로 보고 결과를 낸다")
	void mergeTimeoutClosesWaitingGroups() {
		StartedRoom room = startRoom(2);
		for (String token : room.tokens()) {
			proposalService.propose(room.code(), token, "제육볶음"); // 모두 내면 바로 합치기 단계로 간다
		}
		String groupId = onlyGroup(room).id();
		mergeService.answer(room.code(), room.tokens().get(0), groupId, true);
		mergeService.answer(room.code(), room.tokens().get(1), groupId, true);
		Instant deadline = room(room).getPhaseEndsAt();

		phaseService.advanceExpiredPhases(deadline.minusMillis(1));
		assertThat(room(room).getStatus()).isEqualTo(RoomStatus.MERGING);
		assertThat(onlyGroup(room).status()).isEqualTo(MergeGroupStatus.PENDING);

		phaseService.advanceExpiredPhases(deadline);

		RoomState state = roomService.getRoom(room.code(), room.host()).room();
		assertThat(state.status()).isEqualTo(RoomStatus.VOTING);
		assertThat(onlyGroup(room).status()).isEqualTo(MergeGroupStatus.MERGED);
		assertThat(onlyGroup(room).answeredCount()).isEqualTo(3);
		// 동의한 두 메뉴는 하나로 합쳐지고, 답하지 않은 사람의 메뉴는 따로 남는다.
		assertThat(state.menus()).hasSize(2);
	}

	/** 제안 시간이 30초인 방을 만들고 손님 guestCount명을 들인 뒤 시작한다. */
	private StartedRoom startRoom(int guestCount) {
		CreatedRoom created = roomService.create(PhaseDurations.of(30, null, null, null));
		List<String> tokens = new ArrayList<>(List.of(created.token()));
		for (int i = 0; i < guestCount; i++) {
			tokens.add(roomService.join(created.code(), null).newToken());
		}
		phaseService.advanceByHost(created.code(), created.token(), RoomStatus.WAITING);
		return new StartedRoom(created.code(), tokens);
	}

	private MergeGroupItem onlyGroup(StartedRoom room) {
		List<MergeGroupItem> groups = roomService.getRoom(room.code(), room.host()).room().mergeGroups();
		assertThat(groups).hasSize(1);
		return groups.getFirst();
	}

	private Room room(StartedRoom room) {
		return roomRepository.findByCode(room.code()).orElseThrow();
	}

	/** @param tokens 참여자 토큰들. 첫 번째가 방장이다 */
	private record StartedRoom(String code, List<String> tokens) {

		String host() {
			return tokens.getFirst();
		}

	}

}
