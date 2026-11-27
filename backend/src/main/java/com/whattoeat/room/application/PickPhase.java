package com.whattoeat.room.application;

import java.time.Instant;
import java.util.List;
import java.util.random.RandomGenerator;

import com.whattoeat.common.error.BusinessException;
import com.whattoeat.common.error.ErrorCode;
import com.whattoeat.room.domain.ParticipantRepository;
import com.whattoeat.room.domain.PickTurn;
import com.whattoeat.room.domain.PickTurnRepository;
import com.whattoeat.room.domain.PickTurnStatus;
import com.whattoeat.room.domain.Room;
import org.springframework.stereotype.Component;

/**
 * 랜덤 선택 단계에서 서버가 하는 일: 사람 뽑기, 시간이 다 되면 다시 뽑기(한 바퀴까지만), 방장의 "다른 사람 뽑기".
 * 방을 잠근 트랜잭션 안에서 불러야 한다. 그래서 뽑힌 사람이 정하는 순간과 다시 뽑기가 겹쳐도 하나만 일어난다.
 */
@Component
class PickPhase {

	private final ParticipantRepository participantRepository;

	private final PickTurnRepository pickTurnRepository;

	private final RandomGenerator random;

	PickPhase(ParticipantRepository participantRepository, PickTurnRepository pickTurnRepository,
			RandomGenerator random) {
		this.participantRepository = participantRepository;
		this.pickTurnRepository = pickTurnRepository;
		this.random = random;
	}

	/** 랜덤 선택이 시작될 때: 첫 사람을 뽑는다. 타이머는 방이 이미 걸어 두었다. */
	void start(Room room, Instant now) {
		draw(room, now);
	}

	/**
	 * 뽑힌 사람이 시간 안에 정하지 않았다. 이번 바퀴에 아직 안 뽑힌 사람이 있으면 다음 사람을 뽑고 타이머를 처음부터 다시 건다.
	 * 모두 한 번씩 뽑혔으면 더는 자동으로 뽑지 않고 타이머만 멈춘다. 모두 나가 버린 방이 1분마다 끝없이 다시 뽑지 않게 하려는 것이다.
	 * 이때 마지막에 뽑힌 사람의 차례는 그대로 두므로 그 사람은 시간 제한 없이 정할 수 있고, 방장은 다음 바퀴를 시작할 수 있다.
	 */
	void expire(Room room, Instant now) {
		if (PickTurn.isCycleComplete(participantRepository.findByRoom(room),
				pickTurnRepository.findByRoomOrderByTurnNumber(room))) {
			room.stopPickTimer();
			return;
		}
		activeTurn(room).expire(now);
		redraw(room, now);
	}

	/**
	 * 방장의 "다른 사람 뽑기". 방장 화면에 보이던 차례 번호(expectedTurn)가 지금 차례와 다르면 거절해서,
	 * 두 번 눌러도 한 번만 다시 뽑는다. 랜덤 선택은 단계 이름이 그대로라, 단계 이름만으로는 두 번 누르기를 알 수 없다.
	 */
	void skip(Room room, Integer expectedTurn, Instant now) {
		if (expectedTurn == null) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST);
		}
		PickTurn active = activeTurn(room);
		if (active.getTurnNumber() != expectedTurn) {
			throw new BusinessException(ErrorCode.PHASE_ALREADY_CHANGED);
		}
		active.skip(now);
		redraw(room, now);
	}

	private void redraw(Room room, Instant now) {
		// 지난 차례를 끝낸 것(UPDATE)이 새 차례를 넣는 것(INSERT)보다 먼저 DB에 반영되어야,
		// "방마다 정하는 중인 차례는 하나"라는 DB 규칙에 걸리지 않는다.
		// 지금은 draw()가 지난 차례들을 조회할 때 Hibernate가 바뀐 내용을 먼저 반영해서(자동 flush) 이 줄이 없어도 순서가 맞지만,
		// 조회 순서가 바뀌면 깨지는 숨은 의존이라 직접 부른다.
		pickTurnRepository.flush();
		draw(room, now);
		room.restartPickTimer(now);
	}

	private void draw(Room room, Instant now) {
		List<PickTurn> previousTurns = pickTurnRepository.findByRoomOrderByTurnNumber(room);
		pickTurnRepository
			.save(PickTurn.drawNext(room, participantRepository.findByRoom(room), previousTurns, random, now));
	}

	private PickTurn activeTurn(Room room) {
		return pickTurnRepository.findByRoomAndStatus(room, PickTurnStatus.ACTIVE)
			.orElseThrow(() -> new IllegalStateException("랜덤 선택 중인데 정하는 중인 차례가 없다: " + room.getCode()));
	}

}
