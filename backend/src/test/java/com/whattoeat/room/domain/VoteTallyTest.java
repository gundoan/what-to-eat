package com.whattoeat.room.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.whattoeat.room.domain.VoteTally.MenuScore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 점수 세기: 총점(+1 개수 - -1 개수)이 가장 높은 메뉴가 1등이다.
 */
class VoteTallyTest {

	private static final Instant NOW = Instant.parse("2026-11-26T03:00:00Z");

	private final Room room = Room.create("abcdefghij", PhaseDurations.defaults(), NOW);

	private final Menu jeyuk = menu(1, "제육볶음");

	private final Menu kimbap = menu(2, "김밥");

	private final Menu ramen = menu(3, "라면");

	private final List<Menu> candidates = List.of(kimbap, ramen, jeyuk);

	private long nextVoterId = 100;

	@Test
	@DisplayName("메뉴마다 +1과 -1을 세고, 총점이 가장 높은 메뉴가 1등이다")
	void countsPlusAndMinus() {
		VoteTally tally = VoteTally.count(candidates, List.of(
				ballot(new VoteChoice(jeyuk, 1), new VoteChoice(kimbap, -1)),
				ballot(new VoteChoice(jeyuk, 1), new VoteChoice(ramen, 1)),
				ballot(new VoteChoice(kimbap, 1))));

		assertThat(tally.leaders()).containsExactly(jeyuk);
		assertThat(tally.ranking()).extracting(MenuScore::menu).containsExactly(jeyuk, ramen, kimbap);
		MenuScore kimbapScore = tally.ranking().get(2);
		assertThat(kimbapScore.plus()).isEqualTo(1);
		assertThat(kimbapScore.minus()).isEqualTo(1);
		assertThat(kimbapScore.total()).isZero();
	}

	@Test
	@DisplayName("전부 마이너스여도 가장 높은(덜 싫은) 메뉴가 1등이다")
	void leastDislikedWinsWhenAllNegative() {
		VoteTally tally = VoteTally.count(List.of(jeyuk, kimbap), List.of(
				ballot(new VoteChoice(jeyuk, -1), new VoteChoice(kimbap, -1)),
				ballot(new VoteChoice(jeyuk, -1))));

		assertThat(tally.leaders()).containsExactly(kimbap);
	}

	@Test
	@DisplayName("표를 하나도 못 받은 메뉴는 0점이라, 다른 메뉴가 모두 마이너스면 1등이 될 수 있다")
	void menuWithoutVotesScoresZero() {
		VoteTally tally = VoteTally.count(candidates, List.of(
				ballot(new VoteChoice(jeyuk, -1), new VoteChoice(kimbap, -1))));

		assertThat(tally.leaders()).containsExactly(ramen);
	}

	@Test
	@DisplayName("1등이 여럿이면 동점이고, 동점 메뉴는 원래 순서(가나다순)로 돌려준다")
	void reportsTies() {
		VoteTally tally = VoteTally.count(candidates, List.of(
				ballot(new VoteChoice(jeyuk, 1)),
				ballot(new VoteChoice(kimbap, 1))));

		assertThat(tally.leaders()).containsExactly(kimbap, jeyuk);
	}

	@Test
	@DisplayName("다 좋음 투표지만 있거나 투표지가 없으면 표가 없는 것이고, 1등도 없다")
	void noVotesMeansNoLeader() {
		VoteTally allGood = VoteTally.count(candidates,
				List.of(Ballot.allGood(room, voter(), Ballot.FIRST_ROUND, NOW)));
		VoteTally nobody = VoteTally.count(candidates, List.of());

		assertThat(allGood.hasNoVotes()).isTrue();
		assertThat(allGood.leaders()).isEmpty();
		assertThat(nobody.hasNoVotes()).isTrue();
	}

	@Test
	@DisplayName("일부만 다 좋음을 누르면 나머지 사람들의 표로 정해진다")
	void allGoodBallotsDoNotCount() {
		VoteTally tally = VoteTally.count(candidates, List.of(
				Ballot.allGood(room, voter(), Ballot.FIRST_ROUND, NOW),
				ballot(new VoteChoice(ramen, 1))));

		assertThat(tally.hasNoVotes()).isFalse();
		assertThat(tally.leaders()).containsExactly(ramen);
	}

	private Ballot ballot(VoteChoice... choices) {
		return Ballot.of(room, voter(), Ballot.FIRST_ROUND, new ArrayList<>(List.of(choices)), NOW);
	}

	private Participant voter() {
		Participant voter = Participant.guest(room, "token-hash-" + nextVoterId, NOW);
		ReflectionTestUtils.setField(voter, "id", nextVoterId++);
		return voter;
	}

	private Menu menu(long id, String name) {
		Menu menu = Menu.propose(room, Participant.guest(room, "owner-" + id, NOW), new MenuName(name), NOW);
		ReflectionTestUtils.setField(menu, "id", id);
		return menu;
	}

}
