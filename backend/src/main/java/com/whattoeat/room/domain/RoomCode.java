package com.whattoeat.room.domain;

import java.security.SecureRandom;

/**
 * 방 링크에 들어가는 코드. 번호처럼 순서가 있으면 남의 방을 추측해 들어갈 수 있으므로
 * 영문 대소문자와 숫자(62가지) 10자리를 무작위로 만든다. 경우의 수는 62^10 ≈ 8.4 × 10^17.
 */
public final class RoomCode {

	public static final int LENGTH = 10;

	private static final char[] ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
		.toCharArray();

	// 일반 Random은 다음 값을 예측할 수 있어서, 보안용 난수인 SecureRandom을 쓴다.
	private static final SecureRandom RANDOM = new SecureRandom();

	private RoomCode() {
	}

	public static String generate() {
		char[] code = new char[LENGTH];
		for (int i = 0; i < LENGTH; i++) {
			code[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
		}
		return new String(code);
	}

}
