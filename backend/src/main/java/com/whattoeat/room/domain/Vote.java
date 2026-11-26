package com.whattoeat.room.domain;

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
 * 투표지에 적은 표 하나. {@link Ballot}을 통해서만 만든다.
 */
@Entity
@Table(name = "vote")
public class Vote {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "ballot_id", nullable = false, updatable = false)
	private Ballot ballot;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "menu_id", nullable = false, updatable = false)
	private Menu menu;

	/** +1(먹고 싶다) 또는 -1(먹기 싫다). DB의 CHECK 제약으로도 막는다. */
	@Column(nullable = false, updatable = false)
	private int point;

	protected Vote() {
	}

	Vote(Ballot ballot, VoteChoice choice) {
		this.ballot = ballot;
		this.menu = choice.menu();
		this.point = choice.point();
	}

	public Menu getMenu() {
		return menu;
	}

	public int getPoint() {
		return point;
	}

}
