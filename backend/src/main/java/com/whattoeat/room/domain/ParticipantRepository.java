package com.whattoeat.room.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

	Optional<Participant> findByTokenHash(String tokenHash);

	long countByRoom(Room room);

	List<Participant> findByRoom(Room room);

}
