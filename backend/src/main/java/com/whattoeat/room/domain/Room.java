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

}
