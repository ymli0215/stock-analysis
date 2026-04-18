package com.stockapp.stockserver.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.stockapp.stockserver.service.WebhookService;


@RequestMapping("/webhook")
@RestController
@EnableAsync
@EnableScheduling
public class WebhookController extends AbstractController {
	// 初始化 SLF4J Logger
    private static final Logger logger = LoggerFactory.getLogger(WebhookController.class);

    @Autowired
    private WebhookService webhookService;


	@RequestMapping(value = "/telegram", method = RequestMethod.POST)
	public ResponseEntity<String> receiveTelegram(@RequestBody JsonNode payload) {

        JsonNode messageNode = payload.get("message");
        if (messageNode == null || messageNode.isNull()) {
            return ResponseEntity.badRequest().body("message node not found");
        }

        JsonNode textNode = messageNode.get("text");
        if (textNode == null || textNode.isNull()) {
            return ResponseEntity.badRequest().body("text not found");
        }

        String text = textNode.asText();
        // 這裡就是你要處理 text 的地方
        logger.info("Received text: {}", text);
        
        webhookService.receiveTelegram(text);

        return ResponseEntity.ok("received: " + text);
    }
	
	/**
     * Webhook 接收點
     * 接收 Content-Type: multipart/form-data
     */
    @PostMapping("/stockinfo")
    public ResponseEntity<String> stockinfo(@RequestBody String payload) {
        // 直接將原始 JSON 丟給 Service 處理
    	webhookService.processTelegramUpdate(payload);

        return ResponseEntity.ok("receive ok!");
    }
}
