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
 * 합치기 단계에서 "나"의 답과 "합치기 끝냄"을 다루는 API. 주소의 me는 요청한 본인(쿠키로 확인)을 뜻한다.
 */
@RestController
@RequestMapping("/api/rooms/{code}/me")
public class MergeController {

	private final MergeService mergeService;

	public MergeController(MergeService mergeService) {
		this.mergeService = mergeService;
	}

	/**
	 * 합치기 질문(groupId)에 대한 내 답. 한 번 답하면 바꿀 수 없고, 같은 답을 다시 보내면 그대로 성공한다.
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

	/** "합치기 끝냄". 답하지 않은 질문은 "합치지 않기"로 처리된다. */
	@PostMapping("/merge-done")
	public RoomView finish(@PathVariable String code,
			@CookieValue(name = ParticipantCookie.NAME, required = false) String token) {
		return mergeService.finish(code, token);
	}

	/** @param agree true면 합치기, false면 합치지 않기 */
	public record MergeAnswerRequest(Boolean agree) {
	}

}
