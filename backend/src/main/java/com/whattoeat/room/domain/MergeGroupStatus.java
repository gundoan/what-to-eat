package com.whattoeat.room.domain;

/**
 * 합치기 그룹의 진행 상태. PENDING → MERGED 또는 NOT_MERGED 로 한 번만 바뀐다.
 */
public enum MergeGroupStatus {

	/** 답을 기다리는 중 */
	PENDING,

	/** 동의한 메뉴가 2개 이상이라 대표 메뉴 하나로 합쳐졌다 */
	MERGED,

	/** 동의한 메뉴가 1개 이하라 합치지 않았다 */
	NOT_MERGED

}
