package com.whattoeat.room.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.whattoeat.common.error.BusinessException;
import com.whattoeat.common.error.ErrorCode;
import com.whattoeat.room.domain.Ballot;
import com.whattoeat.room.domain.BallotRepository;
import com.whattoeat.room.domain.Menu;
import com.whattoeat.room.domain.MenuRepository;
import com.whattoeat.room.domain.Participant;
import com.whattoeat.room.domain.ParticipantRepository;
import com.whattoeat.room.domain.Room;
import com.whattoeat.room.domain.RoomRepository;
import com.whattoeat.room.domain.RoomStatus;
import com.whattoeat.room.domain.VoteTally;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 방을 다음 단계로 넘긴다. 넘어가는 길은 세 가지다.
 * <ol>
 * <li>방장이 "시작"이나 "다음 단계로"를 누른다 ({@link #advanceByHost})</li>
 * <li>단계 시간이 다 된다 ({@link #advanceExpiredPhases}, 1초마다 실행)</li>
 * <li>모든 참여자가 그 단계에서 할 일을 끝낸다 ({@link #advanceIfAllDone})</li>
 * </ol>
 * 세 경우 모두 방을 잠근 상태에서 조건을 다시 확인하고 넘긴다. 그래서 동시에 일어나도 딱 한 번만 넘어간다.
 */
@Service
public class RoomPhaseService {

	private static final Logger log = LoggerFactory.getLogger(RoomPhaseService.class);

	private final RoomRepository roomRepository;

	private final ParticipantRepository participantRepository;

	private final MenuRepository menuRepository;

	private final BallotRepository ballotRepository;

	private final MergePhase mergePhase;

	private final RoomAccess access;

	private final RoomStateAssembler assembler;

	private final ApplicationEventPublisher events;

	private final TransactionTemplate transactionTemplate;

	private final Clock clock;

	RoomPhaseService(RoomRepository roomRepository, ParticipantRepository participantRepository,
			MenuRepository menuRepository, BallotRepository ballotRepository, MergePhase mergePhase, RoomAccess access,
			RoomStateAssembler assembler, ApplicationEventPublisher events, TransactionTemplate transactionTemplate,
			Clock clock) {
		this.roomRepository = roomRepository;
		this.participantRepository = participantRepository;
		this.menuRepository = menuRepository;
		this.ballotRepository = ballotRepository;
		this.mergePhase = mergePhase;
		this.access = access;
		this.assembler = assembler;
		this.events = events;
		this.transactionTemplate = transactionTemplate;
		this.clock = clock;
	}

	/**
	 * 방장이 누른 "시작" 또는 "다음 단계로".
	 * from에는 방장 화면에 보이던 단계를 받는다. 이미 다른 단계로 넘어갔다면 거절해서, 버튼을 두 번 눌러도 한 단계만 넘어가게 한다.
	 */
	@Transactional
	public RoomView advanceByHost(String code, String rawToken, RoomStatus from) {
		Room room = access.lock(code);
		Participant me = access.requireParticipant(room, rawToken);
		if (!me.isHost()) {
			throw new BusinessException(ErrorCode.NOT_HOST);
		}
		if (room.getStatus() != from) {
			throw new BusinessException(ErrorCode.PHASE_ALREADY_CHANGED);
		}
		moveToNextPhase(room, clock.instant());
		events.publishEvent(new RoomChangedEvent(code));
		return assembler.view(room, me);
	}

	/**
	 * 시간이 다 된 방들을 다음 단계로 넘긴다. 방마다 따로 트랜잭션을 써서, 한 방에서 문제가 생겨도 다른 방은 넘어간다.
	 * 지금 시각을 인자로 받아서, 테스트가 "시간이 다 된 뒤"를 직접 만들 수 있다.
	 *
	 * @return 넘긴 방의 수
	 */
	public int advanceExpiredPhases(Instant now) {
		int advanced = 0;
		for (String code : roomRepository.findCodesWithPhaseEndingBy(now)) {
			try {
				if (Boolean.TRUE.equals(transactionTemplate.execute(status -> advanceIfExpired(code, now)))) {
					advanced++;
				}
			}
			catch (RuntimeException e) {
				log.warn("방 {}을 시간 초과로 다음 단계로 넘기지 못했다", code, e);
			}
		}
		return advanced;
	}

	/**
	 * 모든 참여자가 지금 단계에서 할 일을 끝냈으면 바로 다음 단계로 넘긴다. 방을 잠근 트랜잭션 안에서 불러야 한다.
	 * 방을 나간 사람도 참여자로 세므로, 그런 사람이 있으면 시간이 다 되거나 방장이 눌러야 넘어간다.
	 *
	 * @return 넘겼는지
	 */
	boolean advanceIfAllDone(Room room, Instant now) {
		if (!isEveryoneFinished(room)) {
			return false;
		}
		moveToNextPhase(room, now);
		return true;
	}

	/** "끝냈다"의 기준은 단계마다 다르고, {@link Participant#hasFinished}에 한곳으로 모여 있다. */
	private boolean isEveryoneFinished(Room room) {
		// 대기 중은 방장의 "시작"으로만 넘어가고, 재투표와 랜덤 선택은 기능 5에서 만든다. 끝난 방은 넘어갈 곳이 없다.
		RoomStatus status = room.getStatus();
		if (status != RoomStatus.PROPOSING && status != RoomStatus.MERGING && status != RoomStatus.VOTING) {
			return false;
		}
		Map<Long, Menu> menuByParticipantId = Menu.byParticipantId(menuRepository.findSortedByRoom(room));
		Map<Long, Ballot> ballotByParticipantId = Ballot
			.byParticipantId(ballotRepository.findWithVotesByRoomAndVoteRound(room, Ballot.FIRST_ROUND));
		return participantRepository.findByRoom(room)
			.stream()
			.allMatch(participant -> participant.hasFinished(status, menuByParticipantId.get(participant.getId()),
					ballotByParticipantId.get(participant.getId())));
	}

	private boolean advanceIfExpired(String code, Instant now) {
		Optional<Room> locked = roomRepository.findByCodeForUpdate(code);
		// 잠그기 전에 방장이나 마지막으로 끝낸 사람이 이미 넘겼을 수 있으므로, 잠근 뒤에 다시 확인한다.
		if (locked.isEmpty() || !locked.get().isPhaseExpired(now)) {
			return false;
		}
		moveToNextPhase(locked.get(), now);
		events.publishEvent(new RoomChangedEvent(code));
		return true;
	}

	private void moveToNextPhase(Room room, Instant now) {
		switch (room.getStatus()) {
			case WAITING -> room.startProposal(now);
			case PROPOSING -> {
				room.finishProposal(menuRepository.findSortedByRoom(room), now);
				if (room.getStatus() == RoomStatus.MERGING) {
					mergePhase.open(room, now);
				}
			}
			case MERGING -> {
				mergePhase.close(room, now);
				room.finishMerging(Menu.remaining(menuRepository.findSortedByRoom(room)), now);
			}
			case VOTING -> {
				List<Menu> candidates = Menu.remaining(menuRepository.findSortedByRoom(room));
				List<Ballot> ballots = ballotRepository.findWithVotesByRoomAndVoteRound(room, Ballot.FIRST_ROUND);
				room.finishVoting(VoteTally.count(candidates, ballots), now);
			}
			// 재투표와 랜덤 선택의 다음 단계는 기능 5에서 만든다. 끝난 방은 더 넘어갈 곳이 없다.
			case REVOTING, RANDOM_PICK, FINISHED -> throw new BusinessException(ErrorCode.NO_NEXT_PHASE);
		}
	}

}
