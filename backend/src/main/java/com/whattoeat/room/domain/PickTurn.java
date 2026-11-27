package com.whattoeat.room.domain;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 랜덤 선택의 뽑기 차례 하나: 서버가 무작위로 뽑은 사람과, 그 사람이 메뉴를 정했는지.
 *
 * <ul>
 * <li>뽑힌 사람만 메뉴를 정할 수 있고, 넘기기는 없다. 시간 안에 안 정하거나 방장이 "다른 사람 뽑기"를 누르면 다음 차례로 넘어간다.</li>
 * <li>카드 뽑기처럼 한 바퀴(cycle) 안에서는 이미 뽑힌 사람을 다시 뽑지 않는다. 모두 뽑히면 다음 바퀴로 처음부터 뽑는다.</li>
 * <li>방마다 정하는 중(ACTIVE)인 차례는 하나뿐이다. DB도 막는다.</li>
 * </ul>
 */
@Entity
@Table(name = "pick_turn")
public class PickTurn {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "room_id", nullable = false, updatable = false)
	private Room room;

	/** 뽑힌 사람. 서버 안에서만 쓰고 다른 참여자에게는 절대 보여주지 않는다. */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "participant_id", nullable = false, updatable = false)
	private Participant participant;

	/** 방 안에서 몇 번째 뽑기인지(1부터). 방장의 "다른 사람 뽑기"가 두 번 눌려도 한 번만 다시 뽑게 하는 데 쓴다. */
	@Column(nullable = false, updatable = false)
	private int turnNumber;

	/** 몇 번째 바퀴인지(1부터) */
	@Column(name = "cycle_number", nullable = false, updatable = false)
	private int cycle;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private PickTurnStatus status;

	/** 뽑힌 사람이 정한 메뉴 이름. 정하기 전에는 비어 있다. */
	@Column(length = MenuName.MAX_LENGTH)
	private String menuName;

	@Column(nullable = false, updatable = false)
	private Instant startedAt;

	private Instant finishedAt;

	protected PickTurn() {
	}

	private PickTurn(Room room, Participant participant, int turnNumber, int cycle, Instant now) {
		this.room = room;
		this.participant = participant;
		this.turnNumber = turnNumber;
		this.cycle = cycle;
		this.status = PickTurnStatus.ACTIVE;
		this.startedAt = now;
	}

	/**
	 * 다음 차례를 뽑는다. 지금 바퀴에서 아직 안 뽑힌 사람 중 무작위로 한 명을 고르고, 모두 뽑혔으면 다음 바퀴로 넘어가 전원 중에서 고른다.
	 * 늦게 들어온 사람은 지금 바퀴에서 아직 안 뽑힌 사람이므로 바로 후보가 된다.
	 *
	 * @param participants 방의 참여자 모두(방을 나간 사람도 포함된다. 뽑히면 시간이 다 되어 다음 차례로 넘어간다)
	 * @param previousTurns 이 방의 지난 차례들(순서대로)
	 * @param random 서버가 뽑아야 화면(클라이언트)에서 결과를 조작할 수 없다
	 */
	public static PickTurn drawNext(Room room, List<Participant> participants, List<PickTurn> previousTurns,
			RandomGenerator random, Instant now) {
		if (participants.isEmpty()) {
			throw new IllegalArgumentException("방에는 방장이 있으므로 참여자가 없을 수 없다");
		}
		int cycle = previousTurns.isEmpty() ? 1 : previousTurns.getLast().getCycle();
		Set<Long> pickedThisCycle = previousTurns.stream()
			.filter(turn -> turn.getCycle() == cycle)
			.map(turn -> turn.getParticipant().getId())
			.collect(Collectors.toSet());
		List<Participant> candidates = participants.stream()
			.filter(participant -> !pickedThisCycle.contains(participant.getId()))
			.toList();
		int nextCycle = cycle;
		if (candidates.isEmpty()) {
			// 한 바퀴를 다 돌았는데 아무도 안 정했다. 카드를 다시 모아 섞듯이 처음부터 뽑는다.
			nextCycle = cycle + 1;
			candidates = participants;
		}
		Participant chosen = candidates.get(random.nextInt(candidates.size()));
		return new PickTurn(room, chosen, previousTurns.size() + 1, nextCycle, now);
	}

	/** 뽑힌 사람이 메뉴를 정했다. */
	public void decide(MenuName name, Instant now) {
		requireActive();
		this.menuName = name.value();
		finish(PickTurnStatus.DECIDED, now);
	}

	/** 시간 안에 정하지 않았다. */
	public void expire(Instant now) {
		requireActive();
		finish(PickTurnStatus.EXPIRED, now);
	}

	/** 방장이 "다른 사람 뽑기"를 눌렀다. */
	public void skip(Instant now) {
		requireActive();
		finish(PickTurnStatus.SKIPPED, now);
	}

	public boolean isActive() {
		return status == PickTurnStatus.ACTIVE;
	}

	/** 이 사람이 뽑힌 차례인지 */
	public boolean isFor(Participant other) {
		return participant.getId().equals(other.getId());
	}

	private void finish(PickTurnStatus result, Instant now) {
		this.status = result;
		this.finishedAt = now;
	}

	private void requireActive() {
		if (!isActive()) {
			// 서비스가 정하는 중인 차례만 골라서 부르므로, 여기에 걸리면 코드의 실수다.
			throw new IllegalStateException("뽑기 차례 " + turnNumber + "은 이미 끝났다");
		}
	}

	public Participant getParticipant() {
		return participant;
	}

	public int getTurnNumber() {
		return turnNumber;
	}

	public int getCycle() {
		return cycle;
	}

	public PickTurnStatus getStatus() {
		return status;
	}

	public String getMenuName() {
		return menuName;
	}

}
