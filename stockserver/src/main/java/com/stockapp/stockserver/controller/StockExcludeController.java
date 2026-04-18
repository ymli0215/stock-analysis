package com.stockapp.stockserver.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.stockapp.stockserver.entity.StockExclude;
import com.stockapp.stockserver.service.StockExcludeService;


@RequestMapping("/stockExclude")
@RestController
@EnableAsync
@EnableScheduling
public class StockExcludeController extends AbstractController {
	// 初始化 SLF4J Logger
    private static final Logger logger = LoggerFactory.getLogger(StockExcludeController.class);

    @Autowired
    private StockExcludeService stockExcludeService; 
    
    /**
	 * 針對當天沒資料的再跑一次
	 * @param stockId 可傳遞指定代碼進行更新
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	@Scheduled(cron = "0 30 20 * * ?")
	public void updateStockExcluddeTask() {
		try {
			updateStockExcludde();
		}
		catch(Exception e) {
			logger.error("{}", e);
		}
	}
	
	/**
	 * 查詢指定條件的除權息資料
	 * @param stockId
	 * @return
	 */
	@RequestMapping(value="/queryExcludeData", method = RequestMethod.GET)
	public @ResponseBody List<StockExclude> queryExcludeData(@RequestParam(value="si",required=false)String stockId) {
		return stockExcludeService.queryExcludeData(stockId);
	}
	

	@RequestMapping(value="/updateStockExclude", method = RequestMethod.GET)
	public void updateStockExcludde() {
		stockExcludeService.updateStockExcludde();
	}

}
