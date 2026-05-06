package com.stockapp.stockserver.controller;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stockapp.stockserver.entity.StockPageRaw;
import com.stockapp.stockserver.repo.StockPageRawRepository;

@RestController
@RequestMapping("/stockPageRaw")
public class StockPageRawController extends AbstractController {

	private static final Logger logger = LoggerFactory.getLogger(StockPageRawController.class);

	@Autowired
	private StockPageRawRepository stockPageRawRepository;

	@PostMapping("/save")
	public ResponseEntity<Map<String, Object>> save(@RequestBody StockPageRaw request) {
		Map<String, Object> result = new HashMap<>();
		try {
			request.setAiStatus(0);
			StockPageRaw saved = stockPageRawRepository.save(request);
			result.put("status", "ok");
			result.put("id", saved.getId());
			logger.info("StockPageRaw saved id={} source={}", saved.getId(), saved.getSource());
		} catch (Exception e) {
			logger.error("StockPageRaw save error", e);
			result.put("status", "error");
			result.put("message", e.getMessage());
			return ResponseEntity.internalServerError().body(result);
		}
		return ResponseEntity.ok(result);
	}
}
