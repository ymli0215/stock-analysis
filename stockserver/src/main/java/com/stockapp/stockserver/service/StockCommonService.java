package com.stockapp.stockserver.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import com.stockapp.stockserver.entity.StockData;
import com.stockapp.stockserver.entity.StockDataId;
import com.stockapp.stockserver.entity.StockDataMA;
import com.stockapp.stockserver.entity.StockDataTurn;
import com.stockapp.stockserver.entity.StockPriceLevel;
import com.stockapp.stockserver.entity.Stocks;
import com.stockapp.stockserver.enums.StockDataTypeType;
import com.stockapp.stockserver.model.CallbackResult;
import com.stockapp.stockserver.repo.StockDataMARepository;
import com.stockapp.stockserver.repo.StockDataRepository;
import com.stockapp.stockserver.repo.StockDataTurnRepository;
import com.stockapp.stockserver.repo.StockGapRepository;
import com.stockapp.stockserver.repo.StockPriceLevelRepository;
import com.stockapp.stockserver.utils.BeanUtils;
import com.stockapp.stockserver.utils.NumberUtils;

@Service
@EnableAsync
public class StockCommonService extends AbstractService {
	// 初始化 SLF4J Logger
    private static final Logger logger = LoggerFactory.getLogger(StockCommonService.class);
    
    @Autowired
    private StockDataRepository stockDataRepository;
    @Autowired
    private StockDataTurnRepository stockDataTurnRepository;
    @Autowired
    private StockDataMARepository stockDataMARepository;
    @Autowired
    private StockPriceLevelRepository stockPriceLevelRepository;
    @Autowired
    private StockGapRepository stockGapRepository;
    
    /**
     * 更新特定代碼股票資料
     * 
     * @param stockId
     */
    @Async("taskExecutor")
	public Future<CallbackResult> updateStockData(String stockId, StockDataTypeType dataType, int dataCount) {
    	CallbackResult cr = new CallbackResult();
		cr.setStockId(stockId);
		cr.setDataType(dataType.getCode());

    	Optional<Stocks> optionalStocks = stocksRepository.findById(stockId);
    	if(optionalStocks.isEmpty()) {
    		logger.warn("Stocks:{} 不存在", stockId);
    		cr.setResult(true);
    		return CompletableFuture.completedFuture(cr);
    	}
    	
    	if(dataCount < 200) {
    		dataCount = dataCount + 20;
    	}
		
		try {
			List<StockData> datas = new ArrayList<>();
			if (stockId.equals("0000")) {
				//大盤
				datas = syncStockData(stockId, dataType, dataCount);
			} else if (stockId.equals("FITX")) {
				//datas = syncFITXData(stockId, dataType, dataCount);
				datas = syncStockData(stockId, dataType, dataCount);
			} else if (stockId.equals("FITXP")) {
				//datas = syncFITXData(stockId, dataType, dataCount);
				datas = syncStockData(stockId, dataType, dataCount);
			} 
//			else if (StockTypeType.find(stocks.getStockType()).getCode()
//					.equalsIgnoreCase(StockTypeType.GLOBAL.getCode())) {
//				commonService.syncGlobalStockHistock(stockId).get();
//			} 
			else {
				//個股，包含美股
				datas = syncStockData(stockId, dataType, dataCount);
			}

			cr.setResult(true);
		}
		catch (Exception e) {
            logger.error("儲存股票資料錯誤: {}", e);
    		cr.setResult(false);
        }
		
		return CompletableFuture.completedFuture(cr);
    }
    
