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

import com.algo.model.AlogLtpData;
import com.algo.model.MyPosition;
import com.algo.paper.trade.connect.GrConnect;
import com.algo.utils.CommonUtils;
import com.algo.utils.Constants;
import com.algo.utils.DateUtils;
import com.algo.utils.ExcelUtils;

@Service
public class StraddleServiceImpl {

	Logger log = LoggerFactory.getLogger(this.getClass());

	@Value("${app.straddle.opstSymbol}")
	private String opstSymbol;
//	@Value("${app.straddle.expiry}")
//	private String expiry;
	@Value("${app.straddle.dataDir}")
	private String dataDir;
	@Value("${app.straddle.qty:50}")
	private Integer qty;
	@Value("${app.straddle.sl:50}")
	private Integer stopLoss;
	@Value("${app.straddle.targetPercInNetPrem:5}")
	private Integer targetPercInNetPrem;


	@Autowired
	GrConnect opstraConnect;

	@PostConstruct  
	public void postConstruct() {  
//		expiry = DateUtils.opstraFormattedExpiry(expiry);
//		System.out.println("Straddle expiry: "+expiry);  
		dataDir = dataDir + File.separator + LocalDate.now().format(DateTimeFormatter.ofPattern(Constants.YYYYMMDD));
	}  


	public void placeStraddleStrategy(boolean createNewFile) {
		LocalDate currentExpiryDate = DateUtils.getCurrentExpiry();
		String expiry = DateUtils.opstraFormattedExpiry(currentExpiryDate);
		List<String> symbols = new ArrayList<>();
		symbols.add(opstSymbol);
		Map<String, AlogLtpData> response =  opstraConnect.getSpotLtpData(symbols);
		AlogLtpData ltpData = response.get(opstSymbol);
		Integer strikePrice = getATMStrikePrice(ltpData);
//		List<Integer> availableStrikes = response.getStrikes();
//		if(!availableStrikes.contains(strikePrice)) {
//			log.info("Found invalid strike price");
//			System.out.println("INVALID STRIKE");
//		}
		log.info("(STRADDLE) Current Price: "+ltpData.getLastPrice()+" Strike: "+strikePrice);
		if(createNewFile)
			ExcelUtils.createExcelFile(dataDir);
		System.out.println("Current Price: "+ltpData.getLastPrice()+" Strike: "+strikePrice);
		symbols = new ArrayList<>();
		String ceTrSymbol = opstSymbol.toUpperCase()+expiry.toUpperCase()+strikePrice+Constants.CE;
		String peTrSymbol = opstSymbol.toUpperCase()+expiry.toUpperCase()+strikePrice+Constants.PE;
		symbols.add(ceTrSymbol);
		symbols.add(peTrSymbol);
		//symbol.toUpperCase()+expiry.toUpperCase()+curStrikePrice+Constants.CE
		Map<String, AlogLtpData> ltps = opstraConnect.getLtpData(symbols);
		Double cePremRcvd = ltps.get(ceTrSymbol).getLastPrice();
		Double pePremRcvd = ltps.get(peTrSymbol).getLastPrice();
		Double netPremRcvd = cePremRcvd  + pePremRcvd;
		boolean ceSell = sell(strikePrice.toString(), opstSymbol, expiry, qty * -1, Constants.CE, ltps.get(ceTrSymbol).getLastPrice());
		boolean peSell = false;
		if(ceSell)
			peSell = sell(strikePrice.toString(), opstSymbol, expiry, qty * -1, Constants.PE, ltps.get(peTrSymbol).getLastPrice());
		if(peSell) {
			Double totalTarget = qty * ((netPremRcvd * targetPercInNetPrem) / 100);
			Double ceSL = cePremRcvd + (ltps.get(ceTrSymbol).getLastPrice() * stopLoss /100);
			Double peSL = pePremRcvd + (ltps.get(peTrSymbol).getLastPrice() * stopLoss /100);
			ExcelUtils.setValueWithRedBgByCellReference(ExcelUtils.getCurrentFileNameWithPath(dataDir), ExcelUtils.SHEET_SL_VAL, StringUtils.EMPTY);
			ExcelUtils.setValueByCellReference(ExcelUtils.getCurrentFileNameWithPath(dataDir), ExcelUtils.SHEET_STRATEGY_NAME_VAL, "STRADDLE");
			ExcelUtils.setValueByCellReference(ExcelUtils.getCurrentFileNameWithPath(dataDir), ExcelUtils.SHEET_TARGET_VAL, totalTarget);
			ExcelUtils.setValueByCellReference(ExcelUtils.getCurrentFileNameWithPath(dataDir), ExcelUtils.SHEET_CE_SL_VAL, ceSL);
			ExcelUtils.setValueByCellReference(ExcelUtils.getCurrentFileNameWithPath(dataDir), ExcelUtils.SHEET_PE_SL_VAL, peSL);
			System.out.println("(STRADDLE) Straddled placed successfully");
			log.info("(STRADDLE) Straddled placed successfully");
		}
	}

