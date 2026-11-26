package com.whattoeat.room.application;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * 띄어쓰기와 대소문자만 다르고 이름이 같은 메뉴끼리 묶는다. 예: "제육 볶음"과 "제육볶음", "Pizza"와 "pizza".
 * "할매집 제육"과 "제육"처럼 글자가 다른 비슷한 메뉴는 묶지 못한다. 그건 기능 6의 AI가 할 일이다.
 */
@Component
class SameNameMenuGrouper implements MenuGrouper {

	@Override
	public List<List<Integer>> group(List<String> names) {
		Map<String, List<Integer>> positionsByKey = new LinkedHashMap<>();
		for (int i = 0; i < names.size(); i++) {
			positionsByKey.computeIfAbsent(comparisonKey(names.get(i)), key -> new ArrayList<>()).add(i);
		}
		return positionsByKey.values().stream().filter(positions -> positions.size() >= 2).toList();
	}

	/**
	 * 비교용 이름. 화면에 보이는 이름은 바꾸지 않고, 같은지 비교할 때만 쓴다.
	 * <ol>
	 * <li>유니코드 정규화(NFKC): 전각 문자 "ＰＩＺＺＡ"를 "PIZZA"로 바꾸는 것처럼, 모양이 같은데 코드가 다른 글자를 하나로 맞춘다.</li>
	 * <li>모든 공백을 지운다. 일반 띄어쓰기뿐 아니라 줄바꿈 없는 공백 같은 특수한 공백도 지운다.</li>
	 * <li>소문자로 바꾼다. 기기 언어 설정에 따라 결과가 달라지지 않게 Locale.ROOT를 쓴다.</li>
	 * </ol>
	 */
	static String comparisonKey(String name) {
		StringBuilder key = new StringBuilder();
		Normalizer.normalize(name, Normalizer.Form.NFKC)
			.codePoints()
			.filter(codePoint -> !Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint))
			.forEach(key::appendCodePoint);
		return key.toString().toLowerCase(Locale.ROOT);
	}

}
