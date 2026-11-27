package com.whattoeat.room.application;

import java.time.Clock;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.whattoeat.room.application.RoomState.MenuItem;
import com.whattoeat.room.application.RoomState.MenuScoreItem;
import com.whattoeat.room.application.RoomState.MergeGroupItem;
import com.whattoeat.room.application.RoomState.ResultItem;
import com.whattoeat.room.application.RoomView.BallotItem;
import com.whattoeat.room.application.RoomView.Me;
import com.whattoeat.room.application.RoomView.VoteItem;
import com.whattoeat.room.domain.Ballot;
import com.whattoeat.room.domain.BallotRepository;
import com.whattoeat.room.domain.Menu;
import com.whattoeat.room.domain.MenuRepository;
import com.whattoeat.room.domain.MergeGroup;
import com.whattoeat.room.domain.MergeGroupRepository;
import com.whattoeat.room.domain.MergeRequestRules;
import com.whattoeat.room.domain.Participant;
import com.whattoeat.room.domain.ParticipantRepository;
import com.whattoeat.room.domain.Room;
import com.whattoeat.room.domain.RoomStatus;
import com.whattoeat.room.domain.VoteTally;
import org.springframework.stereotype.Component;

/**
 * DB에서 필요한 것을 모아 화면에 보낼 방 상태(RoomState)와 참여자별 "나" 정보(Me)를 만든다.
 * 트랜잭션 안에서 불러야 한다.
 */
@Component
class RoomStateAssembler {

	private final ParticipantRepository participantRepository;

	private final MenuRepository menuRepository;

	private final MergeGroupRepository mergeGroupRepository;

	private final BallotRepository ballotRepository;

	private final Clock clock;

	RoomStateAssembler(ParticipantRepository participantRepository, MenuRepository menuRepository,
			MergeGroupRepository mergeGroupRepository, BallotRepository ballotRepository, Clock clock) {
		this.participantRepository = participantRepository;
		this.menuRepository = menuRepository;
		this.mergeGroupRepository = mergeGroupRepository;
		this.ballotRepository = ballotRepository;
		this.clock = clock;
	}

	/** 한 참여자가 받을 화면 정보. 요청에 대한 응답으로 쓴다. */
	RoomView view(Room room, Participant me) {
		return views(room).viewOf(me.getId()).orElseThrow();
	}

	/**
	 * 방 상태와 참여자 모두의 "나" 정보를 한 번에 만든다.
	 * 참여자가 몇 명이든 참여자, 메뉴, 합치기 그룹, 투표지를 한 번씩만 읽는다.
	 */
	RoomViews views(Room room) {
		List<Participant> participants = participantRepository.findByRoom(room);
		List<Menu> menus = menuRepository.findSortedByRoom(room);
		List<MergeGroup> groups = mergeGroupRepository.findWithMenusByRoom(room);
		List<Ballot> allBallots = ballotRepository.findWithVotesByRoom(room);
		List<Ballot> firstRound = ballotsOf(allBallots, Ballot.FIRST_ROUND);
		List<Ballot> revote = ballotsOf(allBallots, Ballot.REVOTE_ROUND);
		List<Ballot> currentRound = room.voteRound() == Ballot.FIRST_ROUND ? firstRound : revote;
		Map<Long, Menu> menuByParticipantId = Menu.byParticipantId(menus);
		Map<Long, Ballot> ballotByParticipantId = Ballot.byParticipantId(currentRound);
		List<Menu> remaining = Menu.remaining(menus);
		List<Menu> revoteCandidates = VoteTally.candidates(Ballot.REVOTE_ROUND, remaining, firstRound);
		RoomStatus status = room.getStatus();

		long doneCount = participants.stream()
			.filter(participant -> participant.hasFinished(status, menuByParticipantId.get(participant.getId()),
					ballotByParticipantId.get(participant.getId())))
			.count();
		RoomState state = new RoomState(room.getCode(), status, room.getPhaseDurations(), participants.size(),
				room.getPhaseEndsAt(), clock.instant(), items(remaining), doneCount, groupItems(groups, menus),
				voteCandidates(status, remaining, revoteCandidates), firstRoundScores(status, remaining, firstRound),
				revoteScores(room, revoteCandidates, revote), result(room));

		MeFactory meFactory = new MeFactory(status, groups, remaining, MergeRequestRules.of(participants, groups));
		Map<Long, Me> meByParticipantId = new HashMap<>();
		for (Participant participant : participants) {
			meByParticipantId.put(participant.getId(), meFactory.me(participant,
					menuByParticipantId.get(participant.getId()), ballotByParticipantId.get(participant.getId())));
		}
		return new RoomViews(state, meByParticipantId);
	}

