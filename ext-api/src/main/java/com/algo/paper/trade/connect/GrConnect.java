package com.algo.paper.trade.connect;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.algo.grow.model.GrOhlc;
import com.algo.grow.model.GrOptionChain;
import com.algo.grow.model.GrOptionChainResponse;
import com.algo.grow.model.GrOptionData;
import com.algo.model.AlgoOptionChain;
import com.algo.model.AlgoOptionChainList;
import com.algo.model.AlgoOptionData;
import com.algo.model.AlogLtpData;
import com.algo.utils.CommonUtils;
import com.algo.utils.DateUtils;
import com.algo.utils.ExcelUtils;

@Service
public class GrConnect implements ExtConnect {

	static Logger log = LoggerFactory.getLogger(ExcelUtils.class);
	@Autowired
	RestTemplate restTemplate;
//	https://groww.in/v1/api/option_chain_service/v1/option_chain/nifty?expiry=2021-11-25
	private static final String GR_OC_URL="https://groww.in/v1/api/option_chain_service/v1/option_chain/";
	//https://groww.in/v1/api/stocks_data/v1/accord_points/exchange/NSE/segment/CASH/latest_prices_ohlc/NIFTY
	private static final String GR_SPOT_LTP_URL="https://groww.in/v1/api/stocks_data/v1/accord_points/exchange/NSE/segment/CASH/latest_prices_ohlc/";
//https://groww.in/v1/api/stocks_fo_data/v1/derivatives/nifty/contract?groww_contract_id=NIFTY21NOV17200CE
	private static final String GR_OPT_LTP_URL="https://groww.in/v1/api/stocks_fo_data/v1/derivatives/nifty/contract?groww_contract_id=";

	
	@Override
	public Map<String, AlogLtpData> getSpotLtpData(List<String> symbols, LocalDate expiry) {
		throw new RuntimeException("Method not implemented, GrConnect -> getSpotLtpData");
	}

	@Override
	public Map<String, AlgoOptionChain> getOpstStrangleStrikes(String symbol, LocalDate expiry, Double deltaVal,
			Double deltaMaxDiff) {
		throw new RuntimeException("Method not implemented, GrConnect -> getOpstStrangleStrikes");
	}
	
