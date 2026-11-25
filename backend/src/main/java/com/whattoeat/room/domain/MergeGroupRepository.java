package com.whattoeat.room.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MergeGroupRepository extends JpaRepository<MergeGroup, Long> {

	Optional<MergeGroup> findByRoomAndPublicId(Room room, String publicId);

	List<MergeGroup> findByRoomAndStatus(Room room, MergeGroupStatus status);

	/** 이 메뉴가 들어 있고 아직 결과가 안 나온 그룹. 한 메뉴는 결과가 안 나온 그룹에 많아야 하나 들어 있다. */
	@Query("""
			select g from MergeGroup g join g.members m
			where m.menu = :menu and g.status = com.whattoeat.room.domain.MergeGroupStatus.PENDING
			""")
	Optional<MergeGroup> findPendingContaining(Menu menu);

	/** 화면에 보여줄 그룹들. 그룹마다 메뉴를 따로 읽지 않도록 그룹, 든 메뉴, 메뉴 정보를 한 번에 읽는다. */
	@Query("select g from MergeGroup g join fetch g.members m join fetch m.menu where g.room = :room")
	List<MergeGroup> findWithMenusByRoom(Room room);

}
