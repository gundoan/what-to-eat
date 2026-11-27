package com.whattoeat.room.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BallotRepository extends JpaRepository<Ballot, Long> {

	Optional<Ballot> findByParticipantAndVoteRound(Participant participant, int voteRound);

	/** 한 판의 투표지를 표까지 한 번에 읽는다. "다 좋음" 투표지는 표가 없어서 left join으로 같이 읽는다. */
	@Query("select b from Ballot b left join fetch b.votes where b.room = :room and b.voteRound = :voteRound")
	List<Ballot> findWithVotesByRoomAndVoteRound(Room room, int voteRound);

	/** 투표와 재투표의 투표지를 표까지 한 번에 읽는다. 화면 정보를 만들 때 쓴다. */
	@Query("select b from Ballot b left join fetch b.votes where b.room = :room")
	List<Ballot> findWithVotesByRoom(Room room);

}
