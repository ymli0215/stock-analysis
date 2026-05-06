package com.stockapp.stockserver.schedule;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.stockapp.stockserver.entity.Stocks;
import com.stockapp.stockserver.repo.StocksRepository;
import com.stockapp.stockserver.service.JpDataFetchService;
import com.stockapp.stockserver.service.StockCommonService;

@Component
public class JpStockScheduler {

    private static final Logger logger = LoggerFactory.getLogger(JpStockScheduler.class);

    private final StocksRepository stocksRepository;
    private final JpDataFetchService jpDataFetchService;
    private final StockCommonService stockCommonService;

    public JpStockScheduler(StocksRepository stocksRepository,
                             JpDataFetchService jpDataFetchService,
                             StockCommonService stockCommonService) {
        this.stocksRepository = stocksRepository;
        this.jpDataFetchService = jpDataFetchService;
        this.stockCommonService = stockCommonService;
    }

    /**
     * 日股收盤後自動更新，每天 15:00 台灣時間（週一到週五）。
     * 日本股市 15:30 JST = 14:30 台灣時間收盤，延後 30 分鐘確保資料就緒。
     * 依序：補資料 → 重算 MA → 重算轉折值。
     */
    @Scheduled(cron = "0 0 15 * * MON-FRI", zone = "Asia/Taipei")
    public void syncJpStocks() {
        List<Stocks> jpStocks = stocksRepository.findByMarket("JP");
        logger.info("JP daily sync start — {} stocks", jpStocks.size());

        for (Stocks stock : jpStocks) {
            String stockId = stock.getStockId();
            try {
                jpDataFetchService.ensureData(stockId, "D");
                stockCommonService.updateDataMAHL(stockId, "D", 300).get();
                stockCommonService.updateDataTurn(stockId, "D", 300).get();
                jpDataFetchService.fetchFundamental(stockId);
                logger.info("JP sync done: {}", stockId);
            } catch (Exception e) {
                logger.error("JP sync failed for {}: {}", stockId, e.getMessage());
            }
        }

        logger.info("JP daily sync complete");
    }
}
