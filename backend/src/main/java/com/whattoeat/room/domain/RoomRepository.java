package com.whattoeat.room.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface RoomRepository extends JpaRepository<Room, Long> {

	Optional<Room> findByCode(String code);

	boolean existsByCode(String code);

	/**
	 * 방을 잠그고 읽는다(SELECT ... FOR UPDATE). 같은 방에 대한 다른 잠금 요청은 이 트랜잭션이 끝날 때까지 기다린다.
	 * 여러 명이 동시에 참여해도 인원을 세고 추가하는 일이 한 명씩 차례로 일어나 정원을 넘지 않게 한다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from Room r where r.code = :code")
	Optional<Room> findByCodeForUpdate(String code);

	/** 지금 단계의 시간이 다 된 방들. 잠그지 않고 읽으므로, 넘기기 전에 잠근 상태에서 한 번 더 확인해야 한다. */
	@Query("select r.code from Room r where r.phaseEndsAt <= :now")
	List<String> findCodesWithPhaseEndingBy(Instant now);

}
