package com.whattoeat.room.application;

import java.util.Optional;

import com.whattoeat.common.error.BusinessException;
import com.whattoeat.common.error.ErrorCode;
import com.whattoeat.room.domain.Participant;
import com.whattoeat.room.domain.ParticipantRepository;
import com.whattoeat.room.domain.ParticipantToken;
import com.whattoeat.room.domain.Room;
import com.whattoeat.room.domain.RoomRepository;
import org.springframework.stereotype.Component;

/**
 * 여러 서비스가 함께 쓰는 "방 찾기·잠그기"와 "이 방의 참여자인지 확인".
 * 방 상태를 바꾸는 요청은 모두 lock()으로 방을 먼저 잠근다. 잠그는 순서가 항상 같아서(방 → 참여자·메뉴) 교착 상태가 생기지 않는다.
 */
@Component
class RoomAccess {

	private final RoomRepository roomRepository;

	private final ParticipantRepository participantRepository;

	RoomAccess(RoomRepository roomRepository, ParticipantRepository participantRepository) {
		this.roomRepository = roomRepository;
		this.participantRepository = participantRepository;
	}

	/** 방을 잠그고 읽는다. 이 트랜잭션이 끝날 때까지 같은 방을 바꾸는 다른 요청은 기다린다. */
	Room lock(String code) {
		return roomRepository.findByCodeForUpdate(code)
			.orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));
	}

	Room find(String code) {
		return roomRepository.findByCode(code).orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));
	}

	/** 토큰이 있어도 다른 방의 토큰이면 이 방의 참여자로 인정하지 않는다. */
	Optional<Participant> findParticipant(Room room, String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			return Optional.empty();
		}
		return participantRepository.findByTokenHash(ParticipantToken.hash(rawToken))
			.filter(participant -> participant.belongsTo(room));
	}

	Participant requireParticipant(Room room, String rawToken) {
		return findParticipant(room, rawToken).orElseThrow(() -> new BusinessException(ErrorCode.NOT_PARTICIPANT));
	}

}
