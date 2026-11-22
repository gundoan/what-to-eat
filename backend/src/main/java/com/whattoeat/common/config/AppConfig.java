package com.whattoeat.common.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class AppConfig {

	/** 현재 시각은 이 시계로만 얻는다. 단계 타이머 테스트에서 시간을 마음대로 바꿀 수 있게 하기 위해서다. */
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
