package com.stockapp.stockserver.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import com.stockapp.stockserver.repo.StockDataTurnRepository;

@Service
@EnableAsync
public class StockDataTurnService extends AbstractService {
	// 初始化 SLF4J Logger
    private static final Logger logger = LoggerFactory.getLogger(StockDataTurnService.class);

	@Autowired
	StockDataTurnRepository stockDataTurnRepository;
	
	
}
