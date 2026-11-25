package com.whattoeat.room.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

import com.whattoeat.common.error.BusinessException;
import com.whattoeat.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MergeGroupTest {

	private static final Instant NOW = Instant.parse("2026-11-25T03:00:00Z");

	private static final Room ROOM = Room.create("abcdefghij", PhaseDurations.defaults(), NOW);

	@Test
	@DisplayName("메뉴가 2개 미만이거나 같은 메뉴가 두 번 들어가면 그룹을 만들 수 없다")
	void needsAtLeastTwoDifferentMenus() {
		Menu a = menu(1, "제육볶음");

		assertThatThrownBy(() -> MergeGroup.auto(ROOM, List.of(a), NOW)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> MergeGroup.auto(ROOM, List.of(a, a), NOW))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("한 번 한 답은 바꿀 수 없고, 같은 답을 다시 보내면 아무것도 바뀌지 않는다")
	void answerCannotBeChanged() {
		Menu a = menu(1, "제육볶음");
		MergeGroup group = MergeGroup.auto(ROOM, List.of(a, menu(2, "제육 볶음")), NOW);

		assertThat(group.answer(a, true, NOW)).isTrue();
		assertThat(group.answer(a, true, NOW)).isFalse();
		assertThatThrownBy(() -> group.answer(a, false, NOW)).isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).errorCode())
			.isEqualTo(ErrorCode.ALREADY_ANSWERED);
		assertThat(group.getAnsweredCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("그룹에 없는 메뉴로는 답할 수 없다")
	void onlyMembersCanAnswer() {
		MergeGroup group = MergeGroup.auto(ROOM, List.of(menu(1, "제육볶음"), menu(2, "제육 볶음")), NOW);

		assertThatThrownBy(() -> group.answer(menu(3, "김밥"), true, NOW)).isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).errorCode())
			.isEqualTo(ErrorCode.NOT_MERGE_MEMBER);
	}

	@Test
	@DisplayName("모두 답해야 '모두 답했다'가 된다")
	void knowsWhenEveryoneAnswered() {
		Menu a = menu(1, "제육볶음");
		Menu b = menu(2, "제육 볶음");
		MergeGroup group = MergeGroup.auto(ROOM, List.of(a, b), NOW);

		group.answer(a, true, NOW);
		assertThat(group.isEveryoneAnswered()).isFalse();
		assertThat(group.isWaitingFor(b)).isTrue();

		group.answer(b, false, NOW);
		assertThat(group.isEveryoneAnswered()).isTrue();
	}

	@Test
	@DisplayName("동의한 메뉴가 2개 이상이면 그중 뽑힌 하나가 대표가 되고, 나머지 동의한 메뉴만 대표에 합쳐진다")
	void mergesAgreedMenusIntoChosenRepresentative() {
		Menu a = menu(1, "제육볶음");
		Menu b = menu(2, "제육 볶음");
		Menu c = menu(3, "제육볶음");
		MergeGroup group = MergeGroup.auto(ROOM, List.of(a, b, c), NOW);
		group.answer(a, true, NOW);
		group.answer(b, true, NOW);
		group.answer(c, false, NOW);

		group.resolve(alwaysPick(1), NOW); // 동의한 메뉴 [a, b] 중 두 번째

		assertThat(group.getStatus()).isEqualTo(MergeGroupStatus.MERGED);
		assertThat(group.getRepresentative()).isSameAs(b);
		assertThat(a.getMergedInto()).isSameAs(b);
		assertThat(b.isMerged()).isFalse();
		assertThat(c.isMerged()).as("거절한 메뉴는 따로 남는다").isFalse();
	}

	@Test
	@DisplayName("동의한 메뉴가 1개뿐이면 합치지 않는다")
	void doesNotMergeWithSingleAgreement() {
		Menu a = menu(1, "제육볶음");
		Menu b = menu(2, "제육 볶음");
		MergeGroup group = MergeGroup.auto(ROOM, List.of(a, b), NOW);
		group.answer(a, true, NOW);
		group.answer(b, false, NOW);

		group.resolve(new SecureRandom(), NOW);

		assertThat(group.getStatus()).isEqualTo(MergeGroupStatus.NOT_MERGED);
		assertThat(group.getRepresentative()).isNull();
		assertThat(a.isMerged()).isFalse();
		assertThat(b.isMerged()).isFalse();
	}

	@Test
	@DisplayName("답하지 않은 사람은 '합치지 않기'로 보고 결과를 낸다")
	void treatsWaitingMembersAsDeclined() {
		Menu a = menu(1, "제육볶음");
		Menu b = menu(2, "제육 볶음");
		Menu c = menu(3, "제육볶음");
		MergeGroup group = MergeGroup.auto(ROOM, List.of(a, b, c), NOW);
		group.answer(a, true, NOW);
		group.answer(b, true, NOW);

		group.resolve(alwaysPick(0), NOW);

		assertThat(group.isWaitingFor(c)).isFalse();
		assertThat(group.getAnsweredCount()).isEqualTo(3);
		assertThat(group.getRepresentative()).isSameAs(a);
		assertThat(b.getMergedInto()).isSameAs(a);
		assertThat(c.isMerged()).isFalse();
	}

	@Test
	@DisplayName("이미 결과가 나온 그룹은 다시 결과를 낼 수 없다")
	void resolvesOnlyOnce() {
		MergeGroup group = MergeGroup.auto(ROOM, List.of(menu(1, "제육볶음"), menu(2, "제육 볶음")), NOW);
		group.resolve(new SecureRandom(), NOW);

		assertThatThrownBy(() -> group.resolve(new SecureRandom(), NOW)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("여러 번 뽑으면 동의한 메뉴가 모두 한 번 이상 대표가 된다 (대표는 무작위로 뽑는다)")
	void representativeIsRandomAmongAgreedMenus() {
		RandomGenerator random = new SecureRandom();
		Set<String> chosenNames = new HashSet<>();
		for (int trial = 0; trial < 200; trial++) {
			Menu a = menu(1, "제육볶음");
			Menu b = menu(2, "제육 볶음");
			Menu declined = menu(3, "제육볶음!");
			MergeGroup group = MergeGroup.auto(ROOM, List.of(a, b, declined), NOW);
			group.answer(a, true, NOW);
			group.answer(b, true, NOW);
			group.answer(declined, false, NOW);

			group.resolve(random, NOW);
			chosenNames.add(group.getRepresentative().getName());
		}
		// 둘 중 하나가 200번 내내 안 뽑힐 확률은 2 × (1/2)^200 이라 사실상 0이다. 거절한 메뉴는 절대 뽑히지 않는다.
		assertThat(chosenNames).containsExactlyInAnyOrder("제육볶음", "제육 볶음");
	}

	private static Menu menu(long id, String name) {
		Participant owner = Participant.guest(ROOM, "token-hash-" + id, NOW);
		Menu menu = Menu.propose(ROOM, owner, new MenuName(name), NOW);
		// DB 없이 테스트하므로, DB가 붙여 주는 번호를 직접 넣는다.
		ReflectionTestUtils.setField(menu, "id", id);
		return menu;
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

}
