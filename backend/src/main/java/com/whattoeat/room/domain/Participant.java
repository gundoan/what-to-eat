package com.whattoeat.room.domain;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 방의 익명 참여자. 닉네임이 없고, 브라우저에 준 토큰의 해시로만 구분한다.
 */
@Entity
@Table(name = "participant")
public class Participant {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "room_id", nullable = false, updatable = false)
	private Room room;

	@Column(nullable = false, updatable = false, length = 64)
	private String tokenHash;

	@Column(nullable = false, updatable = false)
	private boolean host;

	@Column(nullable = false, updatable = false)
	private Instant joinedAt;

	protected Participant() {
	}

	private Participant(Room room, String tokenHash, boolean host, Instant joinedAt) {
		this.room = room;
		this.tokenHash = tokenHash;
		this.host = host;
		this.joinedAt = joinedAt;
	}

	public static Participant host(Room room, String tokenHash, Instant now) {
		return new Participant(room, tokenHash, true, now);
	}

	public static Participant guest(Room room, String tokenHash, Instant now) {
		return new Participant(room, tokenHash, false, now);
	}

	public boolean belongsTo(Room other) {
		return Objects.equals(room.getId(), other.getId());
	}

	public Long getId() {
		return id;
	}

	public boolean isHost() {
		return host;
	}

	public Instant getJoinedAt() {
		return joinedAt;
	}

}
