package com.whattoeat.room.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
		Room room = proposingRoom();
		Instant deadline = room.getPhaseEndsAt();

		assertThat(room.isPhaseExpired(deadline.minusMillis(1))).isFalse();
		assertThat(room.isPhaseExpired(deadline)).isTrue();
	}

	@Test
	@DisplayName("제안이 끝날 때 메뉴가 있으면 합치기, 없으면 랜덤 선택으로 간다")
	void finishProposalChoosesNextPhase() {
		Room withMenus = proposingRoom();
		Room withoutMenus = proposingRoom();

		withMenus.finishProposal(true, START.plusSeconds(5));
		withoutMenus.finishProposal(false, START.plusSeconds(5));

		assertThat(withMenus.getStatus()).isEqualTo(RoomStatus.MERGING);
		assertThat(withoutMenus.getStatus()).isEqualTo(RoomStatus.RANDOM_PICK);
		assertThat(withMenus.getPhaseEndsAt()).isNull();
	}

	@Test
	@DisplayName("단계 순서를 건너뛸 수 없다")
	void cannotSkipPhases() {
		Room waiting = Room.create("abcdefghij", PhaseDurations.defaults(), START);
		Room proposing = proposingRoom();

		assertThatThrownBy(() -> waiting.finishProposal(true, START)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> proposing.startProposal(START)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("대기 중에는 시간 제한이 없다")
	void waitingHasNoDeadline() {
		Room room = Room.create("abcdefghij", PhaseDurations.defaults(), START);

		assertThat(room.getPhaseEndsAt()).isNull();
		assertThat(room.isPhaseExpired(START.plusSeconds(60 * 60 * 24))).isFalse();
	}

	private static Room proposingRoom() {
		Room room = Room.create("abcdefghij", PhaseDurations.defaults(), START.minusSeconds(10));
		room.startProposal(START);
		return room;
	}

}