	@Override
	public AlgoOptionChainList getOptionChainData(String symbol, LocalDate expiry) {
		try {
			HttpHeaders headers = new HttpHeaders();
			headers.set("authority", "groww.in");
			headers.set("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9");
			//		headers.set("accept-encoding", "gzip, deflate, br");
			//		headers.set("accept-language", "en-GB,en;q=0.9,te;q=0.8");
			headers.set("sec-ch-ua", "\" Not A;Brand\";v=\"99\", \"Chromium\";v=\"96\", \"Google Chrome\";v=\"96\"");
			headers.set("sec-ch-ua-mobile", "?0");
			headers.set("sec-ch-ua-platform", "Windows");
			//		headers.set("sec-fetch-dest", "document");
			//		headers.set("sec-fetch-mode", "navigate");
			headers.set("sec-fetch-site", "none");
			headers.set("sec-fetch-user", "?1");
			headers.set("upgrade-insecure-requests", "1");
			headers.set("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.45 Safari/537.36");
			//		cookie: __cfruid=14cd4fa228e4f4b975d11b28513962607d2d519d-1637641954; habsdhkasbdhjkbaskh=U2FsdGVkX1+0wizFk/xhn4y6aXBhiab/mhCdWfgz/ORahLFpb3gkUEgzE744sfFdWJ19ZeUF5Fbo7mLuMBT6Ig==; _gcl_au=1.1.364140183.1637641954; _ga=GA1.2.1026913608.1637641954; _gid=GA1.2.442768587.1637641954; we_luid=a661a5beafd18b09b9d23652d3490a59ec32849b; G_ENABLED_IDPS=google; __cf_bm=xJDl4aQtRuChtKnwrxhHUe71oR7htF0AgG4r7mtV5zM-1637644711-0-AabqySlpOjGpPDe8/ODFXaIO+3hkgID04/aJq+U0mgOmlDDFUFS4tlAMXbFsMVuwu5o9oawueCL2WY8NpEaCi6c=; g_state={"i_p":1637651928672,"i_l":1}; _gat_UA-76725130-1=1
			HttpEntity entity = new HttpEntity(headers);
			String url = GR_OC_URL+symbol+"?expiry="+DateUtils.getExpiryFormat(expiry, "yyyy-MM-dd");
			log.info("Option Chain URL: "+url);
			ResponseEntity<GrOptionChainResponse> response = restTemplate.exchange(url, HttpMethod.GET, entity, GrOptionChainResponse.class);

			GrOptionChainResponse grResponse = response.getBody();
			AlgoOptionChainList processedResponse = processResponse(grResponse);
			return processedResponse;
		} catch (RestClientException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	@Override
	public Map<String, AlogLtpData> getSpotLtpData(List<String> symbols) {
		Map<String, AlogLtpData> ltps = new HashMap<>();
		HttpHeaders headers = new HttpHeaders();
		headers.set("authority", "groww.in");
		headers.set("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9");
		//		headers.set("accept-encoding", "gzip, deflate, br");
		//		headers.set("accept-language", "en-GB,en;q=0.9,te;q=0.8");
		headers.set("sec-ch-ua", "\" Not A;Brand\";v=\"99\", \"Chromium\";v=\"96\", \"Google Chrome\";v=\"96\"");
		headers.set("sec-ch-ua-mobile", "?0");
		headers.set("sec-ch-ua-platform", "Windows");
		//		headers.set("sec-fetch-dest", "document");
		//		headers.set("sec-fetch-mode", "navigate");
		headers.set("sec-fetch-site", "none");
		headers.set("sec-fetch-user", "?1");
		headers.set("upgrade-insecure-requests", "1");
		headers.set("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.45 Safari/537.36");
		//		cookie: __cfruid=14cd4fa228e4f4b975d11b28513962607d2d519d-1637641954; habsdhkasbdhjkbaskh=U2FsdGVkX1+0wizFk/xhn4y6aXBhiab/mhCdWfgz/ORahLFpb3gkUEgzE744sfFdWJ19ZeUF5Fbo7mLuMBT6Ig==; _gcl_au=1.1.364140183.1637641954; _ga=GA1.2.1026913608.1637641954; _gid=GA1.2.442768587.1637641954; we_luid=a661a5beafd18b09b9d23652d3490a59ec32849b; G_ENABLED_IDPS=google; __cf_bm=xJDl4aQtRuChtKnwrxhHUe71oR7htF0AgG4r7mtV5zM-1637644711-0-AabqySlpOjGpPDe8/ODFXaIO+3hkgID04/aJq+U0mgOmlDDFUFS4tlAMXbFsMVuwu5o9oawueCL2WY8NpEaCi6c=; g_state={"i_p":1637651928672,"i_l":1}; _gat_UA-76725130-1=1
		HttpEntity entity = new HttpEntity(headers);
		for(String symbol: symbols) {
			String url = GR_SPOT_LTP_URL+symbol;
			log.info("Spot URL: "+url);
			ResponseEntity<GrOhlc> response = restTemplate.exchange(url, HttpMethod.GET, entity, GrOhlc.class);
			GrOhlc grResponse = response.getBody();
			AlogLtpData ltpData = processLivePriceResponse(grResponse);
			ltps.put(symbol, ltpData);
		}
		return ltps;
	}
	
	// NIFTY21D0217200CE
	//https://groww.in/v1/api/stocks_fo_data/v1/derivatives/nifty/contract?groww_contract_id=NIFTY21NOV17350PE
	@Override
	public Map<String, AlogLtpData> getLtpData(List<String> symbols) {
		Map<String, AlogLtpData> ltps = new HashMap<>();
		HttpHeaders headers = new HttpHeaders();
		headers.set("authority", "groww.in");
		headers.set("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9");
		//		headers.set("accept-encoding", "gzip, deflate, br");
		//		headers.set("accept-language", "en-GB,en;q=0.9,te;q=0.8");
		headers.set("sec-ch-ua", "\" Not A;Brand\";v=\"99\", \"Chromium\";v=\"96\", \"Google Chrome\";v=\"96\"");
		headers.set("sec-ch-ua-mobile", "?0");
		headers.set("sec-ch-ua-platform", "Windows");
		//		headers.set("sec-fetch-dest", "document");
		//		headers.set("sec-fetch-mode", "navigate");
		headers.set("sec-fetch-site", "none");
		headers.set("sec-fetch-user", "?1");
		headers.set("upgrade-insecure-requests", "1");
		headers.set("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.45 Safari/537.36");
		//		cookie: __cfruid=14cd4fa228e4f4b975d11b28513962607d2d519d-1637641954; habsdhkasbdhjkbaskh=U2FsdGVkX1+0wizFk/xhn4y6aXBhiab/mhCdWfgz/ORahLFpb3gkUEgzE744sfFdWJ19ZeUF5Fbo7mLuMBT6Ig==; _gcl_au=1.1.364140183.1637641954; _ga=GA1.2.1026913608.1637641954; _gid=GA1.2.442768587.1637641954; we_luid=a661a5beafd18b09b9d23652d3490a59ec32849b; G_ENABLED_IDPS=google; __cf_bm=xJDl4aQtRuChtKnwrxhHUe71oR7htF0AgG4r7mtV5zM-1637644711-0-AabqySlpOjGpPDe8/ODFXaIO+3hkgID04/aJq+U0mgOmlDDFUFS4tlAMXbFsMVuwu5o9oawueCL2WY8NpEaCi6c=; g_state={"i_p":1637651928672,"i_l":1}; _gat_UA-76725130-1=1
		HttpEntity entity = new HttpEntity(headers);
		String tmp = "";
		for(String symbol: symbols) {
			tmp = symbol.replace(CommonUtils.getOpstraExpiry(symbol), CommonUtils.opstraToGrExpiry(symbol));
			String url = GR_OPT_LTP_URL+tmp;
			log.info("Option Chain URL: "+url);
			ResponseEntity<GrOptionChainResponse> response = restTemplate.exchange(url, HttpMethod.GET, entity, GrOptionChainResponse.class);
			GrOptionChainResponse grResponse = response.getBody();
			AlogLtpData ltpData = processLivePriceResponse(grResponse.getLivePrice());
			ltps.put(symbol, ltpData);
		}
		return ltps;
	}

	private AlogLtpData processLivePriceResponse(GrOhlc ohlc) {
		if(ohlc == null)
			return null;
		AlogLtpData ltp = new AlogLtpData();
		ltp.setClose(ohlc.getClose());
		ltp.setDayChange(ohlc.getDayChange());
		ltp.setDayChangePerc(ohlc.getDayChangePerc());
		ltp.setHigh(ohlc.getHigh());
		ltp.setHighPriceRange(ohlc.getHighPriceRange());
		ltp.setLastPrice(ohlc.getLtp());
		ltp.setLastTradeQty(ohlc.getLastTradeQty());
		ltp.setLastTradeTime(ohlc.getLastTradeTime());
		ltp.setLow(ohlc.getLow());
		ltp.setLowPriceRange(ohlc.getLowPriceRange());
		ltp.setOpen(ohlc.getOpen());
		ltp.setOpenInterest(ohlc.getOpenInterest());
		ltp.setSymbol(ohlc.getSymbol());
		ltp.setTotalBuyQty(ohlc.getTotalBuyQty());
		ltp.setTotalSellQty(ohlc.getTotalSellQty());
		ltp.setTsInMillis(ohlc.getTsInMillis());
		ltp.setVolume(ohlc.getVolume());
		return ltp;
	}

	private AlgoOptionChainList processResponse(GrOptionChainResponse grResponse) {
		AlgoOptionChainList oc = new AlgoOptionChainList();
		List<AlgoOptionChain> alogOptionList = new ArrayList<>();	
		if(CollectionUtils.isNotEmpty(grResponse.getOptionChains())) {
			for(GrOptionChain option : grResponse.getOptionChains()) {
				AlgoOptionChain algoOption = new AlgoOptionChain();
				algoOption.setStrikePrice(option.getStrikePrice().toString().length() == 6 ? option.getStrikePrice()/10 : option.getStrikePrice());
				GrOptionData grPeOption = option.getPutOption();
				AlgoOptionData peOption = convertAlogOptionData(grPeOption);
				GrOptionData grCeOption = option.getCallOption();
				AlgoOptionData ceOption = convertAlogOptionData(grCeOption);
				algoOption.setCallOption(ceOption);
				algoOption.setPutOption(peOption);
				alogOptionList.add(algoOption);
			}
			oc.setOptionChainList(alogOptionList);
		}
		return oc;
	}

	private AlgoOptionData convertAlogOptionData(GrOptionData grOption) {
		AlgoOptionData peOption = new AlgoOptionData();
		peOption.setLtp(grOption.getLtp());
		peOption.setClose(grOption.getClose());
		peOption.setDayChange(grOption.getDayChange());
		peOption.setDayChangePerc(grOption.getDayChangePerc());
		peOption.setHigh(grOption.getHigh());
		peOption.setHighPriceRange(grOption.getHighPriceRange());
		peOption.setLastTradeQty(grOption.getLastTradeQty());
		peOption.setLastTradeTime(grOption.getLastTradeTime());
		peOption.setLow(grOption.getLow());
		peOption.setLowPriceRange(grOption.getLowPriceRange());
		peOption.setOpen(grOption.getOpen());
		peOption.setOpenInterest(grOption.getOpenInterest());
		peOption.setPrevOpenInterest(grOption.getPrevOpenInterest());
		peOption.setTotalBuyQty(grOption.getTotalBuyQty());
		peOption.setTotalSellQty(grOption.getTotalSellQty());
		peOption.setTradingSymbol(grOption.getContractDisplayName());
		peOption.setVolume(grOption.getVolume());
		return peOption;
	}

	

}
