package com.whattoeat.room.application;

/**
 * RoomService가 돌려주는 결과들. 토큰 원본은 쿠키로 내려보내기 위해 이 순간에만 들고 있고, DB에는 해시만 남는다.
 */
public final class RoomResults {

	private RoomResults() {
	}

	public record CreatedRoom(String code, String token) {
	}

	/** newToken이 null이면 이미 참여한 사람이 다시 들어온 것(새로고침 등)이라 쿠키를 새로 줄 필요가 없다. */
	public record JoinResult(RoomView view, String newToken) {

		public boolean isNewParticipant() {
			return newToken != null;
		}

	}

}
