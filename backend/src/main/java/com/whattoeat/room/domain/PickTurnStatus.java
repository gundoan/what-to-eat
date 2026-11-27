package com.whattoeat.room.domain;

/**
 * 뽑기 차례 하나의 상태. ACTIVE에서 나머지 셋 중 하나로 한 번만 바뀐다.
 */
public enum PickTurnStatus {

	/** 뽑힌 사람이 메뉴를 정하는 중 */
	ACTIVE,

	/** 뽑힌 사람이 메뉴를 정했다 */
	DECIDED,

	/** 랜덤 선택 시간 안에 정하지 않았다 */
	EXPIRED,

	/** 방장이 "다른 사람 뽑기"를 눌렀다 */
	SKIPPED

}
