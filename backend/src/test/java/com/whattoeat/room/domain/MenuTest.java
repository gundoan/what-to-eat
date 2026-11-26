package com.whattoeat.room.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MenuTest {

	private static final Instant NOW = Instant.parse("2026-11-26T03:00:00Z");

	private static final Room ROOM = Room.create("abcdefghij", PhaseDurations.defaults(), NOW);

	@Test
	@DisplayName("합쳐진 메뉴는 최종적으로 들어간 메뉴까지 따라간다 (A → B, 다시 B → C면 A는 C)")
	void followsMergesToTheEnd() {
		Menu a = menu(1);
		Menu b = menu(2);
		Menu c = menu(3);

		a.mergeInto(b);
		b.mergeInto(c);

		assertThat(a.finalMenu()).isSameAs(c);
		assertThat(b.finalMenu()).isSameAs(c);
		assertThat(c.finalMenu()).as("합쳐지지 않은 메뉴는 자기 자신이다").isSameAs(c);
	}

	@Test
	@DisplayName("이미 합쳐진 메뉴는 다시 합칠 수 없고, 자기 자신에게 합칠 수도 없다")
	void cannotMergeTwiceOrIntoItself() {
		Menu a = menu(1);
		Menu b = menu(2);
		a.mergeInto(b);

		assertThatThrownBy(() -> a.mergeInto(menu(3))).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> b.mergeInto(b)).isInstanceOf(IllegalArgumentException.class);
	}

	private static Menu menu(long id) {
		Participant owner = Participant.guest(ROOM, "token-hash-" + id, NOW);
		Menu menu = Menu.propose(ROOM, owner, new MenuName("메뉴" + id), NOW);
		ReflectionTestUtils.setField(menu, "id", id);
		return menu;
	}

}
