package com.whattoeat.room.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import com.whattoeat.common.error.BusinessException;
import com.whattoeat.common.error.ErrorCode;
import com.whattoeat.room.application.RoomResults.CreatedRoom;
import com.whattoeat.room.application.RoomResults.JoinResult;
import com.whattoeat.room.domain.Participant;
import com.whattoeat.room.domain.ParticipantRepository;
import com.whattoeat.room.domain.ParticipantToken;
import com.whattoeat.room.domain.PhaseDurations;
import com.whattoeat.room.domain.Room;
import com.whattoeat.room.domain.RoomCode;
import com.whattoeat.room.domain.RoomRepository;
import com.whattoeat.room.domain.RoomStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomService {

	private static final int CODE_GENERATION_ATTEMPTS = 5;

	private final RoomRepository roomRepository;

	private final ParticipantRepository participantRepository;

	private final RoomAccess access;

	private final RoomStateAssembler assembler;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	RoomService(RoomRepository roomRepository, ParticipantRepository participantRepository, RoomAccess access,
			RoomStateAssembler assembler, ApplicationEventPublisher events, Clock clock) {
		this.roomRepository = roomRepository;
		this.participantRepository = participantRepository;
		this.access = access;
		this.assembler = assembler;
		this.events = events;
		this.clock = clock;
	}

	/** 방을 만들고, 만든 사람을 방장으로 참여시킨다. */
	@Transactional
	public CreatedRoom create(PhaseDurations phaseDurations) {
		Instant now = clock.instant();
		Room room = roomRepository.save(Room.create(newUniqueCode(), phaseDurations, now));
		ParticipantToken token = ParticipantToken.generate();
		participantRepository.save(Participant.host(room, token.hash(), now));
		return new CreatedRoom(room.getCode(), token.value());
	}

	/**
	 * 링크로 들어온 사람을 참여시킨다. 이미 이 방의 토큰을 가진 사람(새로고침 등)이면 새로 만들지 않고 그 참여자를 그대로 돌려준다.
	 * 방을 잠근 상태에서 인원을 세고 추가하므로, 여러 명이 동시에 들어와도 정원을 넘지 않는다.
	 */
	@Transactional
	public JoinResult join(String code, String rawToken) {
		Room room = access.lock(code);

		Optional<Participant> existing = access.findParticipant(room, rawToken);
		if (existing.isPresent()) {
			return new JoinResult(assembler.view(room, existing.get()), null);
		}

		// 늦게 온 사람도 투표가 끝나기 전이면 들어올 수 있다. 메뉴가 결정된 뒤에는 이미 참여한 사람만 결과를 볼 수 있다.
		if (room.getStatus() == RoomStatus.FINISHED) {
			throw new BusinessException(ErrorCode.VOTING_ENDED);
		}
		if (room.isFull(participantRepository.countByRoom(room))) {
			throw new BusinessException(ErrorCode.ROOM_FULL);
		}

		ParticipantToken token = ParticipantToken.generate();
		Participant guest = participantRepository.save(Participant.guest(room, token.hash(), clock.instant()));
		events.publishEvent(new RoomChangedEvent(room.getCode()));
		return new JoinResult(assembler.view(room, guest), token.value());
	}

	@Transactional(readOnly = true)
	public RoomView getRoom(String code, String rawToken) {
		Room room = access.find(code);
		return assembler.view(room, access.requireParticipant(room, rawToken));
	}

	/**
	 * 이 방의 참여자인지 확인하고, 참여자 번호를 돌려준다. 참여자가 아니면 예외를 던진다.
	 * SSE 연결처럼 응답 본문 없이 자격만 확인할 때 쓴다. 참여자 번호는 서버 안에서만 쓰고 화면에 내보내지 않는다.
	 */
	@Transactional(readOnly = true)
	public long verifyParticipant(String code, String rawToken) {
		return access.requireParticipant(access.find(code), rawToken).getId();
	}

	private String newUniqueCode() {
		for (int attempt = 0; attempt < CODE_GENERATION_ATTEMPTS; attempt++) {
			String code = RoomCode.generate();
			if (!roomRepository.existsByCode(code)) {
				return code;
			}
		}
		// 경우의 수가 62^10이라 사실상 일어나지 않는다. 그래도 겹치면 DB의 UNIQUE 제약이 마지막으로 막는다.
		throw new IllegalStateException("방 코드를 " + CODE_GENERATION_ATTEMPTS + "번 만들었지만 모두 이미 있었다");
	}

}
