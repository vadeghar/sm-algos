package com.algo.paper.trade.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.json.JSONException;
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
public class PaperTradeStrangle {
	
	Logger log = LoggerFactory.getLogger(this.getClass());
	
	@Value("${app.strangle.adjustmentPerc:50}")
	private Integer adjustmentPerc;
	
	@Value("${app.strangle.closeOnTarget:false}")
	private boolean closeOnTarget;
	
	@Value("${app.strangle.useStopLoss:false}")
	private boolean useStopLoss;
	
	@Value("${app.strangle.adjustAtEnd:false}")
	private boolean adjustAtEnd;
	
	@Value("${app.strangle.keepCePeDiffPercAtEnd:80}")
	private Integer keepCePeDiffPercAtEnd;
	
	LocalTime closeTime = LocalTime.parse(Constants.CLOSEING_TIME);
	LocalTime openingTime = LocalTime.parse(Constants.OPENING_TIME);
	
	@Autowired
	StrangleServiceImpl strangleService;
	
	/**
	 * To start new strangle strategy, Not implemented yet for PAPER
	 * @param opstSymbol
	 * @param expiry
	 * @param deltaVal
	 * @param qty
	 */
	public void placeStrangleStrategy(boolean createNewFile) {
		strangleService.placeStrangleStrategy(createNewFile);
	}
	
	@Scheduled(cron = "${app.strangle.cron.expression}")
	public void monitorPaperStrangleAndDoAdjustments() throws JSONException, IOException {
		strangleService.printAllPositionsFromSheet();
		if((LocalTime.now().isBefore(openingTime)) || (LocalTime.now().isAfter(closeTime) || LocalTime.now().equals(closeTime))) {
			System.out.println("\n\n\n\nMARKET CLOSED");
			log.info("MARKET CLOSED");
			return;
		}
		System.out.println("\n\n\n\n\n\t\t\tPAPER (STRANGLE) - POSITIONS AS ON: "+DateUtils.getDateTime(LocalDateTime.now()));
		log.info("PAPER (STRANGLE) - POSITIONS AS ON: "+DateUtils.getDateTime(LocalDateTime.now()));
		List<MyPosition> allPositions = strangleService.getAllPaperPositions();
		if(CollectionUtils.isEmpty(allPositions))
			placeStrangleStrategy(true);
		if(CollectionUtils.isEmpty(allPositions)) {
			System.out.println("************* NO PAPER (STRANGLE) POSITIONS FOUND ******************");
			log.info("NO PAPER (STRANGLE) POSITIONS FOUND");
			return;
		}
		List<MyPosition> sellPositions = allPositions.stream().filter(p -> p.getNetQuantity() < 0).collect(Collectors.toList());
		if(CollectionUtils.isEmpty(sellPositions) || sellPositions.size() > 2) {
			System.out.println("************* FOUND MORE THAN TWO PAPER (STRANGLE) POSITIONS ******************");
			log.info("FOUND MORE THAN TWO PAPER (STRANGLE) POSITIONS");
			return;
		}
		if(closeOnTarget) {
			boolean isCLosedAll = strangleService.checkTargetAndClosePositions(sellPositions);
			if(isCLosedAll)
				return;
		}
		
		if(useStopLoss) {
			boolean isCLosedAll = strangleService.checkSLAndClosePositions(sellPositions);
			if(isCLosedAll)
				return;
		}
		
		//List<MyPosition> buyPositions = netPositions.stream().filter(p -> p.getNetQuantity() > 0).collect(Collectors.toList());
		MyPosition p1 = sellPositions.get(0);
		MyPosition p2 = sellPositions.get(1);
		Double diffInPerc = CommonUtils.priceDiffInPerc(p1.getCurrentPrice(), p2.getCurrentPrice());
		if(adjustAtEnd && LocalTime.now().isAfter(closeTime.minusMinutes(5)) && LocalTime.now().isBefore(openingTime) && diffInPerc < keepCePeDiffPercAtEnd) {
			System.out.println("\t\t\t** (STRANGLE) Adjustment at the end of the day is started: ");
			log.info("\t\t\t** (STRANGLE) Keeping CE and PE diff : "+keepCePeDiffPercAtEnd);
			initAdjustmentAction(p1, p2);
		}
		System.out.println("\t\t\t(STRANGLE) CE AND PE PRICE DIFFERENCE: "+String.format("%.2f", diffInPerc)+"%\n\t\t\tWAITING FOR DIFFERENCE IF: "+adjustmentPerc+"%");
		log.info("\t\t\t(STRANGLE) CE AND PE PRICE DIFFERENCE: "+String.format("%.2f", diffInPerc)+"%\n\t\t\tWAITING FOR DIFFERENCE IF: "+adjustmentPerc+"%");
		if(Double.valueOf(String.format("%.2f", diffInPerc)) > adjustmentPerc) {
			initAdjustmentAction(p1, p2);
		}
		strangleService.updteTradeFile(false);
		
	}
	
