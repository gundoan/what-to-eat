package com.whattoeat.room.domain;

import java.time.Instant;
import java.util.List;
import java.util.random.RandomGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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

@Entity
@Table(name = "room")
public class Room {

	/** 방장을 포함한 최대 인원. */
	public static final int MAX_PARTICIPANTS = 50;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false, length = RoomCode.LENGTH)
	private String code;

	// MySQL에서 Hibernate는 enum을 MySQL 전용 ENUM 타입으로 기대하므로, 테이블과 같은 VARCHAR로 맞춘다.
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private RoomStatus status;

	@Embedded
	private PhaseDurations phaseDurations;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	/** 지금 단계가 시작된 시각. 대기 중에는 비어 있다. */
	private Instant phaseStartedAt;

	/** 지금 단계가 끝나는 시각. 시간 제한이 없는 단계에서는 비어 있다. */
	private Instant phaseEndsAt;

	/** 결정된 메뉴. 끝나기 전이나, 랜덤 선택에서 뽑힌 사람이 이름을 직접 정한 경우에는 비어 있다. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "result_menu_id")
	private Menu resultMenu;

	/** 결정된 이름. 끝나기 전에는 비어 있다. */
	@Column(length = MenuName.MAX_LENGTH)
	private String resultName;

	/** 어떻게 결정됐는지. 끝나기 전에는 비어 있다. */
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(length = 20)
	private ResultReason resultReason;

	protected Room() {
	}

	private Room(String code, PhaseDurations phaseDurations, Instant createdAt) {
		this.code = code;
		this.status = RoomStatus.WAITING;
		this.phaseDurations = phaseDurations;
		this.createdAt = createdAt;
	}

	public static Room create(String code, PhaseDurations phaseDurations, Instant now) {
		return new Room(code, phaseDurations, now);
	}

	public boolean isFull(long participantCount) {
		return participantCount >= MAX_PARTICIPANTS;
	}

	/** 대기 중 → 제안 중. 방장이 정한 제안 시간만큼 타이머를 건다. */
	public void startProposal(Instant now) {
		requireStatus(RoomStatus.WAITING);
		moveTo(RoomStatus.PROPOSING, now, phaseDurations.proposalSeconds());
	}

	/**
	 * 제안 중 → 다음 단계. 메뉴 수에 따라 갈 곳이 다르다.
	 * <ul>
	 * <li>2개 이상: 합치기 중. 방장이 정한 합치기 시간만큼 타이머를 건다.</li>
	 * <li>1개: 합칠 상대도 투표할 필요도 없으므로 바로 그 메뉴로 결정한다.</li>
	 * <li>0개: 아무도 제안하지 않았으므로 랜덤 선택.</li>
	 * </ul>
	 *
	 * @param menus 제안된 메뉴들
	 */
	public void finishProposal(List<Menu> menus, Instant now) {
		requireStatus(RoomStatus.PROPOSING);
		if (menus.size() >= 2) {
			moveTo(RoomStatus.MERGING, now, phaseDurations.mergeSeconds());
		}
		else if (menus.size() == 1) {
			decide(menus.getFirst(), ResultReason.ONLY_MENU, now);
		}
		else {
			startRandomPick(now);
		}
	}

	/**
	 * 합치기 중 → 다음 단계. 남은 메뉴가 2개 이상이면 투표 중(투표 시간만큼 타이머), 합쳐져서 1개만 남았으면 투표 없이 결정한다.
	 *
	 * @param remainingMenus 다른 메뉴에 합쳐지지 않고 남은 메뉴들
	 */
	public void finishMerging(List<Menu> remainingMenus, Instant now) {
		requireStatus(RoomStatus.MERGING);
		if (remainingMenus.isEmpty()) {
			// 합치면 대표 메뉴가 남으므로 메뉴가 모두 사라질 수는 없다. 여기에 걸리면 코드의 실수다.
			throw new IllegalStateException("방 " + code + "은 합치기 뒤에 남은 메뉴가 없다");
		}
		if (remainingMenus.size() >= 2) {
			moveTo(RoomStatus.VOTING, now, phaseDurations.voteSeconds());
		}
		else {
			decide(remainingMenus.getFirst(), ResultReason.ONLY_MENU, now);
		}
	}

	/**
	 * 투표 중 → 다음 단계. 1등이 하나면 그 메뉴로 결정한다.
	 * 동점이면 동점 메뉴만 놓고 재투표(투표와 같은 시간), 아무도 표를 내지 않았으면 랜덤 선택으로 간다.
	 */
	public void finishVoting(VoteTally tally, Instant now) {
		requireStatus(RoomStatus.VOTING);
		if (tally.hasNoVotes()) {
			startRandomPick(now);
			return;
		}
		List<Menu> leaders = tally.leaders();
		if (leaders.size() == 1) {
			decide(leaders.getFirst(), ResultReason.VOTE, now);
		}
		else {
			moveTo(RoomStatus.REVOTING, now, phaseDurations.voteSeconds());
		}
	}

	/**
	 * 재투표 중 → 결정. 재투표는 한 번뿐이라 이 단계 다음은 항상 결정이다.
	 * <ul>
	 * <li>1등이 하나면 그 메뉴로 결정한다.</li>
	 * <li>또 동점이면 재투표의 1등끼리 중에서, 아무도 표를 내지 않았으면 재투표 후보 전체 중에서 서버가 무작위로 고른다.
	 * 재투표에서 1등이 아닌 메뉴는 재투표에서 밀렸으므로 무작위 후보에서 빠진다.</li>
	 * </ul>
	 *
	 * @param candidates 재투표 후보(첫 투표에서 동점이었던 메뉴들)
	 */
	public void finishRevote(VoteTally tally, List<Menu> candidates, RandomGenerator random, Instant now) {
		requireStatus(RoomStatus.REVOTING);
		if (!tally.hasNoVotes() && tally.leaders().size() == 1) {
			decide(tally.leaders().getFirst(), ResultReason.REVOTE, now);
			return;
		}
		List<Menu> pool = tally.hasNoVotes() ? candidates : tally.leaders();
		decide(pool.get(random.nextInt(pool.size())), ResultReason.REVOTE_RANDOM, now);
	}

	/**
	 * 랜덤 선택에서 뽑힌 사람이 정한 이름으로 결정한다. 메뉴 목록에 없는 이름일 수도 있어서 이름만 남긴다.
	 */
	public void decideByPick(MenuName name, Instant now) {
		requireStatus(RoomStatus.RANDOM_PICK);
		this.resultMenu = null;
		this.resultName = name.value();
		this.resultReason = ResultReason.PICKED;
		moveTo(RoomStatus.FINISHED, now, null);
	}

	/** 랜덤 선택에서 다른 사람을 다시 뽑았다. 새로 뽑힌 사람에게 랜덤 선택 시간을 처음부터 준다. */
	public void restartPickTimer(Instant now) {
		requireStatus(RoomStatus.RANDOM_PICK);
		moveTo(RoomStatus.RANDOM_PICK, now, phaseDurations.randomPickSeconds());
	}

	/**
	 * 지금(또는 마지막으로 끝난) 투표가 몇 번째 판인지. 투표 중이면 1, 재투표 중이면 2이고,
	 * 끝난 방은 재투표로 결정됐으면 2, 아니면 1이다.
	 */
	public int voteRound() {
		boolean revoted = status == RoomStatus.REVOTING || resultReason == ResultReason.REVOTE
				|| resultReason == ResultReason.REVOTE_RANDOM;
		return revoted ? Ballot.REVOTE_ROUND : Ballot.FIRST_ROUND;
	}

	/** 아무도 메뉴를 내지 않았거나 아무도 투표하지 않았다. 뽑기 차례는 서비스가 만들고, 뽑힌 사람에게 랜덤 선택 시간을 준다. */
	private void startRandomPick(Instant now) {
		moveTo(RoomStatus.RANDOM_PICK, now, phaseDurations.randomPickSeconds());
	}

	/** 메뉴를 결정하고 끝낸다. 끝난 방에는 더 넘어갈 단계가 없다. */
	private void decide(Menu menu, ResultReason reason, Instant now) {
		this.resultMenu = menu;
		this.resultName = menu.getName();
		this.resultReason = reason;
		moveTo(RoomStatus.FINISHED, now, null);
	}

	public boolean isPhaseExpired(Instant now) {
		return phaseEndsAt != null && !now.isBefore(phaseEndsAt);
	}

	/** @param seconds 이 단계의 시간. null이면 시간 제한이 없다 */
	private void moveTo(RoomStatus next, Instant now, Integer seconds) {
		this.status = next;
		this.phaseStartedAt = now;
		this.phaseEndsAt = seconds == null ? null : now.plusSeconds(seconds);
	}

	private void requireStatus(RoomStatus expected) {
		if (status != expected) {
			// 서비스가 단계를 확인한 뒤에 부르므로, 여기에 걸리면 코드의 실수다.
			throw new IllegalStateException("방 " + code + "은 " + expected + " 단계가 아니라 " + status + " 단계다");
		}
	}

	public Long getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public RoomStatus getStatus() {
		return status;
	}

	public PhaseDurations getPhaseDurations() {
		return phaseDurations;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getPhaseStartedAt() {
		return phaseStartedAt;
	}

	public Instant getPhaseEndsAt() {
		return phaseEndsAt;
	}

	/** 결정된 메뉴. 끝나기 전이거나 뽑힌 사람이 이름을 직접 정했으면 null */
	public Menu getResultMenu() {
		return resultMenu;
	}

	/** 결정된 이름. 끝나기 전이면 null */
	public String getResultName() {
		return resultName;
	}

	/** 어떻게 결정됐는지. 끝나기 전이면 null */
	public ResultReason getResultReason() {
		return resultReason;
	}

}
