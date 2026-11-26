package com.whattoeat.room.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import com.whattoeat.common.error.BusinessException;
import com.whattoeat.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 투표지 규칙: 한 사람 최대 2표, 표마다 +1 또는 -1, 한 메뉴에 1표.
 */
class BallotTest {

	private static final Instant NOW = Instant.parse("2026-11-26T03:00:00Z");

	private final Room room = Room.create("abcdefghij", PhaseDurations.defaults(), NOW);

	private final Participant voter = Participant.guest(room, "token-hash", NOW);

	private final Menu jeyuk = menu(1, "제육볶음");

	private final Menu kimbap = menu(2, "김밥");

	private final Menu ramen = menu(3, "라면");

	@Test
	@DisplayName("1표나 2표를 낼 수 있고, +1 두 개, -1 두 개, 섞기 모두 된다")
	void acceptsOneOrTwoVotes() {
		assertThat(ballot(new VoteChoice(jeyuk, 1)).getVotes()).hasSize(1);
		assertThat(ballot(new VoteChoice(jeyuk, 1), new VoteChoice(kimbap, 1)).getVotes()).hasSize(2);
		assertThat(ballot(new VoteChoice(jeyuk, -1), new VoteChoice(kimbap, -1)).getVotes()).hasSize(2);
		assertThat(ballot(new VoteChoice(jeyuk, 1), new VoteChoice(kimbap, -1)).getVotes()).hasSize(2);
	}

	@Test
	@DisplayName("3표 이상이나 0표는 낼 수 없다 (안 찍으려면 다 좋음)")
	void rejectsTooManyOrNoVotes() {
		assertInvalid(() -> ballot(new VoteChoice(jeyuk, 1), new VoteChoice(kimbap, 1), new VoteChoice(ramen, -1)));
		assertInvalid(() -> ballot());
	}

	@Test
	@DisplayName("한 메뉴에는 1표만 줄 수 있다")
	void rejectsTwoVotesOnOneMenu() {
		assertInvalid(() -> ballot(new VoteChoice(jeyuk, 1), new VoteChoice(jeyuk, 1)));
		assertInvalid(() -> ballot(new VoteChoice(jeyuk, 1), new VoteChoice(jeyuk, -1)));
	}

	@Test
	@DisplayName("표는 +1 또는 -1만 된다")
	void rejectsOtherPoints() {
		assertInvalid(() -> new VoteChoice(jeyuk, 2));
		assertInvalid(() -> new VoteChoice(jeyuk, 0));
		assertInvalid(() -> new VoteChoice(null, 1));
	}

	@Test
	@DisplayName("같은 내용인지는 표의 순서와 상관없이 비교하고, 다 좋음은 다 좋음끼리만 같다")
	void comparesContentRegardlessOfOrder() {
		Ballot twoVotes = ballot(new VoteChoice(jeyuk, 1), new VoteChoice(kimbap, -1));
		Ballot allGood = Ballot.allGood(room, voter, Ballot.FIRST_ROUND, NOW);

		assertThat(twoVotes.hasSameContent(false, List.of(new VoteChoice(kimbap, -1), new VoteChoice(jeyuk, 1))))
			.isTrue();
		assertThat(twoVotes.hasSameContent(false, List.of(new VoteChoice(jeyuk, 1)))).isFalse();
		assertThat(twoVotes.hasSameContent(false, List.of(new VoteChoice(jeyuk, 1), new VoteChoice(kimbap, 1))))
			.isFalse();
		assertThat(twoVotes.hasSameContent(true, List.of())).isFalse();
		assertThat(allGood.hasSameContent(true, List.of())).isTrue();
		assertThat(allGood.hasSameContent(false, List.of(new VoteChoice(jeyuk, 1)))).isFalse();
	}

	private Ballot ballot(VoteChoice... choices) {
		return Ballot.of(room, voter, Ballot.FIRST_ROUND, List.of(choices), NOW);
	}

	private static void assertInvalid(Runnable action) {
		assertThatThrownBy(action::run).isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).errorCode())
			.isEqualTo(ErrorCode.INVALID_BALLOT);
	}

	private Menu menu(long id, String name) {
		Menu menu = Menu.propose(room, voter, new MenuName(name), NOW);
		ReflectionTestUtils.setField(menu, "id", id);
		return menu;
	}

}
