package com.whattoeat.room.application;

import java.time.Clock;
import java.util.concurrent.TimeUnit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 1초마다 DB에서 시간이 다 된 방을 찾아 다음 단계로 넘긴다.
 * 끝나는 시각을 DB에 두므로, 서버가 재시작돼도 시간이 지켜진다(재시작 동안 지난 방은 켜지자마자 넘어간다).
 */
@Component
class PhaseTimer {

	private final RoomPhaseService phaseService;

	private final Clock clock;

	PhaseTimer(RoomPhaseService phaseService, Clock clock) {
		this.phaseService = phaseService;
		this.clock = clock;
	}

	@Scheduled(fixedDelay = 1, timeUnit = TimeUnit.SECONDS)
	void advanceExpiredPhases() {
		phaseService.advanceExpiredPhases(clock.instant());
	}

}