	/**
	 * Gives the current positions which are not closed yet
	 * @return
	 */
	public List<MyPosition> getPaperNetPositions() {
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
		return null;
	}

	public void addStopLossToSheet(MyPosition posToKeep, MyPosition posToOpen) {
		CommonUtils.addStopLossToSheet(posToKeep, posToOpen, dataDir);
	}
	
	public boolean checkTargetAndClosePositions(List<MyPosition> netPositions) {
		boolean isClosedAll =false;
		String target = ExcelUtils.getValueByCellReference(ExcelUtils.getCurrentFileNameWithPath(dataDir), ExcelUtils.SHEET_TARGET_VAL);
		System.out.println("\t\t\t(STRADDLE) TARGET: "+target);
		if(StringUtils.isNotBlank(target)) {
			Double taregtDbl = Double.valueOf(target);
			Double netPnl = CommonUtils.getNetPnl(CommonUtils.getAllPaperPositions(dataDir));
			System.out.println("\t\t\t(STRADDLE) Current P/L: "+Constants.DECIMAL_FORMAT.format(netPnl)+" OF "+target);
			if(netPnl >= taregtDbl) {
				System.err.println("***** (STRADDLE) TARGET ACHIVED: TARGET:"+target+" NET P/L:"+netPnl);
				isClosedAll = closeAllSellPositions(netPositions);
			}
		}
		return isClosedAll;
	}
	
	public boolean checkSLAndClosePositions(List<MyPosition> netPositions) {
		boolean isClosedAll = false;
		String ceStopLoss = ExcelUtils.getValueByCellReference(ExcelUtils.getCurrentFileNameWithPath(dataDir), ExcelUtils.SHEET_CE_SL_VAL);
		String peStopLoss = ExcelUtils.getValueByCellReference(ExcelUtils.getCurrentFileNameWithPath(dataDir), ExcelUtils.SHEET_PE_SL_VAL);
		System.out.print("\t\t\t(STRADDLE) STOP LOSS CALL: "+ceStopLoss);
		System.out.println(", PUT: "+peStopLoss);
		if(StringUtils.isNotBlank(ceStopLoss) && Double.valueOf(ceStopLoss) > 0.0) {
			List<MyPosition> cePositions = netPositions.stream().filter(mp -> mp.getNetQuantity() < 0 && mp.getOptionType().equals(Constants.CE)).collect(Collectors.toList());
			Double totCePremHve = 0.0;
			if(CollectionUtils.isNotEmpty(cePositions)) {
				for(MyPosition cePos:cePositions) {
					totCePremHve = totCePremHve + cePos.getCurrentPrice();
				}
			}
			if(totCePremHve >= Double.valueOf(ceStopLoss)) {
				isClosedAll |= closeAllSellPositions(netPositions);
			}
		}
		
		if(StringUtils.isNotBlank(peStopLoss) && Double.valueOf(peStopLoss) > 0.0) {
			List<MyPosition> pePositions = netPositions.stream().filter(mp -> mp.getNetQuantity() < 0 && mp.getOptionType().equals(Constants.PE)).collect(Collectors.toList());
			Double totPePremHve = 0.0;
			if(CollectionUtils.isNotEmpty(pePositions)) {
				for(MyPosition cePos:pePositions) {
					totPePremHve = totPePremHve + cePos.getCurrentPrice();
				}
			}
			if(totPePremHve >= Double.valueOf(peStopLoss)) {
				isClosedAll |= closeAllSellPositions(netPositions);
			}
		}
		return isClosedAll;
	}
	
