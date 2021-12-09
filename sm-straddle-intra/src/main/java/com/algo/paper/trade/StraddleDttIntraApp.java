package com.algo.paper.trade;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StraddleDttIntraApp {

	public static void main(String[] args) {
		SpringApplication.run(StraddleDttIntraApp.class, args);
	}

}
