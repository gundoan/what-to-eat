package com.whattoeat.room.application;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 동시성 테스트에서 여러 요청을 "정말 동시에" 보내기 위한 도구.
 */
final class Concurrently {

	private Concurrently() {
	}

	/**
	 * 모든 작업을 출발선에 세웠다가 한꺼번에 출발시키고, 모두 끝날 때까지 기다린다.
	 * 작업 하나라도 예외를 던지면 그 예외로 테스트가 실패한다.
	 */
	static void runAllAtOnce(List<Callable<Void>> tasks) throws Exception {
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
