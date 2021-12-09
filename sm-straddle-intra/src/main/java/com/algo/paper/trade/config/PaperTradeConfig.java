//package com.algo.paper.trade.config;
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.web.client.RestTemplate;
//
//@Configuration
//public class PaperTradeConfig {
//	
////	@Value("${app.angel.apiKey}")
////	private String apiKey;
////	
////	@Value("${app.angel.clientCode}")
////	private String clientCode;
////	
////	@Value("${app.angel.password}")
////	private String password;
//	
//
////	@Bean
////	public RestTemplate getRestTemplate() {
////		return new RestTemplate();
////	}
////	
////	@Bean
////	public SmartConnect getSmartConnect() {
////		SmartConnect smartConnect = new SmartConnect(apiKey);
////		User aglUser = smartConnect.generateSession(clientCode, password);
////		smartConnect.setAccessToken(aglUser.getAccessToken());
////		smartConnect.setUserId(aglUser.getUserId());
////		return smartConnect;
////	}
////	
////	
////	@Bean
////	public SmartWebsocket getSmartWebsocket(@Qualifier("getSmartConnect") SmartConnect smartConnect) {
////		User user = smartConnect.getUser();
////		String jwtToken = user.getAccessToken(); 
////		return new SmartWebsocket(clientCode, jwtToken, apiKey, null, null);
////	}
////	
////	@Bean
////	public SmartAPITicker getSmartAPITicker() {
////		return new SmartAPITicker(clientCode, null, null, null);
////	}
//}
