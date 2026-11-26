package com.whattoeat.room.domain;

import com.whattoeat.common.error.BusinessException;
import com.whattoeat.common.error.ErrorCode;

/**
 * 투표지에 적을 표 하나: 어느 메뉴에 +1(먹고 싶다) 또는 -1(먹기 싫다)을 줄지.
 *
 * @param point +1 또는 -1
 */
public record VoteChoice(Menu menu, int point) {

	public static final int LIKE = 1;

	public static final int DISLIKE = -1;

	public VoteChoice {
		if (menu == null || (point != LIKE && point != DISLIKE)) {
			throw new BusinessException(ErrorCode.INVALID_BALLOT);
		}
	}

}
