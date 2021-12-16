package com.algo.paper.trade.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.algo.model.MyPosition;
import com.algo.utils.CommonUtils;
import com.algo.utils.Constants;
import com.algo.utils.DateUtils;

@Service
public class PaperTradeStraddle {
	Logger log = LoggerFactory.getLogger(this.getClass());
	LocalTime closeTime = LocalTime.parse(Constants.CLOSEING_TIME);
	LocalTime openingTime = LocalTime.parse(Constants.OPENING_TIME);
	LocalTime tradeStartTime = LocalTime.parse(Constants.INTRADAY_STRADDLE_START);
	LocalTime tradeEndTime = LocalTime.parse(Constants.INTRADAY_STRADDLE_END);
	LocalTime lastHourStartAt = LocalTime.parse(Constants.LAST_HOUR_STARTS_AT);
	@Value("${app.straddle.closeOnTarget:false}")
	private boolean closeOnTarget;

	@Value("${app.straddle.useStopLoss:false}")
	private boolean useStopLoss;
	
	@Autowired
	StraddleServiceImpl straddleService;
	public void placeStraddleStrategy(boolean createNewFile) {
		straddleService.placeStraddleStrategy(createNewFile);
	}
	public void placeStraddleStrategy() {
//		DayOfWeek dayOfWeek = LocalDate.now().getDayOfWeek();
//		if(dayOfWeek.getValue() == 4) {
//			log.info("NO TRADES TODAY, THURSDAY");
//			System.out.println("NO TRADES TODAY, THURSDAY");
//			return;
//		}
		straddleService.placeStraddleStrategy(true);
	}
	// Theta gainers
	@Scheduled(cron = "${app.straddle.cron.expression}")
	public void monitorPaperStraddleAndDoAdjustments() {
//		if((LocalTime.now().isBefore(openingTime)) || (LocalTime.now().isAfter(closeTime) || LocalTime.now().equals(closeTime))) {
//			System.out.println("\n\n\n\n(STRADDLE) MARKET CLOSED, TIME: "+DateUtils.getDateTime(LocalDateTime.now()));
//			log.info("(STRADDLE) MARKET CLOSED");
//			straddleService.printAllPositionsFromSheet();
//			return;
//		}
		boolean isCLosingTime = (LocalTime.now().isAfter(tradeEndTime) && LocalTime.now().isBefore(tradeEndTime.plusSeconds(30)));
		List<MyPosition> allPositions = straddleService.getPaperNetPositions();
		List<MyPosition> sellPositions = allPositions.stream().filter(p -> p.getNetQuantity() < 0).collect(Collectors.toList());
		if(CollectionUtils.isEmpty(sellPositions) && !isCLosingTime) {
			System.out.println("************* CREATING NEW (STRADDLE) POSITIONS ******************");
			log.info("CREATING NEW (STRADDLE) POSITIONS");
			placeStraddleStrategy(true);// Start every day trade
		}
		allPositions = straddleService.getPaperNetPositions();
		sellPositions = allPositions.stream().filter(p -> p.getNetQuantity() < 0).collect(Collectors.toList());
//		if((LocalTime.now().isAfter(tradeStartTime) && LocalTime.now().isBefore(tradeStartTime.plusSeconds(30))) && CollectionUtils.isNotEmpty(sellPositions)) {
//			log.info("New Itraday order placing...");
//			System.out.println("New Itraday order placing...");
//			placeStraddleStrategy();
//			return;
//		}
		// Close every day trade
		if(isCLosingTime && CollectionUtils.isNotEmpty(sellPositions)) {
			log.info("Closing all itraday positions...");
			System.out.println("Closing all itraday positions...");
			straddleService.closeAllSellPositions(sellPositions);
			return;
		}
		
		System.out.println("\n\n\n\n\n\t\t\t(STRADDLE) PAPER - POSITIONS AS ON: "+DateUtils.getDateTime(LocalDateTime.now()));
		log.info("PAPER (STRADDLE) - POSITIONS AS ON: "+DateUtils.getDateTime(LocalDateTime.now()));
		
		if(CollectionUtils.isEmpty(sellPositions) || sellPositions.size() > 2) {
			System.out.println("************* (STRADDLE) FOUND MORE THAN TWO PAPER (STRADDLE) POSITIONS ******************");
			log.info("FOUND MORE THAN TWO PAPER (STRADDLE) POSITIONS");
			return;
		}
		
		if(closeOnTarget) {
			boolean isCLosedAll = straddleService.checkTargetAndClosePositions(sellPositions);
			if(isCLosedAll && LocalTime.now().isBefore(lastHourStartAt)) {
				placeStraddleStrategy(false);
			} 
		}
		if(useStopLoss) {
			boolean isCLosedAll = straddleService.checkSLAndClosePositions(sellPositions);
			if(isCLosedAll  && LocalTime.now().isBefore(lastHourStartAt)) {
				placeStraddleStrategy(false);
			} 
		}
		straddleService.updteTradeFile(false);
		straddleService.printAllPositionsFromSheet();
		
	}

}
