package com.algo.paper.trade.connect;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.algo.model.AlgoOptionChain;
import com.algo.model.AlgoOptionChainList;
import com.algo.model.AlgoOptionData;
import com.algo.model.AlogLtpData;
import com.algo.opstra.model.OpstOptionChain;
import com.algo.opstra.model.OpstOptionData;
import com.algo.opstra.model.OpstraResponse;
import com.algo.opstra.model.OpstraSpotResponse;
import com.algo.utils.CommonUtils;
import com.algo.utils.Constants;
import com.algo.utils.DateUtils;
import com.algo.utils.ExcelUtils;

@Service
public class OpstraConnect implements ExtConnect {

	private static final Logger log = LoggerFactory.getLogger(ExcelUtils.class);
	private static final String OPSTR_OC_URL="https://opstra.definedge.com/api/free/strategybuilder/optionchain/";
	private static final String OPSTR_SPOT_LTP_URL="https://opstra.definedge.com/api/free/strategybuilder/spot/";
	private static final String OPSTR_OPT_LTP_URL="https://opstra.definedge.com/api/free/strategybuilder/optionprice/";
	
	
	@Autowired
	RestTemplate restTemplate;
	

	@Override
	public Map<String, AlogLtpData> getSpotLtpData(List<String> symbols) {
		throw new RuntimeException("Method not implemented, OpstraConnect -> getSpotLtpData");
	}

	
	@Override
	public AlgoOptionChainList getOptionChainData(String symbol, LocalDate expiry) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/95.0.4638.54 Safari/537.36");
		headers.set("sec-ch-ua", "\"Google Chrome\";v=\"95\", \"Chromium\";v=\"95\", \";Not A Brand\";v=\"99\"");
		headers.set("sec-ch-ua-mobile", "?0");
		headers.set("sec-ch-ua-platform", "\"Windows\"");
		headers.set("sec-fetch-mode", "cors");
		headers.set("sec-fetch-site", "same-origin");
		headers.set("referer", "https://opstra.definedge.com/strategy-builder");
		HttpEntity entity = new HttpEntity(headers);
		String url = OPSTR_OC_URL+symbol+"&"+DateUtils.opstraFormattedExpiry(expiry);
		ResponseEntity<OpstOptionChain> response = restTemplate.exchange(url, HttpMethod.GET, entity, OpstOptionChain.class);
		AlgoOptionChainList processedResponse = processResponse(response.getBody());
		return processedResponse;
	}
	
	@Override
	public Map<String, AlogLtpData> getSpotLtpData(List<String> symbols, LocalDate expiry) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/95.0.4638.54 Safari/537.36");
		headers.set("sec-ch-ua", "\" Not A;Brand\";v=\"99\", \"Chromium\";v=\"96\", \"Google Chrome\";v=\"96\"");
		headers.set("sec-ch-ua-mobile", "?0");
		headers.set("sec-ch-ua-platform", "\"Windows\"");
		headers.set("sec-fetch-mode", "cors");
		headers.set("sec-fetch-site", "same-origin");
//		headers.set("cookie", cookieVal);
		headers.set("referer", "https://opstra.definedge.com/strategy-builder");
		HttpEntity entity = new HttpEntity(headers);
		Map<String, AlogLtpData> ltps = new HashMap<>();
		for(String symbol: symbols) {
			String url = OPSTR_SPOT_LTP_URL+symbol+"&"+DateUtils.opstraFormattedExpiry(expiry);
			log.info("URL: "+url);
			ResponseEntity<OpstraSpotResponse> response = restTemplate.exchange(url, HttpMethod.GET, entity, OpstraSpotResponse.class);
			OpstraSpotResponse r = response.getBody();
			ltps.put(symbol, new AlogLtpData(symbol, r != null ? (r.getSpotPrice() != null ? Double.valueOf(r.getSpotPrice()) : 0.0) : 0.0));
		}
		return ltps;
	}
	
	@Override
	public Map<String, AlogLtpData> getLtpData(List<String> symbols) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/95.0.4638.54 Safari/537.36");
		headers.set("sec-ch-ua", "\"Google Chrome\";v=\"95\", \"Chromium\";v=\"95\", \";Not A Brand\";v=\"99\"");
		headers.set("sec-ch-ua-mobile", "?0");
		headers.set("sec-ch-ua-platform", "\"Windows\"");
		headers.set("sec-fetch-mode", "cors");
		headers.set("sec-fetch-site", "same-origin");
