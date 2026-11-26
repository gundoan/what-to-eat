package com.whattoeat.room.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.whattoeat.common.error.BusinessException;
import com.whattoeat.common.error.ErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * 투표지: 한 사람이 한 판에 한 번 내는 투표. 표(1~2개)를 내거나 "다 좋음"(투표하지 않음)을 누른다.
 * 한 번 내면 바꿀 수 없다. 같은 사람이 같은 판에 두 번 내는 것은 DB의 UNIQUE 제약으로도 막는다.
 */
@Entity
@Table(name = "ballot")
public class Ballot {

	/** 첫 투표. 동점일 때의 재투표(기능 5)는 2판이다. */
	public static final int FIRST_ROUND = 1;

	/** 한 사람이 한 판에 낼 수 있는 표의 수 */
	public static final int MAX_VOTES = 2;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "room_id", nullable = false, updatable = false)
	private Room room;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "participant_id", nullable = false, updatable = false)
	private Participant participant;

	@Column(name = "vote_round", nullable = false, updatable = false)
	private int voteRound;

	/** "다 좋음"(투표하지 않음)을 눌렀는지. 참이면 표가 없다. */
	@Column(nullable = false, updatable = false)
	private boolean allGood;

	@Column(nullable = false, updatable = false)
	private Instant submittedAt;

	@OneToMany(mappedBy = "ballot", cascade = CascadeType.PERSIST)
	@OrderBy("id")
	private List<Vote> votes = new ArrayList<>();

	protected Ballot() {
	}

	private Ballot(Room room, Participant participant, int voteRound, boolean allGood, List<VoteChoice> choices,
			Instant now) {
		this.room = room;
		this.participant = participant;
		this.voteRound = voteRound;
		this.allGood = allGood;
		this.submittedAt = now;
		for (VoteChoice choice : choices) {
			votes.add(new Vote(this, choice));
		}
	}

	/**
	 * 표를 낸 투표지. 표는 1~2개이고, 한 메뉴에는 1표만 줄 수 있다. 투표하지 않으려면 {@link #allGood}를 쓴다.
	 */
	public static Ballot of(Room room, Participant participant, int voteRound, List<VoteChoice> choices, Instant now) {
		long distinctMenus = choices.stream().map(choice -> choice.menu().getId()).distinct().count();
		if (choices.isEmpty() || choices.size() > MAX_VOTES || distinctMenus != choices.size()) {
			throw new BusinessException(ErrorCode.INVALID_BALLOT);
		}
		return new Ballot(room, participant, voteRound, false, choices, now);
	}

	/** "다 좋음"을 누른 투표지. 표가 없고, 남은 사람들의 표로 결정된다. */
	public static Ballot allGood(Room room, Participant participant, int voteRound, Instant now) {
		return new Ballot(room, participant, voteRound, true, List.of(), now);
	}

	/** 한 판의 투표지들을 낸 사람(참여자 번호)으로 찾을 수 있게 모은다. 한 사람은 한 판에 하나만 낸다. */
	public static Map<Long, Ballot> byParticipantId(List<Ballot> ballots) {
		return ballots.stream().collect(Collectors.toMap(ballot -> ballot.getParticipant().getId(), Function.identity()));
	}

	/**
	 * 같은 내용인지. 버튼 두 번 누르기처럼 같은 투표를 다시 보냈는지 확인할 때 쓴다. 표의 순서는 따지지 않는다.
	 */
	public boolean hasSameContent(boolean otherAllGood, List<VoteChoice> otherChoices) {
		if (allGood || otherAllGood) {
			return allGood && otherAllGood;
		}
		Set<String> mine = votes.stream()
			.map(vote -> vote.getMenu().getId() + ":" + vote.getPoint())
			.collect(Collectors.toSet());
		Set<String> other = otherChoices.stream()
			.map(choice -> choice.menu().getId() + ":" + choice.point())
			.collect(Collectors.toSet());
		return mine.size() == otherChoices.size() && mine.equals(other);
	}

	public Participant getParticipant() {
		return participant;
	}

	public int getVoteRound() {
		return voteRound;
	}

	public boolean isAllGood() {
		return allGood;
	}

	public List<Vote> getVotes() {
		return votes;
	}

}
