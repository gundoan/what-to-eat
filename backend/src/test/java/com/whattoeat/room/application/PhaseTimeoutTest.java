package com.whattoeat.room.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import com.whattoeat.IntegrationTest;
import com.whattoeat.room.application.RoomResults.CreatedRoom;
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
	RoomRepository roomRepository;

	@Test
	@DisplayName("제안 시간이 다 되기 전에는 그대로이고, 다 되면 다음 단계로 넘어간다")
	void movesOnOnlyAfterDeadline() {
		CreatedRoom created = startRoom();
		Instant deadline = room(created).getPhaseEndsAt();

		phaseService.advanceExpiredPhases(deadline.minusMillis(1));
		assertThat(room(created).getStatus()).isEqualTo(RoomStatus.PROPOSING);

		phaseService.advanceExpiredPhases(deadline);
		// 아무도 제안하지 않았으므로 랜덤 선택으로 간다.
		assertThat(room(created).getStatus()).isEqualTo(RoomStatus.RANDOM_PICK);
	}

	@Test
	@DisplayName("시간이 다 됐을 때 메뉴가 있으면 합치기 단계로 간다 (안 끝낸 사람이 있어도)")
	void movesToMergingWhenTimeIsUp() {
		CreatedRoom created = startRoomWithOneGuest();
		proposalService.propose(created.code(), created.token(), "제육");
		Instant deadline = room(created).getPhaseEndsAt();

		phaseService.advanceExpiredPhases(deadline.plusSeconds(1));

		assertThat(room(created).getStatus()).isEqualTo(RoomStatus.MERGING);
	}

	@Test
	@DisplayName("이미 다른 이유로 넘어간 방은 시간 초과 처리에서 다시 넘기지 않는다")
	void doesNotAdvanceTwice() {
		CreatedRoom created = startRoom();
		Instant deadline = room(created).getPhaseEndsAt();
		proposalService.propose(created.code(), created.token(), "제육"); // 혼자라서 바로 합치기 단계로 간다

		phaseService.advanceExpiredPhases(deadline.plusSeconds(1));

		assertThat(room(created).getStatus()).isEqualTo(RoomStatus.MERGING);
	}

	private CreatedRoom startRoom() {
		CreatedRoom created = roomService.create(PhaseDurations.of(30, null, null, null));
		phaseService.advanceByHost(created.code(), created.token(), RoomStatus.WAITING);
		return created;
	}

	private CreatedRoom startRoomWithOneGuest() {
		CreatedRoom created = roomService.create(PhaseDurations.of(30, null, null, null));
		roomService.join(created.code(), null);
		phaseService.advanceByHost(created.code(), created.token(), RoomStatus.WAITING);
		return created;
	}

	private Room room(CreatedRoom created) {
		return roomRepository.findByCode(created.code()).orElseThrow();
	}

}
