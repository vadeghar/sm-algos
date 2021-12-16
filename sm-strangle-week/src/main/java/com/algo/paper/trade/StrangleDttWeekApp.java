package com.algo.paper.trade;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StrangleDttWeekApp {

	public static void main(String[] args) {
		SpringApplication.run(StrangleDttWeekApp.class, args);
	}

}
