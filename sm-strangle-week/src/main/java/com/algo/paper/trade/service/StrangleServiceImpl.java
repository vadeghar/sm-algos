package com.algo.paper.trade.service;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.algo.model.AlgoOptionChain;
import com.algo.model.AlogLtpData;
import com.algo.model.MyPosition;
import com.algo.paper.trade.connect.OpstraConnect;
import com.algo.utils.CommonUtils;
import com.algo.utils.Constants;
import com.algo.utils.DateUtils;
import com.algo.utils.ExcelUtils;

@Service
public class StrangleServiceImpl {
	

	Logger log = LoggerFactory.getLogger(this.getClass());

	@Value("${app.strangle.dataDir}")
	private String dataDir;
	
	@Value("${app.strangle.opstSymbol}")
	private String opstSymbol;
	
	@Value("${app.strangle.deltaVal}")
	private Double deltaVal;
	
	@Value("${app.strangle.deltaPlusOrMinus:3}")
	private Double deltaPlusOrMinus;
	
	@Value("${app.strangle.qty:50}")
	private Integer qty;
	
	@Value("${app.strangle.targetPercInNetPrem:80}")
	private Integer targetPercInNetPrem;
	
	private LocalDate defaultExpiryDate;
	
	@Autowired
	OpstraConnect opstraConnect;
	
	@PostConstruct  
    public void postConstruct() {  
		defaultExpiryDate = DateUtils.getNextThursdayFrom(LocalDate.now());
		dataDir = dataDir + File.separator + LocalDate.now().format(DateTimeFormatter.ofPattern(Constants.YYYYMMDD));
		System.out.println("Default Strangle expiry date: "+defaultExpiryDate.toString());  
    }  

	
	public void placeStrangleStrategy(boolean createNewFile) {
//		String opstExpiry = DateUtils.opstraFormattedExpiry(expiry);
		Map<String, AlgoOptionChain> strikes = opstraConnect.getOpstStrangleStrikes(opstSymbol, defaultExpiryDate, deltaVal, deltaPlusOrMinus);
		if(strikes == null || CollectionUtils.isEmpty(strikes.keySet()))
			throw new RuntimeException("(STRANGLE) Strike prices not Found near "+deltaVal+" +/- "+deltaPlusOrMinus+" delta");
		if(strikes.get(Constants.CE) == null)
			throw new RuntimeException("(STRANGLE) Strike price CALL not Found near "+deltaVal+" +/- "+deltaPlusOrMinus+" delta");
		if(strikes.get(Constants.PE) == null)
			throw new RuntimeException("(STRANGLE) Strike price PUT not Found near "+deltaVal+" +/- "+deltaPlusOrMinus+" delta");
		if(createNewFile)
			ExcelUtils.createExcelFile(dataDir);
		AlgoOptionChain peOption = strikes.get(strikes.keySet().stream().filter(s -> s.equals(Constants.PE)).findFirst().get());
		System.out.println("(STRANGLE) peOption: "+peOption);
		AlgoOptionChain ceOption = strikes.get(strikes.keySet().stream().filter(s -> s.equals(Constants.CE)).findFirst().get());
		System.out.println("(STRANGLE) ceOption: "+ceOption);
		System.out.println("*******************************************************************************************");
		boolean peSell = false;
		boolean ceSell = sell(ceOption.getStrikePrice().toString(), opstSymbol, DateUtils.opstraFormattedExpiry(defaultExpiryDate), qty * -1, Constants.CE, Double.valueOf(ceOption.getCallOption().getLtp()));
		if(ceSell) {
			peSell = sell(peOption.getStrikePrice().toString(), opstSymbol, DateUtils.opstraFormattedExpiry(defaultExpiryDate), qty * -1, Constants.PE, Double.valueOf(peOption.getPutOption().getLtp()));
		}
		if(peSell) {
			Double totalPremReceived = (Double.valueOf(ceOption.getCallOption().getLtp()) + Double.valueOf(peOption.getPutOption().getLtp())) * qty;
			Double totalTarget = (totalPremReceived * targetPercInNetPrem) / 100;
			ExcelUtils.setValueByCellReference(ExcelUtils.getCurrentFileNameWithPath(dataDir), ExcelUtils.SHEET_TARGET_VAL, Constants.DECIMAL_FORMAT.format(totalTarget));
			ExcelUtils.setValueByCellReference(ExcelUtils.getCurrentFileNameWithPath(dataDir), ExcelUtils.SHEET_STRATEGY_NAME_VAL, "STRANGLE");
			ExcelUtils.setValueWithRedBgByCellReference(ExcelUtils.getCurrentFileNameWithPath(dataDir), ExcelUtils.SHEET_CE_SL_VAL, StringUtils.EMPTY);
			ExcelUtils.setValueWithRedBgByCellReference(ExcelUtils.getCurrentFileNameWithPath(dataDir), ExcelUtils.SHEET_PE_SL_VAL, StringUtils.EMPTY);
		}
		System.out.println("*******************************************************************************************\n\n");
	}
	
	
	/**
	 * Gives the current positions which are not closed yet
	 * @return
	 */
	public List<MyPosition> getAllPaperPositions() {
		List<MyPosition> myNetPositions = new ArrayList<>();
		try {
			List<MyPosition> paperPositions = getNetPaperPositions();
			if(CollectionUtils.isNotEmpty(paperPositions)) {
				for(MyPosition p: paperPositions) {
					MyPosition myPosition = new MyPosition();
					myPosition.setTradingSymbol(p.getTradingSymbol());
					myPosition.setSymbol(CommonUtils.getOpstraSymbol(p.getTradingSymbol()));
					myPosition.setExpiry(CommonUtils.getOpstraExpiry(p.getTradingSymbol()));
					myPosition.setStrikePrice(CommonUtils.getOpstraStrikePrice(p.getTradingSymbol()));
					myPosition.setOptionType(CommonUtils.getOpstraOptionType(p.getTradingSymbol()));
					myPosition.setBuyPrice(p.getBuyPrice());
					myPosition.setSellPrice(p.getSellPrice());
					myPosition.setNetQuantity(p.getNetQuantity());
					myPosition.setCurrentPrice(null);
					myPosition.setPositionPnl(p.getPositionPnl());
					myPosition.setSellQuantity(p.getNetQuantity() < 0 ? Math.abs(p.getNetQuantity()) : 0);
					myPosition.setBuyQuantity(p.getNetQuantity() > 0 ? Math.abs(p.getNetQuantity()) : 0);
					myNetPositions.add(myPosition);
				}
				setPaperCurrentPrices(myNetPositions);
			}
			myNetPositions = myNetPositions.stream().sorted(Comparator.comparing(MyPosition::getTradingSymbol)).collect(Collectors.toList());
		} catch (Exception e) {
			e.printStackTrace();
		}
		return myNetPositions;
	}