	private static List<Ballot> ballotsOf(List<Ballot> ballots, int round) {
		return ballots.stream().filter(ballot -> ballot.getVoteRound() == round).toList();
	}

	/** 지금 표를 줄 수 있는 메뉴. 투표면 남은 메뉴 전부, 재투표면 동점이었던 메뉴만이다. */
	private static List<MenuItem> voteCandidates(RoomStatus status, List<Menu> remaining,
			List<Menu> revoteCandidates) {
		return switch (status) {
			case VOTING -> items(remaining);
			case REVOTING -> items(revoteCandidates);
			default -> List.of();
		};
	}

	/**
	 * 첫 투표의 점수. 투표가 끝난 뒤(재투표로 가거나 결정됐을 때)에만 보여준다.
	 * 투표 중에 보여주면 누가 표를 낸 순간 바뀐 점수로 그 사람의 표를 짐작할 수 있고, 앞선 메뉴로 표가 쏠릴 수 있다.
	 * 투표 없이 결정된 경우(메뉴가 하나뿐)에는 보여줄 점수가 없다.
	 */
	private static List<MenuScoreItem> firstRoundScores(RoomStatus status, List<Menu> remaining,
			List<Ballot> ballots) {
		if (status != RoomStatus.REVOTING && status != RoomStatus.FINISHED) {
			return List.of();
		}
		return scores(VoteTally.count(remaining, ballots));
	}

	/** 재투표의 점수. 재투표가 끝나 결정된 뒤에만 보여준다. */
	private static List<MenuScoreItem> revoteScores(Room room, List<Menu> revoteCandidates, List<Ballot> ballots) {
		boolean revoteFinished = room.getStatus() == RoomStatus.FINISHED
				&& room.voteRound() == Ballot.REVOTE_ROUND;
		return revoteFinished ? scores(VoteTally.count(revoteCandidates, ballots)) : List.of();
	}

	private static List<MenuScoreItem> scores(VoteTally tally) {
		if (tally.hasNoVotes()) {
			return List.of();
		}
		return tally.ranking()
			.stream()
			.map(score -> new MenuScoreItem(item(score.menu()), score.plus(), score.minus(), score.total()))
			.toList();
	}

	private static ResultItem result(Room room) {
		return room.getStatus() == RoomStatus.FINISHED ? new ResultItem(room.getResultName(), room.getResultReason())
				: null;
	}

	private static List<MenuItem> items(List<Menu> menus) {
		return menus.stream().map(RoomStateAssembler::item).toList();
	}

