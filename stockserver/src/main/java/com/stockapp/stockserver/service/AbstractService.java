package com.stockapp.stockserver.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockapp.stockserver.comparator.StockDataComparator;
import com.stockapp.stockserver.entity.StockData;
import com.stockapp.stockserver.entity.StockDataId;
import com.stockapp.stockserver.entity.Stocks;
import com.stockapp.stockserver.enums.StockDataTypeType;
import com.stockapp.stockserver.model.CallbackResult;
import com.stockapp.stockserver.repo.StockDataRepository;
import com.stockapp.stockserver.repo.StocksRepository;

@Service
public abstract class AbstractService {
    private static final Logger logger = LoggerFactory.getLogger(AbstractService.class);

    public final String HISTOCK_STOCK_DATA_URL = "https://histock.tw/Stock/tv/udf.asmx/history?symbol=%s&resolution=1%s&from=%s&to=%s&countback=2";
    public final String YAHOO_FITX_URL = "https://tw.screener.finance.yahoo.net/future/q?type=ta&perd=%s&mkt=01&sym=WTX%26&v=1";

    @Autowired
    StocksRepository stocksRepository;

    @Autowired
    StockDataRepository stockDataRepository;

    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private BizDateService bizDateService;

    private CloseableHttpClient createTrustAllHttpClient() throws Exception {
        TrustStrategy trustAllStrategy = (X509Certificate[] chain, String authType) -> true;
        SSLContext sslContext = SSLContexts.custom()
                .loadTrustMaterial(null, trustAllStrategy)
                .build();

        SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(
                sslContext,
                (hostname, session) -> true
        );

        return HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setSSLSocketFactory(sslSocketFactory)
                        .build())
                .build();
    }

    public List<StockData> receiveHistockData(String stockId, StockDataTypeType dataType, int dataCount) {
        try (CloseableHttpClient client = createTrustAllHttpClient()) {
            String referer = "https://histock.tw/stock/chart/teach.aspx?no=" + stockId.toLowerCase();
            String url = getHistockDataUrl(stockId, dataType, dataCount);

            HttpClientContext context = HttpClientContext.create();
            HttpGet httpGet = new HttpGet(url);
            httpGet.setHeader("Referer", referer);
            logger.info("發送 GET 請求到: {}, Referer: {}", url, referer);

            HttpClientResponseHandler<String> responseHandler = response -> {
                int statusCode = response.getCode();
                logger.info("收到回應，狀態碼: {}", statusCode);
                if (statusCode == HttpStatus.SC_OK) {
                    String result = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                    logger.info("成功獲取數據，長度: {}", result.length());
                    return result;
                } else {
                    logger.warn("請求失敗，狀態碼: {}", statusCode);
                    throw new IOException("HTTP 請求失敗，狀態碼: " + statusCode);
                }
            };

            String response = client.execute(httpGet, context, responseHandler);
            List<StockData> datas = parseHistockStockJson(response, stockId, dataType);
            stockDataRepository.saveAll(datas);

            return datas;
        } catch (Exception e) {
            logger.error("執行 GET 請求時發生錯誤: {}", e);
            throw new RuntimeException("無法獲取股票數據", e);
        }
    }

    private List<StockData> parseHistockStockJson(String jsonString, String stockId, StockDataTypeType stockDataType) {
        try {
            if (jsonString.contains("no_data")) {
                return new ArrayList<>();
            }

            JsonNode rootNode = objectMapper.readTree(jsonString);
            JsonNode tNode = rootNode.get("t");
            JsonNode cNode = rootNode.get("c");
            JsonNode oNode = rootNode.get("o");
            JsonNode hNode = rootNode.get("h");
            JsonNode lNode = rootNode.get("l");
            JsonNode vNode = rootNode.get("v");

            if (tNode == null || !tNode.isArray() || cNode == null || !cNode.isArray() ||
                oNode == null || !oNode.isArray() || hNode == null || !hNode.isArray() ||
                lNode == null || !lNode.isArray() || vNode == null || !vNode.isArray()) {
                throw new IllegalArgumentException("JSON 格式錯誤，缺少 t, c, o, h 或 l 陣列");
            }

            int startIndex = 0;
            if (stockDataType.isUNKNOWN()) {
                startIndex = tNode.size() - 1;
            }

            List<StockData> stockDataList = new ArrayList<>();
            for (int i = startIndex; i < tNode.size(); i++) {
                long timestamp = tNode.get(i).asLong();
                double close = cNode.get(i).asDouble();
                double open = oNode.get(i).asDouble();
                double high = hNode.get(i).asDouble();
                double low = lNode.get(i).asDouble();
                int value = vNode.get(i).asInt();

                StockDataId id = new StockDataId();
                id.setStockId(stockId);
                id.setDataType(stockDataType.getCode());

                LocalDateTime dateTime = LocalDateTime.ofEpochSecond(timestamp, 0, ZoneId.of("Asia/Taipei").getRules().getOffset(LocalDateTime.now()));
                dateTime = dateTime.withHour(8).withMinute(0).withSecond(0).withNano(0);

                if (stockDataType.isDAYILY()) {
                    dateTime = dateTime.minusDays(1);
                } else if (stockDataType.isWEEK()) {
                    dateTime = dateTime.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
                } else if (stockDataType.isMONTH()) {
                    dateTime = dateTime.withDayOfMonth(1);
                }
                id.setDataTime(dateTime.atZone(ZoneId.of("Asia/Taipei")).toInstant().toEpochMilli());

                StockData stockData = new StockData();
                stockData.setId(id);
                stockData.setClose(close);
                stockData.setDataDate(dateTime);
                stockData.setDateString(jsonString);
                stockData.setHigh(high);
                stockData.setLow(low);
                stockData.setOpen(open);
                stockData.setVolume(value);

                stockDataList.add(stockData);
                logger.info("解析資料: timestamp={}, close={}, open={}, high={}, low={}",
                        timestamp, close, open, high, low);
            }

            return stockDataList;
        } catch (Exception e) {
            logger.error("解析 JSON 時發生錯誤: {}", e);
            throw new RuntimeException("無法解析股票 JSON 數據", e);
        }
    }

    private String getHistockDataUrl(String stockId, StockDataTypeType dataType, int count) {
        LocalDateTime to = LocalDateTime.now(ZoneId.of("Asia/Taipei"));
        LocalDateTime from = LocalDateTime.now(ZoneId.of("Asia/Taipei"));
        String datatype = "";
        if (dataType.isDAYILY()) {
            from = from.minusDays(count - 1);
            datatype = dataType.getCode();
        } else if (dataType.isWEEK()) {
            from = from.minusWeeks(count - 1);
            datatype = dataType.getCode();
        } else if (dataType.isMONTH()) {
            from = from.minusMonths(count - 1);
            datatype = dataType.getCode();
        } else {
            from = from.withHour(8).withMinute(0).withSecond(0).withNano(0);
        }
        //如果下午一點半後來查詢，就要把to的時間改成下一個交易日+1d的00:00:00
        // 定義收盤時間 13:30
        LocalTime marketCloseTime = LocalTime.of(13, 30);

        //避免跑過頭
        int index = 0;
        if (to.toLocalTime().isAfter(marketCloseTime)) {
            // 如果是 13:30 之後，開始尋找「下一個開盤的交易日」
            
            // 從「明天」開始找
            LocalDate targetDate = to.toLocalDate().plusDays(1);
            
            // 使用 while 迴圈連續往下判斷，直到 isBizDate 為 true
            while (!bizDateService.isBizDate(targetDate) && index <= 30) {
                targetDate = targetDate.plusDays(1);
                index++;
            }

            // 邏輯：找到的交易日 + 1天 的 00:00:00
            // 注意：這裡 targetDate 已經是下一個開盤日了，再加一天即為您的 to
            to = targetDate.atStartOfDay().plusDays(1);
        }

        String url = String.format(HISTOCK_STOCK_DATA_URL, stockId.toLowerCase(), datatype,
                from.atZone(ZoneId.of("Asia/Taipei")).toEpochSecond(), 
                to.atZone(ZoneId.of("Asia/Taipei")).toEpochSecond());
        logger.info("url:{}", url);

        return url;
    }
    
    /**
     * 尋找下一個交易日 (週一至週五)
     */
    private static LocalDateTime getNextTradingDay(LocalDateTime date) {
        LocalDateTime next = date.plusDays(1);
        DayOfWeek dow = next.getDayOfWeek();
        
        if (dow == DayOfWeek.SATURDAY) {
            return date.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        } else if (dow == DayOfWeek.SUNDAY) {
            return date.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        }
        return next;
    }

    public List<StockData> syncStockFITX(String stockId, StockDataTypeType dataType) {
        CallbackResult cr = new CallbackResult();
        cr.setStockId(stockId);
        cr.setDataType(dataType.getCode());

        try (CloseableHttpClient client = createTrustAllHttpClient()) {
            LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Taipei")).withHour(8).withMinute(0).withSecond(0).withNano(0);

            String url = YAHOO_FITX_URL.replace("%s", dataType.getCode().toLowerCase());

            HttpClientContext context = HttpClientContext.create();
            HttpGet httpGet = new HttpGet(url);
            logger.info("發送 GET 請求到: {}", url);

            HttpClientResponseHandler<String> responseHandler = response -> {
                int statusCode = response.getCode();
                logger.info("收到回應，狀態碼: {}", statusCode);
                if (statusCode == HttpStatus.SC_OK) {
                    String result = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                    logger.info("成功獲取數據，長度: {}", result.length());
                    return result;
                } else {
                    logger.error("請求失敗，狀態碼: {}", statusCode);
                    throw new IOException("HTTP 請求失敗，狀態碼: " + statusCode);
                }
            };

            String response = client.execute(httpGet, context, responseHandler);
            return parseFITXData(response, stockId, dataType);
        } catch (Exception e) {
            logger.error("執行 GET 請求時發生錯誤: {}", e);
            throw new RuntimeException("無法獲取股票數據", e);
        }
    }

    private List<StockData> parseFITXData(String response, String stockId, StockDataTypeType dataType) {
        try {
            List<StockData> datas = new ArrayList<>();

            int startIndex = response.indexOf("\"ta\":[") + 5;
            int endIndex = response.indexOf("]}", startIndex) + 1;
            String jsonString = response.substring(startIndex, endIndex);
            logger.debug("jsonString={}", jsonString);

            JsonNode rootNode = objectMapper.readTree(jsonString);
            if (rootNode == null || !rootNode.isArray()) {
                throw new IllegalArgumentException("JSON 格式錯誤，應為陣列");
            }

            for (JsonNode node : rootNode) {
                String dateStr = node.get("t").asText();
                double open = node.get("o").asDouble();
                double high = node.get("h").asDouble();
                double low = node.get("l").asDouble();
                double close = node.get("c").asDouble();
                int value = node.get("v").asInt();

                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
                LocalDateTime today = LocalDateTime.parse(dateStr, formatter).withHour(8).withMinute(0).withSecond(0).withNano(0);

                if (dataType.isWEEK()) {
                    today = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.TUESDAY));
                } else if (dataType.isMONTH()) {
                    today = today.withDayOfMonth(1);
                }

                StockDataId id = new StockDataId();
                id.setDataTime(today.atZone(ZoneId.of("Asia/Taipei")).toInstant().toEpochMilli());
                id.setDataType(dataType.getCode());
                id.setStockId(stockId);

                StockData stockData = new StockData();
                stockData.setId(id);

                Optional<StockData> stockDataOptional = stockDataRepository.findById(id);
                if (!stockDataOptional.isEmpty()) {
                    stockData = stockDataOptional.get();
                }

                stockData.setOpen(open);
                stockData.setHigh(high);
                stockData.setLow(low);
                stockData.setClose(close);
                stockData.setDataDate(today);
                stockData.setVolume(value);

                datas.add(stockData);
            }

            stockDataRepository.saveAll(datas);
            return datas;
        } catch (Exception e) {
            logger.error("解析 JSON 時發生錯誤: {}", e);
            throw new RuntimeException("無法解析股票 JSON 數據", e);
        }
    }

    public List<StockData> syncFITXData(String stockId, StockDataTypeType dataType, int dataCount) {
        syncStockFITX(stockId, StockDataTypeType.DAYILY);

        if (dataType.isWEEK()) {
            syncStockFITX(stockId, dataType);
            updateWeekData(stockId);
        } else if (dataType.isMONTH()) {
            syncStockFITX(stockId, dataType);
            updateMonthData(stockId);
        }

        return findStockData(stockId, dataType.getCode(), dataCount);
    }

    public List<StockData> syncStockData(String stockId, StockDataTypeType dataType, int dataCount) {
        int count = dataCount;
        if (dataType.isWEEK()) {
            count = dataCount * 7;
        } else if (dataType.isMONTH()) {
            count = dataCount * 31;
        }
        if (count > 400) {
            count = 400;
        }

        receiveHistockData(stockId, StockDataTypeType.DAYILY, count);
        receiveHistockData(stockId, StockDataTypeType.UNKNOWN, 1);

        if (dataType.isWEEK()) {
            receiveHistockData(stockId, dataType, dataCount);
            updateWeekData(stockId);
        } else if (dataType.isMONTH()) {
            receiveHistockData(stockId, dataType, dataCount);
            updateMonthData(stockId);
        }

        return findStockData(stockId, dataType.getCode(), dataCount);
    }

    public void updateWeekData(String stockId) {
        List<Stocks> stocksList = new ArrayList<>();
        Optional<Stocks> stockOptional = stocksRepository.findById(stockId);
        if (stockOptional.isEmpty()) {
            logger.warn("更新股票周資料失敗:{}", stockId);
            return;
        }
        stocksList.add(stockOptional.get());

        logger.info("更新股票周資料:{}", stockId);

        List<StockData> datas = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Taipei"));
        long nowTime = now.atZone(ZoneId.of("Asia/Taipei")).toInstant().toEpochMilli();

        LocalDateTime monday = now.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                .withHour(8).withMinute(0).withSecond(0).withNano(0);
        long startTime = monday.atZone(ZoneId.of("Asia/Taipei")).toInstant().toEpochMilli();
        long mondayTime = monday.atZone(ZoneId.of("Asia/Taipei")).toInstant().toEpochMilli();

        for (Stocks stock : stocksList) {
            if (stock == null) {
                continue;
            }
            try {
                Double high = null;
                Double low = null;
                Double open = null;
                Double close = null;
                Integer volume = 0;

                List<StockData> dayDatas = stockDataRepository.findDatasBetweenDate(stock.getStockId(),
                        StockDataTypeType.DAYILY.getCode(), startTime, nowTime);

                if (!dayDatas.isEmpty()) {
                    open = dayDatas.get(0).getOpen();
                    close = dayDatas.get(dayDatas.size() - 1).getClose();

                    for (StockData data : dayDatas) {
                        if (high == null) {
                            high = data.getHigh();
                        }
                        if (low == null) {
                            low = data.getLow();
                        }

                        if (high < data.getHigh()) {
                            high = data.getHigh();
                        }

                        if (low > data.getLow()) {
                            low = data.getLow();
                        }

                        volume += data.getVolume();
                    }

                    StockDataId id = new StockDataId();
                    id.setDataTime(mondayTime);
                    id.setDataType(StockDataTypeType.WEEK.getCode());
                    id.setStockId(stock.getStockId());

                    StockData stockData;
                    Optional<StockData> stockDataOptional = stockDataRepository.findById(id);
                    if (stockDataOptional.isEmpty()) {
                        stockData = new StockData();
                        stockData.setId(id);
                    } else {
                        stockData = stockDataOptional.get();
                    }
                    stockData.setLow(low);
                    stockData.setOpen(open);
                    stockData.setClose(close);
                    stockData.setHigh(high);
                    stockData.setVolume(volume);
                    stockData.setDataDate(monday);

                    datas.add(stockData);
                }
            } catch (Exception e) {
            }
        }
        if (!datas.isEmpty()) {
            stockDataRepository.saveAll(datas);
        }
    }

    public void updateMonthData(String stockId) {
        List<Stocks> stocksList = new ArrayList<>();
        Optional<Stocks> stockOptional = stocksRepository.findById(stockId);
        if (stockOptional.isEmpty()) {
            logger.warn("更新股票月資料失敗:{}", stockId);
            return;
        }
        stocksList.add(stockOptional.get());

        logger.info("更新股票月資料:{}", stockId);

        List<StockData> datas = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Taipei"));
        long nowTime = now.atZone(ZoneId.of("Asia/Taipei")).toInstant().toEpochMilli();

        LocalDateTime firstDay = now.withDayOfMonth(1).withHour(8).withMinute(0).withSecond(0).withNano(0);
        long startTime = firstDay.atZone(ZoneId.of("Asia/Taipei")).toInstant().toEpochMilli();
        long firstDayTime = firstDay.atZone(ZoneId.of("Asia/Taipei")).toInstant().toEpochMilli();

        for (Stocks stock : stocksList) {
            if (stock == null) {
                continue;
            }
            try {
                Double high = null;
                Double low = null;
                Double open = null;
                Double close = null;
                Integer volume = 0;

                List<StockData> dayDatas = stockDataRepository.findDatasBetweenDate(stock.getStockId(),
                        StockDataTypeType.DAYILY.getCode(), startTime, nowTime);

                if (!dayDatas.isEmpty()) {
                    open = dayDatas.get(0).getOpen();
                    close = dayDatas.get(dayDatas.size() - 1).getClose();

                    for (StockData data : dayDatas) {
                        if (high == null) {
                            high = data.getHigh();
                        }
                        if (low == null) {
                            low = data.getLow();
                        }

                        if (high < data.getHigh()) {
                            high = data.getHigh();
                        }

                        if (low > data.getLow()) {
                            low = data.getLow();
                        }

                        volume += data.getVolume();
                    }

                    StockDataId id = new StockDataId();
                    id.setDataTime(firstDayTime);
                    id.setDataType(StockDataTypeType.MONTH.getCode());
                    id.setStockId(stock.getStockId());

                    StockData stockData;
                    Optional<StockData> stockDataOptional = stockDataRepository.findById(id);
                    if (stockDataOptional.isEmpty()) {
                        stockData = new StockData();
                        stockData.setId(id);
                    } else {
                        stockData = stockDataOptional.get();
                    }
                    stockData.setLow(low);
                    stockData.setOpen(open);
                    stockData.setClose(close);
                    stockData.setHigh(high);
                    stockData.setVolume(volume);
                    stockData.setDataDate(firstDay);

                    datas.add(stockData);
                }
            } catch (Exception e) {
            }
        }
        if (!datas.isEmpty()) {
            stockDataRepository.saveAll(datas);
        }
    }

    public List<StockData> findStockData(String stockId, String dataType, int dataCount) {
        List<StockData> datas = stockDataRepository.findDataDesc(stockId, dataType, dataCount);
        Collections.sort(datas, new StockDataComparator());

        return datas;
    }

    public List<StockData> findStockData(String stockId, String dataType) {
        List<StockData> datas = stockDataRepository.findDataDesc(stockId, dataType);
        Collections.sort(datas, new StockDataComparator());

        return datas;
    }
}
