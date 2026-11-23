package com.whattoeat.room.application;

import java.time.Clock;
import java.time.Instant;

import com.whattoeat.common.error.BusinessException;
import com.whattoeat.common.error.ErrorCode;
import com.whattoeat.room.domain.Menu;
import com.whattoeat.room.domain.MenuName;
import com.whattoeat.room.domain.MenuRepository;
import com.whattoeat.room.domain.Participant;
import com.whattoeat.room.domain.Room;
import com.whattoeat.room.domain.RoomStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 제안 단계에서 참여자가 하는 일: 메뉴 제안·수정, "다 좋음"(이미 낸 메뉴는 취소된다).
 * 할 때마다 모두 끝냈는지 확인해서, 끝났으면 바로 다음 단계로 넘긴다.
 */
@Service
public class ProposalService {

	private final MenuRepository menuRepository;

	private final RoomAccess access;

	private final RoomPhaseService phaseService;

	private final RoomStateAssembler assembler;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	ProposalService(MenuRepository menuRepository, RoomAccess access, RoomPhaseService phaseService,
			RoomStateAssembler assembler, ApplicationEventPublisher events, Clock clock) {
		this.menuRepository = menuRepository;
		this.access = access;
		this.phaseService = phaseService;
		this.assembler = assembler;
		this.events = events;
		this.clock = clock;
	}

	/** 메뉴를 제안한다. 이미 낸 메뉴가 있으면 새로 만들지 않고 이름만 바꾼다(한 사람 한 메뉴). */
	@Transactional
	public RoomView propose(String code, String rawToken, String rawMenuName) {
		Room room = access.lock(code);
		Participant me = access.requireParticipant(room, rawToken);
		requireProposalPhase(room);
		MenuName name = new MenuName(rawMenuName);
		Instant now = clock.instant();

		menuRepository.findByParticipant(me)
			.ifPresentOrElse(menu -> menu.rename(name, now), () -> menuRepository.save(Menu.propose(room, me, name, now)));
		me.clearProposalAllGood();

		phaseService.advanceIfAllDone(room, now);
		events.publishEvent(new RoomChangedEvent(code));
		return assembler.view(room, me);
	}

	/** "다 좋음". 이미 낸 메뉴가 있으면 취소된다. 단계가 끝나기 전에는 다시 제안할 수 있다. */
	@Transactional
	public RoomView allGood(String code, String rawToken) {
		Room room = access.lock(code);
		Participant me = access.requireParticipant(room, rawToken);
		requireProposalPhase(room);

		menuRepository.findByParticipant(me).ifPresent(menuRepository::delete);
		me.markProposalAllGood();

		phaseService.advanceIfAllDone(room, clock.instant());
		events.publishEvent(new RoomChangedEvent(code));
		return assembler.view(room, me);
	}

	private void requireProposalPhase(Room room) {
		if (room.getStatus() != RoomStatus.PROPOSING) {
			throw new BusinessException(ErrorCode.NOT_PROPOSAL_PHASE);
		}
	}

}
