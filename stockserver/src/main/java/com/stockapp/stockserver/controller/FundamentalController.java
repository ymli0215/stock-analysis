package com.stockapp.stockserver.controller;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.stockapp.stockserver.entity.StockFundamental;
import com.stockapp.stockserver.repo.StockFundamentalRepository;

@RestController
@RequestMapping("/fundamental")
public class FundamentalController extends AbstractController {

    private static final Logger logger = LoggerFactory.getLogger(FundamentalController.class);
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd");

    @Autowired
    private StockFundamentalRepository fundamentalRepository;

    /**
     * 查詢基本面資料。
     * 回傳最新估值快照 + 最近 8 季季報。
     * 支援 JSONP（callback 參數由 JsonpControllerAdvice 統一處理）。
     *
     * @param stockId 股票代碼，例如 "7203.T", "2330.TW", "AAPL"
     */
    @RequestMapping(value = "/query", method = RequestMethod.GET)
    public @ResponseBody Map<String, Object> query(
            @RequestParam(value = "si") String stockId) {

        Map<String, Object> result = new HashMap<>();

        try {
            // ── snapshot ──────────────────────────────────────────────────────
            Optional<StockFundamental> snapOpt = fundamentalRepository.findLatestSnapshot(stockId);
            if (snapOpt.isPresent()) {
                StockFundamental s = snapOpt.get();
                Map<String, Object> snapshot = new HashMap<>();
                snapshot.put("fiscalDate", DATE_FMT.format(s.getId().getFiscalDate()));
                snapshot.put("pe",       s.getPe());
                snapshot.put("pb",       s.getPb());
                snapshot.put("roe",      s.getRoe());
                snapshot.put("divYield", s.getDivYield());
                snapshot.put("eps",      s.getEps());
                result.put("snapshot", snapshot);
            } else {
                result.put("snapshot", null);
            }

            // ── quarters ──────────────────────────────────────────────────────
            List<StockFundamental> quarters = fundamentalRepository.findRecentQuarters(stockId);
            List<Map<String, Object>> qList = new ArrayList<>();
            for (StockFundamental q : quarters) {
                Map<String, Object> row = new HashMap<>();
                row.put("fiscalDate",  DATE_FMT.format(q.getId().getFiscalDate()));
                row.put("period",      q.getId().getPeriod());
                row.put("revenue",     q.getRevenue());
                row.put("netIncome",   q.getNetIncome());
                row.put("eps",         q.getEps());
                row.put("totalAssets", q.getTotalAssets());
                row.put("totalDebt",   q.getTotalDebt());
                qList.add(row);
            }
            result.put("quarters", qList);
            result.put("stockId",  stockId);

        } catch (Exception e) {
            logger.error("fundamental query error for {}: {}", stockId, e.getMessage());
            result.put("error", e.getMessage());
        }

        return result;
    }
}
