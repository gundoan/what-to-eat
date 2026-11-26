package com.whattoeat.room.domain;

/**
 * 합치기 질문에 대한 한 사람의 답.
 */
public enum MergeAnswer {

	/** 아직 답하지 않았다 */
	WAITING,

	/** 합치기 */
	AGREED,

	/** 합치지 않기. 답하지 않은 채로 시간이 다 되거나 "합치기 끝냄"을 누른 경우도 여기에 든다 */
	DECLINED

}
