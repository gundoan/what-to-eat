package com.whattoeat.room.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.whattoeat.IntegrationTest;
import com.whattoeat.common.error.BusinessException;
import com.whattoeat.common.error.ErrorCode;
import com.whattoeat.room.domain.ParticipantRepository;
import com.whattoeat.room.domain.PhaseDurations;
import com.whattoeat.room.domain.Room;
import com.whattoeat.room.domain.RoomRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class RoomJoinConcurrencyTest {

	private static final int VISITORS = 60;

	@Autowired
	RoomService roomService;

	@Autowired
	RoomRepository roomRepository;

	@Autowired
	ParticipantRepository participantRepository;

	@Test
	@DisplayName("정원 50명인 방에 60명이 동시에 들어와도 방장 포함 정확히 50명만 참여된다")
	void neverExceedsCapacityUnderConcurrentJoins() throws Exception {
		String code = roomService.create(PhaseDurations.defaults()).code();

		AtomicInteger joined = new AtomicInteger();
		AtomicInteger rejectedAsFull = new AtomicInteger();
		CountDownLatch ready = new CountDownLatch(VISITORS);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(VISITORS);
		List<Future<?>> results = new ArrayList<>();
		try {
			for (int i = 0; i < VISITORS; i++) {
				results.add(pool.submit(() -> {
					ready.countDown();
					start.await(); // 모든 스레드가 준비되면 한꺼번에 출발한다
					try {
						roomService.join(code, null);
						joined.incrementAndGet();
					}
					catch (BusinessException e) {
						if (e.errorCode() != ErrorCode.ROOM_FULL) {
							throw e;
						}
						rejectedAsFull.incrementAndGet();
					}
					return null;
				}));
			}
			ready.await();
			start.countDown();
			for (Future<?> result : results) {
				result.get(60, TimeUnit.SECONDS); // 예상하지 못한 예외가 있으면 여기서 테스트가 실패한다
			}
		}
		finally {
			pool.shutdownNow();
		}

		Room room = roomRepository.findByCode(code).orElseThrow();
		assertThat(joined).hasValue(Room.MAX_PARTICIPANTS - 1); // 방장 1명 + 49명
		assertThat(rejectedAsFull).hasValue(VISITORS - (Room.MAX_PARTICIPANTS - 1));
		assertThat(participantRepository.countByRoom(room)).isEqualTo(Room.MAX_PARTICIPANTS);
	}

}
