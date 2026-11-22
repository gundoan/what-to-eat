package com.whattoeat;

import org.springframework.boot.SpringApplication;

public class TestWhatToEatApplication {

	public static void main(String[] args) {
		SpringApplication.from(WhatToEatApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
