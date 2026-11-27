package com.whattoeat.room.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.whattoeat.IntegrationTest;
import com.whattoeat.room.application.RoomResults.CreatedRoom;
import com.whattoeat.room.application.VoteService.VoteInput;
import com.whattoeat.room.domain.PhaseDurations;
import com.whattoeat.room.domain.RoomRepository;
import com.whattoeat.room.domain.RoomStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 투표 시간이 다 됐을 때. 실제로 기다리지 않고 "지금 시각"을 직접 넣는다.
 */
@IntegrationTest
class VoteTimeoutTest {

	@Autowired
	RoomService roomService;

	@Autowired
	RoomPhaseService phaseService;

	@Autowired
	ProposalService proposalService;

	@Autowired
	MergeService mergeService;

	@Autowired
	VoteService voteService;

	@Autowired
	RoomRepository roomRepository;

	@Test
	@DisplayName("투표 시간이 다 되면, 안 낸 사람이 있어도 지금까지 낸 표로 결정된다")
	void decidesWithBallotsSoFar() {
		VotingRoom room = votingRoom("김밥", "라면", "짜장면");
		String ramen = menuIdNamed(room, "라면");
		voteService.submit(room.code(), room.tokens().get(0), false, List.of(new VoteInput(ramen, 1)));
		Instant deadline = deadline(room);

		phaseService.advanceExpiredPhases(deadline.minusMillis(1));
		assertThat(state(room).status()).isEqualTo(RoomStatus.VOTING);

		phaseService.advanceExpiredPhases(deadline);

		RoomState state = state(room);
		assertThat(state.status()).isEqualTo(RoomStatus.FINISHED);
		assertThat(state.result().name()).isEqualTo("라면");
	}

	@Test
	@DisplayName("투표 시간이 다 됐는데 아무도 표를 내지 않았으면 랜덤 선택으로 간다")
	void nobodyVotedGoesToRandomPick() {
		VotingRoom room = votingRoom("김밥", "라면");

		phaseService.advanceExpiredPhases(deadline(room));

		assertThat(state(room).status()).isEqualTo(RoomStatus.RANDOM_PICK);
	}

	/** 각자 다른 메뉴를 내고 모두 "합치기 끝냄"을 눌러 투표 단계가 된 방. 첫 번째 토큰이 방장이다. */
	private VotingRoom votingRoom(String... menus) {
		CreatedRoom created = roomService.create(PhaseDurations.of(null, null, 30, null));
		List<String> tokens = new ArrayList<>(List.of(created.token()));
		for (int i = 1; i < menus.length; i++) {
			tokens.add(roomService.join(created.code(), null).newToken());
		}
		phaseService.advanceByHost(created.code(), created.token(), RoomStatus.WAITING);
		for (int i = 0; i < menus.length; i++) {
			proposalService.propose(created.code(), tokens.get(i), menus[i]);
		}
		for (String token : tokens) {
			mergeService.finish(created.code(), token);
		}
		VotingRoom room = new VotingRoom(created.code(), tokens);
		assertThat(state(room).status()).isEqualTo(RoomStatus.VOTING);
		return room;
	}

	private String menuIdNamed(VotingRoom room, String name) {
		return state(room).menus().stream().filter(menu -> menu.name().equals(name)).findFirst().orElseThrow().id();
	}

	private Instant deadline(VotingRoom room) {
		return roomRepository.findByCode(room.code()).orElseThrow().getPhaseEndsAt();
	}

	private RoomState state(VotingRoom room) {
		return roomService.getRoom(room.code(), room.tokens().getFirst()).room();
	}

	private record VotingRoom(String code, List<String> tokens) {
	}

}
