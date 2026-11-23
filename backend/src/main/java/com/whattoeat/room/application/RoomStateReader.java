package com.whattoeat.room.application;

import java.util.Optional;

import com.whattoeat.room.domain.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * SSE로 보낼 최신 방 상태를 DB에서 읽는다.
 */
@Service
public class RoomStateReader {

	private final RoomRepository roomRepository;

	private final RoomStateAssembler assembler;

	RoomStateReader(RoomRepository roomRepository, RoomStateAssembler assembler) {
		this.roomRepository = roomRepository;
		this.assembler = assembler;
	}

	/**
	 * 다른 트랜잭션이 커밋된 직후(AFTER_COMMIT)에 불리므로, 끝난 트랜잭션에 섞이지 않게 항상 새 트랜잭션에서 읽는다(REQUIRES_NEW).
	 */
	@Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
	public Optional<RoomState> read(String code) {
		return roomRepository.findByCode(code).map(assembler::state);
	}

}
