package com.whattoeat.room.web;

import com.whattoeat.room.application.PickService;
import com.whattoeat.room.application.RoomView;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 랜덤 선택 단계에서 뽑힌 "나"가 메뉴를 정하는 API. 주소의 me는 요청한 본인(쿠키로 확인)을 뜻한다.
 */
@RestController
@RequestMapping("/api/rooms/{code}/me")
public class PickController {

	private final PickService pickService;

	public PickController(PickService pickService) {
		this.pickService = pickService;
	}

	/** 뽑힌 사람이 메뉴 이름을 정한다. 정하면 바로 그 이름으로 결정되고, 누가 정했는지는 공개하지 않는다. */
	@PostMapping("/pick")
	public RoomView decide(@PathVariable String code,
			@CookieValue(name = ParticipantCookie.NAME, required = false) String token,
			@RequestBody PickRequest request) {
		return pickService.decide(code, token, request.menuName());
	}

	public record PickRequest(String menuName) {
	}

}
