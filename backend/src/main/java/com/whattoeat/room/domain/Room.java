package com.whattoeat.room.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
	 * <li>1개: 합칠 상대가 없으므로 바로 투표 중.</li>
	 * <li>0개: 아무도 제안하지 않았으므로 랜덤 선택.</li>
	 * </ul>
	 */
	public void finishProposal(long menuCount, Instant now) {
		requireStatus(RoomStatus.PROPOSING);
		if (menuCount >= 2) {
			moveTo(RoomStatus.MERGING, now, phaseDurations.mergeSeconds());
		}
		else if (menuCount == 1) {
			// 투표 단계의 타이머와 "메뉴가 1개면 바로 결정"은 기능 4·5에서 만든다.
			moveTo(RoomStatus.VOTING, now, null);
		}
		else {
			// 랜덤 선택의 타이머는 기능 5에서 건다.
			moveTo(RoomStatus.RANDOM_PICK, now, null);
		}
	}

	/** 합치기 중 → 투표 중. */
	public void finishMerging(Instant now) {
		requireStatus(RoomStatus.MERGING);
		// 투표 단계의 타이머는 기능 4에서 건다.
		moveTo(RoomStatus.VOTING, now, null);
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

}
