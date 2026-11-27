package com.whattoeat.room.web;

import java.net.URI;

import com.whattoeat.common.error.BusinessException;
import com.whattoeat.common.error.ErrorCode;
import com.whattoeat.room.application.RoomPhaseService;
import com.whattoeat.room.application.RoomResults.CreatedRoom;
import com.whattoeat.room.application.RoomResults.JoinResult;
import com.whattoeat.room.application.RoomService;
import com.whattoeat.room.application.RoomView;
import com.whattoeat.room.domain.PhaseDurations;
import com.whattoeat.room.domain.RoomStatus;
import com.whattoeat.room.sse.RoomEventBroadcaster;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

	private final RoomService roomService;

	private final RoomPhaseService phaseService;

	private final RoomEventBroadcaster broadcaster;

	private final ParticipantCookie participantCookie;

	public RoomController(RoomService roomService, RoomPhaseService phaseService, RoomEventBroadcaster broadcaster,
			ParticipantCookie participantCookie) {
		this.roomService = roomService;
		this.phaseService = phaseService;
		this.broadcaster = broadcaster;
		this.participantCookie = participantCookie;
	}

	/** 방 만들기. 본문을 아예 보내지 않으면 모든 단계가 기본값이다(제안, 합치기, 투표 5분, 랜덤 선택 1분). */
	@PostMapping
	public ResponseEntity<CreateRoomResponse> create(@RequestBody(required = false) CreateRoomRequest request) {
		PhaseDurations durations = request == null ? PhaseDurations.defaults() : request.toPhaseDurations();
		CreatedRoom created = roomService.create(durations);
		return ResponseEntity.created(URI.create("/api/rooms/" + created.code()))
			.header(HttpHeaders.SET_COOKIE, participantCookie.create(created.code(), created.token()).toString())
			.body(new CreateRoomResponse(created.code()));
	}

	/** 링크로 참여. 이미 참여한 브라우저면 200과 기존 참여자, 새로 들어오면 201과 새 쿠키를 준다. */
	@PostMapping("/{code}/participants")
	public ResponseEntity<RoomView> join(@PathVariable String code,
			@CookieValue(name = ParticipantCookie.NAME, required = false) String token) {
		JoinResult result = roomService.join(code, token);
		if (!result.isNewParticipant()) {
			return ResponseEntity.ok(result.view());
		}
		return ResponseEntity.status(HttpStatus.CREATED)
			.header(HttpHeaders.SET_COOKIE, participantCookie.create(code, result.newToken()).toString())
			.body(result.view());
	}

	@GetMapping("/{code}")
	public RoomView get(@PathVariable String code,
			@CookieValue(name = ParticipantCookie.NAME, required = false) String token) {
		return roomService.getRoom(code, token);
	}

	/**
	 * 방장의 "시작"(대기 중일 때), "다음 단계로"(그 밖의 단계), "다른 사람 뽑기"(랜덤 선택일 때).
	 * from에 방장 화면에 보이던 단계를 보내야 한다. 이미 넘어갔으면 409로 거절해서 두 번 눌러도 한 단계만 넘어간다.
	 * 랜덤 선택에서는 단계 이름이 그대로라서, 화면에 보이던 뽑기 차례 번호(pickTurn)도 함께 보내야 한다.
	 */
	@PostMapping("/{code}/next-phase")
	public RoomView nextPhase(@PathVariable String code,
			@CookieValue(name = ParticipantCookie.NAME, required = false) String token,
			@RequestBody NextPhaseRequest request) {
		if (request.from() == null) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST);
		}
		return phaseService.advanceByHost(code, token, request.from(), request.pickTurn());
	}

	/**
	 * SSE 연결. 연결하면 현재 화면 정보(방 상태 + 내 정보)를 바로 받고, 이후 방 상태가 바뀔 때마다 받는다.
	 * 내 정보는 쿠키로 확인한 본인 것만 온다.
	 */
	@GetMapping(path = "/{code}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter events(@PathVariable String code,
			@CookieValue(name = ParticipantCookie.NAME, required = false) String token) {
		long participantId = roomService.verifyParticipant(code, token);
		return broadcaster.subscribe(code, participantId);
	}

	public record CreateRoomResponse(String code) {
	}

	/**
	 * @param from 방장 화면에 보이던 단계
	 * @param pickTurn 랜덤 선택일 때 방장 화면에 보이던 뽑기 차례 번호. 다른 단계에서는 보내지 않는다
	 */
	public record NextPhaseRequest(RoomStatus from, Integer pickTurn) {
	}

}
