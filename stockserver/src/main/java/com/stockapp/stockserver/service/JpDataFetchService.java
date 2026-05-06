package com.stockapp.stockserver.service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Calls Python FastAPI (port 8090) to ensure JP stock data is up-to-date.
 * Uses a Cache-first strategy: the Python service decides whether to fetch
 * from Yahoo Finance or return immediately (no-op) when data is current.
 * Failures are logged and swallowed — callers continue with existing DB data.
 */
@Service
public class JpDataFetchService {

    private static final Logger logger = LoggerFactory.getLogger(JpDataFetchService.class);

    @Value("${jp-fetcher.url}")
    private String fetchUrl;

    private final RestTemplate restTemplate;

    public JpDataFetchService(RestTemplateBuilder builder,
                               @Value("${jp-fetcher.timeout-seconds:30}") int timeoutSeconds) {
        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        this.restTemplate = builder
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .build();
    }

    /**
     * Fetch and store fundamental data (PE/PB/ROE/quarterly financials) for one stock.
     * Calls /api/fetch/fundamental on the Python FastAPI service synchronously.
     * Failures are logged and swallowed — callers continue normally.
     *
     * @param stockId e.g. "7203.T"
     */
    public void fetchFundamental(String stockId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = new HashMap<>();
            body.put("stockId", stockId);
            body.put("dataType", "D");

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

            String fundamentalUrl = fetchUrl.replace("/api/fetch/jp", "/api/fetch/fundamental");
            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.postForEntity(fundamentalUrl, entity, Map.class);

            logger.info("JP fundamental [{}] → status={} snap={} quarterly={}",
                    stockId,
                    response.getBody() != null ? response.getBody().get("status") : "?",
                    response.getBody() != null ? response.getBody().get("snap_written") : "?",
                    response.getBody() != null ? response.getBody().get("quarterly_written") : "?");

        } catch (Exception e) {
            logger.error("JP fundamental fetch failed for {}: {}. Skipping.", stockId, e.getMessage());
        }
    }

    /**
     * Ensure the given JP stock's data is up-to-date in the DB.
     * Calls the Python FastAPI endpoint synchronously.
     * On any failure, logs the error and returns without throwing,
     * so the caller can fall back to whatever data is already in the DB.
     *
     * @param stockId  e.g. "7203.T"
     * @param dataType e.g. "D"
     */
    public void ensureData(String stockId, String dataType) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = new HashMap<>();
            body.put("stockId", stockId);
            body.put("dataType", dataType);

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.postForEntity(fetchUrl, entity, Map.class);

            logger.info("JP fetch [{}][{}] → status={} action={} written={}",
                    stockId, dataType,
                    response.getBody() != null ? response.getBody().get("status") : "?",
                    response.getBody() != null ? response.getBody().get("action") : "?",
                    response.getBody() != null ? response.getBody().get("written") : "?");

        } catch (Exception e) {
            logger.error("JP data fetch failed for {} [{}]: {}. Falling back to existing DB data.",
                    stockId, dataType, e.getMessage());
        }
    }
}