//		headers.set("cookie", cookieVal);
		headers.set("referer", "https://opstra.definedge.com/strategy-builder");
		HttpEntity entity = new HttpEntity(headers);
		Map<String, AlogLtpData> ltps = new HashMap<>();
		for(String symbol : symbols) {
			String url = OPSTR_OPT_LTP_URL+CommonUtils.getOpstraSymbol(symbol)+"&"+CommonUtils.getOpstraExpiry(symbol)+"&"+CommonUtils.getOpstraStrikePrice(symbol)+"&"+CommonUtils.getOpstraOptionType(symbol);
			log.info("URL: "+url);
			ResponseEntity<OpstraResponse> response = restTemplate.exchange(url, HttpMethod.GET, entity, OpstraResponse.class);
			if(response.getStatusCode() == HttpStatus.OK) {
				OpstraResponse resp = response.getBody();
				ltps.put(symbol, new AlogLtpData(symbol, resp != null ? (resp.getOptionPrice() != null ? Double.valueOf(resp.getOptionPrice()) : 0.0) : 0.0));
			}
		}
		return ltps;
	}
	
	@Override
	public Map<String, AlgoOptionChain> getOpstStrangleStrikes(String symbol, LocalDate expiry, Double deltaVal, Double deltaMaxDiff) {
		AlgoOptionChainList chain = getOptionChainData(symbol, expiry);
		Map<String, AlgoOptionChain> response = new HashMap<>();
		if(chain == null || CollectionUtils.isEmpty(chain.getOptionChainList()))
			return null;
		Double ceMaxDelta = deltaVal + deltaMaxDiff;
		Double ceMinDelta = deltaVal  - deltaMaxDiff;
		Double peMaxDelta = deltaVal - (2*deltaVal) + deltaMaxDiff; // 15 - 2*15 + 1 = -14
		Double peMinDelta = deltaVal - (2*deltaVal) - deltaMaxDiff; // 15 - 2*15 - 1 = -16
		log.info("CE MAX: "+ceMaxDelta+" CE MIN:"+ceMinDelta);
		log.info("PE MAX: "+peMaxDelta+" PE MIN:"+peMinDelta);
		for(AlgoOptionChain data: chain.getOptionChainList()) {
			if(data.getCallOption() == null)
				continue;
			if(data.getCallOption().getDelta() < ceMaxDelta && data.getCallOption().getDelta() > ceMinDelta) {
				response.put(Constants.CE, data);
			}
			if(data.getPutOption().getDelta() < peMaxDelta && data.getPutOption().getDelta() > peMinDelta) {
				response.put(Constants.PE, data);
			}
		}
		return response;
	}
	
	private AlgoOptionChainList processResponse(OpstOptionChain body) {
		AlgoOptionChainList processedResponse = new AlgoOptionChainList();
		List<AlgoOptionChain> optionChainList = new ArrayList<>();
		for(OpstOptionData optData : body.getData()) {
			AlgoOptionChain optionData = new AlgoOptionChain();
			AlgoOptionData callOption = new AlgoOptionData();
			AlgoOptionData putOption = new AlgoOptionData();
			
			optionData.setStrikePrice((StringUtils.isEmpty(optData.getStrikePrice()) || optData.getStrikePrice().trim().equals("-")) ? 0 : Integer.valueOf(optData.getStrikePrice()));
			
			callOption.setDelta((StringUtils.isEmpty(optData.getCallDelta()) || optData.getCallDelta().trim().equals("-")) ? 0.0 : Double.valueOf(optData.getCallDelta()));
			callOption.setGamma((StringUtils.isEmpty(optData.getCallGamma()) || optData.getCallGamma().trim().equals("-")) ? 0.0 : Double.valueOf(optData.getCallGamma()));
			callOption.setIv((StringUtils.isEmpty(optData.getCallIV()) || optData.getCallIV().trim().equals("-")) ? 0.0 : Double.valueOf(optData.getCallIV()));
			callOption.setLtp((StringUtils.isEmpty(optData.getCallLTP()) || optData.getCallLTP().trim().equals("-")) ? 0.0 : Double.valueOf(optData.getCallLTP()));
			callOption.setVega((StringUtils.isEmpty(optData.getCallVega()) || optData.getCallVega().trim().equals("-")) ? 0.0 : Double.valueOf(optData.getCallVega()));
			callOption.setTheta((StringUtils.isEmpty(optData.getCallTheta()) || optData.getCallTheta().trim().equals("-")) ? 0.0 : Double.valueOf(optData.getCallTheta()));
			
			putOption.setDelta((StringUtils.isEmpty(optData.getPutDelta()) || optData.getPutDelta().trim().equals("-")) ? 0.0 : Double.valueOf(optData.getPutDelta()));
			putOption.setGamma((StringUtils.isEmpty(optData.getPutGamma()) || optData.getPutGamma().trim().equals("-")) ? 0.0 : Double.valueOf(optData.getPutGamma()));
			putOption.setIv((StringUtils.isEmpty(optData.getPutIV()) || optData.getPutIV().trim().equals("-")) ? 0.0 : Double.valueOf(optData.getPutIV()));
			putOption.setLtp((StringUtils.isEmpty(optData.getPutLTP()) || optData.getPutLTP().trim().equals("-")) ? 0.0 : Double.valueOf(optData.getPutLTP()));
			putOption.setVega((StringUtils.isEmpty(optData.getPutVega()) || optData.getPutVega().trim().equals("-")) ? 0.0 : Double.valueOf(optData.getPutVega()));
			putOption.setTheta((StringUtils.isEmpty(optData.getPutTheta()) || optData.getPutTheta().trim().equals("-")) ? 0.0 : Double.valueOf(optData.getPutTheta()));
			
			optionData.setCallOption(callOption);
			optionData.setPutOption(putOption);
			optionChainList.add(optionData);
		}
		processedResponse.setOptionChainList(optionChainList);
		return processedResponse;
	}

	

}