    @Async("taskExecutor")
	public CompletableFuture<CallbackResult> updateDataTurn(String stockId, String dataType, Integer dataCount) {
		CallbackResult cr = new CallbackResult();
		cr.setStockId(stockId);
		cr.setDataType(dataType);
		
		try {
			List<StockDataTurn> mas = new ArrayList<StockDataTurn>();

			//資料由新到舊
			List<StockData> datas = null;
			if(dataCount != null) {
				datas = findStockData(stockId, dataType, dataCount+240);				
			}
			else {
				datas = findStockData(stockId, dataType);
			}

			for (int i = 0; i < datas.size(); i++) {
				StockData data = datas.get(i);
				StockDataId id = data.getId();
				
				StockDataTurn ma;
				Optional<StockDataTurn> turn = stockDataTurnRepository.findById(id);
				if (turn.isEmpty()) {
					ma = new StockDataTurn();
					ma.setId(id);
				}
				else {
					ma = turn.get();
				}

				if(i<11) {
					mas.add(ma);
					continue;
				}
				
				// DD開盤破站不上空
				Double level9 = 0D;
				for (int j = 0; j < 4; j++) {
					StockData data4 = datas.get(i - j);
	
					level9 += data4.getOpen() + data4.getHigh()
							+ data4.getLow() + data4.getClose();
				}
				ma.setValue1(NumberUtils.round(level9 / 16D, "##.00"));
	
				//高控多停利
				double high11 = 0D;
				double low11 = 99999D;
				for (int j = i-11; j < i; j++) {
					StockData data2 = datas.get(j);
					if (data2.getHigh() >= high11) {
						high11 = data2.getHigh();
					}
	
					if (data2.getLow() <= low11) {
						low11 = data2.getLow();
					}
				}
				ma.setValue5(NumberUtils.round((high11*2 + low11)/3, "##.00"));
				

				if(i<26) {
					mas.add(ma);
					continue;
				}
				
				//易多空線
				ma.setValue2(NumberUtils.round((ma.getValue5() + mas.get(i-1).getValue5() + mas.get(i-2).getValue5())/3, "##.00"));
				
				//超強
				Double value = 0D;
				for (int j = i-19; j <= i; j++) {
					StockData data2 = datas.get(j);
					value += data2.getClose() + data2.getClose() + data2.getLow();
				}
				ma.setValue3(NumberUtils.round(value*1.0618/60, "##.00"));
				
				//乖離短賣
				ma.setValue4(NumberUtils.round(value*1.0874/60, "##.00"));
				
				//低控空回補
				ma.setValue6(NumberUtils.round((high11 + low11*2)/3, "##.00"));
				
				double high12 = 0D;
				double low12 = 99999D;
				for (int j = i-12; j < i; j++) {
					StockData data2 = datas.get(j);
					if (data2.getHigh() >= high12) {
						high12 = data2.getHigh();
					}
	
					if (data2.getLow() <= low12) {
						low12 = data2.getLow();
					}
				}
				//嘎空點
				ma.setValue7(NumberUtils.round((high12-low12)*0.84+low12, "##.00"));
				
				//D殺多
				ma.setValue8(NumberUtils.round((high12-low12)*0.16+low12, "##.00"));

				double high_avg6 = 0D;
				for (int j = i-5; j <= i; j++) {
					StockData data2 = datas.get(j);
					high_avg6 += data2.getHigh();
				}
				high_avg6 = NumberUtils.round((high_avg6)/6, "##.00");

				double low_avg13 = 0D;
				for (int j = i-12; j <= i; j++) {
					StockData data2 = datas.get(j);
					low_avg13 += data2.getLow();
				}
				low_avg13 = NumberUtils.round((low_avg13)/13, "##.00");

				double high_avg26 = 0D;
				for (int j = i-25; j <= i; j++) {
					StockData data2 = datas.get(j);
					high_avg26 += data2.getHigh();
				}
				high_avg26 = NumberUtils.round((high_avg26)/26, "##.00");
				
				//日多空中軸
				ma.setMiddle(NumberUtils.round((high_avg6 + low_avg13 + high_avg26)/3, "##.00"));
				
				//末跌
				ma.setValue9(NumberUtils.round(ma.getMiddle()*(1-0.0764), "##.00"));
				
				//日續跌
				ma.setValue10(NumberUtils.round(ma.getMiddle()*(1-0.0618), "##.00"));
				
				//破續跌續空
				ma.setValue11(NumberUtils.round(ma.getMiddle()*(1-0.05), "##.00"));
				
				//超跌17
				ma.setValue29(NumberUtils.round(ma.getMiddle()*(1-0.123), "##.00"));
				
				//超跌16
				ma.setValue30(NumberUtils.round(ma.getMiddle()*(1-0.0989), "##.00"));
				
				//超跌15
				ma.setValue31(NumberUtils.round(ma.getMiddle()*(1-0.088), "##.00"));
				
				//碰主跌=搶短點
				ma.setValue12(NumberUtils.round(ma.getMiddle()*(1-0.0382), "##.00"));
				
				//碰主跌3
				ma.setValue14(NumberUtils.round(ma.getMiddle()*(1-(0.0236+0.0382)/2), "##.00"));
				
				//彈仍跌(放空)
				ma.setValue15(NumberUtils.round(ma.getMiddle()*(1-0.0236), "##.00"));
				
				//回跌
				ma.setValue16(NumberUtils.round(ma.getMiddle()*(1-0.0191), "##.00"));
				
				//回檔買點/反彈賣點
				ma.setValue17(NumberUtils.round(ma.getMiddle()*(1-0.0146), "##.00"));
				
				//盤跌
				ma.setValue19(NumberUtils.round(ma.getMiddle()*(1-0.0087446), "##.00"));
				
				//T盤漲
				ma.setValue20(NumberUtils.round(ma.getMiddle()*1.0087446, "##.00"));
				
				//反彈峰B
				ma.setValue21(NumberUtils.round(ma.getMiddle()*1.01236, "##.00"));
				
				//續漲-不破續多
				ma.setValue22(NumberUtils.round(ma.getMiddle()*1.0236, "##.00"));
				
				//過起漲3
				ma.setValue23(NumberUtils.round(ma.getMiddle()*(1+(0.0382+0.0236)/2), "##.00"));
				
				//過起漲
				ma.setValue24(NumberUtils.round(ma.getMiddle()*1.0382, "##.00"));
				
				//日續漲
				ma.setValue25(NumberUtils.round(ma.getMiddle()*1.05, "##.00"));
				
				//主升
				ma.setValue26(NumberUtils.round(ma.getMiddle()*1.0618, "##.00"));
				
				//強波2000點
				ma.setValue27(NumberUtils.round(ma.getMiddle()*1.0764, "##.00"));

				mas.add(ma);
			}

			stockDataTurnRepository.saveAll(mas);
		} catch (Exception e) {
			logger.error("fail : {}", e);
		}
		cr.setResult(true);
		return CompletableFuture.completedFuture(cr);
	}
    
