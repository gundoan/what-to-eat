package com.whattoeat.room.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MenuRepository extends JpaRepository<Menu, Long> {

	Optional<Menu> findByParticipant(Participant participant);

	long countByRoom(Room room);

	/**
	 * 방의 메뉴를 합쳐진 것까지 모두 가나다순으로 읽는다. 올라온 순서대로 보여주면 누가 냈는지 짐작할 수 있어서다.
	 * 이름이 같으면 무작위 공개 번호 순이라, 같은 이름끼리의 순서도 올라온 순서와 상관없다.
	 */
	@Query("select m from Menu m where m.room = :room order by m.name, m.publicId")
	List<Menu> findSortedByRoom(Room room);

}
