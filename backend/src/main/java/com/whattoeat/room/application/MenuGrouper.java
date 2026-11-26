package com.whattoeat.room.application;

import java.util.List;

/**
 * 같은 메뉴로 보이는 이름끼리 묶는 부품. 합치기 단계가 열릴 때 이 결과로 자동 그룹을 만든다.
 * 지금은 이름이 같은 메뉴만 묶고({@link SameNameMenuGrouper}), 기능 6에서 AI로 바꾼다. 부품만 바꾸면 되도록 따로 둔다.
 */
public interface MenuGrouper {

	/**
	 * @param names 메뉴 이름들
	 * @return 묶음마다 names 안의 위치(0부터) 목록. 2개 이상인 묶음만 돌려주고, 한 위치는 많아야 한 묶음에 들어간다
	 */
	List<List<Integer>> group(List<String> names);

}
