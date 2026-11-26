package com.whattoeat.room.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SameNameMenuGrouperTest {

	private final SameNameMenuGrouper grouper = new SameNameMenuGrouper();

	@Test
	@DisplayName("띄어쓰기와 대소문자만 다른 이름을 한 그룹으로 묶는다")
	void ignoresSpacesAndCase() {
		List<List<Integer>> groups = grouper.group(List.of("제육 볶음", "김밥", "제육볶음", "Pizza", "pizza"));

		assertThat(groups).containsExactly(List.of(0, 2), List.of(3, 4));
	}

	@Test
	@DisplayName("전각 문자와 특수한 공백도 같은 글자로 본다")
	void normalizesFullWidthLettersAndUnusualSpaces() {
		// "ＰＩＺＺＡ"는 전각 문자, " "은 줄바꿈 없는 공백이다.
		List<List<Integer>> groups = grouper.group(List.of("ＰＩＺＺＡ", "pizza", "제육 볶음", "제육볶음"));

		assertThat(groups).containsExactly(List.of(0, 1), List.of(2, 3));
	}

	@Test
	@DisplayName("이름이 하나뿐이면 그룹을 만들지 않는다")
	void singleNamesAreNotGrouped() {
		assertThat(grouper.group(List.of("김밥", "라면", "짜장면"))).isEmpty();
		assertThat(grouper.group(List.of())).isEmpty();
	}

	@Test
	@DisplayName("가게 이름이 붙은 비슷한 메뉴는 묶지 못한다 (기능 6의 AI가 할 일)")
	void doesNotGroupSimilarButDifferentNames() {
		assertThat(grouper.group(List.of("할매집 제육", "제육볶음", "제육"))).isEmpty();
	}

}
