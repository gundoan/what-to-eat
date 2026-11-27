package com.whattoeat.room.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 랜덤 선택의 뽑기: 카드 뽑기처럼 한 바퀴 안에서는 이미 뽑힌 사람을 다시 뽑지 않고, 모두 뽑히면 처음부터 다시 뽑는다.
 */
class PickTurnTest {

	private static final Instant NOW = Instant.parse("2026-11-27T03:00:00Z");

	private final Room room = Room.create("abcdefghij", PhaseDurations.defaults(), NOW);

	private final RandomGenerator random = new SecureRandom();

	@Test
	@DisplayName("첫 뽑기는 1번째 차례, 1바퀴이고, 뽑힌 사람이 정하는 중이다")
	void firstDraw() {
		List<Participant> people = people(3);

		PickTurn first = PickTurn.drawNext(room, people, List.of(), random, NOW);

		assertThat(first.getTurnNumber()).isEqualTo(1);
		assertThat(first.getCycle()).isEqualTo(1);
		assertThat(first.isActive()).isTrue();
		assertThat(people).anySatisfy(person -> assertThat(first.isFor(person)).isTrue());
	}

	@Test
	@DisplayName("한 바퀴 안에서는 이미 뽑힌 사람을 다시 뽑지 않아서, 사람 수만큼 뽑으면 모두 한 번씩 뽑힌다")
	void everyoneOncePerCycle() {
		List<Participant> people = people(4);
		List<PickTurn> turns = drawAndExpire(people, 4);

		Set<Long> picked = new HashSet<>();
		for (PickTurn turn : turns) {
			picked.add(turn.getParticipant().getId());
			assertThat(turn.getCycle()).isEqualTo(1);
		}
		assertThat(picked).hasSize(4);
	}

	@Test
	@DisplayName("모두 한 번씩 뽑혔는데 아무도 안 정했으면, 다음 바퀴로 넘어가 처음부터 전원 중에서 뽑는다")
	void startsOverAfterEveryoneWasPicked() {
		List<Participant> people = people(2);
		List<PickTurn> turns = drawAndExpire(people, 2);

		PickTurn next = PickTurn.drawNext(room, people, turns, random, NOW);

		assertThat(next.getCycle()).isEqualTo(2);
		assertThat(next.getTurnNumber()).isEqualTo(3);
	}

	@Test
	@DisplayName("중간에 들어온 사람은 지금 바퀴에서 아직 안 뽑힌 사람이라 바로 후보가 된다")
	void lateJoinerIsACandidate() {
		List<Participant> people = new ArrayList<>(people(1));
		List<PickTurn> turns = drawAndExpire(people, 1);
		Participant lateComer = participant(99);
		people.add(lateComer);

		PickTurn next = PickTurn.drawNext(room, people, turns, random, NOW);

		assertThat(next.isFor(lateComer)).as("1바퀴에서 아직 안 뽑힌 사람은 늦게 온 사람뿐이다").isTrue();
		assertThat(next.getCycle()).isEqualTo(1);
	}

	@Test
	@DisplayName("여러 번 뽑으면 모든 사람이 한 번 이상 첫 번째로 뽑힌다 (무작위)")
	void firstPickIsRandom() {
		List<Participant> people = people(3);
		Set<Long> firstPicked = new HashSet<>();

		for (int trial = 0; trial < 300; trial++) {
			firstPicked.add(PickTurn.drawNext(room, people, List.of(), random, NOW).getParticipant().getId());
		}

		// 세 명 중 한 명이 300번 내내 안 뽑힐 확률은 3 × (2/3)^300 이라 사실상 0이다.
		assertThat(firstPicked).hasSize(3);
	}

	@Test
	@DisplayName("정했거나 시간이 다 됐거나 방장이 넘긴 차례는 다시 끝낼 수 없다")
	void finishesOnlyOnce() {
		PickTurn decided = PickTurn.drawNext(room, people(1), List.of(), random, NOW);
		decided.decide(new MenuName("김밥"), NOW);
		PickTurn skipped = PickTurn.drawNext(room, people(1), List.of(), random, NOW);
		skipped.skip(NOW);

		assertThat(decided.getStatus()).isEqualTo(PickTurnStatus.DECIDED);
		assertThat(decided.getMenuName()).isEqualTo("김밥");
		assertThat(skipped.getStatus()).isEqualTo(PickTurnStatus.SKIPPED);
		assertThatThrownBy(() -> decided.expire(NOW)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> skipped.decide(new MenuName("라면"), NOW)).isInstanceOf(IllegalStateException.class);
	}

	/** count번 뽑고, 뽑을 때마다 아무도 안 정해서 시간이 다 된 것으로 끝낸다. */
	private List<PickTurn> drawAndExpire(List<Participant> people, int count) {
		List<PickTurn> turns = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			PickTurn turn = PickTurn.drawNext(room, people, turns, random, NOW);
			turn.expire(NOW);
			turns.add(turn);
		}
		return turns;
	}

	private List<Participant> people(int count) {
		List<Participant> people = new ArrayList<>();
		for (int i = 1; i <= count; i++) {
			people.add(participant(i));
		}
		return people;
	}

	private Participant participant(long id) {
		Participant participant = Participant.guest(room, "token-hash-" + id, NOW);
		ReflectionTestUtils.setField(participant, "id", id);
		return participant;
	}

}
