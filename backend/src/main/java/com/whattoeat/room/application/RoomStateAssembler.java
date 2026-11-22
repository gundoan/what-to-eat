package com.whattoeat.room.application;

import java.time.Clock;

import com.whattoeat.room.application.RoomView.Me;
import com.whattoeat.room.domain.Menu;
import com.whattoeat.room.domain.MenuRepository;
import com.whattoeat.room.domain.Participant;
import com.whattoeat.room.domain.ParticipantRepository;
import com.whattoeat.room.domain.Room;
import com.whattoeat.room.domain.RoomStatus;
import org.springframework.stereotype.Component;

/**
 * DB에서 필요한 것을 모아 화면에 보낼 방 상태(RoomState)와 참여자별 화면 정보(RoomView)를 만든다.
 * 트랜잭션 안에서 불러야 한다.
 */
@Component
class RoomStateAssembler {

	private final ParticipantRepository participantRepository;

	private final MenuRepository menuRepository;

	private final Clock clock;

	RoomStateAssembler(ParticipantRepository participantRepository, MenuRepository menuRepository, Clock clock) {
		this.participantRepository = participantRepository;
		this.menuRepository = menuRepository;
		this.clock = clock;
	}

	RoomState state(Room room) {
		long doneCount = room.getStatus() == RoomStatus.PROPOSING ? participantRepository.countProposalDone(room) : 0;
		return new RoomState(room.getCode(), room.getStatus(), room.getPhaseDurations(),
				participantRepository.countByRoom(room), room.getPhaseEndsAt(), clock.instant(),
				menuRepository.findNamesByRoom(room), doneCount);
	}

	RoomView view(Room room, Participant me) {
		String proposal = menuRepository.findByParticipant(me).map(Menu::getName).orElse(null);
		return new RoomView(state(room), new Me(me.isHost(), proposal, me.isProposalAllGood()));
	}

}
