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

	/** 제안 단계에서 "다 좋음"을 눌렀는지. 메뉴를 냈거나 이 값이 참이면 제안 단계를 끝낸 것이다. */
	@Column(nullable = false)
	private boolean proposalAllGood;

	/** 합치기 단계에서 "합치기 끝냄"을 눌렀는지. */
	@Column(nullable = false)
	private boolean mergeDone;

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

	public void markProposalAllGood() {
		this.proposalAllGood = true;
	}

	public void clearProposalAllGood() {
		this.proposalAllGood = false;
	}

	public boolean isProposalAllGood() {
		return proposalAllGood;
	}

	public void markMergeDone() {
		this.mergeDone = true;
	}

	public boolean isMergeDone() {
		return mergeDone;
	}

	/**
	 * 지금 단계에서 할 일을 끝냈는지. "N명 중 M명 끝냄"과 "모두 끝내면 바로 다음 단계로"가 모두 이 기준 하나를 쓴다.
	 *
	 * @param myMenu 이 참여자가 낸 메뉴. 내지 않았으면 null
	 */
	public boolean hasFinished(RoomStatus phase, Menu myMenu) {
		return switch (phase) {
			// 메뉴를 냈거나 "다 좋음"을 눌렀다.
			case PROPOSING -> myMenu != null || proposalAllGood;
			// "합치기 끝냄"을 눌렀거나, 합칠 메뉴가 없다(제안하지 않았거나, 메뉴가 합쳐져서 사라졌다).
			case MERGING -> mergeDone || myMenu == null || myMenu.isMerged();
			// 대기 중은 방장의 "시작"으로만 넘어가고, 투표와 랜덤 선택은 기능 4·5에서 정한다.
			case WAITING, VOTING, RANDOM_PICK -> false;
		};
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
