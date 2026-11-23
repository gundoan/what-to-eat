package com.whattoeat.room.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.whattoeat.common.error.BusinessException;
import com.whattoeat.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class MenuNameTest {

	@Test
	@DisplayName("앞뒤 공백은 지운다")
	void stripsSurroundingSpaces() {
		assertThat(new MenuName("  할매집 제육  ").value()).isEqualTo("할매집 제육");
	}

	@Test
	@DisplayName("30자까지는 허용한다")
	void acceptsThirtyCharacters() {
		String thirty = "가".repeat(30);

		assertThat(new MenuName(thirty).value()).hasSize(30);
	}

	@Test
	@DisplayName("이모지도 한 글자로 센다")
	void countsEmojiAsOneCharacter() {
		// 🍜는 자바 문자열로는 2칸이지만 화면에서는 1자다. 29자 + 🍜 = 30자라서 허용해야 한다.
		String name = "가".repeat(29) + "🍜";

		assertThat(new MenuName(name).value()).isEqualTo(name);
	}

	@ParameterizedTest(name = "[{index}] \"{0}\"는 거절한다")
	@NullSource
	@ValueSource(strings = { "", "   ", "\t", "제육\n볶음" })
	@DisplayName("비어 있거나, 공백뿐이거나, 줄바꿈 같은 제어 문자가 있으면 거절한다")
	void rejectsBlankOrControlCharacters(String raw) {
		assertInvalid(raw);
	}

	@Test
	@DisplayName("31자는 거절한다")
	void rejectsThirtyOneCharacters() {
		assertInvalid("가".repeat(31));
	}

	private static void assertInvalid(String raw) {
		assertThatThrownBy(() -> new MenuName(raw)).isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).errorCode())
			.isEqualTo(ErrorCode.INVALID_MENU_NAME);
	}

}
