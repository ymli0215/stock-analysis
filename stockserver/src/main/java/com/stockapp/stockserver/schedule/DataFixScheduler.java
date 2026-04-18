package com.stockapp.stockserver.schedule;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.stockapp.stockserver.entity.StockData;
import com.stockapp.stockserver.repo.StockDataRepository;

@Component
public class DataFixScheduler {
	// 初始化 SLF4J Logger
    private static final Logger logger = LoggerFactory.getLogger(DataFixScheduler.class);

	private final StockDataRepository repo;

    // 透過建構子注入 Repository
    public DataFixScheduler(StockDataRepository repo) {
        this.repo = repo;
    }
    
	@Scheduled(cron = "0 0 1 * * ?") // 每天凌晨1點執行
    public void fixDataSchedule() {
        List<StockData> records = repo.findByDataDateHourEight();
		logger.info("需要修正資料，共發現 " + records.size() + " 筆記錄。");
        for (StockData record : records) {
        	LocalDateTime oldDate = record.getDataDate();
        	LocalDateTime newDate = oldDate.withHour(8).withMinute(0).withSecond(0).withNano(0);
            
            record.setDataDate(newDate);
            record.getId().setDataTime(newDate.atZone(ZoneId.of("Asia/Taipei")).toInstant().toEpochMilli());
        }
        repo.saveAll(records);
        logger.info("資料修正完成，共處理 " + records.size() + " 筆記錄。");
    }
}
