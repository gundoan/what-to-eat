package com.whattoeat.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

	INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다."),
	INVALID_PHASE_DURATION(HttpStatus.BAD_REQUEST, "단계 시간은 30초에서 60분 사이(30초 단위)나 제한 없음(0)으로 정해야 합니다."),
	INVALID_MENU_NAME(HttpStatus.BAD_REQUEST, "메뉴 이름은 1자에서 30자 사이로 써 주세요."),
	NOT_PARTICIPANT(HttpStatus.FORBIDDEN, "이 방의 참여자가 아닙니다. 링크로 먼저 참여해 주세요."),
	NOT_HOST(HttpStatus.FORBIDDEN, "방장만 할 수 있습니다."),
	ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "방을 찾을 수 없습니다."),
	ROOM_FULL(HttpStatus.CONFLICT, "방 정원이 가득 찼습니다."),
	PHASE_ALREADY_CHANGED(HttpStatus.CONFLICT, "이미 다음 단계로 넘어갔습니다."),
	NO_NEXT_PHASE(HttpStatus.CONFLICT, "이 단계에서는 아직 다음 단계로 넘어갈 수 없습니다."),
	NOT_PROPOSAL_PHASE(HttpStatus.CONFLICT, "지금은 메뉴를 제안할 수 있는 단계가 아닙니다."),
	NOT_MERGE_PHASE(HttpStatus.CONFLICT, "지금은 메뉴를 합칠 수 있는 단계가 아닙니다."),
	MERGE_GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "합치기 질문을 찾을 수 없습니다."),
	NOT_MERGE_MEMBER(HttpStatus.FORBIDDEN, "이 합치기 질문을 받은 사람만 답할 수 있습니다."),
	ALREADY_ANSWERED(HttpStatus.CONFLICT, "이미 답했습니다. 한 번 한 답은 바꿀 수 없습니다."),
	NO_MENU_TO_MERGE(HttpStatus.CONFLICT, "남아 있는 내 메뉴가 없어서 합치기를 요청할 수 없습니다."),
	MERGE_ALREADY_FINISHED(HttpStatus.CONFLICT, "합치기를 끝낸 뒤에는 합치기를 요청할 수 없습니다."),
	MENU_NOT_FOUND(HttpStatus.NOT_FOUND, "메뉴를 찾을 수 없습니다."),
	CANNOT_MERGE_WITH_OWN_MENU(HttpStatus.BAD_REQUEST, "내 메뉴에는 합치기를 요청할 수 없습니다."),
	TARGET_FINISHED_MERGING(HttpStatus.CONFLICT, "이 메뉴의 제안자는 합치기를 끝내서 요청을 받을 수 없습니다."),
	MENU_BUSY(HttpStatus.CONFLICT, "결과가 안 나온 합치기에 든 메뉴라서 지금은 요청할 수 없습니다."),
	ALREADY_DECLINED(HttpStatus.CONFLICT, "이 메뉴와는 이미 합치지 않기로 했습니다."),
	WAITING_FOR_REQUEST_ANSWER(HttpStatus.CONFLICT, "보낸 요청의 답을 기다리는 중이라 아직 합치기를 끝낼 수 없습니다."),
	NOT_VOTING_PHASE(HttpStatus.CONFLICT, "지금은 투표할 수 있는 단계가 아닙니다."),
	INVALID_BALLOT(HttpStatus.BAD_REQUEST, "표는 1~2개, 한 메뉴에 1표씩, +1 또는 -1로 낼 수 있습니다. 투표하지 않으려면 '다 좋음'을 눌러 주세요."),
	ALREADY_VOTED(HttpStatus.CONFLICT, "이미 투표했습니다. 낸 표는 바꿀 수 없습니다."),
	VOTING_ENDED(HttpStatus.CONFLICT, "투표가 끝난 방이라 새로 들어올 수 없습니다."),
	NOT_A_CANDIDATE(HttpStatus.BAD_REQUEST, "이번 투표의 후보가 아닌 메뉴입니다. 재투표는 동점이었던 메뉴에만 할 수 있습니다."),
	NOT_RANDOM_PICK_PHASE(HttpStatus.CONFLICT, "지금은 메뉴를 정하는 단계가 아닙니다."),
	NOT_PICKER(HttpStatus.FORBIDDEN, "랜덤으로 뽑힌 사람만 메뉴를 정할 수 있습니다.");

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
