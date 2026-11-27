package com.whattoeat.room.application;

import java.util.Set;

/**
 * 지금 방 화면을 열어 둔(SSE로 연결된) 참여자를 알려준다. SSE 연결을 관리하는 쪽이 구현한다.
 * 화면에 "지금 접속 중 N명"을 보여주는 데만 쓰고, 정원과 단계를 넘기는 규칙("모든 참여자가 끝냄")에는 쓰지 않는다.
 * 서버는 탭을 닫은 것, 새로고침, 다른 앱으로 잠깐 넘어간 것을 구별하지 못해서다.
 */
public interface RoomPresence {

	/** 지금 이 방에 연결된 참여자 번호들. 한 사람이 연결(탭)을 여러 개 열어도 한 번만 들어간다 */
	Set<Long> connectedParticipantIds(String roomCode);

}
