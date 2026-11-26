package com.whattoeat.room.domain;

/**
 * 합치기 그룹이 어떻게 만들어졌는지.
 */
public enum MergeGroupKind {

	/** 합치기 단계가 열릴 때 자동으로 묶은 그룹. 지금은 이름이 같은 메뉴끼리, 기능 6부터는 AI가 묶는다. 대표는 무작위로 뽑는다. */
	AUTO,

	/** 참여자가 직접 보낸 합치기 요청. 요청한 메뉴와 요청받은 메뉴 2개로 이뤄지고, 받아들여지면 요청받은 메뉴가 대표로 남는다. */
	DIRECT

}
