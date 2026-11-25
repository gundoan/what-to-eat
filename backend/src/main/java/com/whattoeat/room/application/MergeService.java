package com.whattoeat.room.application;

import java.time.Clock;
import java.time.Instant;

import com.whattoeat.common.error.BusinessException;
import com.whattoeat.common.error.ErrorCode;
import com.whattoeat.room.domain.Menu;
import com.whattoeat.room.domain.MenuRepository;
import com.whattoeat.room.domain.MergeGroup;
import com.whattoeat.room.domain.MergeGroupRepository;
import com.whattoeat.room.domain.Participant;
import com.whattoeat.room.domain.Room;
import com.whattoeat.room.domain.RoomStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 합치기 단계에서 참여자가 하는 일: 합치기 질문에 답하기, "합치기 끝냄".
 * 할 때마다 그룹의 모두가 답했는지, 방의 모두가 끝냈는지 확인해서 바로 다음으로 진행한다.
 */
@Service
public class MergeService {

	private final MenuRepository menuRepository;

	private final MergeGroupRepository mergeGroupRepository;

	private final MergePhase mergePhase;

	private final RoomAccess access;

	private final RoomPhaseService phaseService;

	private final RoomStateAssembler assembler;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	MergeService(MenuRepository menuRepository, MergeGroupRepository mergeGroupRepository, MergePhase mergePhase,
			RoomAccess access, RoomPhaseService phaseService, RoomStateAssembler assembler,
			ApplicationEventPublisher events, Clock clock) {
		this.menuRepository = menuRepository;
		this.mergeGroupRepository = mergeGroupRepository;
		this.mergePhase = mergePhase;
		this.access = access;
		this.phaseService = phaseService;
		this.assembler = assembler;
		this.events = events;
		this.clock = clock;
	}

	/**
	 * 합치기 질문에 답한다. 한 번 한 답은 바꿀 수 없고, 같은 답을 다시 보내면 아무것도 바꾸지 않는다.
	 * 그룹의 모두가 답하면 바로 결과가 나오고, 그 결과로 모두 끝냈으면(메뉴가 합쳐져 사라진 사람은 끝낸 것) 다음 단계로 넘어간다.
	 */
	@Transactional
	public RoomView answer(String code, String rawToken, String groupId, boolean agree) {
		Room room = access.lock(code);
		Participant me = access.requireParticipant(room, rawToken);
		requireMergePhase(room);
		MergeGroup group = mergeGroupRepository.findByRoomAndPublicId(room, groupId)
			.orElseThrow(() -> new BusinessException(ErrorCode.MERGE_GROUP_NOT_FOUND));
		Menu myMenu = menuRepository.findByParticipant(me)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_MERGE_MEMBER));

		Instant now = clock.instant();
		if (group.answer(myMenu, agree, now)) {
			mergePhase.resolveIfEveryoneAnswered(group, now);
			phaseService.advanceIfAllDone(room, now);
			events.publishEvent(new RoomChangedEvent(code));
		}
		return assembler.view(room, me);
	}

	/**
	 * "합치기 끝냄". 나는 더 합칠 생각이 없다는 뜻이라, 답하지 않은 질문이 있으면 "합치지 않기"로 답한 것으로 본다.
	 * 한 번 누르면 되돌릴 수 없고, 다시 눌러도 아무것도 바뀌지 않는다.
	 */
	@Transactional
	public RoomView finish(String code, String rawToken) {
		Room room = access.lock(code);
		Participant me = access.requireParticipant(room, rawToken);
		requireMergePhase(room);

		if (!me.isMergeDone()) {
			Instant now = clock.instant();
			me.markMergeDone();
			menuRepository.findByParticipant(me).ifPresent(myMenu -> declineWaitingQuestion(myMenu, now));
			phaseService.advanceIfAllDone(room, now);
			events.publishEvent(new RoomChangedEvent(code));
		}
		return assembler.view(room, me);
	}

	private void declineWaitingQuestion(Menu myMenu, Instant now) {
		mergeGroupRepository.findPendingContaining(myMenu)
			.filter(group -> group.isWaitingFor(myMenu))
			.ifPresent(group -> {
				group.answer(myMenu, false, now);
				mergePhase.resolveIfEveryoneAnswered(group, now);
			});
	}

	private void requireMergePhase(Room room) {
		if (room.getStatus() != RoomStatus.MERGING) {
			throw new BusinessException(ErrorCode.NOT_MERGE_PHASE);
		}
	}

}
