package com.stockapp.stockserver.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stockapp.stockserver.entity.YoutubeTranscribeQueue;
import com.stockapp.stockserver.repo.YoutubeTranscribeQueueRepository;

@RestController
@RequestMapping("/youtubeTranscribe")
public class YoutubeTranscribeController extends AbstractController {

	private static final Logger logger = LoggerFactory.getLogger(YoutubeTranscribeController.class);

	@Autowired
	private YoutubeTranscribeQueueRepository repository;

	@PostMapping("/save")
	public ResponseEntity<Map<String, Object>> save(@RequestBody Map<String, String> request) {
		Map<String, Object> result = new HashMap<>();
		try {
			YoutubeTranscribeQueue entity = new YoutubeTranscribeQueue();
			entity.setUrl(request.get("url"));
			entity.setAiStatus(0);
			YoutubeTranscribeQueue saved = repository.save(entity);
			result.put("status", "ok");
			result.put("id", saved.getId());
			logger.info("YoutubeTranscribeQueue saved id={} url={}", saved.getId(), saved.getUrl());
		} catch (Exception e) {
			logger.error("YoutubeTranscribeQueue save error", e);
			result.put("status", "error");
			result.put("message", e.getMessage());
			return ResponseEntity.internalServerError().body(result);
		}
		return ResponseEntity.ok(result);
	}

	@GetMapping("/unprocessed")
	public ResponseEntity<List<Map<String, Object>>> unprocessed() {
		DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		List<YoutubeTranscribeQueue> list = repository.findByAiStatusOrderByCreatedAtAsc(0, PageRequest.of(0, 3));
		List<Map<String, Object>> result = list.stream().map(item -> {
			Map<String, Object> row = new HashMap<>();
			row.put("id", item.getId());
			row.put("url", item.getUrl());
			row.put("createdAt", item.getCreatedAt() != null ? item.getCreatedAt().format(fmt) : null);
			return row;
		}).collect(Collectors.toList());
		return ResponseEntity.ok(result);
	}

	@PutMapping("/updateStatus")
	public ResponseEntity<Map<String, Object>> updateStatus(@RequestBody Map<String, Object> request) {
		Map<String, Object> result = new HashMap<>();
		try {
			Long id = Long.valueOf(request.get("id").toString());
			Integer aiStatus = Integer.valueOf(request.get("aiStatus").toString());
			String aiResult = request.get("aiResult") != null ? request.get("aiResult").toString() : null;

			YoutubeTranscribeQueue entity = repository.findById(id).orElseThrow(
				() -> new IllegalArgumentException("id not found: " + id));
			entity.setAiStatus(aiStatus);
			entity.setAiResult(aiResult);
			entity.setUpdatedAt(LocalDateTime.now());
			repository.save(entity);
			result.put("status", "ok");
			logger.info("YoutubeTranscribeQueue updated id={} aiStatus={}", id, aiStatus);
		} catch (Exception e) {
			logger.error("YoutubeTranscribeQueue updateStatus error", e);
			result.put("status", "error");
			result.put("message", e.getMessage());
			return ResponseEntity.internalServerError().body(result);
		}
		return ResponseEntity.ok(result);
	}
}
