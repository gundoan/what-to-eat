package com.whattoeat.room.application;

/**
 * 방 상태가 바뀌었다는 신호. 트랜잭션이 커밋된 뒤에 SSE로 방 참여자들에게 새 상태를 보내는 데 쓴다.
 */
public record RoomChangedEvent(String roomCode) {
}
