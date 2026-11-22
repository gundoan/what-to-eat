package com.whattoeat.room.web;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 참여자 토큰을 담는 쿠키를 만든다.
 *
 * <ul>
 * <li>HttpOnly: 자바스크립트가 읽을 수 없다. 화면에 악성 스크립트가 끼어들어도 토큰을 훔쳐 갈 수 없다.</li>
 * <li>Path=/api/rooms/{code}: 그 방의 API에만 쿠키가 붙는다. 방마다 다른 참여자로 따로 관리된다.</li>
 * <li>SameSite=Lax: 다른 사이트에서 몰래 보내는 요청에는 쿠키가 붙지 않는다.</li>
 * <li>Secure: 배포(HTTPS)에서는 켜서, 암호화된 연결로만 쿠키를 보낸다.</li>
 * </ul>
 * SSE 연결(EventSource)은 요청 헤더를 직접 붙일 수 없지만 쿠키는 자동으로 보내므로, 같은 방식으로 참여자를 확인할 수 있다.
 */
@Component
public class ParticipantCookie {

	public static final String NAME = "wte_participant";

	private static final Duration MAX_AGE = Duration.ofDays(1);

	private final boolean secure;

	public ParticipantCookie(@Value("${app.participant-cookie.secure}") boolean secure) {
		this.secure = secure;
	}

	public ResponseCookie create(String roomCode, String token) {
		return ResponseCookie.from(NAME, token)
			.httpOnly(true)
			.secure(secure)
			.sameSite("Lax")
			.path("/api/rooms/" + roomCode)
			.maxAge(MAX_AGE)
			.build();
	}

}