	/**
	 * This will gives the new position details part of adjustment
	 * @param posToClose
	 * @return
	 */
	public MyPosition getNewSellPositionNearPremium(MyPosition posToClose, Double otherOptPrem) {
		MyPosition posToOpen = new MyPosition();
		try {
			Double newSellPremium = (otherOptPrem * 85) / 100;
			System.out.println("\t\t\t(STRANGLE) FINDING CALL OPTION AT PRICE: "+newSellPremium);
			List<String> nearestTenSymbols = getNearestTenKiteSymbols(posToClose);
			Map<String, AlogLtpData> ltps = opstraConnect.getLtpData(nearestTenSymbols);
			String tradeSymbol = null;
			for(int i =1; i<=10; i++) {
				tradeSymbol = CommonUtils.getNearestTradingSymbolAtNPrice(newSellPremium, ltps, i);
				if(StringUtils.isNotBlank(tradeSymbol))
					break;
			}
			if(tradeSymbol == null)
				return null;
			posToOpen.setTradingSymbol(tradeSymbol);
			posToOpen.setExpiry(posToClose.getExpiry());
			posToOpen.setOptionType(posToClose.getOptionType());
			posToOpen.setCurrentPrice(ltps.get(tradeSymbol).getLastPrice());
			posToOpen.setStrikePrice(CommonUtils.getOpstraStrikePrice(posToOpen.getTradingSymbol()));
			posToOpen.setSymbol(CommonUtils.getOpstraSymbol(posToOpen.getTradingSymbol()));
			posToOpen.setSellQuantity(posToClose.getSellQuantity()); // Same quantity
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return posToOpen;
	}
	
	public void addStopLossToSheet(MyPosition posToKeep, MyPosition posToOpen) {
		CommonUtils.addStopLossToSheet(posToKeep, posToOpen, dataDir);
	}

	/**
	 * Set the CMP on all net positions
	 * @param myNetPositions
	 */
	private void setPaperCurrentPrices(List<MyPosition> myNetPositions) {
		List<String> symbols = new ArrayList<>();
		Map<String, AlogLtpData> ltps = new HashMap<>();
		try {
			for(MyPosition p: myNetPositions) {
				symbols.add(p.getTradingSymbol());
			}
			ltps = opstraConnect.getLtpData(symbols);
		} catch (Exception e) {
			e.printStackTrace();
		}
		for(MyPosition p: myNetPositions) {
			p.setCurrentPrice(ltps.get(p.getTradingSymbol()) !=null ? ltps.get(p.getTradingSymbol()).getLastPrice() : null);
			CommonUtils.getPositionPnl(p);
		}

	}

	/**
	 * Print net positions on console
	 */
	public void printAllPositionsFromSheet() {
		CommonUtils.printAllPositionsFromSheet(dataDir);
	}
	
	/**
	 * Updates the excel sheet with current trades with pnl
	 */
	public void updteTradeFile(boolean isNewPositions) {
		try {
//			List<MyPosition> netPositions = CommonUtils.getAllPaperPositions(dataDir);
			updateLatestPricesInFile();
			//CommonUtils.updateExcelSheet(dataDir, netPositions, isNewPositions);
		} catch(Exception e) {
			e.printStackTrace();
		}

	}

	/**
	 * Adjustment method
	 * @param posToClose
	 * @param posToOpen
	 */
	public void startAdjustment(MyPosition posToClose, MyPosition posToOpen) {
		System.out.println("************* (STRANGLE) ADJUSTMENT STARTED *********************");
		log.info("************* (STRANGLE) ADJUSTMENT STARTED *********************");
		List<MyPosition> closePositions = new ArrayList<>();
		closePositions.add(posToClose);
		boolean buyCompleted = closeAllSellPositions(closePositions);

		if(!buyCompleted)
			return;
		boolean sellCompleted = sell(posToOpen.getStrikePrice(), 
				posToOpen.getSymbol(), 
				posToOpen.getExpiry(),
				posToOpen.getSellQuantity() * -1, 
				posToOpen.getOptionType(),
				posToOpen.getCurrentPrice());
		if(sellCompleted) {
			System.out.println("************* (STRANGLE) ADJUSTMENT COMPLETED *********************");
			log.info("************* (STRANGLE) ADJUSTMENT COMPLETED *********************");
		}
	}



	/**
	 * Place BUY order (Market Order - Regular)
	 * @param strikePrice
	 * @param symbol
	 * @param expiry
	 * @param exchange
	 * @param qty
	 * @param product
	 * @param ceOrPe
	 * @return
	 */
	public boolean buy(String strikePrice, String symbol, String expiry, Integer qty, String ceOrPe, double price, boolean isClose) {
		try {
			System.out.println("************* (STRANGLE) NEW BUY POSITION TRADING SYMBOL: "+(symbol+expiry+strikePrice+ceOrPe)+" ***************\n");
			log.info("(STRANGLE) NEW BUY POSITION TRADING SYMBOL: "+(symbol+expiry+strikePrice+ceOrPe));
			String fileToUpdate = ExcelUtils.getCurrentFileNameWithPath(dataDir);
			List<Object[]> netPositionRows = new ArrayList<>();
			netPositionRows.add(
					ExcelUtils.prepareDataRow(symbol+expiry+strikePrice+ceOrPe, // Position
					isClose ? 0 : qty, // Wty
					StringUtils.EMPTY, // Sell Price
					price, // Buy Price
					price, // Current Price
					StringUtils.EMPTY, //P&L
					StringUtils.EMPTY, //Ex Time
					DateUtils.getDateTime(LocalDateTime.now()) // Close Time
					));
			ExcelUtils.addOrUpdateRows(fileToUpdate, netPositionRows);
			System.out.println("************* (STRANGLE) POSITION CLOSED ***************\n ORDER ID: ");
			return true;
		} catch (JSONException e) {
			e.printStackTrace();
			System.out.println("******************* (STRANGLE) "+e.getLocalizedMessage()+" ******************************");
			System.out.println("*************  (STRANGLE) PROBLEM WHILE PLACING A NEW BUY ORDER - DO IT MANUALLY : "+symbol+expiry+strikePrice+ceOrPe);
			log.error(e.getLocalizedMessage());
			log.error("(STRANGLE) PROBLEM WHILE PLACING A NEW BUY ORDER - DO IT MANUALLY : "+symbol+expiry+strikePrice+ceOrPe);
			CommonUtils.beep();
		}
		return false;
	}


	/**
	 * Place SELL order (Market Order - Regular)
	 * @param strikePrice
	 * @param symbol
	 * @param expiry
	 * @param exchange
	 * @param qty
	 * @param product
	 * @param ceOrPe
	 * @return
	 */
	public boolean sell(String strikePrice, String symbol, String expiry, Integer qty, String ceOrPe, double price) {
		try {
			System.out.println("************* (STRANGLE) SELL ORDER FOR TRADING SYMBOL: "+(symbol+expiry+strikePrice+ceOrPe)+" ***************\n");
			log.info("(STRANGLE) SELL ORDER FOR TRADING SYMBOL: "+(symbol+expiry+strikePrice+ceOrPe));
			String fileToUpdate = ExcelUtils.getCurrentFileNameWithPath(dataDir);
			List<Object[]> netPositionRows = new ArrayList<>();
			netPositionRows.add(
					ExcelUtils.prepareDataRow(symbol+expiry+strikePrice+ceOrPe, // Position
					qty, // Wty
					price, // Sell Price
					0, // Buy Price
					price, // Current Price
					0, //P&L
					DateUtils.getDateTime(LocalDateTime.now()), //Ex Time
					StringUtils.EMPTY // Close Time
					));
			ExcelUtils.addOrUpdateRows(fileToUpdate, netPositionRows);
			System.out.println("************* (STRANGLE) SELL ORDER FOR TRADING SYMBOL: "+(symbol+expiry+strikePrice+ceOrPe)+" IS COMPLETED ");
			log.info("(STRANGLE) SELL ORDER FOR TRADING SYMBOL: "+(symbol+expiry+strikePrice+ceOrPe)+" IS COMPLETED ");
			return true;
		} catch (JSONException e) {
			e.printStackTrace();
			System.out.println("******************* (STRANGLE) "+e.getLocalizedMessage()+" ******************************");
			System.out.println("************* (STRANGLE) PROBLEM WHILE PLACING A NEW SELL ORDER - DO IT MANUALLY : "+symbol+expiry+strikePrice+ceOrPe);
			log.error(e.getLocalizedMessage());
			log.error("(STRANGLE) PROBLEM WHILE PLACING A NEW SELL ORDER - DO IT MANUALLY : "+symbol+expiry+strikePrice+ceOrPe);
			CommonUtils.beep();
		}
		return false;
	}
	
	
	public boolean checkTargetAndClosePositions(List<MyPosition> netPositions) {
		boolean isClosedAll =false;
		String target = ExcelUtils.getValueByCellReference(ExcelUtils.getCurrentFileNameWithPath(dataDir), ExcelUtils.SHEET_TARGET_VAL);
		System.out.println("\t\t\t(STRANGLE) TARGET: "+target);
		if(StringUtils.isNotBlank(target)) {
			Double taregtDbl = Double.valueOf(target);
			Double netPnl = CommonUtils.getNetPnl(CommonUtils.getAllPaperPositions(dataDir));
			System.out.println("\t\t\t(STRANGLE) Current P/L: "+Constants.DECIMAL_FORMAT.format(netPnl)+" OF "+target);
			if(netPnl >= taregtDbl) {
				System.err.println("***** (STRANGLE) TARGET ACHIVED: TARGET:"+target+" NET P/L:"+netPnl);
				isClosedAll = closeAllSellPositions(netPositions);
			}
		}
		return isClosedAll;
	}
	
	public boolean checkSLAndClosePositions(List<MyPosition> netPositions) {
		boolean isClosedAll = false;
		String stopLoss = ExcelUtils.getValueByCellReference(ExcelUtils.getCurrentFileNameWithPath(dataDir), ExcelUtils.SHEET_SL_VAL);
		if(StringUtils.isBlank(stopLoss) || Double.valueOf(stopLoss) == 0.0) {
			System.out.println("\t\t\t(STRANGLE) STOP LOSS: (NO STOP LOSS ADDED YET)");
			return isClosedAll;
		}
		if(StringUtils.isNotBlank(stopLoss)) {
			Double stopLossPremium = Double.valueOf(stopLoss);
			Double netPremiumHave = CommonUtils.getNetPremiumCollected(netPositions);
			System.out.println("\t\t\t(STRANGLE) STOP LOSS: "+stopLoss+" / "+ Constants.DECIMAL_FORMAT.format(netPremiumHave));
			if(netPremiumHave  >= stopLossPremium) {
				System.err.println("(STRANGLE) STOP LOSS HIT: "+stopLossPremium+" NET PRICE OF EXISTING POSITIONS: "+netPremiumHave);
				log.info("(STRANGLE) STOP LOSS HIT: "+stopLossPremium+" NET PRICE OF EXISTING POSITIONS: "+netPremiumHave);
				isClosedAll = closeAllSellPositions(netPositions);
			}
		}
		return isClosedAll;
	}
	
	
	
	
	private boolean closeAllSellPositions(List<MyPosition> netPositions) {
		boolean closed = false;
		try {
			for(MyPosition p: netPositions) {
				if(p.getNetQuantity() < 0)
					closed |= buy(p.getStrikePrice(), p.getSymbol(), p.getExpiry(), p.getSellQuantity(), p.getOptionType(), p.getCurrentPrice(), true);
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
		return closed;
	}
	
	private boolean closeAllBuyPositions(List<MyPosition> netPositions) {
		boolean closed = false;
		try {
			for(MyPosition p: netPositions) {
				if(p.getNetQuantity() > 0)
					closed |= sell(p.getStrikePrice(), p.getSymbol(), p.getExpiry(), p.getBuyQuantity(), p.getOptionType(), p.getCurrentPrice());
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
		return closed;
	}

	private void updateLatestPricesInFile() {
		List<MyPosition> netPositions = CommonUtils.getAllPaperPositions(dataDir);
		List<String> tradingSymbols = ExcelUtils.getAllSymbols(ExcelUtils.getCurrentFileNameWithPath(dataDir));
		Map<String, AlogLtpData> ltps = opstraConnect.getLtpData(tradingSymbols);
		List<Object[]> netPositionRows = new ArrayList<>();
		String fileToUpdate = ExcelUtils.getCurrentFileNameWithPath(dataDir);
		double netPnl = 0.0;
		for(MyPosition position : netPositions) {
			double lastPrice = ltps.get(position.getTradingSymbol()).getLastPrice();
			double pnl = 0.0;
			if(position.getNetQuantity() == 0) {
				pnl = position.getPositionPnl();
			} else {
				pnl = (position.getSellPrice() - lastPrice) * position.getNetQuantity();
			}
			if(position.getNetQuantity() < 0 && position.getSellPrice() > lastPrice)
				pnl = Math.abs(pnl);
			if(position.getNetQuantity() < 0 && position.getSellPrice() < lastPrice)
				pnl = -1 * pnl;
			netPnl = netPnl + pnl;
			
			netPositionRows.add(
					ExcelUtils.prepareDataRow(position.getTradingSymbol(), // Position
							StringUtils.EMPTY, // Wty
							StringUtils.EMPTY, // Sell Price
							StringUtils.EMPTY, // Buy Price
							Double.valueOf(lastPrice), // Current Price
							pnl, //P&L
							StringUtils.EMPTY, //Ex Time
							StringUtils.EMPTY // Close Time
							));
			
		} // For loop end
		//System.out.println(" TRADES: "+netPositionRows.size());
		ExcelUtils.addOrUpdateRows(fileToUpdate, netPositionRows);
	}



	//NIFTY&25NOV2021&17650&CE - NIFTY18NOV202118300CE
	private List<String> getNearestTenKiteSymbols(MyPosition posToClose) {
		List<String> tradingSymbols = new ArrayList<>();
		Integer strikeDiff = posToClose.getSymbol().equals("NIFTY") ? 50 : 100;
		Integer curStrikePrice= Integer.valueOf(posToClose.getStrikePrice());
		String symbol = posToClose.getSymbol(); //NIFTY21NOV16800PE
		String expiry = posToClose.getExpiry();
		if(posToClose.getOptionType().equals(Constants.CE)) {
			for(int i=0; i<15;i++) {
				curStrikePrice = curStrikePrice - strikeDiff;
				tradingSymbols.add(symbol.toUpperCase()+expiry.toUpperCase()+curStrikePrice+Constants.CE);
			}
		} else if(posToClose.getOptionType().equals(Constants.PE)) {
			for(int i=0; i<15;i++) {
				curStrikePrice = curStrikePrice + strikeDiff;
				tradingSymbols.add(symbol.toUpperCase()+expiry.toUpperCase()+curStrikePrice+Constants.PE);
			}
		}
		System.out.println("\t\t\t(STRANGLE) GETTING LTP FOR SYMBOLS: "+tradingSymbols);
		return tradingSymbols;
	}

	
	
	private List<MyPosition> getNetPaperPositions() {
		List<MyPosition> netPositions = CommonUtils.getAllPaperPositions(dataDir);
		netPositions = netPositions.stream().filter(p -> p.getNetQuantity() != 0.0).collect(Collectors.toList());
		return netPositions;
	}
	
	public double getPositionPnl(MyPosition p1) {
		return CommonUtils.getPositionPnl(p1);
	}

	
}
