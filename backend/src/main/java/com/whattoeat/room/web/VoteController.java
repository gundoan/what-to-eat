package com.whattoeat.room.web;

import java.util.List;

import com.whattoeat.common.error.BusinessException;
import com.whattoeat.common.error.ErrorCode;
import com.whattoeat.room.application.RoomView;
import com.whattoeat.room.application.VoteService;
import com.whattoeat.room.application.VoteService.VoteInput;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 투표 단계에서 "나"의 투표지를 다루는 API. 주소의 me는 요청한 본인(쿠키로 확인)을 뜻한다.
 */
@RestController
@RequestMapping("/api/rooms/{code}/me")
public class VoteController {

	private final VoteService voteService;

	public VoteController(VoteService voteService) {
		this.voteService = voteService;
	}

	/**
	 * 투표지 내기. 표 1~2개({"votes": [{"menuId": "...", "point": 1}]}) 또는 "다 좋음"({"allGood": true}).
	 * 한 번 내면 바꿀 수 없고, 같은 내용을 다시 보내면 그대로 성공한다.
	 */
	@PutMapping("/ballot")
	public RoomView submit(@PathVariable String code,
			@CookieValue(name = ParticipantCookie.NAME, required = false) String token,
			@RequestBody BallotRequest request) {
		if (request == null) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST);
		}
		List<VoteInput> votes = request.votes() == null ? List.of() : request.votes();
		return voteService.submit(code, token, Boolean.TRUE.equals(request.allGood()), votes);
	}

	/**
	 * @param votes 표들(메뉴 공개 번호와 +1/-1). "다 좋음"이면 비워 둔다
	 * @param allGood "다 좋음"(투표하지 않음)이면 true
	 */
	public record BallotRequest(List<VoteInput> votes, Boolean allGood) {
	}

}
