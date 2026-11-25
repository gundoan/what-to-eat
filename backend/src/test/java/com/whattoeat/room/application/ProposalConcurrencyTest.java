package com.whattoeat.room.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.whattoeat.IntegrationTest;
import com.whattoeat.common.error.BusinessException;
import com.whattoeat.common.error.ErrorCode;
import com.whattoeat.room.application.RoomResults.CreatedRoom;
import com.whattoeat.room.domain.MenuRepository;
import com.whattoeat.room.domain.PhaseDurations;
import com.whattoeat.room.domain.Room;
import com.whattoeat.room.domain.RoomRepository;
import com.whattoeat.room.domain.RoomStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class ProposalConcurrencyTest {

	@Autowired
	RoomService roomService;

	@Autowired
	RoomPhaseService phaseService;

	@Autowired
	ProposalService proposalService;

	@Autowired
	RoomRepository roomRepository;

	@Autowired
	MenuRepository menuRepository;

	@Test
	@DisplayName("50명이 동시에 제안해도 메뉴 50개가 모두 저장되고, 단계는 정확히 한 번 넘어간다")
	void everyoneProposingAtOnceMovesOnExactlyOnce() throws Exception {
		CreatedRoom created = roomService.create(PhaseDurations.defaults());
		List<String> tokens = new ArrayList<>(List.of(created.token()));
		for (int i = 1; i < Room.MAX_PARTICIPANTS; i++) {
			tokens.add(roomService.join(created.code(), null).newToken());
		}
		phaseService.advanceByHost(created.code(), created.token(), RoomStatus.WAITING);

		List<Callable<Void>> proposals = new ArrayList<>();
		for (int i = 0; i < tokens.size(); i++) {
			String token = tokens.get(i);
			String menu = "메뉴" + i;
			proposals.add(() -> {
				proposalService.propose(created.code(), token, menu);
				return null;
			});
		}
		runAllAtOnce(proposals); // 하나라도 실패하면 예외로 테스트가 실패한다

		Room room = roomRepository.findByCode(created.code()).orElseThrow();
		assertThat(room.getStatus()).isEqualTo(RoomStatus.MERGING);
		assertThat(menuRepository.findSortedByRoom(room)).hasSize(Room.MAX_PARTICIPANTS);
	}

	@Test
	@DisplayName("방장이 시작을 동시에 두 번 눌러도 한 번만 넘어가고, 나머지는 거절된다")
	void concurrentDoublePressIsRejectedOnce() throws Exception {
		CreatedRoom created = roomService.create(PhaseDurations.defaults());
		roomService.join(created.code(), null);
		AtomicInteger succeeded = new AtomicInteger();
		AtomicInteger rejected = new AtomicInteger();

		Callable<Void> press = () -> {
			try {
				phaseService.advanceByHost(created.code(), created.token(), RoomStatus.WAITING);
				succeeded.incrementAndGet();
			}
			catch (BusinessException e) {
				assertThat(e.errorCode()).isEqualTo(ErrorCode.PHASE_ALREADY_CHANGED);
				rejected.incrementAndGet();
			}
			return null;
		};
		runAllAtOnce(List.of(press, press));

		assertThat(succeeded).hasValue(1);
		assertThat(rejected).hasValue(1);
		assertThat(roomRepository.findByCode(created.code()).orElseThrow().getStatus())
			.isEqualTo(RoomStatus.PROPOSING);
	}

	/** 모든 작업을 출발선에 세웠다가 한꺼번에 출발시키고, 모두 끝날 때까지 기다린다. */
	private static void runAllAtOnce(List<Callable<Void>> tasks) throws Exception {
		CountDownLatch ready = new CountDownLatch(tasks.size());
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
		try {
			List<Future<Void>> results = new ArrayList<>();
			for (Callable<Void> task : tasks) {
				results.add(pool.submit(() -> {
					ready.countDown();
					start.await();
					return task.call();
				}));
			}
			ready.await();
			start.countDown();
			for (Future<Void> result : results) {
				result.get(60, TimeUnit.SECONDS);
			}
		}
		finally {
			pool.shutdownNow();
		}
	}

}
