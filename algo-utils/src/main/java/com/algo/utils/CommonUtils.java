package com.algo.utils;

import java.awt.Toolkit;
import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.algo.model.AlogLtpData;
import com.algo.model.MyPosition;



public class CommonUtils {
	
	static Logger log = LoggerFactory.getLogger(CommonUtils.class);
	
	public static void beep() {
		for(int i= 0; i<5;i++) {
			try {
				Toolkit.getDefaultToolkit().beep();
				Thread.sleep(2000);
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}
		}
	}
	
	public static Double priceDiffInPerc(Double p1, Double p2) {
		Double big = p1 > p2 ? p1  : p2;
		Double small = p1 < p2 ? p1  : p2;
		Double perc = (big - small) / big * 100;
		return perc;
	}
	
	public static Double getNetPremiumCollected(List<MyPosition> netPositions) {
		Double netPremium = 0.0;
		for(MyPosition p : netPositions) {
			if(p.getNetQuantity() < 0)
				netPremium = p.getCurrentPrice() != null ? (netPremium + p.getCurrentPrice()) : 0.0;
		}
		return netPremium;
	}
	
	public static List<MyPosition> getAllPaperPositions(String dataDir) {
		String kiteTradeFile = ExcelUtils.getCurrentFileNameWithPath(dataDir);
		List<MyPosition> allPositions = new ArrayList<>();
		if(!(new File(kiteTradeFile).exists())) {
			log.info("getAllPaperPositions> File: "+kiteTradeFile+" Not exist, Returning 0");
			System.out.println("getAllPaperPositions> File: "+kiteTradeFile+" Not exist, Returning 0");
			return allPositions;
		}
		String[][] data = ExcelUtils.getFileData(kiteTradeFile);
		int i = 0;
		for(String[] row : data) {
			if(i == 0) {
				i++;
				continue;
			}
			if(StringUtils.isBlank(row[0])) {
				i++;
				continue;
			}
			MyPosition p = new MyPosition();
			p.setTradingSymbol(row[0]); // Col: Position
			p.setNetQuantity(Double.valueOf(row[1]).intValue()); // Col: Qty
			p.setSellPrice(Double.valueOf(row[2])); // Col: Sell Price
			p.setBuyPrice(Double.valueOf(row[3])); // Col: Buy Price
			p.setCurrentPrice(Double.valueOf(row[4])); // Col: Current Price
			p.setPositionPnl(Double.valueOf(row[5])); // Col: P&L
			p.setStartDate(StringUtils.isNotBlank(row[6]) ?  row[6] : null); // Col: Ex Time
			p.setEndDate(StringUtils.isNotBlank(row[7]) ?  row[7] : null); // Col: Close Time
			allPositions.add(p);
		}
		allPositions = allPositions.stream().sorted(Comparator.comparing(MyPosition::getNetQuantity).reversed()).collect(Collectors.toList());
		return allPositions;
	}
	
	public static void addStopLossToSheet(MyPosition posToKeep, MyPosition posToOpen, String dataDir) {
		String otherStrikePrice = posToKeep.getStrikePrice();
		if(Integer.valueOf(posToOpen.getStrikePrice()) <= Integer.valueOf(otherStrikePrice)) {
			System.out.println("\t\t\t** POSITION IS strangle NOW, ADDING STOP LOSS **");
			log.info("\t\t\t** POSITION IS strangle NOW, ADDING STOP LOSS **");
			Double totPrem = posToOpen.getCurrentPrice() + posToKeep.getCurrentPrice();
			Double sl = totPrem + (totPrem * 0.1);
			System.out.println("\t\t\tSTOP LOSS IS: "+sl);
			log.info("\t\t\tSTOP LOSS IS: "+sl);
			ExcelUtils.setValueByCellReference(ExcelUtils.getCurrentFileNameWithPath(dataDir), Constants.SHEET_SL_VAL, Constants.DECIMAL_FORMAT.format(sl));
		}
	}
	
