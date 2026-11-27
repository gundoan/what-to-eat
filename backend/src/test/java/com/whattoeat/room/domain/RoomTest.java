package com.whattoeat.room.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RoomTest {

	private static final Instant START = Instant.parse("2026-11-23T03:00:00Z");

	@Test
	@DisplayName("시작하면 제안 단계가 되고, 방장이 정한 제안 시간 뒤에 끝난다")
	void startProposalSetsDeadline() {
		Room room = Room.create("abcdefghij", PhaseDurations.of(90, null, null, null), START.minusSeconds(10));

		room.startProposal(START);

		assertThat(room.getStatus()).isEqualTo(RoomStatus.PROPOSING);
		assertThat(room.getPhaseStartedAt()).isEqualTo(START);
		assertThat(room.getPhaseEndsAt()).isEqualTo(START.plusSeconds(90));
	}

	@Test
	@DisplayName("끝나는 시각 1밀리초 전에는 아직이고, 끝나는 시각이 되면 시간이 다 된 것이다")
	void phaseExpiresExactlyAtDeadline() {
		Room room = proposingRoom(PhaseDurations.defaults());
		Instant deadline = room.getPhaseEndsAt();

		assertThat(room.isPhaseExpired(deadline.minusMillis(1))).isFalse();
		assertThat(room.isPhaseExpired(deadline)).isTrue();
	}

	@Test
	@DisplayName("대기 중에는 시간 제한이 없다")
	void waitingHasNoDeadline() {
		Room room = Room.create("abcdefghij", PhaseDurations.defaults(), START);

		assertThat(room.getPhaseEndsAt()).isNull();
		assertThat(room.isPhaseExpired(START.plusSeconds(60 * 60 * 24))).isFalse();
	}

	@Nested
	@DisplayName("제안이 끝날 때")
	class FinishProposal {

		@Test
		@DisplayName("메뉴가 2개 이상이면 합치기 단계가 되고, 방장이 정한 합치기 시간 뒤에 끝난다")
		void startsMergingWithTwoOrMoreMenus() {
			Room room = proposingRoom(PhaseDurations.of(null, 120, null, null));
			Instant finishedAt = START.plusSeconds(5);

			room.finishProposal(menus(room, 2), finishedAt);

			assertThat(room.getStatus()).isEqualTo(RoomStatus.MERGING);
			assertThat(room.getPhaseStartedAt()).isEqualTo(finishedAt);
			assertThat(room.getPhaseEndsAt()).isEqualTo(finishedAt.plusSeconds(120));
		}

		@Test
		@DisplayName("메뉴가 1개면 합치기도 투표도 없이 바로 그 메뉴로 결정되고, 0개면 랜덤 선택으로 간다")
		void decidesOrPicksRandomlyWithoutEnoughMenus() {
			Room oneMenu = proposingRoom(PhaseDurations.defaults());
			Room noMenu = proposingRoom(PhaseDurations.defaults());
			List<Menu> only = menus(oneMenu, 1);

			oneMenu.finishProposal(only, START.plusSeconds(5));
			noMenu.finishProposal(List.of(), START.plusSeconds(5));

			assertThat(oneMenu.getStatus()).isEqualTo(RoomStatus.FINISHED);
			assertThat(oneMenu.getResultMenu()).isSameAs(only.getFirst());
			assertThat(oneMenu.getResultName()).isEqualTo("메뉴1");
			assertThat(oneMenu.getResultReason()).isEqualTo(ResultReason.ONLY_MENU);
			assertThat(oneMenu.getPhaseEndsAt()).isNull();
			assertThat(noMenu.getStatus()).isEqualTo(RoomStatus.RANDOM_PICK);
			assertThat(noMenu.getPhaseEndsAt()).as("뽑힌 사람에게 랜덤 선택 시간(기본 1분)을 준다")
				.isEqualTo(START.plusSeconds(65));
			assertThat(noMenu.getResultMenu()).isNull();
		}

	}

	@Nested
	@DisplayName("합치기가 끝날 때")
	class FinishMerging {

		@Test
		@DisplayName("남은 메뉴가 2개 이상이면 투표 단계가 되고, 방장이 정한 투표 시간 뒤에 끝난다")
		void startsVotingWithTimer() {
			Room room = mergingRoom(PhaseDurations.of(null, null, 90, null));
			Instant finishedAt = START.plusSeconds(30);

			room.finishMerging(menus(room, 2), finishedAt);

			assertThat(room.getStatus()).isEqualTo(RoomStatus.VOTING);
			assertThat(room.getPhaseEndsAt()).isEqualTo(finishedAt.plusSeconds(90));
		}

		@Test
		@DisplayName("합쳐져서 메뉴가 1개만 남았으면 투표 없이 그 메뉴로 결정된다")
		void decidesWhenOneMenuRemains() {
			Room room = mergingRoom(PhaseDurations.defaults());
			List<Menu> remaining = menus(room, 1);

			room.finishMerging(remaining, START.plusSeconds(30));

			assertThat(room.getStatus()).isEqualTo(RoomStatus.FINISHED);
			assertThat(room.getResultMenu()).isSameAs(remaining.getFirst());
		}

	}

	@Nested
	@DisplayName("투표가 끝날 때")
	class FinishVoting {

		@Test
		@DisplayName("1등이 하나면 그 메뉴로 결정된다")
		void decidesUniqueLeader() {
			Room room = votingRoom();
			List<Menu> menus = menus(room, 2);
			Participant voter = participant(room, 1);

			room.finishVoting(VoteTally.count(menus, List.of(ballot(room, voter, menus.get(1), 1))), START);

			assertThat(room.getStatus()).isEqualTo(RoomStatus.FINISHED);
			assertThat(room.getResultMenu()).isSameAs(menus.get(1));
			assertThat(room.getResultReason()).isEqualTo(ResultReason.VOTE);
			assertThat(room.voteRound()).isEqualTo(Ballot.FIRST_ROUND);
		}

		@Test
		@DisplayName("1등이 둘 이상(동점)이면 재투표 단계가 되고, 투표와 같은 시간 뒤에 끝난다")
		void tieGoesToRevote() {
			Room room = votingRoom();
			List<Menu> menus = menus(room, 2);

			room.finishVoting(VoteTally.count(menus, List.of(ballot(room, participant(room, 1), menus.get(0), 1),
					ballot(room, participant(room, 2), menus.get(1), 1))), START);

			assertThat(room.getStatus()).isEqualTo(RoomStatus.REVOTING);
			assertThat(room.getPhaseEndsAt()).isEqualTo(START.plusSeconds(PhaseDurations.DEFAULT_VOTE_SECONDS));
			assertThat(room.voteRound()).isEqualTo(Ballot.REVOTE_ROUND);
			assertThat(room.getResultMenu()).isNull();
		}

		@Test
		@DisplayName("아무도 표를 내지 않았으면(모두 다 좋음) 랜덤 선택이 되고, 랜덤 선택 시간을 준다")
		void noVotesGoToRandomPick() {
			Room room = votingRoom();
			List<Menu> menus = menus(room, 2);

			room.finishVoting(VoteTally.count(menus,
					List.of(Ballot.allGood(room, participant(room, 1), Ballot.FIRST_ROUND, START))), START);

			assertThat(room.getStatus()).isEqualTo(RoomStatus.RANDOM_PICK);
			assertThat(room.getPhaseEndsAt()).isEqualTo(START.plusSeconds(PhaseDurations.DEFAULT_RANDOM_PICK_SECONDS));
		}

	}

	@Nested
	@DisplayName("재투표가 끝날 때")
	class FinishRevote {

		@Test
		@DisplayName("1등이 하나면 그 메뉴로 결정된다")
		void decidesUniqueLeader() {
			Room room = revotingRoom();
			List<Menu> candidates = menus(room, 2);

			room.finishRevote(VoteTally.count(candidates, List.of(revoteBallot(room, 1, candidates.get(0), 1))),
					candidates, alwaysPick(1), START);

			assertThat(room.getStatus()).isEqualTo(RoomStatus.FINISHED);
			assertThat(room.getResultMenu()).isSameAs(candidates.get(0));
			assertThat(room.getResultReason()).isEqualTo(ResultReason.REVOTE);
			assertThat(room.voteRound()).as("끝난 뒤에도 재투표로 정해졌다는 것을 안다").isEqualTo(Ballot.REVOTE_ROUND);
		}

		@Test
		@DisplayName("또 동점이면 재투표의 1등끼리 중에서 무작위로 결정되고, 재투표에서 밀린 메뉴는 뽑히지 않는다")
		void tieAgainPicksAmongRevoteLeaders() {
			Room room = revotingRoom();
			List<Menu> candidates = menus(room, 3);
			VoteTally tally = VoteTally.count(candidates, List.of(revoteBallot(room, 1, candidates.get(0), 1),
					revoteBallot(room, 2, candidates.get(2), 1), revoteBallot(room, 3, candidates.get(1), -1)));

			room.finishRevote(tally, candidates, alwaysPick(1), START); // 재투표 1등 [0번, 2번] 중 두 번째

			assertThat(room.getResultMenu()).isSameAs(candidates.get(2));
			assertThat(room.getResultReason()).isEqualTo(ResultReason.REVOTE_RANDOM);
		}

		@Test
		@DisplayName("재투표에서 아무도 표를 내지 않았으면 재투표 후보 전체 중에서 무작위로 결정된다")
		void noVotesPicksAmongAllCandidates() {
			Room room = revotingRoom();
			List<Menu> candidates = menus(room, 3);

			room.finishRevote(VoteTally.count(candidates, List.of()), candidates, alwaysPick(2), START);

			assertThat(room.getResultMenu()).isSameAs(candidates.get(2));
			assertThat(room.getResultReason()).isEqualTo(ResultReason.REVOTE_RANDOM);
		}

	}

	@Nested
	@DisplayName("랜덤 선택")
	class RandomPick {

		@Test
		@DisplayName("다시 뽑으면 새로 뽑힌 사람에게 랜덤 선택 시간을 처음부터 준다")
		void restartsTimerOnRedraw() {
			Room room = randomPickRoom(PhaseDurations.of(null, null, null, 90));

			room.restartPickTimer(START.plusSeconds(200));

			assertThat(room.getStatus()).isEqualTo(RoomStatus.RANDOM_PICK);
			assertThat(room.getPhaseEndsAt()).isEqualTo(START.plusSeconds(290));
		}

		@Test
		@DisplayName("모두 한 번씩 뽑혀 자동 다시 뽑기를 멈추면 시간 제한이 없어지고, 단계는 그대로다")
		void stopsTimerAfterEveryoneWasPicked() {
			Room room = randomPickRoom(PhaseDurations.defaults());

			room.stopPickTimer();

			assertThat(room.getStatus()).isEqualTo(RoomStatus.RANDOM_PICK);
			assertThat(room.getPhaseEndsAt()).isNull();
			assertThat(room.isPhaseExpired(START.plusSeconds(100_000))).isFalse();
		}

		@Test
		@DisplayName("뽑힌 사람이 정한 이름으로 결정되고, 메뉴 목록에 없는 이름이어도 된다")
		void decidesWithPickedName() {
			Room room = randomPickRoom(PhaseDurations.defaults());

			room.decideByPick(new MenuName("  아무거나 국밥  "), START.plusSeconds(10));

			assertThat(room.getStatus()).isEqualTo(RoomStatus.FINISHED);
			assertThat(room.getResultName()).isEqualTo("아무거나 국밥");
			assertThat(room.getResultMenu()).isNull();
			assertThat(room.getResultReason()).isEqualTo(ResultReason.PICKED);
			assertThat(room.getPhaseEndsAt()).isNull();
		}

	}

	@Test
	@DisplayName("단계 순서를 건너뛸 수 없다")
	void cannotSkipPhases() {
		Room waiting = Room.create("abcdefghij", PhaseDurations.defaults(), START);
		Room proposing = proposingRoom(PhaseDurations.defaults());

		assertThatThrownBy(() -> waiting.finishProposal(menus(waiting, 2), START))
			.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> proposing.startProposal(START)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> proposing.finishMerging(menus(proposing, 2), START))
			.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> proposing.finishVoting(VoteTally.count(List.of(), List.of()), START))
			.isInstanceOf(IllegalStateException.class);
	}

	private static Room proposingRoom(PhaseDurations durations) {
		Room room = Room.create("abcdefghij", durations, START.minusSeconds(10));
		room.startProposal(START);
		return room;
	}

	private static Room mergingRoom(PhaseDurations durations) {
		Room room = proposingRoom(durations);
		room.finishProposal(menus(room, 2), START.plusSeconds(5));
		return room;
	}

	private static Room votingRoom() {
		Room room = mergingRoom(PhaseDurations.defaults());
		room.finishMerging(menus(room, 2), START.plusSeconds(10));
		return room;
	}

	private static Room revotingRoom() {
		Room room = votingRoom();
		List<Menu> menus = menus(room, 2);
		room.finishVoting(VoteTally.count(menus, List.of(ballot(room, participant(room, 1), menus.get(0), 1),
				ballot(room, participant(room, 2), menus.get(1), 1))), START);
		return room;
	}

	private static Room randomPickRoom(PhaseDurations durations) {
		Room room = proposingRoom(durations);
		room.finishProposal(List.of(), START.plusSeconds(5));
		return room;
	}

	private static Ballot revoteBallot(Room room, long voterId, Menu menu, int point) {
		return Ballot.of(room, participant(room, voterId), Ballot.REVOTE_ROUND, List.of(new VoteChoice(menu, point)),
				START);
	}

	/** 항상 index번째를 고르는 난수. 무작위 결과를 정해 두고 나머지 동작을 확인하려고 쓴다. */
	private static RandomGenerator alwaysPick(int index) {
		return new RandomGenerator() {

			@Override
			public long nextLong() {
				return index;
			}

			@Override
			public int nextInt(int bound) {
				return index;
			}

		};
	}

	/** 번호가 붙은 메뉴 count개. DB 없이 테스트하므로 DB가 붙여 주는 번호를 직접 넣는다. */
	private static List<Menu> menus(Room room, int count) {
		List<Menu> menus = new ArrayList<>();
		for (int i = 1; i <= count; i++) {
			Menu menu = Menu.propose(room, participant(room, 100 + i), new MenuName("메뉴" + i), START);
			ReflectionTestUtils.setField(menu, "id", (long) i);
			menus.add(menu);
		}
		return menus;
	}

	private static Participant participant(Room room, long id) {
		Participant participant = Participant.guest(room, "token-hash-" + id, START);
		ReflectionTestUtils.setField(participant, "id", id);
		return participant;
	}

	private static Ballot ballot(Room room, Participant voter, Menu menu, int point) {
		return Ballot.of(room, voter, Ballot.FIRST_ROUND, List.of(new VoteChoice(menu, point)), START);
	}

}
