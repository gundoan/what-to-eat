package com.whattoeat.room.web;

import com.whattoeat.room.application.ProposalService;
import com.whattoeat.room.application.RoomView;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 제안 단계에서 "나"의 메뉴를 다루는 API. 주소의 me는 요청한 본인(쿠키로 확인)을 뜻한다.
 */
@RestController
@RequestMapping("/api/rooms/{code}/me")
public class ProposalController {

	private final ProposalService proposalService;

	public ProposalController(ProposalService proposalService) {
		this.proposalService = proposalService;
	}

	/** 메뉴 제안. 이미 냈으면 이름만 바꾼다. */
	@PutMapping("/proposal")
	public RoomView propose(@PathVariable String code,
			@CookieValue(name = ParticipantCookie.NAME, required = false) String token,
			@RequestBody ProposeRequest request) {
		return proposalService.propose(code, token, request.menuName());
	}

	/** "다 좋음". 이미 낸 메뉴가 있으면 취소된다. */
	@PostMapping("/all-good")
	public RoomView allGood(@PathVariable String code,
			@CookieValue(name = ParticipantCookie.NAME, required = false) String token) {
		return proposalService.allGood(code, token);
	}

	public record ProposeRequest(String menuName) {
	}

}
