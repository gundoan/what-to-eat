package com.whattoeat.room.domain;

import java.time.Instant;

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
 * 참여자가 제안한 메뉴. 한 사람은 한 방에서 메뉴를 하나만 낼 수 있다(DB의 UNIQUE 제약으로도 막는다).
 */
@Entity
@Table(name = "menu")
public class Menu {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "room_id", nullable = false, updatable = false)
	private Room room;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "participant_id", nullable = false, updatable = false)
	private Participant participant;

	@Column(nullable = false, length = MenuName.MAX_LENGTH)
	private String name;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected Menu() {
	}

	private Menu(Room room, Participant participant, MenuName name, Instant now) {
		this.room = room;
		this.participant = participant;
		this.name = name.value();
		this.createdAt = now;
		this.updatedAt = now;
	}

	public static Menu propose(Room room, Participant participant, MenuName name, Instant now) {
		return new Menu(room, participant, name, now);
	}

	public void rename(MenuName newName, Instant now) {
		this.name = newName.value();
		this.updatedAt = now;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

}
