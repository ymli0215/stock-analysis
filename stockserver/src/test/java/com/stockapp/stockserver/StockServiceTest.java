package com.stockapp.stockserver;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Date;

import org.apache.commons.lang3.time.DateUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stockapp.stockserver.repo.StocksRepository;
import com.stockapp.stockserver.service.StockService;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    private static final Logger logger = LoggerFactory.getLogger(StockServiceTest.class);

    @Mock
    private StocksRepository stocksRepository;

    @Mock
    private CloseableHttpClient httpClient;

    @Mock
    private CloseableHttpResponse httpResponse;

    @InjectMocks
    private StockService stockService;

    @Test
    void testFetchStockData_Success() throws IOException {
    	Date now = new Date();
    	Date before10D = DateUtils.addDays(now, -10);

    	String url = "https://histock.tw/Stock/tv/udf.asmx/history?symbol=%s&resolution=1D&from=%s&to=%s&countback=2";
    	url = String.format(url, "0000", before10D.getTime()/1000, now.getTime()/1000);
    	logger.debug("url:{}", url);
    	
    	try (CloseableHttpClient client = HttpClients.createDefault()) {
    	    HttpGet httpGet = new HttpGet(url);
    	    //加入header referer
    	    httpGet.addHeader("referer", "https://histock.tw/stock/chart/teach.aspx?no=0000");
    	    
    	    // 定義回應處理器
            HttpClientResponseHandler<String> responseHandler = response -> {
                int statusCode = response.getCode();
                logger.debug("收到回應，狀態碼: {}", statusCode);

                if (statusCode == HttpStatus.SC_OK) {
                    // 解析回應內容，使用 Big5 編碼
                    String result = EntityUtils.toString(response.getEntity(), "Big5");
                    logger.info("成功獲取數據，長度: {}", result.length());
                    // 可選：將數據儲存到資料庫
                    // stocksRepository.save(...);
                    return result;
                } else {
                    logger.error("請求失敗，狀態碼: {}", statusCode);
                    throw new IOException("HTTP 請求失敗，狀態碼: " + statusCode);
                }
            };

            // 執行請求
            String result = client.execute(httpGet, responseHandler);
            logger.info("result={}", result);
    	}
    }
}