	/**
	 * 그룹 안의 메뉴는 목록과 같은 가나다순으로, 그룹끼리는 첫 메뉴의 가나다순으로 놓는다.
	 * 그룹이 만들어진 순서는 드러내지 않는다.
	 */
	private static List<MergeGroupItem> groupItems(List<MergeGroup> groups, List<Menu> sortedMenus) {
		Map<Long, Integer> displayOrder = new HashMap<>();
		for (int i = 0; i < sortedMenus.size(); i++) {
			displayOrder.put(sortedMenus.get(i).getId(), i);
		}
		Comparator<Menu> inDisplayOrder = Comparator.comparing(menu -> displayOrder.get(menu.getId()));

		record SortedGroup(MergeGroup group, List<Menu> menus) {
		}
		return groups.stream()
			.map(group -> new SortedGroup(group, group.getMenus().stream().sorted(inDisplayOrder).toList()))
			.sorted(Comparator.comparing(sorted -> displayOrder.get(sorted.menus().getFirst().getId())))
			.map(sorted -> {
				MergeGroup group = sorted.group();
				Menu target = group.getTarget();
				Menu representative = group.getRepresentative();
				return new MergeGroupItem(group.getPublicId(), group.getKind(), group.getStatus(),
						sorted.menus().stream().map(RoomStateAssembler::item).toList(),
						target == null ? null : target.getPublicId(),
						representative == null ? null : representative.getName(), group.getAnsweredCount());
			})
			.toList();
	}

	private static MenuItem item(Menu menu) {
		return new MenuItem(menu.getPublicId(), menu.getName());
	}

	/** 참여자마다의 "나" 정보를 만든다. 방 전체에서 한 번만 계산하면 되는 것(그룹별 대기, 요청 규칙)을 미리 들고 있다. */
	private static final class MeFactory {

		private final RoomStatus status;

		private final List<Menu> remaining;

		private final MergeRequestRules requestRules;

		/** 메뉴 번호 → 그 메뉴를 낸 사람의 답을 기다리는 그룹 번호 */
		private final Map<Long, String> waitingGroupIdByMenuId = new HashMap<>();

		/** 메뉴 번호 → 그 메뉴를 낸 사람이 보내고 답을 기다리는 직접 요청 번호 */
		private final Map<Long, String> sentGroupIdByMenuId = new HashMap<>();

		MeFactory(RoomStatus status, List<MergeGroup> groups, List<Menu> remaining, MergeRequestRules requestRules) {
			this.status = status;
			this.remaining = remaining;
			this.requestRules = requestRules;
			// 한 메뉴는 결과가 안 나온 그룹에 많아야 하나 들어 있어서, 메뉴 하나에 그룹 번호 하나다.
			for (MergeGroup group : groups) {
				for (Menu menu : group.getWaitingMenus()) {
					waitingGroupIdByMenuId.put(menu.getId(), group.getPublicId());
				}
				for (Menu menu : group.getMenus()) {
					if (group.isSentBy(menu)) {
						sentGroupIdByMenuId.put(menu.getId(), group.getPublicId());
					}
				}
			}
		}

		Me me(Participant participant, Menu myMenu, Ballot myBallot) {
			boolean proposed = myMenu != null;
			boolean merged = proposed && myMenu.isMerged();
			return new Me(participant.isHost(), proposed ? myMenu.getName() : null, participant.isProposalAllGood(),
					proposed && !merged ? myMenu.getPublicId() : null, merged ? myMenu.finalMenu().getName() : null,
					proposed ? waitingGroupIdByMenuId.get(myMenu.getId()) : null,
					proposed ? sentGroupIdByMenuId.get(myMenu.getId()) : null, requestableMenuIds(participant, myMenu),
					ballotItem(myBallot), participant.hasFinished(status, myMenu, myBallot));
		}

		private static BallotItem ballotItem(Ballot ballot) {
			if (ballot == null) {
				return null;
			}
			List<VoteItem> votes = ballot.getVotes()
				.stream()
				.map(vote -> new VoteItem(vote.getMenu().getPublicId(), vote.getPoint()))
				.toList();
			return new BallotItem(ballot.isAllGood(), votes);
		}

		/** 직접 합치기는 합치기 단계에서만 할 수 있다. 버튼을 켤 메뉴는 요청 API와 같은 규칙으로 고른다. */
		private List<String> requestableMenuIds(Participant participant, Menu myMenu) {
			if (status != RoomStatus.MERGING) {
				return List.of();
			}
			return requestRules.requestableTargets(participant, myMenu, remaining)
				.stream()
				.map(Menu::getPublicId)
				.toList();
		}

	}

}