	private void initAdjustmentAction(MyPosition p1, MyPosition p2) {
		System.out.println("(STRANGLE) **************************************************************************************");
		log.info("(STRANGLE) TIME TO TAKE ROBO ACTION");
		MyPosition posToClose = null;
		MyPosition posToKeep = null;
		double p1Pnl = strangleService.getPositionPnl(p1);
		double p2Pnl = strangleService.getPositionPnl(p2);
		Double otherOptPrem = 0.0;
		if(p1Pnl >  p2Pnl) {
			System.out.println("\t\t\t(STRANGLE) CLOSING POSITION: "+p1.getTradingSymbol());
			log.info("(STRANGLE) CLOSING POSITION: "+p1.getTradingSymbol());
			System.out.println("\t\t\t(STRANGLE) P/L of : "+p1.getTradingSymbol()+" ("+p1Pnl+") IS HIGHER THAN OF POSITION: "+p2.getTradingSymbol()+" ("+p2Pnl+")");
			log.info("(STRANGLE) P/L of : "+p1.getTradingSymbol()+" ("+p1Pnl+") IS HIGHER THAN OF POSITION: "+p2.getTradingSymbol()+" ("+p2Pnl+")");
			posToClose = p1;
			posToKeep = p2;
		}
		if(p2Pnl >  p1Pnl) {
			System.out.println("(STRANGLE) CLOSING POSITION: "+p2.getTradingSymbol());
			log.info("(STRANGLE) CLOSING POSITION: "+p2.getTradingSymbol());
			System.out.println("\t\t\t(STRANGLE) P/L of : "+p2.getTradingSymbol()+" ("+p2Pnl+") IS HIGHER THAN OF POSITION: "+p1.getTradingSymbol()+" ("+p1Pnl+")");
			log.info("(STRANGLE) P/L of : "+p2.getTradingSymbol()+" ("+p2Pnl+") IS HIGHER THAN OF POSITION: "+p1.getTradingSymbol()+" ("+p1Pnl+")");
			posToClose = p2;
			posToKeep = p1;
		}
		otherOptPrem = posToKeep.getCurrentPrice();
		System.out.println("(STRANGLE) CLOSING POSITION: "+posToClose.getTradingSymbol());
		log.info("(STRANGLE) CLOSING POSITION: "+posToClose.getTradingSymbol());
		MyPosition posToOpen = strangleService.getNewSellPositionNearPremium(posToClose, otherOptPrem);
		if(posToOpen == null) {
			System.out.println("\t\t\t***(STRANGLE) NOT FOUND NEAREST OPTION");
			log.info("***(STRANGLE) NOT FOUND NEAREST OPTION");
			return;
		}
		strangleService.addStopLossToSheet(posToKeep, posToOpen);
		strangleService.startAdjustment(posToClose, posToOpen);
		System.out.println("\n**************************************************************************************");
		log.info("**************************************************************************************");
	}
	
	

}
