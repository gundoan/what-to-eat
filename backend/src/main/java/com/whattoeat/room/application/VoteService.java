package com.whattoeat.room.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import com.whattoeat.room.domain.VoteTally;
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
	 * 투표지를 낸다. 투표 중이면 첫 판, 재투표 중이면 둘째 판에 낸다(첫 판에 냈어도 재투표에는 다시 낸다).
	 * 같은 내용을 다시 보내면(버튼 두 번 누르기 등) 아무것도 바꾸지 않고, 다른 내용이면 거절한다.
	 *
	 * @param allGood "다 좋음"(투표하지 않음)이면 true. 이때 votes는 비어 있어야 한다
	 * @param votes 메뉴 공개 번호와 점수(+1 또는 -1)
	 */
	@Transactional
	public RoomView submit(String code, String rawToken, boolean allGood, List<VoteInput> votes) {
		Room room = access.lock(code);
		Participant me = access.requireParticipant(room, rawToken);
		if (room.getStatus() != RoomStatus.VOTING && room.getStatus() != RoomStatus.REVOTING) {
			throw new BusinessException(ErrorCode.NOT_VOTING_PHASE);
		}
		if (allGood && !votes.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_BALLOT);
		}
		int round = room.voteRound();
		List<Menu> remaining = Menu.remaining(menuRepository.findSortedByRoom(room));
		List<Menu> candidates = VoteTally.candidates(round, remaining,
				ballotRepository.findWithVotesByRoomAndVoteRound(room, Ballot.FIRST_ROUND));
		List<VoteChoice> choices = toChoices(votes, remaining, candidates);

		Optional<Ballot> submitted = ballotRepository.findByParticipantAndVoteRound(me, round);
		if (submitted.isPresent()) {
			if (submitted.get().hasSameContent(allGood, choices)) {
				return assembler.view(room, me);
			}
			throw new BusinessException(ErrorCode.ALREADY_VOTED);
		}

		Instant now = clock.instant();
		ballotRepository.save(allGood ? Ballot.allGood(room, me, round, now) : Ballot.of(room, me, round, choices, now));
		phaseService.advanceIfAllDone(room, now);
		events.publishEvent(new RoomChangedEvent(code));
		return assembler.view(room, me);
	}

	/**
	 * 화면이 보낸 메뉴 번호를 메뉴로 바꾼다. 남은 메뉴가 아니면(없거나 합쳐져 사라진 메뉴) 찾을 수 없다고 하고,
	 * 남은 메뉴지만 이번 판의 후보가 아니면(재투표에서 동점이 아니었던 메뉴) 후보가 아니라고 한다.
	 */
	private static List<VoteChoice> toChoices(List<VoteInput> votes, List<Menu> remaining, List<Menu> candidates) {
		Map<String, Menu> remainingByPublicId = remaining.stream()
			.collect(Collectors.toMap(Menu::getPublicId, Function.identity()));
		Set<String> candidateIds = candidates.stream().map(Menu::getPublicId).collect(Collectors.toSet());
		return votes.stream().map(vote -> {
			if (vote.menuId() == null || vote.point() == null) {
				throw new BusinessException(ErrorCode.INVALID_BALLOT);
			}
			Menu menu = remainingByPublicId.get(vote.menuId());
			if (menu == null) {
				throw new BusinessException(ErrorCode.MENU_NOT_FOUND);
			}
			if (!candidateIds.contains(vote.menuId())) {
				throw new BusinessException(ErrorCode.NOT_A_CANDIDATE);
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
