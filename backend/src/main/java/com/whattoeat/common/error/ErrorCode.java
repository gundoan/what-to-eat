package com.whattoeat.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

	INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다."),
	INVALID_PHASE_DURATION(HttpStatus.BAD_REQUEST, "단계 시간은 30초에서 10분 사이, 30초 단위로 정해야 합니다."),
	INVALID_MENU_NAME(HttpStatus.BAD_REQUEST, "메뉴 이름은 1자에서 30자 사이로 써 주세요."),
	NOT_PARTICIPANT(HttpStatus.FORBIDDEN, "이 방의 참여자가 아닙니다. 링크로 먼저 참여해 주세요."),
	NOT_HOST(HttpStatus.FORBIDDEN, "방장만 할 수 있습니다."),
	ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "방을 찾을 수 없습니다."),
	ROOM_FULL(HttpStatus.CONFLICT, "방 정원이 가득 찼습니다."),
	PHASE_ALREADY_CHANGED(HttpStatus.CONFLICT, "이미 다음 단계로 넘어갔습니다."),
	NO_NEXT_PHASE(HttpStatus.CONFLICT, "이 단계에서는 아직 다음 단계로 넘어갈 수 없습니다."),
	NOT_PROPOSAL_PHASE(HttpStatus.CONFLICT, "지금은 메뉴를 제안할 수 있는 단계가 아닙니다.");

	private final HttpStatus status;
	private final String message;

	ErrorCode(HttpStatus status, String message) {
		this.status = status;
		this.message = message;
	}

	public HttpStatus status() {
		return status;
	}

	public String message() {
		return message;
	}

}
