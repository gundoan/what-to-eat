package com.whattoeat.room.domain;

/**
 * 방의 진행 단계. 기능을 하나씩 만들면서 제안 중, 합치기 중, 투표 중 등을 추가한다.
 */
public enum RoomStatus {

	/** 방을 만든 직후. 방장이 "시작"을 누르면 제안 단계로 넘어간다(시작은 기능 2에서 만든다). */
	WAITING

}