    /**
     * 更新股票MA資料
     * 
     * @param stockId
     * @param dataType
     * @param dataCount
     * @return
     */
    @Async("taskExecutor")
	public Future<CallbackResult> updateDataMAHL(String stockId, String dataType, Integer dataCount) {
		CallbackResult cr = new CallbackResult();
		cr.setStockId(stockId);
		cr.setDataType(dataType);
		
		try {
			Map<String, Object> map = new HashMap<>();
			List<StockDataMA> mas = new ArrayList<>();
	
			//資料由新到舊
			int startIndex = 2;
			List<StockData> datas = null;
			if(dataCount != null) {
				//多取出來好計算超過233均線
				datas = findStockData(stockId, dataType, dataCount+240);
				//因為要算233均線，所以資料超過240筆，從240開始
				startIndex = 240;
			}
			else {
				datas = findStockData(stockId, dataType);
			}

			for (int i = startIndex; i < datas.size(); i++) {
				StockData data = datas.get(i);
				StockDataId id = data.getId();
				
				StockDataMA ma;
				Optional<StockDataMA> maOptional = stockDataMARepository.findById(id);
				if (maOptional.isEmpty()) {
					ma = new StockDataMA();
					ma.setId(id);
	
					ma.setDataDate(new Date(id.getDataTime()));
				}
				else {
					ma = maOptional.get();
				}
				
				Double ma3 = 0D;
				if (i>=2) {
					for (int j = i - 2; j <= i; j++) {
						StockData data2 = datas.get(j);
						ma3 += data2.getClose();
					}
				}
				ma3 = Math.round(ma3 * 100D / 3D) / 100D;
	
				Double ma5 = 0D;
				if (i>=4) {
					for (int j = i - 4; j <= i; j++) {
						StockData data2 = datas.get(j);
						ma5 += data2.getClose();
					}
				}
				ma5 = Math.round(ma5 * 100D / 5D) / 100D;
	
				Double ma8 = 0D;
				if (i>=7) {
					for (int j = i-7; j <= i; j++) {
						StockData data2 = datas.get(j);
						ma8 += data2.getClose();
					}
				}
				ma8 = Math.round(ma8 * 100D / 8D) / 100D;
	
				Double ma10 = 0D;
				if (i >= 9) {
					for (int j = i-9; j <= i; j++) {
						StockData data2 = datas.get(j);
						ma10 += data2.getClose();
					}
				}
				ma10 = Math.round(ma10 * 100D / 10D) / 100D;
	
				Double ma13 = 0D;
				if (i >=  12) {
					for (int j = i-12; j <= i; j++) {
						StockData data2 = datas.get(j);
						ma13 += data2.getClose();
					}
				}
				ma13 = Math.round(ma13 * 100D / 13D) / 100D;
	
				Double ma20 = 0D;
				if (i >=  19) {
					for (int j = i-19; j <= i; j++) {
						StockData data2 = datas.get(j);
						ma20 += data2.getClose();
					}
				}
				ma20 = Math.round(ma20 * 100D / 20D) / 100D;
	
				Double ma21 = 0D;
				if (i >=  20) {
					for (int j = i-20; j <= i; j++) {
						StockData data2 = datas.get(j);
						ma21 += data2.getClose();
					}
				}
				ma21 = Math.round(ma21 * 100D / 21D) / 100D;
	
				int index = 34;
				Double ma34 = 0D;
				if (i >= (index-1)) {
					for (int j = i-(index-1); j <= i; j++) {
						StockData data2 = datas.get(j);
						ma34 += data2.getClose();
					}
				}
				ma34 = Math.round(ma34 * 100D / Double.valueOf(index)) / 100D;
	
				ma.setMa3(ma3);
				ma.setMa5(ma5);
				ma.setMa8(ma8);
				ma.setMa10(ma10);
				ma.setMa20(ma20);
				ma.setMa13(ma13);
				ma.setMa21(ma21);
				ma.setMa34(ma34);
				
				index = 55;
				Double maValue = 0D;
				if (i >= (index-1)) {
					for (int j = i-(index-1); j <= i; j++) {
						StockData data2 = datas.get(j);
						maValue += data2.getClose();
					}
				}
				maValue = Math.round(maValue * 100D / Double.valueOf(index)) / 100D;
				ma.setMa55(maValue);
				
				index = 60;
				maValue = 0D;
				if (i >= (index-1)) {
					for (int j = i-(index-1); j <= i; j++) {
						StockData data2 = datas.get(j);
						maValue += data2.getClose();
					}
				}
				maValue = Math.round(maValue * 100D / Double.valueOf(index)) / 100D;
				ma.setMa60(maValue);
				
				index = 89;
				maValue = 0D;
				if (i >= (index-1)) {
					for (int j = i-(index-1); j <= i; j++) {
						StockData data2 = datas.get(j);
						maValue += data2.getClose();
					}
				}
				maValue = Math.round(maValue * 100D / Double.valueOf(index)) / 100D;
				ma.setMa89(maValue);
				
				index = 144;
				maValue = 0D;
				if (i >= (index-1)) {
					for (int j = i-(index-1); j <= i; j++) {
						StockData data2 = datas.get(j);
						maValue += data2.getClose();
					}
				}
				maValue = Math.round(maValue * 100D / Double.valueOf(index)) / 100D;
				ma.setMa144(maValue);
				
				index = 233;
				maValue = 0D;
				if (i >= (index-1)) {
					for (int j = i-(index-1); j <= i; j++) {
						StockData data2 = datas.get(j);
						maValue += data2.getClose();
					}
				}
				maValue = Math.round(maValue * 100D / Double.valueOf(index)) / 100D;
				ma.setMa233(maValue);
	
				mas.add(ma);
			}
	
			stockDataMARepository.saveAll(mas);
		} catch (Exception e) {
			logger.error("fail : {}", e);
		}
		cr.setResult(true);
		return CompletableFuture.completedFuture(cr);
	}
    
