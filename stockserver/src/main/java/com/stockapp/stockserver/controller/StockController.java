package com.stockapp.stockserver.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.stockapp.stockserver.controller.bean.StockInfo;
import com.stockapp.stockserver.entity.Stocks;
import com.stockapp.stockserver.service.StockService;


@RequestMapping("/stock")
@RestController
@EnableAsync
@EnableScheduling
public class StockController extends AbstractController {
	// 初始化 SLF4J Logger
    private static final Logger logger = LoggerFactory.getLogger(StockController.class);

    @Autowired
    private StockService stockService; 

    /**
     * 多空轉折戰k圖
     * 
     * @param stockId
     * @param dataType
     * @param dataCount
     * @param updateData
     * @param getWants
     * @return
     */
	@RequestMapping(value = "/queryStockTurnDataK", method = RequestMethod.GET)
	public @ResponseBody Map<String, Object> queryStockTurnDataK(@RequestParam(value = "si") String stockId,
			@RequestParam(value = "dt") String dataType, 
			@RequestParam(value = "dn", required = false, defaultValue = "20") Integer dataCount) {
		return stockService.queryStockTurnDataK(stockId, dataType, dataCount);
	}

	/**
	 * 依據資料筆數 代碼 資料型別回傳指定的資料 for 蘭姐xcel 只抓取開高低收 為了excel方便使用，不直接回傳data list
	 */
	@RequestMapping(value = "/queryStockData2", method = RequestMethod.GET)
	public @ResponseBody String queryStockDataForExcel(@RequestParam(value = "si") String stockId,
			@RequestParam(value = "dt") String dataType, @RequestParam(value = "dn") Integer dataCount) {
		return stockService.queryStockDataForExcel(stockId, dataType, dataCount);
	}
	
	/**
	 * 查詢指定個股的多空轉折資料 資料可以預測隔天，所以回傳的資料會多一個"明天"，
	 * 但是抓出的StockData的資料，最舊3筆會被忽略
	 * 
	 * @param stockId
	 * @param dataType
	 * @param dataCount
	 * @param updateData
	 * @param getWants
	 * @return
	 */
	@RequestMapping(value = "/queryStockTurnData", method = RequestMethod.GET)
	public @ResponseBody Map<String, Object> queryStockTurnData(@RequestParam(value = "si") String stockId,
			@RequestParam(value = "dt") String dataType, @RequestParam(value = "dn") Integer dataCount,
			@RequestParam(value = "up", required = false, defaultValue = "1") String updateData,
			@RequestParam(value = "gw", required = false, defaultValue = "1") String getWants) {
		return stockService.queryStockTurnData(stockId, dataType, dataCount);
	}
	
	/**
	 * 抓出股票資料列表
	 */
	@RequestMapping(value = "/findStocks", method = RequestMethod.GET)
	public @ResponseBody List<Stocks> findStocks() {
		return stockService.findStocks();
	}
	
	/**
	 * 抓出股票資料
	 */
	@RequestMapping(value = "/findStockInfo", method = RequestMethod.GET)
	public @ResponseBody StockInfo findStockInfo(@RequestParam(value = "si", required = false) String stockId) {
		return stockService.findStockInfo(stockId);
	}
	
	@RequestMapping(value = "/updateStockData", method = RequestMethod.GET)
	public void updateStockData(@RequestParam(value = "dc", required = false, defaultValue = "100") Integer dataCount,
			@RequestParam(value = "si", required = false) String stockId,
			@RequestParam(value = "dataType", required = false) String dataType,
			@RequestParam(value = "startid", required = false) String startId,
			@RequestParam(value = "endid", required = false) String endId,
			@RequestParam(value = "beforedate", required = false) String beforedate) {
		stockService.updateStockData(dataCount, stockId, dataType, startId, endId);
	}
	
	
	/**
	 * 每週六下午自動執行
	 * 
	 */
	@Scheduled(cron = "0 10 13 ? * 6")
	public void syncStockData() {
		stockService.syncStockData();
	}
}
