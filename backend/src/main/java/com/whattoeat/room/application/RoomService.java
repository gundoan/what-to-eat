package com.whattoeat.room.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import com.whattoeat.common.error.BusinessException;
import com.whattoeat.common.error.ErrorCode;
import com.whattoeat.room.application.RoomResults.CreatedRoom;
import com.whattoeat.room.application.RoomResults.JoinResult;
import com.whattoeat.room.application.RoomView.Me;
import com.whattoeat.room.domain.Participant;
import com.whattoeat.room.domain.ParticipantRepository;
import com.whattoeat.room.domain.ParticipantToken;
import com.whattoeat.room.domain.PhaseDurations;
import com.whattoeat.room.domain.Room;
import com.whattoeat.room.domain.RoomCode;
import com.whattoeat.room.domain.RoomRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomService {

	private static final int CODE_GENERATION_ATTEMPTS = 5;

	private final RoomRepository roomRepository;

	private final ParticipantRepository participantRepository;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	public RoomService(RoomRepository roomRepository, ParticipantRepository participantRepository,
			ApplicationEventPublisher events, Clock clock) {
		this.roomRepository = roomRepository;
		this.participantRepository = participantRepository;
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
		Room room = roomRepository.findByCodeForUpdate(code)
			.orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

		Optional<Participant> existing = findParticipant(room, rawToken);
		if (existing.isPresent()) {
			return new JoinResult(toView(room, existing.get()), null);
		}

		long participantCount = participantRepository.countByRoom(room);
		if (room.isFull(participantCount)) {
			throw new BusinessException(ErrorCode.ROOM_FULL);
		}

		ParticipantToken token = ParticipantToken.generate();
		participantRepository.save(Participant.guest(room, token.hash(), clock.instant()));
		events.publishEvent(new RoomChangedEvent(room.getCode()));

		RoomView view = new RoomView(RoomState.of(room, participantCount + 1), new Me(false));
		return new JoinResult(view, token.value());
	}

	@Transactional(readOnly = true)
	public RoomView getRoom(String code, String rawToken) {
		Room room = findRoom(code);
		Participant me = findParticipant(room, rawToken)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_PARTICIPANT));
		return toView(room, me);
	}

	/** 이 방의 참여자가 아니면 예외를 던진다. SSE 연결처럼 응답 본문 없이 자격만 확인할 때 쓴다. */
	@Transactional(readOnly = true)
	public void verifyParticipant(String code, String rawToken) {
		Room room = findRoom(code);
		if (findParticipant(room, rawToken).isEmpty()) {
			throw new BusinessException(ErrorCode.NOT_PARTICIPANT);
		}
	}

	private Room findRoom(String code) {
		return roomRepository.findByCode(code).orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));
	}

	/** 토큰이 있어도 다른 방의 토큰이면 이 방의 참여자로 인정하지 않는다. */
	private Optional<Participant> findParticipant(Room room, String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			return Optional.empty();
		}
		return participantRepository.findByTokenHash(ParticipantToken.hash(rawToken))
			.filter(participant -> participant.belongsTo(room));
	}

	private RoomView toView(Room room, Participant me) {
		long participantCount = participantRepository.countByRoom(room);
		return new RoomView(RoomState.of(room, participantCount), new Me(me.isHost()));
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