    @Async("taskExecutor")
    public Future<CallbackResult> updateDataLevel(String stockId, String dataType, Integer dataCount) {
        CallbackResult cr = new CallbackResult();
        cr.setStockId(stockId);
        cr.setDataType(dataType);

        List<StockPriceLevel> mas = new ArrayList<>();
        try {
            // 資料由新到舊
            int startIndex = 2;
            List<StockData> datas = null;
            if (dataCount != null) {
                datas = findStockData(stockId, dataType, dataCount + 13);
                // 轉折要13筆以上
                startIndex = 13;
            } else {
                datas = findStockData(stockId, dataType);
            }

            // 拿最新的一筆複製成往後的三筆資料
            LocalDateTime baseDate = datas.get(datas.size() - 1).getDataDate();
            // 最新的一筆
            StockData lastestData = datas.get(datas.size() - 1);
            for (int i = 0; i < 3; i++) {
                StockData d = new StockData();
                StockDataId id =  new StockDataId();
                BeanUtils.copyProperties(id, lastestData.getId());
                BeanUtils.copyProperties(d, lastestData, "dateString");
                LocalDateTime newDate;
                if (dataType.equals("D")) {
                    newDate = baseDate.plusDays(i + 1);
                } else if (dataType.equals("W")) {
                    newDate = baseDate.plusDays(7 * (i + 1));
                } else if (dataType.equals("M")) {
                    newDate = baseDate.plusMonths(i + 1);
                } else {
                    newDate = baseDate.plusDays(i + 1); // 預設按日
                }
                id.setDataTime(newDate.atZone(ZoneId.of("Asia/Taipei")).toInstant().toEpochMilli());
                d.setDataDate(newDate);
                d.setId(id);
                datas.add(d);
            }

            for (int i = startIndex; i < datas.size(); i++) {
                StockData data = datas.get(i);
                StockDataId id = data.getId();

                StockPriceLevel ma;
                Optional<StockPriceLevel> maOptional = stockPriceLevelRepository.findById(id);
                if (maOptional.isEmpty()) {
                    ma = new StockPriceLevel();
                    ma.setId(id);
                    ma.setDataDate(LocalDateTime.ofEpochSecond(id.getDataTime() / 1000, 0, ZoneId.of("Asia/Taipei").getRules().getOffset(LocalDateTime.now())));
                } else {
                    ma = maOptional.get();
                }

                // 計算轉折
                if (i >= 11) {
                    // h12 l12
                    Double high = 0D;
                    Double low = 99999D;
                    for (int j = i - 11; j <= i; j++) {
                        StockData data2 = datas.get(j);
                        if (data2.getHigh() >= high) {
                            high = data2.getHigh();
                        }
                        if (data2.getLow() <= low) {
                            low = data2.getLow();
                        }
                    }
                    ma.setH12(high);
                    ma.setL12(low);
                }

                // 第13筆之後才能計算轉折價
                if (i >= 12 && (i - 1 - startIndex) >= 0) {
                    // 直接抓取前一組ma
                    StockPriceLevel lastma = mas.get(i - 1 - startIndex);

                    // 箱幅
                    Double box = ma.getH12() - ma.getL12();
                    Double box2 = lastma.getH12() - lastma.getL12();

                    // 買耗
                    ma.setLevel1(NumberUtils.round(lastma.getH12() + box2 * 0.2, "##.00"));
                    // 賣耗
                    ma.setLevel8(NumberUtils.round(lastma.getL12() - box2 * 0.2, "##.00"));
                    // 軋空
                    ma.setLevel2(NumberUtils.round(ma.getL12() + box * 0.84, "##.00"));
                    // 轉強
                    ma.setLevel3(NumberUtils.round(ma.getL12() + box * 0.666, "##.00"));
                    // 中軸
                    ma.setLevel4(NumberUtils.round(ma.getL12() + box * 0.555, "##.00"));
                    // 回撐
                    ma.setLevel5(NumberUtils.round(ma.getL12() + box * 0.444, "##.00"));
                    // 轉弱
                    ma.setLevel6(NumberUtils.round(ma.getL12() + box * 0.333, "##.00"));
                    // 殺多
                    ma.setLevel7(NumberUtils.round(ma.getL12() + box * 0.16, "##.00"));
                    // 多空線
                    Double level9 = 0D;
                    Double averO = 0D;
                    Double averH = 0D;
                    Double averL = 0D;
                    Double averC = 0D;
                    for (int j = 0; j < 4; j++) {
                        StockData data4 = datas.get(i - j);
                        level9 += data4.getOpen() + data4.getHigh() + data4.getLow() + data4.getClose();
                        averO += data4.getOpen();
                        averH += data4.getHigh();
                        averL += data4.getLow();
                        averC += data4.getClose();
                    }
                    double aver = Math.round(averO / 4D) + Math.round(averO / 4D) + Math.round(averO / 4D) + Math.round(averO / 4D);
                    ma.setLevel9(NumberUtils.round(level9 / 16D, "##.00"));

                    // 轉折價 2*三天前收-六天前收
                    double turnPrice = NumberUtils.round(2 * datas.get(i - 3).getClose() - datas.get(i - 6).getClose(), "##.00");
                    ma.setTurn(turnPrice);
                }

                mas.add(ma);
            }

            stockPriceLevelRepository.saveAll(mas);
        } catch (Exception e) {
            logger.error("fail : ", e);
            if (e instanceof ConstraintViolationException) {
                logger.error("=================================");
                for (StockPriceLevel ma : mas) {
                    logger.error("StockPriceLevel id : {}", ma.getId());
                }
                logger.error("=================================");
            }
        }
        cr.setResult(true);
        return CompletableFuture.completedFuture(cr);
    }
}
