package com.whattoeat.room.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.whattoeat.common.error.BusinessException;
import com.whattoeat.common.error.ErrorCode;
import com.whattoeat.room.domain.Ballot;
import com.whattoeat.room.domain.BallotRepository;
import com.whattoeat.room.domain.Menu;
import com.whattoeat.room.domain.MenuRepository;
import com.whattoeat.room.domain.Participant;
import com.whattoeat.room.domain.Room;
import com.whattoeat.room.domain.RoomStatus;
import com.whattoeat.room.domain.VoteChoice;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 투표 단계에서 참여자가 하는 일: 표(1~2개)를 내거나 "다 좋음"을 누르기. 한 번 내면 바꿀 수 없다.
 * 낼 때마다 모두 냈는지 확인해서, 모두 냈으면 바로 점수를 세서 다음으로 진행한다.
 */
@Service
public class VoteService {

	private final MenuRepository menuRepository;

	private final BallotRepository ballotRepository;

	private final RoomAccess access;

	private final RoomPhaseService phaseService;

	private final RoomStateAssembler assembler;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	VoteService(MenuRepository menuRepository, BallotRepository ballotRepository, RoomAccess access,
			RoomPhaseService phaseService, RoomStateAssembler assembler, ApplicationEventPublisher events, Clock clock) {
		this.menuRepository = menuRepository;
		this.ballotRepository = ballotRepository;
		this.access = access;
		this.phaseService = phaseService;
		this.assembler = assembler;
		this.events = events;
		this.clock = clock;
	}

	/**
	 * 투표지를 낸다. 같은 내용을 다시 보내면(버튼 두 번 누르기 등) 아무것도 바꾸지 않고, 다른 내용이면 거절한다.
	 *
	 * @param allGood "다 좋음"(투표하지 않음)이면 true. 이때 votes는 비어 있어야 한다
	 * @param votes 메뉴 공개 번호와 점수(+1 또는 -1)
	 */
	@Transactional
	public RoomView submit(String code, String rawToken, boolean allGood, List<VoteInput> votes) {
		Room room = access.lock(code);
		Participant me = access.requireParticipant(room, rawToken);
		if (room.getStatus() != RoomStatus.VOTING) {
			throw new BusinessException(ErrorCode.NOT_VOTING_PHASE);
		}
		if (allGood && !votes.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_BALLOT);
		}
		List<VoteChoice> choices = toChoices(votes, Menu.remaining(menuRepository.findSortedByRoom(room)));

		Optional<Ballot> submitted = ballotRepository.findByParticipantAndVoteRound(me, Ballot.FIRST_ROUND);
		if (submitted.isPresent()) {
			if (submitted.get().hasSameContent(allGood, choices)) {
				return assembler.view(room, me);
			}
			throw new BusinessException(ErrorCode.ALREADY_VOTED);
		}

		Instant now = clock.instant();
		ballotRepository.save(allGood ? Ballot.allGood(room, me, Ballot.FIRST_ROUND, now)
				: Ballot.of(room, me, Ballot.FIRST_ROUND, choices, now));
		phaseService.advanceIfAllDone(room, now);
		events.publishEvent(new RoomChangedEvent(code));
		return assembler.view(room, me);
	}

	/** 화면이 보낸 메뉴 번호를 메뉴로 바꾼다. 투표는 다른 메뉴에 합쳐지지 않고 남은 메뉴에만 할 수 있다. */
	private static List<VoteChoice> toChoices(List<VoteInput> votes, List<Menu> candidates) {
		Map<String, Menu> candidateByPublicId = candidates.stream()
			.collect(Collectors.toMap(Menu::getPublicId, Function.identity()));
		return votes.stream().map(vote -> {
			if (vote.menuId() == null || vote.point() == null) {
				throw new BusinessException(ErrorCode.INVALID_BALLOT);
			}
			Menu menu = candidateByPublicId.get(vote.menuId());
			if (menu == null) {
				throw new BusinessException(ErrorCode.MENU_NOT_FOUND);
			}
			return new VoteChoice(menu, vote.point());
		}).toList();
	}

	/**
	 * 화면에서 받은 표 하나.
	 *
	 * @param menuId 메뉴의 공개 번호
	 * @param point +1 또는 -1
	 */
	public record VoteInput(String menuId, Integer point) {
	}

}
