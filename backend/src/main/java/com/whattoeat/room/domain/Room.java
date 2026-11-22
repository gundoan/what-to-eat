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
		this.status = RoomStatus.PROPOSING;
		this.phaseStartedAt = now;
		this.phaseEndsAt = now.plusSeconds(phaseDurations.proposalSeconds());
	}

	/** 제안 중 → 메뉴가 하나라도 있으면 합치기 중, 아무도 제안하지 않았으면 랜덤 선택. */
	public void finishProposal(boolean anyMenuProposed, Instant now) {
		requireStatus(RoomStatus.PROPOSING);
		this.status = anyMenuProposed ? RoomStatus.MERGING : RoomStatus.RANDOM_PICK;
		this.phaseStartedAt = now;
		// 합치기와 랜덤 선택에도 시간 제한이 있지만, 그 단계의 동작을 만드는 기능 3·5에서 타이머를 건다.
		this.phaseEndsAt = null;
	}

	public boolean isPhaseExpired(Instant now) {
		return phaseEndsAt != null && !now.isBefore(phaseEndsAt);
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
