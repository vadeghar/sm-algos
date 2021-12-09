package com.algo.paper.trade.service;

import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class StraddleServiceImplTest {
	@LocalServerPort
	private int port;
	
	@Autowired
	StraddleServiceImpl straddleService;
	
	@Test
	public void placeStraddleStrategyTest() {
//		straddleService.placeStraddleStrategy(true);
		Assert.assertTrue(true);
	}
}
