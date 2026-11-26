package com.whattoeat.room.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;

import com.whattoeat.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 직접 합치기를 요청할 수 있는지에 대한 규칙. 규칙마다 거절 이유가 맞게 나오는지 확인한다.
 */
class MergeRequestRulesTest {

	private static final Instant NOW = Instant.parse("2026-11-26T03:00:00Z");

	private final Room room = Room.create("abcdefghij", PhaseDurations.defaults(), NOW);

	private final Participant alice = participant(1);

	private final Participant bob = participant(2);

	private final Participant carol = participant(3);

	private final Menu aliceMenu = menu(11, alice);

	private final Menu bobMenu = menu(12, bob);

	private final Menu carolMenu = menu(13, carol);

	private final List<Participant> everyone = List.of(alice, bob, carol);

	@Test
	@DisplayName("두 메뉴 모두 걸린 데가 없으면 요청할 수 있다")
	void allowsFreeMenus() {
		MergeRequestRules rules = MergeRequestRules.of(everyone, List.of());

		assertThat(rules.check(alice, aliceMenu, bobMenu)).isEmpty();
	}

	@Test
	@DisplayName("메뉴를 내지 않았거나 내 메뉴가 이미 합쳐졌으면 요청할 수 없다")
	void requiresMyRemainingMenu() {
		MergeRequestRules rules = MergeRequestRules.of(everyone, List.of());
		aliceMenu.mergeInto(carolMenu);

		assertThat(rules.check(bob, null, carolMenu)).hasValue(ErrorCode.NO_MENU_TO_MERGE);
		assertThat(rules.check(alice, aliceMenu, bobMenu)).hasValue(ErrorCode.NO_MENU_TO_MERGE);
	}

	@Test
	@DisplayName("없는 메뉴, 합쳐져 사라진 메뉴, 내 메뉴에는 요청할 수 없다")
	void requiresAnotherRemainingMenu() {
		MergeRequestRules rules = MergeRequestRules.of(everyone, List.of());
		carolMenu.mergeInto(bobMenu);

		assertThat(rules.check(alice, aliceMenu, null)).hasValue(ErrorCode.MENU_NOT_FOUND);
		assertThat(rules.check(alice, aliceMenu, carolMenu)).hasValue(ErrorCode.MENU_NOT_FOUND);
		assertThat(rules.check(alice, aliceMenu, aliceMenu)).hasValue(ErrorCode.CANNOT_MERGE_WITH_OWN_MENU);
	}

	@Test
	@DisplayName("합치기를 끝낸 사람은 요청할 수 없고, 끝낸 사람의 메뉴에도 요청할 수 없다")
	void finishedPeopleAreOut() {
		bob.markMergeDone();
		MergeRequestRules rules = MergeRequestRules.of(everyone, List.of());

		assertThat(rules.check(bob, bobMenu, aliceMenu)).hasValue(ErrorCode.MERGE_ALREADY_FINISHED);
		assertThat(rules.check(alice, aliceMenu, bobMenu)).hasValue(ErrorCode.TARGET_FINISHED_MERGING);
	}

	@Test
	@DisplayName("결과가 안 나온 자동 그룹에 든 메뉴는 요청을 보낼 수도 받을 수도 없다")
	void menusInWaitingAutoGroupAreBusy() {
		MergeGroup waiting = MergeGroup.auto(room, List.of(aliceMenu, bobMenu), NOW);
		MergeRequestRules rules = MergeRequestRules.of(everyone, List.of(waiting));

		assertThat(rules.check(alice, aliceMenu, carolMenu)).hasValue(ErrorCode.MENU_BUSY);
		assertThat(rules.check(carol, carolMenu, bobMenu)).hasValue(ErrorCode.MENU_BUSY);
	}

