package com.whattoeat.room.application;

import java.time.Clock;
import java.time.Instant;

import com.whattoeat.common.error.BusinessException;
import com.whattoeat.common.error.ErrorCode;
import com.whattoeat.room.domain.MenuName;
import com.whattoeat.room.domain.Participant;
import com.whattoeat.room.domain.PickTurn;
import com.whattoeat.room.domain.PickTurnRepository;
import com.whattoeat.room.domain.PickTurnStatus;
import com.whattoeat.room.domain.Room;
import com.whattoeat.room.domain.RoomStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 랜덤 선택 단계에서 뽑힌 사람이 하는 일: 메뉴 이름 정하기. 정하면 바로 그 이름으로 결정되고, 누가 정했는지는 공개하지 않는다.
 */
@Service
public class PickService {

	private final PickTurnRepository pickTurnRepository;

	private final RoomAccess access;

	private final RoomStateAssembler assembler;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	PickService(PickTurnRepository pickTurnRepository, RoomAccess access, RoomStateAssembler assembler,
			ApplicationEventPublisher events, Clock clock) {
		this.pickTurnRepository = pickTurnRepository;
		this.access = access;
		this.assembler = assembler;
		this.events = events;
		this.clock = clock;
	}

	/**
	 * 뽑힌 사람이 메뉴 이름을 정한다. 이름 규칙은 제안과 같다(앞뒤 공백을 뺀 1~30자). 메뉴 목록에 없는 이름이어도 된다.
	 * 방을 잠그고 처리하므로, 정하는 순간 시간이 다 되어 다른 사람이 뽑혀도 둘 중 하나만 일어난다.
	 */
	@Transactional
	public RoomView decide(String code, String rawToken, String rawMenuName) {
		Room room = access.lock(code);
		Participant me = access.requireParticipant(room, rawToken);
		if (room.getStatus() != RoomStatus.RANDOM_PICK) {
			throw new BusinessException(ErrorCode.NOT_RANDOM_PICK_PHASE);
		}
		PickTurn turn = pickTurnRepository.findByRoomAndStatus(room, PickTurnStatus.ACTIVE)
			.filter(active -> active.isFor(me))
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_PICKER));
		MenuName name = new MenuName(rawMenuName);

		Instant now = clock.instant();
		turn.decide(name, now);
		room.decideByPick(name, now);
		events.publishEvent(new RoomChangedEvent(code));
		return assembler.view(room, me);
	}

}
