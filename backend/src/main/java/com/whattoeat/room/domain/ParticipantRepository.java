package com.whattoeat.room.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

	Optional<Participant> findByTokenHash(String tokenHash);

	long countByRoom(Room room);

	/** 제안 단계를 끝낸 사람 수: 메뉴를 냈거나 "다 좋음"을 누른 사람. */
	@Query("""
			select count(p) from Participant p
			where p.room = :room
			  and (p.proposalAllGood = true or exists (select m.id from Menu m where m.participant = p))
			""")
	long countProposalDone(Room room);

}
