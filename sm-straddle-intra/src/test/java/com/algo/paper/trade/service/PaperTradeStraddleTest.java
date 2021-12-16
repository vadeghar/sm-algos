package com.algo.paper.trade.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class PaperTradeStraddleTest {
	
	@LocalServerPort
	private int port;
	
	@Autowired PaperTradeStraddle paperTradeStraddle;
	
	@Test
	public void monitorPaperStraddleAndDoAdjustmentsTest() {
		paperTradeStraddle.monitorPaperStraddleAndDoAdjustments();
	}

}
