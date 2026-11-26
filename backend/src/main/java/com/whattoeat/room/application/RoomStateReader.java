package com.whattoeat.room.application;

import java.util.Optional;

import com.whattoeat.room.domain.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * SSE로 보낼 최신 방 상태와 참여자별 "나" 정보를 DB에서 읽는다.
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
	 * 한 트랜잭션 안에서 읽으므로 방 상태와 모든 사람의 "나" 정보가 같은 순간의 내용이다.
	 */
	@Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
	public Optional<RoomViews> read(String code) {
		return roomRepository.findByCode(code).map(assembler::views);
	}

}
