package com.whattoeat.room.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

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
			assertThat(participant.hasFinished(RoomStatus.PROPOSING, null)).isFalse();
			assertThat(participant.hasFinished(RoomStatus.PROPOSING, menu(1))).isTrue();

			participant.markProposalAllGood();
			assertThat(participant.hasFinished(RoomStatus.PROPOSING, null)).isTrue();
		}

	}

	@Nested
	@DisplayName("합치기 단계")
	class Merging {

		@Test
		@DisplayName("메뉴가 남아 있으면 합치기 끝냄을 눌러야 끝낸 것이다")
		void menuOwnerMustPressDone() {
			Menu mine = menu(1);
			assertThat(participant.hasFinished(RoomStatus.MERGING, mine)).isFalse();

			participant.markMergeDone();
			assertThat(participant.hasFinished(RoomStatus.MERGING, mine)).isTrue();
		}

		@Test
		@DisplayName("메뉴를 내지 않았거나 메뉴가 다른 메뉴에 합쳐졌으면 처음부터 끝낸 것이다")
		void finishedWithoutMenuToMerge() {
			Menu mine = menu(1);
			mine.mergeInto(menu(2));

			assertThat(participant.hasFinished(RoomStatus.MERGING, null)).isTrue();
			assertThat(participant.hasFinished(RoomStatus.MERGING, mine)).isTrue();
		}

	}

	@Test
	@DisplayName("대기 중과 아직 만들지 않은 단계(투표, 랜덤 선택)에서는 끝낸 것으로 보지 않는다")
	void otherPhasesAreNeverFinished() {
		participant.markProposalAllGood();
		participant.markMergeDone();

		assertThat(participant.hasFinished(RoomStatus.WAITING, null)).isFalse();
		assertThat(participant.hasFinished(RoomStatus.VOTING, null)).isFalse();
		assertThat(participant.hasFinished(RoomStatus.RANDOM_PICK, null)).isFalse();
	}

	private Menu menu(long id) {
		Menu menu = Menu.propose(room, participant, new MenuName("메뉴" + id), NOW);
		ReflectionTestUtils.setField(menu, "id", id);
		return menu;
	}

}
