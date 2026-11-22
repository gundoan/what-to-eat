package com.whattoeat.room.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 참여자를 알아보기 위해 브라우저 쿠키에 저장하는 무작위 토큰(32바이트).
 * 서버에는 원본이 아니라 SHA-256 해시만 저장한다.
 * 비밀번호와 달리 사람이 정한 값이 아니라 추측할 수 없는 무작위 값이라, 느린 해시(bcrypt 등)가 필요 없다.
 */
public record ParticipantToken(String value) {

	private static final int BYTES = 32;

	private static final SecureRandom RANDOM = new SecureRandom();

	public static ParticipantToken generate() {
		byte[] bytes = new byte[BYTES];
		RANDOM.nextBytes(bytes);
		return new ParticipantToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
	}

	public String hash() {
		return hash(value);
	}

	public static String hash(String rawToken) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256은 모든 Java 실행 환경에 있어야 한다", e);
		}
	}

}
