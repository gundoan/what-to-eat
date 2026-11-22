package com.whattoeat.room.domain;

import com.whattoeat.common.error.BusinessException;
import com.whattoeat.common.error.ErrorCode;

/**
 * 메뉴 이름. 앞뒤 공백을 지운 뒤 1자 이상 30자 이하여야 한다.
 * 글자 수는 화면에 보이는 글자 기준으로 센다(이모지도 1자). DB의 VARCHAR(30)도 같은 기준이다.
 */
public record MenuName(String value) {

	public static final int MAX_LENGTH = 30;

	public MenuName {
		if (value == null) {
			throw invalid();
		}
		value = value.strip();
		int length = value.codePointCount(0, value.length());
		// 줄바꿈 같은 제어 문자가 들어가면 목록 화면이 깨지므로 막는다.
		if (length == 0 || length > MAX_LENGTH || value.codePoints().anyMatch(Character::isISOControl)) {
			throw invalid();
		}
	}

	private static BusinessException invalid() {
		return new BusinessException(ErrorCode.INVALID_MENU_NAME);
	}

}
