package com.whattoeat.room.web;

import com.whattoeat.common.error.BusinessException;
import com.whattoeat.common.error.ErrorCode;
import com.whattoeat.room.application.MergeService;
import com.whattoeat.room.application.RoomView;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 합치기 단계에서 "나"의 직접 합치기 요청, 답, "합치기 끝냄"을 다루는 API. 주소의 me는 요청한 본인(쿠키로 확인)을 뜻한다.
 */
@RestController
@RequestMapping("/api/rooms/{code}/me")
public class MergeController {

	private final MergeService mergeService;

	public MergeController(MergeService mergeService) {
		this.mergeService = mergeService;
	}

	/** 직접 합치기 요청: 내 메뉴를 다른 메뉴에 합치자고 요청한다. 받아들여지면 요청받은 메뉴가 대표로 남는다. */
	@PostMapping("/merge-requests")
	public RoomView request(@PathVariable String code,
			@CookieValue(name = ParticipantCookie.NAME, required = false) String token,
			@RequestBody MergeRequestRequest request) {
		if (request.targetMenuId() == null || request.targetMenuId().isBlank()) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST);
		}
		return mergeService.request(code, token, request.targetMenuId());
	}

	/**
	 * 합치기 질문(groupId)에 대한 내 답. 한 번 답하면 바꿀 수 없고, 같은 답을 다시 보내면 그대로 성공한다.
	 * 자동 그룹의 질문과 내가 받은 직접 요청 모두 여기에 답한다.
	 */
	@PutMapping("/merge-answers/{groupId}")
	public RoomView answer(@PathVariable String code, @PathVariable String groupId,
			@CookieValue(name = ParticipantCookie.NAME, required = false) String token,
			@RequestBody MergeAnswerRequest request) {
		if (request.agree() == null) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST);
		}
		return mergeService.answer(code, token, groupId, request.agree());
	}

	/** "합치기 끝냄". 답하지 않은 질문은 "합치지 않기"로 처리된다. 보낸 요청의 답을 기다리는 동안에는 누를 수 없다. */
	@PostMapping("/merge-done")
	public RoomView finish(@PathVariable String code,
			@CookieValue(name = ParticipantCookie.NAME, required = false) String token) {
		return mergeService.finish(code, token);
	}

	/** @param targetMenuId 합치자고 요청할 메뉴의 공개 번호 */
	public record MergeRequestRequest(String targetMenuId) {
	}

	/** @param agree true면 합치기, false면 합치지 않기 */
	public record MergeAnswerRequest(Boolean agree) {
	}

}