	public boolean checkSLAndClosePositions2(List<MyPosition> netPositions) {
		boolean isClosedAll = false;
		Double totCePremHve = 0.0;
		Double totPePremHve = 0.0;
		List<MyPosition> cePositions = netPositions.stream().filter(mp -> mp.getNetQuantity() < 0 && mp.getOptionType().equals(Constants.CE)).collect(Collectors.toList());
		if(CollectionUtils.isNotEmpty(cePositions)) {
			for(MyPosition cePos:cePositions) {
				totCePremHve = totCePremHve + cePos.getCurrentPrice();
			}
		}
		
		List<MyPosition> pePositions = netPositions.stream().filter(mp -> mp.getNetQuantity() < 0 && mp.getOptionType().equals(Constants.PE)).collect(Collectors.toList());
		if(CollectionUtils.isNotEmpty(pePositions)) {
			for(MyPosition cePos:pePositions) {
				totPePremHve = totPePremHve + cePos.getCurrentPrice();
			}
		}
		Double diffInPerc = CommonUtils.priceDiffInPerc(totPePremHve, totCePremHve);
		log.info("\t\t\t(STRADDLE) CE & PE PREM DIFF: "+Constants.DECIMAL_FORMAT.format(diffInPerc));
		log.info(", IF >= 80%, CLOSE ALL CE& PE POSITIONS");
		System.out.print("\t\t\t(STRADDLE) CE & PE PREM DIFF: "+Constants.DECIMAL_FORMAT.format(diffInPerc));
		System.out.println(", IF >= 80%, CLOSE ALL CE& PE POSITIONS");
		if(diffInPerc >= 80.0) {
			isClosedAll |= closeAllSellPositions(netPositions);
		}
		return isClosedAll;
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
		System.out.println("************* (STRADDLE) ADJUSTMENT STARTED *********************");
		log.info("************* (STRADDLE) ADJUSTMENT STARTED *********************");
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
			System.out.println("************* (STRADDLE) ADJUSTMENT COMPLETED *********************");
			log.info("************* (STRADDLE) ADJUSTMENT COMPLETED *********************");
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
			System.out.println("************* (STRADDLE) NEW BUY POSITION TRADING SYMBOL: "+(symbol+expiry+strikePrice+ceOrPe)+" ***************\n");
			log.info("(STRADDLE) NEW BUY POSITION TRADING SYMBOL: "+(symbol+expiry+strikePrice+ceOrPe));
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
			System.out.println("************* (STRADDLE) POSITION CLOSED ***************\n ORDER ID: ");
			return true;
		} catch (JSONException e) {
			e.printStackTrace();
			System.out.println("******************* (STRADDLE) "+e.getLocalizedMessage()+" ******************************");
			System.out.println("*************  (STRADDLE) PROBLEM WHILE PLACING A NEW BUY ORDER - DO IT MANUALLY : "+symbol+expiry+strikePrice+ceOrPe);
			log.error(e.getLocalizedMessage());
			log.error("(STRADDLE) PROBLEM WHILE PLACING A NEW BUY ORDER - DO IT MANUALLY : "+symbol+expiry+strikePrice+ceOrPe);
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
			System.out.println("************* (STRADDLE) SELL ORDER FOR TRADING SYMBOL: "+(symbol+expiry+strikePrice+ceOrPe)+" ***************\n");
			log.info("(STRADDLE) SELL ORDER FOR TRADING SYMBOL: "+(symbol+expiry+strikePrice+ceOrPe));
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
			System.out.println("************* (STRADDLE) SELL ORDER FOR TRADING SYMBOL: "+(symbol+expiry+strikePrice+ceOrPe)+" IS COMPLETED ");
			log.info("(STRADDLE) SELL ORDER FOR TRADING SYMBOL: "+(symbol+expiry+strikePrice+ceOrPe)+" IS COMPLETED ");
			return true;
		} catch (JSONException e) {
			e.printStackTrace();
			System.out.println("******************* (STRADDLE) "+e.getLocalizedMessage()+" ******************************");
			System.out.println("************* (STRADDLE) PROBLEM WHILE PLACING A NEW SELL ORDER - DO IT MANUALLY : "+symbol+expiry+strikePrice+ceOrPe);
			log.error(e.getLocalizedMessage());
			log.error("(STRADDLE) PROBLEM WHILE PLACING A NEW SELL ORDER - DO IT MANUALLY : "+symbol+expiry+strikePrice+ceOrPe);
			CommonUtils.beep();
		}
		return false;
	}

	public boolean closeAllSellPositions(List<MyPosition> netPositions) {
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

	private List<MyPosition> getNetPaperPositions() {
		String dataFile = ExcelUtils.getCurrentFileNameWithPath(dataDir);
		if(new File(dataFile).exists()) {
			List<MyPosition> netPositions = CommonUtils.getAllPaperPositions(dataDir);
			netPositions = netPositions.stream().filter(p -> p.getNetQuantity() != 0.0).collect(Collectors.toList());
			return netPositions;
		}
		return null;
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


	private Integer getATMStrikePrice(AlogLtpData response) {
		Integer strikePrice = 0;
		int l = String.valueOf(response.getLastPrice()).length();
		String d = "1";
		for(int i=0; i<l; i++) {
			d = d+"0";
		}
		if(opstSymbol.equals("BANKNIFTY"))
			strikePrice = closestNumber(response.getLastPrice(), 100);
		if(opstSymbol.equals("NIFTY"))
			strikePrice = closestNumber(response.getLastPrice(), 50);
		return strikePrice;
	}



	private int closestNumber(double n, int m) {
		int q = Double.valueOf(n).intValue() / m;
		int n1 = m * q;
		int n2 = (n * m) > 0 ? (m * (q + 1)) : (m * (q - 1));
		if (Math.abs(n - n1) < Math.abs(n - n2))
			return n1;
		return n2;   
	}

}