	@Test
	@DisplayName("한 메뉴는 한 번에 요청 하나에만 걸린다 (보내는 쪽, 받는 쪽 모두)")
	void menuTakesOneRequestAtATime() {
		MergeGroup aliceToBob = MergeGroup.direct(room, aliceMenu, bobMenu, NOW);
		MergeRequestRules rules = MergeRequestRules.of(everyone, List.of(aliceToBob));

		assertThat(rules.check(carol, carolMenu, bobMenu)).as("이미 요청받은 메뉴").hasValue(ErrorCode.MENU_BUSY);
		assertThat(rules.check(carol, carolMenu, aliceMenu)).as("이미 요청을 보낸 메뉴").hasValue(ErrorCode.MENU_BUSY);
		assertThat(rules.check(alice, aliceMenu, carolMenu)).as("보낸 사람이 또 보내기").hasValue(ErrorCode.MENU_BUSY);
		assertThat(rules.check(bob, bobMenu, carolMenu)).as("받은 사람이 다른 곳에 보내기").hasValue(ErrorCode.MENU_BUSY);
	}

	@Test
	@DisplayName("거절된 직접 요청의 두 메뉴 사이에는 방향과 상관없이 다시 요청할 수 없다")
	void declinedPairCannotBeRequestedAgain() {
		MergeGroup aliceToBob = MergeGroup.direct(room, aliceMenu, bobMenu, NOW);
		aliceToBob.answer(bobMenu, false, NOW);
		aliceToBob.resolve(new SecureRandom(), NOW);
		MergeRequestRules rules = MergeRequestRules.of(everyone, List.of(aliceToBob));

		assertThat(rules.check(alice, aliceMenu, bobMenu)).hasValue(ErrorCode.ALREADY_DECLINED);
		assertThat(rules.check(bob, bobMenu, aliceMenu)).hasValue(ErrorCode.ALREADY_DECLINED);
		assertThat(rules.check(alice, aliceMenu, carolMenu)).as("다른 메뉴에는 요청할 수 있다").isEmpty();
	}

	@Test
	@DisplayName("자동 그룹에서 안 합쳐진 메뉴끼리는 직접 요청할 수 있다")
	void notMergedAutoGroupDoesNotBlockRequests() {
		MergeGroup autoGroup = MergeGroup.auto(room, List.of(aliceMenu, bobMenu), NOW);
		autoGroup.answer(aliceMenu, true, NOW);
		autoGroup.answer(bobMenu, false, NOW);
		autoGroup.resolve(new SecureRandom(), NOW);
		MergeRequestRules rules = MergeRequestRules.of(everyone, List.of(autoGroup));

		assertThat(rules.check(alice, aliceMenu, bobMenu)).isEmpty();
	}

	@Test
	@DisplayName("요청할 수 있는 메뉴만 골라 준다 (화면의 버튼을 켤 메뉴)")
	void listsRequestableTargets() {
		MergeGroup carolToBob = MergeGroup.direct(room, carolMenu, bobMenu, NOW);
		Participant dave = participant(4);
		Menu daveMenu = menu(14, dave);
		MergeRequestRules rules = MergeRequestRules.of(List.of(alice, bob, carol, dave), List.of(carolToBob));

		assertThat(rules.requestableTargets(alice, aliceMenu, List.of(aliceMenu, bobMenu, carolMenu, daveMenu)))
			.containsExactly(daveMenu);
		assertThat(rules.requestableTargets(carol, carolMenu, List.of(aliceMenu, bobMenu, carolMenu, daveMenu)))
			.as("요청을 보내 놓은 사람은 아무 데도 보낼 수 없다")
			.isEmpty();
	}

	private Participant participant(long id) {
		Participant participant = Participant.guest(room, "token-hash-" + id, NOW);
		ReflectionTestUtils.setField(participant, "id", id);
		return participant;
	}

	private Menu menu(long id, Participant owner) {
		Menu menu = Menu.propose(room, owner, new MenuName("메뉴" + id), NOW);
		ReflectionTestUtils.setField(menu, "id", id);
		return menu;
	}

}
