package com.algo.paper.trade.connect;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.web.server.LocalServerPort;

import com.algo.model.AlgoOptionChainList;
import com.algo.model.AlogLtpData;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class GrConnectTest {
	@LocalServerPort
	private int port;
	
	@Autowired
	GrConnect grConnect;
	
	
	public void getOptionChainDataTest() {
		LocalDate expiry = LocalDate.of(2021, 12, 02);
		AlgoOptionChainList chainList = grConnect.getOptionChainData("nifty", expiry);
		System.out.println("Response:** "+chainList);
	}
	
//	@Test
	public void getLtpDataTest() {
		List<String> symbols = new ArrayList<>();
		symbols.add("NIFTY21NOV17200CE");
		Map<String, AlogLtpData> ltps = grConnect.getLtpData(symbols);
		System.out.println("Response** "+ltps);
	}
	
	@Test
	public void getSpotLtpDataTest() {
		List<String> symbols = new ArrayList<>();
		symbols.add("NIFTY");
		Map<String, AlogLtpData> ltps = grConnect.getSpotLtpData(symbols);
		System.out.println("Response** "+ltps);
	}
}
