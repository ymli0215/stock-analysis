package com.stockapp.stockserver.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockapp.stockserver.entity.StockRawData;
import com.stockapp.stockserver.entity.Stockai;
import com.stockapp.stockserver.entity.Stocks;
import com.stockapp.stockserver.entity.Webhook;
import com.stockapp.stockserver.entity.WebhookId;
import com.stockapp.stockserver.repo.StockRawDataRepository;
import com.stockapp.stockserver.repo.StockaiRepository;
import com.stockapp.stockserver.repo.StocksRepository;
import com.stockapp.stockserver.repo.WebhookRepository;

@Service
@EnableAsync
public class WebhookService extends AbstractService {
    private static final Logger logger = LoggerFactory.getLogger(WebhookService.class);

    @Autowired
    private WebhookRepository webhookRepository;
    @Autowired
    private StocksRepository stockRepository;
    @Autowired
    private StockaiRepository stockaiRepository;
    @Autowired
    private StockRawDataRepository stockRawDataRepository;

    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * 處理傳入的訊息 (文字、圖片或 PDF)
     * * @param chatId Telegram Chat ID
     * @param messageType 訊息類型 (TEXT, IMAGE, PDF)
     * @param text 純文字內容 (可為 null)
     * @param file 上傳的檔案 (可為 null)
     * @return 儲存後的 Entity
     */
    @Transactional
    public void processTelegramUpdate(String jsonPayload) {
        try {
            JsonNode root = objectMapper.readTree(jsonPayload);

            // 1. 基本檢查：確認是 Message Update
            if (!root.has("message")) {
                return; // 可能是編輯訊息或其他事件，暫不處理
            }
            JsonNode message = root.get("message");
            
            // 2. 取得 Chat ID
            String chatId = message.get("chat").get("id").asText();
            
            String messageType = "TEXT";
            String contentText = null;
            String telegramFileId = null;
            String fileName = null; // 🌟 新增：用來儲存檔案名稱

            // 3. 解析內容類型
            if (message.has("photo")) {
                messageType = "IMAGE";
                // 圖片通常有 caption
                if (message.has("caption")) {
                    contentText = message.get("caption").asText();
                }
                // 取得解析度最高的圖片 (陣列的最後一個)
                JsonNode photos = message.get("photo");
                JsonNode largestPhoto = photos.get(photos.size() - 1);
                telegramFileId = largestPhoto.get("file_id").asText();
                
                // 🌟 新增：圖片通常沒有 file_name，直接給予預設命名避免後續找不到副檔名
                fileName = "image_" + System.currentTimeMillis() + ".jpg";

            } else if (message.has("document")) {
                messageType = "DOCUMENT";
                // 文件也可能有 caption
                if (message.has("caption")) {
                    contentText = message.get("caption").asText();
                }
                
                JsonNode documentNode = message.get("document");
                telegramFileId = documentNode.get("file_id").asText();
                
                // 🌟 關鍵新增：擷取 Telegram 提供的真實檔案名稱
                if (documentNode.has("file_name")) {
                    fileName = documentNode.get("file_name").asText();
                } else {
                    // 防呆：如果真的沒有檔名，給個預設值
                    fileName = "document_" + System.currentTimeMillis();
                }

            } else if (message.has("text")) {
                messageType = "TEXT";
                contentText = message.get("text").asText();
            } else {
                // 其他類型 (Sticker, Voice...) 暫略過，或標記為 UNKNOWN
                return; 
            }

            // 4. 存入資料庫
            StockRawData data = new StockRawData();
            data.setChatId(chatId);
            data.setMessageType(messageType);
            data.setContentText(contentText);
            data.setTelegramFileId(telegramFileId);
            data.setFileName(fileName); // 🌟 新增：將檔名寫入 Entity
            data.setStatus(0); // 待處理

            stockRawDataRepository.save(data);

        } catch (Exception e) {
            // 建議加上 Log，這裡簡單 print
            System.err.println("Error parsing Telegram JSON: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void receiveTelegram(String text) {
    	logger.info("receiveTelegram text={}", text);
    	//如果空白或是文字太長就跳出
    	if(StringUtils.isEmpty(text) || text.length() > 10) {
    		return;
    	}
    	
    	String[] values = text.toUpperCase().split("\\.");
    	//如果輸入的資料沒有.，就查詢stocks中是否有資料
    	if(values.length == 1) {
    		Optional<Stocks> stock = stockRepository.findById(values[0]);
    		if(stock.isPresent()) {
    			//判斷是否為US或非US
    			if(StringUtils.equals("US", stock.get().getStockType())) {
    				values = ("US."+text).toUpperCase().split("\\.");
    			}
    			else {
    				values = ("TW."+text).toUpperCase().split("\\.");
    			}
    		}
    		else {
    			//如果沒有股票資料就跳出
    			return;
    		}
    	}
    	
    	logger.info("start process:{} {}", values[0], values[1]);
    	
    	if(StringUtils.equals("TW", values[0]) ||
    			StringUtils.equals("US", values[0]) ||
    			StringUtils.equals("JP", values[0]))  {
    		WebhookId id = new WebhookId();
    		id.setMarket(values[0]);
    		id.setId(values[1]);
    		
    		Optional<Webhook> webhookOptional = webhookRepository.findById(id);
    		logger.info("check Webhook is empty? {}", webhookOptional.isEmpty());
    		//沒有才新增
    		if(webhookOptional.isEmpty()) {
    			//但是先判斷stockai中是否在6小時內已經有查詢過了，有查過就不要花時間去查
    			List<Stockai> stockais = stockaiRepository.findByMarketAndId(values[0], values[1]);
    			if(!stockais.isEmpty()) {
    				Stockai stockai = stockais.get(0);
    				LocalDateTime now = LocalDateTime.now().minusHours(6);
    				if(now.atZone(ZoneId.of("Asia/Taipei")).toInstant().toEpochMilli() <= stockai.getDatetime()) {
    					return;
    				}
    			}
    			
    			Webhook webhook = new Webhook();
    			webhook.setWebhookId(id);
    			webhookRepository.save(webhook);
    		}
    	}
    }
}