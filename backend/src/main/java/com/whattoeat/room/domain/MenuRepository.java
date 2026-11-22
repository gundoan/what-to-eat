package com.whattoeat.room.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MenuRepository extends JpaRepository<Menu, Long> {

	Optional<Menu> findByParticipant(Participant participant);

	boolean existsByRoom(Room room);

	/**
	 * 메뉴 이름만 가나다순으로 읽는다. 올라온 순서대로 보여주면 누가 냈는지 짐작할 수 있어서다.
	 * 누가 냈는지(participant)는 읽지 않는다.
	 */
	@Query("select m.name from Menu m where m.room = :room order by m.name")
	List<String> findNamesByRoom(Room room);

}
