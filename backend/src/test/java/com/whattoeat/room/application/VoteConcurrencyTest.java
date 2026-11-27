package com.whattoeat.room.application;

import static com.whattoeat.room.application.Concurrently.runAllAtOnce;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import com.whattoeat.IntegrationTest;
import com.whattoeat.common.error.BusinessException;
import com.whattoeat.common.error.ErrorCode;
import com.whattoeat.room.application.RoomResults.CreatedRoom;
import com.whattoeat.room.application.RoomState.MenuItem;
import com.whattoeat.room.application.VoteService.VoteInput;
import com.whattoeat.room.domain.Ballot;
import com.whattoeat.room.domain.BallotRepository;
import com.whattoeat.room.domain.PhaseDurations;
import com.whattoeat.room.domain.Room;
import com.whattoeat.room.domain.RoomRepository;
import com.whattoeat.room.domain.RoomStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class VoteConcurrencyTest {

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

	@Autowired
	BallotRepository ballotRepository;

	@Test
	@DisplayName("50명이 동시에 표를 내도 모두 저장되고, 단계는 정확히 한 번 넘어가 메뉴가 결정된다")
	void everyoneVotingAtOnceDecidesExactlyOnce() throws Exception {
		VotingRoom room = votingRoom(Room.MAX_PARTICIPANTS);
		String favorite = room.menus().getFirst().id();

		List<Callable<Void>> votes = new ArrayList<>();
		for (String token : room.tokens()) {
			votes.add(() -> {
				voteService.submit(room.code(), token, false, List.of(new VoteInput(favorite, 1)));
				return null;
			});
		}
		runAllAtOnce(votes); // 하나라도 실패하면 예외로 테스트가 실패한다

		RoomState state = roomService.getRoom(room.code(), room.tokens().getFirst()).room();
		assertThat(state.status()).isEqualTo(RoomStatus.FINISHED);
		assertThat(state.result().name()).isEqualTo(room.menus().getFirst().name());
		assertThat(ballots(room)).hasSize(Room.MAX_PARTICIPANTS);
	}

	@Test
	@DisplayName("같은 사람이 서로 다른 표를 동시에 10번 보내도 투표지는 하나만 저장되고, 나머지는 '이미 투표함'으로 거절된다")
	void concurrentBallotsFromOnePersonKeepOnlyOne() throws Exception {
		VotingRoom room = votingRoom(3);
		String voter = room.tokens().getFirst();
		AtomicInteger succeeded = new AtomicInteger();
		AtomicInteger rejected = new AtomicInteger();

		List<Callable<Void>> attempts = new ArrayList<>();
		for (List<VoteInput> ballot : tenDifferentBallots(room.menus())) {
			attempts.add(() -> {
				try {
					voteService.submit(room.code(), voter, false, ballot);
					succeeded.incrementAndGet();
				}
				catch (BusinessException e) {
					assertThat(e.errorCode()).isEqualTo(ErrorCode.ALREADY_VOTED);
					rejected.incrementAndGet();
				}
				return null;
			});
		}
		runAllAtOnce(attempts);

		assertThat(succeeded).hasValue(1);
		assertThat(rejected).hasValue(9);
		assertThat(ballots(room)).hasSize(1);
	}

	/** 메뉴 3개로 만들 수 있는 서로 다른 투표지 10가지 (1표짜리 6가지 + 2표짜리 4가지). */
	private static List<List<VoteInput>> tenDifferentBallots(List<MenuItem> menus) {
		List<List<VoteInput>> ballots = new ArrayList<>();
		for (MenuItem menu : menus) {
			ballots.add(List.of(new VoteInput(menu.id(), 1)));
			ballots.add(List.of(new VoteInput(menu.id(), -1)));
		}
		ballots.add(List.of(new VoteInput(menus.get(0).id(), 1), new VoteInput(menus.get(1).id(), 1)));
		ballots.add(List.of(new VoteInput(menus.get(0).id(), 1), new VoteInput(menus.get(1).id(), -1)));
		ballots.add(List.of(new VoteInput(menus.get(0).id(), -1), new VoteInput(menus.get(2).id(), -1)));
		ballots.add(List.of(new VoteInput(menus.get(1).id(), 1), new VoteInput(menus.get(2).id(), -1)));
		return ballots;
	}

	/** people명이 각자 다른 메뉴를 내고 모두 "합치기 끝냄"을 눌러 투표 단계가 된 방. 첫 번째 토큰이 방장이다. */
	private VotingRoom votingRoom(int people) {
		CreatedRoom created = roomService.create(PhaseDurations.defaults());
		List<String> tokens = new ArrayList<>(List.of(created.token()));
		for (int i = 1; i < people; i++) {
			tokens.add(roomService.join(created.code(), null).newToken());
		}
		phaseService.advanceByHost(created.code(), created.token(), RoomStatus.WAITING);
		for (int i = 0; i < people; i++) {
			proposalService.propose(created.code(), tokens.get(i), "메뉴" + i);
		}
		for (String token : tokens) {
			mergeService.finish(created.code(), token);
		}
		RoomState state = roomService.getRoom(created.code(), created.token()).room();
		assertThat(state.status()).isEqualTo(RoomStatus.VOTING);
		return new VotingRoom(created.code(), tokens, state.menus());
	}

	private List<Ballot> ballots(VotingRoom room) {
		Room entity = roomRepository.findByCode(room.code()).orElseThrow();
		return ballotRepository.findWithVotesByRoomAndVoteRound(entity, Ballot.FIRST_ROUND);
	}

	private record VotingRoom(String code, List<String> tokens, List<MenuItem> menus) {
	}

}
