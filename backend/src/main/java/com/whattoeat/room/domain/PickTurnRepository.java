package com.whattoeat.room.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PickTurnRepository extends JpaRepository<PickTurn, Long> {

	/** 이 방의 뽑기 차례들을 뽑은 순서대로 읽는다. */
	List<PickTurn> findByRoomOrderByTurnNumber(Room room);

	/** 지금 정하는 중인 차례. 방마다 많아야 하나다(DB의 UNIQUE로도 막는다). */
	Optional<PickTurn> findByRoomAndStatus(Room room, PickTurnStatus status);

}
