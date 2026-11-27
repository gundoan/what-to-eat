package com.whattoeat.room.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * "이 단계에서 할 일을 끝냈는가"의 기준. "N명 중 M명 끝냄"과 "모두 끝내면 바로 다음 단계로"가 이 기준을 같이 쓴다.
 */
class ParticipantTest {

	private static final Instant NOW = Instant.parse("2026-11-25T03:00:00Z");

	private final Room room = Room.create("abcdefghij", PhaseDurations.defaults(), NOW);

	private final Participant participant = Participant.guest(room, "token-hash", NOW);

	@Nested
	@DisplayName("제안 단계")
	class Proposing {

		@Test
		@DisplayName("메뉴를 냈거나 다 좋음을 눌렀으면 끝낸 것이다")
		void finishedWhenProposedOrAllGood() {
			assertThat(participant.hasFinished(RoomStatus.PROPOSING, null, null)).isFalse();
			assertThat(participant.hasFinished(RoomStatus.PROPOSING, menu(1), null)).isTrue();

			participant.markProposalAllGood();
			assertThat(participant.hasFinished(RoomStatus.PROPOSING, null, null)).isTrue();
		}

	}

	@Nested
	@DisplayName("합치기 단계")
	class Merging {

		@Test
		@DisplayName("메뉴가 남아 있으면 합치기 끝냄을 눌러야 끝낸 것이다")
		void menuOwnerMustPressDone() {
			Menu mine = menu(1);
			assertThat(participant.hasFinished(RoomStatus.MERGING, mine, null)).isFalse();

			participant.markMergeDone();
			assertThat(participant.hasFinished(RoomStatus.MERGING, mine, null)).isTrue();
		}

		@Test
		@DisplayName("메뉴를 내지 않았거나 메뉴가 다른 메뉴에 합쳐졌으면 처음부터 끝낸 것이다")
		void finishedWithoutMenuToMerge() {
			Menu mine = menu(1);
			mine.mergeInto(menu(2));

			assertThat(participant.hasFinished(RoomStatus.MERGING, null, null)).isTrue();
			assertThat(participant.hasFinished(RoomStatus.MERGING, mine, null)).isTrue();
		}

	}

	@Nested
	@DisplayName("투표 단계")
	class Voting {

		@Test
		@DisplayName("표를 냈거나 다 좋음을 눌렀으면(투표지를 냈으면) 끝낸 것이다")
		void finishedWhenBallotSubmitted() {
			Menu menu = menu(1);

			assertThat(participant.hasFinished(RoomStatus.VOTING, menu, null)).isFalse();
			assertThat(participant.hasFinished(RoomStatus.VOTING, menu,
					Ballot.of(room, participant, Ballot.FIRST_ROUND, List.of(new VoteChoice(menu, 1)), NOW)))
				.isTrue();
			assertThat(participant.hasFinished(RoomStatus.VOTING, null,
					Ballot.allGood(room, participant, Ballot.FIRST_ROUND, NOW)))
				.isTrue();
		}

		@Test
		@DisplayName("재투표도 이번 판(재투표)의 투표지를 냈으면 끝낸 것이다")
		void finishedWhenRevoteBallotSubmitted() {
			assertThat(participant.hasFinished(RoomStatus.REVOTING, null, null)).isFalse();
			assertThat(participant.hasFinished(RoomStatus.REVOTING, null,
					Ballot.allGood(room, participant, Ballot.REVOTE_ROUND, NOW)))
				.isTrue();
		}

	}

	@Test
	@DisplayName("대기 중, 랜덤 선택(뽑힌 한 사람만 할 일이 있음), 끝난 방에서는 끝낸 것으로 보지 않는다")
	void otherPhasesAreNeverFinished() {
		participant.markProposalAllGood();
		participant.markMergeDone();
		Ballot ballot = Ballot.allGood(room, participant, Ballot.FIRST_ROUND, NOW);

		assertThat(participant.hasFinished(RoomStatus.WAITING, null, ballot)).isFalse();
		assertThat(participant.hasFinished(RoomStatus.RANDOM_PICK, null, ballot)).isFalse();
		assertThat(participant.hasFinished(RoomStatus.FINISHED, null, ballot)).isFalse();
	}

	private Menu menu(long id) {
		Menu menu = Menu.propose(room, participant, new MenuName("메뉴" + id), NOW);
		ReflectionTestUtils.setField(menu, "id", id);
		return menu;
	}

}
