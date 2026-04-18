package com.stockapp.stockserver.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import com.stockapp.stockserver.service.StockCommonService;
import com.stockapp.stockserver.service.StockService;

@Controller
public class AbstractController {
	@Autowired
	private StockCommonService stockCommonService;
	@Autowired
	private StockService stockService;
}