	public static void updateExcelSheet(String dataDir, List<MyPosition> netPositions, boolean isNewLine) {
		List<Object[]> netPositionRows = new ArrayList<>();
		String fileToUpdate = ExcelUtils.getCurrentFileNameWithPath(dataDir);
		double netPnl = 0.0;
		for(MyPosition position : netPositions) {
			double lastPrice = position.getCurrentPrice();
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
							position.getNetQuantity(), // Qty
							position.getSellPrice(),  // Sell Price
							position.getBuyPrice(), // Buy Price
							Double.valueOf(lastPrice), // Current Price
							Double.valueOf(pnl), // P&L
							isNewLine ? DateUtils.getDateTime(LocalDateTime.now()) : StringUtils.EMPTY, // Ex Time
							StringUtils.EMPTY) // Close Time
					); 
		} 
		ExcelUtils.addOrUpdateRows(fileToUpdate, netPositionRows);
		
	}
	
	/**
	 * Print net positions on console
	 */
	public static void printAllPositionsFromSheet(String dataDir) {
		log.info("printAllPositionsFromSheet>");
		List<MyPosition> netPositions = CommonUtils.getAllPaperPositions(dataDir);
		System.out.println("\t-------------------------------------------------------------------------------------------------------------------------------------------------------------------------\n");
		System.out.println("\tTRADING SYMBOL\t\t|\tTRADE TYPE\t|\tQty\t|\tSell Price\t|\tBuy Price\t|\tCurrent Price\t|\tP/L\t| Net P/L\t|\n");
		System.out.println("\t-------------------------------------------------------------------------------------------------------------------------------------------------------------------------\n");
		log.info("\t-------------------------------------------------------------------------------------------------------------------------------------------------------------------------\n");
		log.info("\tTRADING SYMBOL\t\t|\tTRADE TYPE\t|\tQty\t|\tSell Price\t|\tBuy Price\t|\tCurrent Price\t|\tP/L\t| Net P/L\t|\n");
		log.info("\t-------------------------------------------------------------------------------------------------------------------------------------------------------------------------\n");
		String sell = Constants.SELL;
		String buy = Constants.BUY;
		double netPnl = 0.0;
		for(MyPosition p: netPositions) {
			double lastPrice = p.getCurrentPrice();
			double pnl = 0.0;
			if(p.getNetQuantity() == 0) {
				pnl = p.getPositionPnl();
			} else {
				pnl = (p.getSellPrice() - lastPrice) * p.getNetQuantity();
			}
			if(p.getNetQuantity() < 0 && p.getSellPrice() > lastPrice)
				pnl = Math.abs(pnl);
			if(p.getNetQuantity() < 0 && p.getSellPrice() < lastPrice)
				pnl = -1 * pnl;
			netPnl = netPnl + pnl;
			System.out.println("\t"+p.getTradingSymbol()+
					"\t|\t"+(p.getNetQuantity() < 0 ? sell : (p.getNetQuantity() > 0) ? buy : "CLOSED")+
					"\t\t|\t"+p.getNetQuantity()+
					"\t|\t\t"+String.format("%.2f",p.getSellPrice())+
					"\t|\t"+String.format("%.2f",p.getBuyPrice())+
					"\t\t|\t"+String.format("%.2f",lastPrice)+
					"\t\t|\t"+String.format("%.2f",pnl)+
					"\t| "+String.format("%.2f",netPnl)+"\t"+
					"|\n");
			log.info("\t"+p.getTradingSymbol()+
					"\t|\t"+(p.getNetQuantity() < 0 ? sell : (p.getNetQuantity() > 0) ? buy : "CLOSED")+
					"\t\t|\t"+p.getNetQuantity()+
					"\t|\t\t"+String.format("%.2f",p.getSellPrice())+
					"\t|\t"+String.format("%.2f",p.getBuyPrice())+
					"\t\t|\t"+String.format("%.2f",lastPrice)+
					"\t\t|\t"+String.format("%.2f",pnl)+
					"\t| "+String.format("%.2f",netPnl)+"\t"+
					"|\n");
		}
		System.out.println("\t-------------------------------------------------------------------------------------------------------------------------------------------------------------------------\n");
		log.info("<printAllPositionsFromSheet");

	}
	
	/**
	 * Gives the current position's pnl and set this pnl on same object
	 * @param p
	 * @return
	 */
	public static double getPositionPnl(MyPosition p) {
		if(p.getCurrentPrice() == null) {
			log.info("No current price set on position, Might me an api probelm");
			System.out.println("No current price set on position, Might me an api probelm");
			return 0.0;
		}
		double lastPrice = p.getCurrentPrice();
		double pnl = 0.0;
		if(p.getNetQuantity() == 0) {
			pnl = p.getPositionPnl();
		} else {
			pnl = (p.getSellPrice() - lastPrice) * p.getNetQuantity();
		}
		if(p.getNetQuantity() < 0 && p.getSellPrice() > lastPrice)
			pnl = Math.abs(pnl);
		if(p.getNetQuantity() < 0 && p.getSellPrice() < lastPrice)
			pnl = -1 * pnl;
		p.setPositionPnl(pnl);
		return pnl;
	}
	
	public static Double getNetPnl(List<MyPosition> netPositions) {
		Double netPnl = 0.0;
		for(MyPosition p : netPositions) {
			netPnl = p.getPositionPnl() != null ? (netPnl + p.getPositionPnl()) : 0.0;
		}
		return netPnl;
	}
	
	/**
	 * NIFTY21NOV16800PE = 16800
	 * @param tradingSymbol
	 * @return
	 */
	public static  String getKiteStrikePrice(String tradingSymbol) {
		String strikePrice = tradingSymbol
				.replace(getKiteSymbol(tradingSymbol), StringUtils.EMPTY)
				.replace(getKiteExpiry(tradingSymbol), StringUtils.EMPTY)
				.replace(Constants.CE, StringUtils.EMPTY)
				.replace(Constants.PE, StringUtils.EMPTY);
		return strikePrice;
	}

	/**
	 * NIFTY21NOV16800PE = PE
	 * @param tradingSymbol
	 * @return
	 */
	public static  String getKiteOptionType(String tradingSymbol) {
		String optionType = tradingSymbol
				.replace(getKiteSymbol(tradingSymbol), StringUtils.EMPTY)
				.replace(getKiteExpiry(tradingSymbol), StringUtils.EMPTY)
				.replace(getKiteStrikePrice(tradingSymbol), StringUtils.EMPTY);
		return optionType;
	}

	/**
	 * NIFTY21NOV16800PE = NIFTY
	 * @param tradingSymbol
	 * @return
	 */
	public static  String getKiteSymbol(String tradingSymbol) {
		String opstSymbol = tradingSymbol.substring(0, tradingSymbol.length() - 12);
		return opstSymbol;
	}

	/**
	 * NIFTY21NOV16800PE = 21NOV/21N03/21N18
	 * @param tradingSymbol
	 * @return
	 */
	public static  String getKiteExpiry(String tradingSymbol) {
		String opstSymbol = tradingSymbol.substring(0, tradingSymbol.length() - 12);
		String opstExpiry = tradingSymbol.substring(opstSymbol.length(), tradingSymbol.length() - 7);
		return opstExpiry;
	}
	//BANKNIFTY02DEC202137100PE = 02DEC2021
	public static  String getSpecialExpiry(String tradingSymbol, boolean isMonthly) {
		String strikeOptType = tradingSymbol.substring(tradingSymbol.length() - 7); // 37100PE
		tradingSymbol = tradingSymbol.replace(strikeOptType, StringUtils.EMPTY); // BANKNIFTY02DEC2021
		String exp = tradingSymbol.substring(tradingSymbol.length() - 9); // 02DEC2021
		String symbol = tradingSymbol.replace(exp, StringUtils.EMPTY); // BANKNIFTY
		
		String year = exp.substring(exp.length() - 4); // 2021
		String dayMonth = exp.replace(year, StringUtils.EMPTY);
		String day = dayMonth.substring(0, 2);
		String mnth = dayMonth.substring(2);
		year = year.substring(2);
		if(isMonthly)
			return year+mnth;
		return year+mnth.charAt(0)+day;
	}
	
	/**
	 * NIFTY21NOV16800PE = PE = NIFTY18NOV202118400CE = CE
	 * @param tradingSymbol
	 * @return
	 */
	public static String getOpstraOptionType(String tradingSymbol) {
		return tradingSymbol.substring(tradingSymbol.length()-2);
	}
	
	
	/**
	 * NIFTY21NOV16800PE = 16800 = NIFTY18NOV2118400CE
	 * @param tradingSymbol
	 * @return
	 */
	public static String getOpstraStrikePrice(String tradingSymbol) {
		String optType = getOpstraOptionType(tradingSymbol);
		tradingSymbol = tradingSymbol.replace(optType, StringUtils.EMPTY); // NIFTY18NOV2118400
		String strikePrice = tradingSymbol.substring(tradingSymbol.length()-5);
		return strikePrice;
	}
	
	/**
	 * NIFTY21NOV16800PE = 21NOV/21N03/21N18 = NIFTY18NOV202118400CE = 18NOV2021/25NOV2021
	 * @param tradingSymbol "BANKNIFTY21D0235700PE"
	 * @return
	 */
	public static String getOpstraExpiry(String tradingSymbol) {
		tradingSymbol = tradingSymbol
					.replace(getOpstraOptionType(tradingSymbol), StringUtils.EMPTY)
					.replace(getOpstraStrikePrice(tradingSymbol), StringUtils.EMPTY); // NIFTY18NOV2021
		String opstExpiry = tradingSymbol.substring(tradingSymbol.length() - 9); //BANKNIFTY21D02
		return opstExpiry;
	}
	
	public static String grToOpstraExpiry(String tradingSymbol) {
		tradingSymbol = tradingSymbol
					.replace(getOpstraOptionType(tradingSymbol), StringUtils.EMPTY)
					.replace(getOpstraStrikePrice(tradingSymbol), StringUtils.EMPTY); // NIFTY18NOV2021
		String opstExpiry = tradingSymbol.substring(tradingSymbol.length() - 5); //21D02
		String dt = opstExpiry.substring(opstExpiry.length() - 2); // 02
		String tmp = opstExpiry.replace(dt, StringUtils.EMPTY); // 21D
		String mnth = tmp.substring(tmp.length() - 1); // D
		String yr = tmp.replace(mnth, StringUtils.EMPTY); // 21
		yr = "20"+yr;
		if(mnth.equals("D")) {
			mnth = "DEC";
		}
		opstExpiry = dt+mnth+yr;
		return opstExpiry;
	}
	
	// input: NIFTY18NOV202118400CE: output: 18N21
	public static String opstraToGrExpiry(String tradingSymbol) {
		tradingSymbol = tradingSymbol
					.replace(getOpstraOptionType(tradingSymbol), StringUtils.EMPTY)
					.replace(getOpstraStrikePrice(tradingSymbol), StringUtils.EMPTY); // NIFTY18NOV2021
		String opstExpiry = tradingSymbol.substring(tradingSymbol.length() - 9); //18NOV2021
		String yr = opstExpiry.substring(opstExpiry.length() - 4); // 2021
		String tmp = opstExpiry.replace(yr, StringUtils.EMPTY); // 18NOV
		String mnth = tmp.substring(tmp.length() - 3); // NOV
		String dt = tmp.replace(mnth, StringUtils.EMPTY); // 18
		
		yr = yr.substring(yr.length() - 2); // 21
		if(mnth.equals("DEC")) {
			mnth = "D";
		}
		opstExpiry = yr+mnth+dt;
		return opstExpiry;
	}
	
	/**
	 * NIFTY21NOV16800PE = NIFTY = NIFTY18NOV202118400CE
	 * @param tradingSymbol
	 * @return
	 */
	public static String getOpstraSymbol(String tradingSymbol) {
		tradingSymbol = tradingSymbol
				.replace(getOpstraExpiry(tradingSymbol), StringUtils.EMPTY)
				.replace(getOpstraOptionType(tradingSymbol), StringUtils.EMPTY)
				.replace(getOpstraStrikePrice(tradingSymbol), StringUtils.EMPTY); // NIFTY18NOV2021
		return tradingSymbol;
	}
	
	
	public static String getNearestTradingSymbolAtNPrice(Double priceNear, Map<String, AlogLtpData> ltps, double d) {
		for(Entry<String, AlogLtpData> e: ltps.entrySet()) {
			System.out.println("Symbol: "+e.getKey()+": "+e.getValue().getLastPrice());
			if(e.getValue().getLastPrice() >= (priceNear-d) && e.getValue().getLastPrice() <= (priceNear+d)) {
				return e.getKey();
			}
		}
		return null;
	}
	
	
	/**
	 * NIFTY21NOV16800PE = 16800
	 * @param tradingSymbol
	 * @return
	 */
	public static String getAngelStrikePrice(String tradingSymbol) {
		String strikePrice = tradingSymbol
				.replace(getAngelSymbol(tradingSymbol), StringUtils.EMPTY)
				.replace(getAngelExpiry(tradingSymbol), StringUtils.EMPTY)
				.replace(Constants.CE, StringUtils.EMPTY)
				.replace(Constants.PE, StringUtils.EMPTY);
		return strikePrice;
	}

	/**
	 * NIFTY21NOV16800PE = PE
	 * @param tradingSymbol
	 * @return
	 */
	public static String getAngelOptionType(String tradingSymbol) {
//		String opstSymbol = tradingSymbol.substring(0, tradingSymbol.length() - 12);
//		String opstExpiry = DateUtils.angelToOpstra.get(tradingSymbol.substring(opstSymbol.length(), tradingSymbol.length() - 7));
//		String strikePrice = tradingSymbol.replace(opstSymbol, StringUtils.EMPTY).replace(opstExpiry, StringUtils.EMPTY).replace(Constants.CE, StringUtils.EMPTY).replace(Constants.PE, StringUtils.EMPTY);
//		String optionType = tradingSymbol.replace(opstSymbol, StringUtils.EMPTY).replace(opstExpiry, StringUtils.EMPTY).replace(strikePrice, StringUtils.EMPTY);
//		return optionType;
		return null;
	}

	/**
	 * NIFTY21NOV16800PE = NIFTY
	 * @param tradingSymbol
	 * @return
	 */
	public static String getAngelSymbol(String tradingSymbol) {
		String opstSymbol = tradingSymbol.substring(0, tradingSymbol.length() - 12);
		return opstSymbol;
	}

	/**
	 * NIFTY21NOV16800PE = 21NOV/21N03/21N18
	 * @param tradingSymbol
	 * @return
	 */
	public static String getAngelExpiry(String tradingSymbol) {
		String opstSymbol = tradingSymbol.substring(0, tradingSymbol.length() - 12);
		String opstExpiry = tradingSymbol.substring(opstSymbol.length(), tradingSymbol.length() - 7);
		return opstExpiry;
	}

}
