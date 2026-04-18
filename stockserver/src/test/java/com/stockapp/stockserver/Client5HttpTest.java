package com.stockapp.stockserver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.net.ssl.SSLContext;

import org.apache.commons.lang3.time.DateUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockapp.stockserver.entity.StockData;
import com.stockapp.stockserver.entity.StockDataId;
import com.stockapp.stockserver.enums.StockDataTypeType;

@SpringBootTest
@ActiveProfiles("base")
class Client5HttpTest {

    private static final Logger logger = LoggerFactory.getLogger(Client5HttpTest.class);
    

	/** yahoo 期指資料 */
	public final String YAHOO_FITX_URL = "https://tw.screener.finance.yahoo.net/future/q?type=ta&perd=###&mkt=01&sym=WTX%26&v=1";

	/** yahoo 盤後資料 */
	public final String YAHOO_FITXP_URL = "https://tw.screener.finance.yahoo.net/future/q?type=ta&perd=d&mkt=01&sym=WTX%26&callback=jQuery111306961738331038583_%s";

	
    @Autowired
    private ObjectMapper objectMapper;

    private CloseableHttpClient createTrustAllHttpClient() throws Exception {
        // 信任所有憑證
        TrustStrategy trustAllStrategy = (X509Certificate[] chain, String authType) -> true;
        SSLContext sslContext = SSLContexts.custom()
                .loadTrustMaterial(null, trustAllStrategy)
                .build();

        // 跳過主機名稱驗證
        SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(
                sslContext,
                (hostname, session) -> true
        );

        // 建立連線管理器
        return HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setSSLSocketFactory(sslSocketFactory)
                        .build())
                .build();
    }
    
    @Test
    public void syncStockFITXP() {
//    	try (CloseableHttpClient client = createTrustAllHttpClient()) {
//    		Date now = new Date();
//    		now = DateUtils.setHours(now, 0);
//    		now = DateUtils.setMinutes(now, 0);
//    		now = DateUtils.setSeconds(now, 0);
//    		
//    		String url = YAHOO_FITXP_URL.replace("%s", now.getTime()+"");
//        	
//            HttpClientContext context = HttpClientContext.create();
//            HttpGet httpGet = new HttpGet(url);
//            logger.info("發送 GET 請求到: {}", url);
//
//            HttpClientResponseHandler<String> responseHandler = response -> {
//                int statusCode = response.getCode();
//                logger.debug("收到回應，狀態碼: {}", statusCode);
//                if (statusCode == HttpStatus.SC_OK) {
//                    String result = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
//                    logger.info("成功獲取數據，長度: {}", result.length());
//                    return result;
//                } else {
//                    logger.error("請求失敗，狀態碼: {}", statusCode);
//                    throw new IOException("HTTP 請求失敗，狀態碼: " + statusCode);
//                }
//            };
//
//            client.execute(httpGet, context, responseHandler);
//        } catch (Exception e) {
//            logger.error("執行 GET 請求時發生錯誤: {}", e.getMessage(), e);
//            throw new RuntimeException("無法獲取股票數據", e);
//        }
    }

    @Test
    public void fetchStockDataWithGet() {
//        try (CloseableHttpClient client = createTrustAllHttpClient()) {
//        	String referer = "https://histock.tw/stock/chart/teach.aspx?no=0000";
//        	String url = getHistockUrl("0000", StockDataTypeType.DAYILY, 10);
//        	
//            HttpClientContext context = HttpClientContext.create();
//            HttpGet httpGet = new HttpGet(url);
//            httpGet.setHeader("Referer", referer);
//            logger.info("發送 GET 請求到: {}, Referer: {}", url, referer);
//
//            HttpClientResponseHandler<String> responseHandler = response -> {
//                int statusCode = response.getCode();
//                logger.debug("收到回應，狀態碼: {}", statusCode);
//                if (statusCode == HttpStatus.SC_OK) {
//                    String result = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
//                    logger.info("成功獲取數據，長度: {}", result.length());
//                    return result;
//                } else {
//                    logger.error("請求失敗，狀態碼: {}", statusCode);
//                    throw new IOException("HTTP 請求失敗，狀態碼: " + statusCode);
//                }
//            };
//
//            client.execute(httpGet, context, responseHandler);
//        } catch (Exception e) {
//            logger.error("執行 GET 請求時發生錯誤: {}", e.getMessage(), e);
//            throw new RuntimeException("無法獲取股票數據", e);
//        }
    }

    public String fetchStockDataWithPost(String url, String referer, String payload) {
        try (CloseableHttpClient client = createTrustAllHttpClient()) {
            HttpClientContext context = HttpClientContext.create();
            HttpPost httpPost = new HttpPost(url);
            httpPost.setHeader("Referer", referer);
            httpPost.setHeader("Content-Type", "application/json");
            if (payload != null) {
                httpPost.setEntity(new StringEntity(payload, StandardCharsets.UTF_8));
            }
            logger.info("發送 POST 請求到: {}, Referer: {}, Payload: {}", url, referer, payload);

            HttpClientResponseHandler<String> responseHandler = response -> {
                int statusCode = response.getCode();
                logger.debug("收到回應，狀態碼: {}", statusCode);
                if (statusCode == HttpStatus.SC_OK) {
                	String result = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                    logger.info("成功獲取數據，長度: {}", result.length());
                    return result;
                } else {
                    logger.error("請求失敗，狀態碼: {}", statusCode);
                    throw new IOException("HTTP 請求失敗，狀態碼: " + statusCode);
                }
            };

            return client.execute(httpPost, context, responseHandler);
        } catch (Exception e) {
            logger.error("執行 POST 请求时出错: {}", e.getMessage(), e);
            throw new RuntimeException("無法獲取股票數據", e);
        }
    }
    
    public List<StockData> parseStockJson(String jsonString, String stockId, String dataType) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonString);
            JsonNode tNode = rootNode.get("t");
            JsonNode cNode = rootNode.get("c");
            JsonNode oNode = rootNode.get("o");
            JsonNode hNode = rootNode.get("h");
            JsonNode lNode = rootNode.get("l");

            if (tNode == null || !tNode.isArray() || cNode == null || !cNode.isArray() ||
                oNode == null || !oNode.isArray() || hNode == null || !hNode.isArray() ||
                lNode == null || !lNode.isArray()) {
                throw new IllegalArgumentException("JSON 格式錯誤，缺少 t, c, o, h 或 l 陣列");
            }

            List<StockData> stockDataList = new ArrayList<>();
            for (int i = 0; i < tNode.size(); i++) {
                long timestamp = tNode.get(i).asLong();
                double close = cNode.get(i).asDouble();
                double open = oNode.get(i).asDouble();
                double high = hNode.get(i).asDouble();
                double low = lNode.get(i).asDouble();

                StockDataTypeType stockDataType = StockDataTypeType.find(dataType);
                StockDataId id = new StockDataId();
                id.setStockId(stockId);
                id.setDataType(dataType);

            	Date date = new Date(timestamp*1000);
                if(stockDataType.isDAYILY()) {
                	//時間設為當天
                	id.setDataTime(date.getTime());
                }
                else if(stockDataType.isWEEK()) {
                	//時間設為該周一
                	Calendar calendar = Calendar.getInstance();
                	calendar.setTime(date);
                	calendar.set(Calendar.DAY_OF_WEEK, 2);
                	id.setDataTime(calendar.getTimeInMillis());
                }
                else if(stockDataType.isMONTH()) {
                	//時間設為該月一號
                	date = DateUtils.setDays(date, 1);
                	id.setDataTime(date.getTime());
                }

                StockData stockData = new StockData();
                stockData.setClose(close);
                stockData.setDataDate(date);
                stockData.setDateString(jsonString);
                stockData.setHigh(high);
                stockData.setLow(low);
                stockData.setOpen(open);
                
                stockDataList.add(stockData);
                logger.info("解析資料: timestamp={}, close={}, open={}, high={}, low={}",
                        timestamp, close, open, high, low);
            }

            return stockDataList;
        } catch (Exception e) {
            logger.error("解析 JSON 時發生錯誤: {}", e.getMessage(), e);
            throw new RuntimeException("無法解析股票 JSON 數據", e);
        }
    }
    
    /**
     * 去 histock 抓取個股/大盤的價格資料
     * 
     * @param stockId
     * @param dataType
     * @param count
     * @return
     */
    private String getHistockUrl(String stockId, StockDataTypeType dataType, int count) {
    	Date to = new Date();
    	Date from = new Date();
    	if(dataType.isDAYILY()) {
    		from = DateUtils.addDays(from, -1*count+1);
    	}
    	else if(dataType.isWEEK()) {
    		from = DateUtils.addWeeks(from, -1*count+1);
    	}
    	else if(dataType.isMONTH()) {
    		from = DateUtils.addMonths(from, -1*count+1);
    	}

    	String url = "https://histock.tw/Stock/tv/udf.asmx/history?symbol=%s&resolution=1%s&from=%s&to=%s&countback=2";
    	url = String.format(url, stockId, dataType.getCode(), from.getTime()/1000, to.getTime()/1000);
    	logger.debug("url:{}", url);
    	
    	return url;
    }
}