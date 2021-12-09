package com.algo.paper.trade.connect;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.algo.model.AlgoOptionChain;
import com.algo.model.AlgoOptionChainList;
import com.algo.model.AlogLtpData;

public interface ExtConnect {

	public AlgoOptionChainList getOptionChainData(String symbol, LocalDate expiry);
	public Map<String, AlogLtpData> getSpotLtpData(List<String> symbols);
	public Map<String, AlogLtpData> getLtpData(List<String> symbols);
	public Map<String, AlogLtpData> getSpotLtpData(List<String> symbols, LocalDate expiry);
	public Map<String, AlgoOptionChain> getOpstStrangleStrikes(String symbol, LocalDate expiry, Double deltaVal, Double deltaMaxDiff);
	
}
