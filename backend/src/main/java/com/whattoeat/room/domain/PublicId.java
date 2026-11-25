package com.whattoeat.room.domain;

import java.util.UUID;

/**
 * 화면에 내보내는 메뉴·합치기 그룹 번호.
 * DB 번호(1, 2, 3...)를 그대로 보여주면 올라온 순서가 드러나서(가장 작은 번호 = 가장 먼저 낸 메뉴) 누가 냈는지 짐작할 수 있다.
 * 그래서 순서가 없는 무작위 UUID를 따로 둔다. 무작위 부분이 122비트라 겹칠 걱정이 없다.
 * (방 코드는 링크에 들어가서 짧아야 하므로 따로 10자로 만든다. {@link RoomCode})
 */
public final class PublicId {

	public static final int LENGTH = 36;

	private PublicId() {
	}

	public static String generate() {
		// randomUUID()는 안에서 보안용 난수(SecureRandom)를 쓴다.
		return UUID.randomUUID().toString();
	}

}
