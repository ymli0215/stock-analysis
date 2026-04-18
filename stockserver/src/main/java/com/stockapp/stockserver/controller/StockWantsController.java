package com.stockapp.stockserver.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.stockapp.stockserver.entity.StockWants;
import com.stockapp.stockserver.service.StockWantsService;


@RequestMapping("/stockwants")
@RestController
@EnableAsync
@EnableScheduling
public class StockWantsController extends AbstractController {
	// 初始化 SLF4J Logger
    private static final Logger logger = LoggerFactory.getLogger(StockWantsController.class);

    @Autowired
    private StockWantsService stockWantsService; 

    /**
     * 查詢權證資料
     * 
     * @param warrantCode
     * 
     * @return
     */
	@RequestMapping(value = "/queryStockwants", method = RequestMethod.GET)
	public @ResponseBody StockWants queryStockTurnDataK(@RequestParam(value = "warrantCode") String warrantCode) {
		return stockWantsService.queryStockwants(warrantCode);
	}

	@RequestMapping(value = "/importWarrant", method = RequestMethod.GET)
	public @ResponseBody String importFromCsv() {
		stockWantsService.importFromCsv();
		
		return "success";
	}
}
