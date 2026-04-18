package com.stockapp.stockserver.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.ResponseBody;

import com.stockapp.stockserver.comparator.StockDataComparator;
import com.stockapp.stockserver.comparator.TurnKDataComparator;
import com.stockapp.stockserver.controller.bean.StockInfo;
import com.stockapp.stockserver.entity.StockData;
import com.stockapp.stockserver.entity.StockDataId;
import com.stockapp.stockserver.entity.StockDataMA;
import com.stockapp.stockserver.entity.StockDataTurn;
import com.stockapp.stockserver.entity.StockPriceLevel;
import com.stockapp.stockserver.entity.StockWants;
import com.stockapp.stockserver.entity.Stocks;
import com.stockapp.stockserver.enums.StockDataTypeType;
import com.stockapp.stockserver.model.CallbackResult;
import com.stockapp.stockserver.model.ChartData;
import com.stockapp.stockserver.model.TurnKData;
import com.stockapp.stockserver.repo.StockDataMARepository;
import com.stockapp.stockserver.repo.StockDataTurnRepository;
import com.stockapp.stockserver.repo.StockPriceLevelRepository;
import com.stockapp.stockserver.repo.StockWantsRepository;
import com.stockapp.stockserver.repo.StocksRepository;
import com.stockapp.stockserver.utils.BeanUtils;
import com.stockapp.stockserver.utils.NumberUtils;

@Service
@EnableAsync
public class StockService extends AbstractService {
    private static final Logger logger = LoggerFactory.getLogger(StockService.class);

    @Autowired
    private BizDateService bizDateService;

    @Autowired
    private StockCommonService stockCommonService;
    @Autowired
    private StockDataTurnRepository stockDataTurnRepository;
    @Autowired
    private StockDataMARepository stockDataMARepository;
    @Autowired
    private StockPriceLevelRepository stockPriceLevelRepository;
    @Autowired
    StocksRepository stocksRepository;
    @Autowired
    StockWantsRepository stockWantsRepository;

    public Map<String, Object> queryStockTurnDataK(String stockId, String dataTypeString, Integer dataCount) {
        try {
            Optional<Stocks> stockOptional = stocksRepository.findById(stockId);
            if (stockOptional.isEmpty()) {
                return null;
            }

            StockDataTypeType dataType = StockDataTypeType.find(dataTypeString);

            if (dataType.isUNKNOWN()) {
                dataType = StockDataTypeType.DAYILY;
            }

            // 交易日就去更新最新資料
//            if (bizDateService.isBizDate(LocalDateTime.now(ZoneId.of("Asia/Taipei")).toLocalDate())) {
                // 先更新最新資料
                stockCommonService.updateStockData(stockId, dataType, dataCount).get();

                List<Future<CallbackResult>> result2 = new ArrayList<Future<CallbackResult>>();
                result2.add(stockCommonService.updateDataTurn(stockId, dataType.getCode(), dataCount));
                for (Future<CallbackResult> r : result2) {
                    r.get();
                }
//            }
            List<TurnKData> charts = new ArrayList<TurnKData>();

            // 資料第一筆為最新資料
            List<StockData> datas = stockDataRepository.findDataDesc(stockId, dataType.getCode(), dataCount.intValue());
            for (int i = 0; i < datas.size() - 6; i++) {
                TurnKData chart = new TurnKData();

                StockData data = datas.get(i);
                Optional<StockDataTurn> turnOptional = stockDataTurnRepository.findById(data.getId());
                if (turnOptional.isEmpty() || turnOptional.get().getMiddle() == null) {
                    // 先做一次轉折資料的更新
                    stockCommonService.updateDataTurn(stockId, dataType.getCode(), dataCount).get();
                    turnOptional = stockDataTurnRepository.findById(data.getId());
                }

                StockDataTurn ma = turnOptional.get();
                BeanUtils.copyProperties(chart, data);
                if (ma != null) {
                    BeanUtils.copyProperties(chart, ma);
                } else {
                    logger.error("copyProperties ma error : {}", data.getId());
                }
                chart.setStockId(stockOptional.get().getStockId());
                chart.setStockName(stockOptional.get().getStockName());
                chart.setDataTime(data.getId().getDataTime());
                chart.setDataType(dataType.getCode());

                charts.add(0, chart);
            }
            // 算出後三天的價格，開高低收都直接拿最後一筆取代
            StockData lastdata = datas.get(0);
            for (int i = 0; i < 3; i++) {
                StockData data = new StockData();
                BeanUtils.copyProperties(data, lastdata);
                LocalDateTime newDate = lastdata.getDataDate().plusDays(i + 1);
                data.setDataDate(newDate);

                StockDataId id = new StockDataId();
                id.setDataTime(newDate.atZone(ZoneId.of("Asia/Taipei")).toInstant().toEpochMilli());
                id.setDataType(lastdata.getId().getDataType());
                id.setStockId(lastdata.getId().getStockId());
                data.setId(id);
                datas.add(0, data);
            }

            Collections.sort(datas, new StockDataComparator());

            LocalDateTime baseDate = datas.get(datas.size()-1).getDataDate();
            for (int i = 0; i < 3; i++) {
                LocalDateTime calDate = baseDate;
                if (dataType.isDAYILY()) {
                    calDate = baseDate.plusDays(i + 1);
                } else if (dataType.isWEEK()) {
                    calDate = baseDate.plusDays(7 * (i + 1));
                } else if (dataType.isMONTH()) {
                    calDate = baseDate.plusMonths(i + 1);
                }

                int currentIndex = datas.size() - (3 - i);
                StockData data = datas.get(currentIndex);
                TurnKData ma = new TurnKData();
                ma.setStockId(data.getId().getStockId());
                ma.setDataTime(calDate.atZone(ZoneId.of("Asia/Taipei")).toInstant().toEpochMilli());
                ma.setDataType(data.getId().getDataType());
                ma.setOpen(data.getOpen());
                ma.setHigh(data.getHigh());
                ma.setLow(data.getLow());
                ma.setClose(data.getClose());
                BeanUtils.copyProperties(ma, data);
                // 先把暫時產生的放到list中
                charts.add(ma);

                // DD開盤破站不上空
                Double level9 = 0D;
                for (int j = 0; j < 4; j++) {
                    StockData data4 = datas.get(currentIndex - j);
                    level9 += data4.getOpen() + data4.getHigh() + data4.getLow() + data4.getClose();
                }
                ma.setValue1(NumberUtils.round(level9 / 16D, "##.00"));

                // 高控多停利
                double high11 = 0D;
                double low11 = 99999D;
                for (int j = currentIndex - 11; j < currentIndex; j++) {
                    StockData data2 = datas.get(j);
                    if (data2.getHigh() >= high11) {
                        high11 = data2.getHigh();
                    }
                    if (data2.getLow() <= low11) {
                        low11 = data2.getLow();
                    }
                }
                ma.setValue5(NumberUtils.round((high11 * 2 + low11) / 3, "##.00"));

                // 易多空線
                ma.setValue2(NumberUtils.round((ma.getValue5() + charts.get(charts.size() - 1).getValue5()
                        + charts.get(charts.size() - 2).getValue5()) / 3, "##.00"));

                // 超強
                Double value = 0D;
                for (int j = currentIndex - 19; j <= currentIndex; j++) {
                    StockData data2 = datas.get(j);
                    value += data2.getClose() + data2.getClose() + data2.getLow();
                }
                ma.setValue3(NumberUtils.round(value * 1.0618 / 60, "##.00"));

                // 乖離短賣
                ma.setValue4(NumberUtils.round(value * 1.0874 / 60, "##.00"));

                // 低控空回補
                ma.setValue6(NumberUtils.round((high11 + low11 * 2) / 3, "##.00"));

                double high12 = 0D;
                double low12 = 99999D;
                for (int j = currentIndex - 12; j < currentIndex; j++) {
                    StockData data2 = datas.get(j);
                    if (data2.getHigh() >= high12) {
                        high12 = data2.getHigh();
                    }
                    if (data2.getLow() <= low12) {
                        low12 = data2.getLow();
                    }
                }
                // 嘎空點
                ma.setValue7(NumberUtils.round((high12 - low12) * 0.84 + low12, "##.00"));

                // D殺多
                ma.setValue8(NumberUtils.round((high12 - low12) * 0.16 + low12, "##.00"));

                double high_avg6 = 0D;
                for (int j = currentIndex - 5; j <= currentIndex; j++) {
                    StockData data2 = datas.get(j);
                    high_avg6 += data2.getHigh();
                }
                high_avg6 = NumberUtils.round((high_avg6) / 6, "##.00");

                double low_avg13 = 0D;
                for (int j = currentIndex - 12; j <= currentIndex; j++) {
                    StockData data2 = datas.get(j);
                    low_avg13 += data2.getLow();
                }
                low_avg13 = NumberUtils.round((low_avg13) / 13, "##.00");

                double high_avg26 = 0D;
                for (int j = currentIndex - 25; j <= currentIndex; j++) {
                    StockData data2 = datas.get(j);
                    high_avg26 += data2.getHigh();
                }
                high_avg26 = NumberUtils.round((high_avg26) / 26, "##.00");

                // 日多空中軸
                ma.setMiddle(NumberUtils.round((high_avg6 + low_avg13 + high_avg26) / 3, "##.00"));

                // 末跌
                ma.setValue9(NumberUtils.round(ma.getMiddle() * (1 - 0.0764), "##.00"));

                // 日續跌
                ma.setValue10(NumberUtils.round(ma.getMiddle() * (1 - 0.0618), "##.00"));

                // 破續跌續空
                ma.setValue11(NumberUtils.round(ma.getMiddle() * (1 - 0.05), "##.00"));

                // 超跌17
                ma.setValue29(NumberUtils.round(ma.getMiddle() * (1 - 0.123), "##.00"));

                // 超跌16
                ma.setValue30(NumberUtils.round(ma.getMiddle() * (1 - 0.0989), "##.00"));

                // 超跌15
                ma.setValue31(NumberUtils.round(ma.getMiddle() * (1 - 0.088), "##.00"));

                // 碰主跌=搶短點
                ma.setValue12(NumberUtils.round(ma.getMiddle() * (1 - 0.0382), "##.00"));

                // 碰主跌3
                ma.setValue14(NumberUtils.round(ma.getMiddle() * (1 - (0.0236 + 0.0382) / 2), "##.00"));

                // 彈仍跌(放空)
                ma.setValue15(NumberUtils.round(ma.getMiddle() * (1 - 0.0236), "##.00"));

                // 回跌
                ma.setValue16(NumberUtils.round(ma.getMiddle() * (1 - 0.0191), "##.00"));

                // 回檔買點/反彈賣點
                ma.setValue17(NumberUtils.round(ma.getMiddle() * (1 - 0.0146), "##.00"));

                // 盤跌
                ma.setValue19(NumberUtils.round(ma.getMiddle() * (1 - 0.0087446), "##.00"));

                // T盤漲
                ma.setValue20(NumberUtils.round(ma.getMiddle() * 1.0087446, "##.00"));

                // 反彈峰B
                ma.setValue21(NumberUtils.round(ma.getMiddle() * 1.01236, "##.00"));

                // 續漲-不破續多
                ma.setValue22(NumberUtils.round(ma.getMiddle() * 1.0236, "##.00"));

                // 過起漲3
                ma.setValue23(NumberUtils.round(ma.getMiddle() * (1 + (0.0382 + 0.0236) / 2), "##.00"));

                // 過起漲
                ma.setValue24(NumberUtils.round(ma.getMiddle() * 1.0382, "##.00"));

                // 日續漲
                ma.setValue25(NumberUtils.round(ma.getMiddle() * 1.05, "##.00"));

                // 主升
                ma.setValue26(NumberUtils.round(ma.getMiddle() * 1.0618, "##.00"));

                // 強波2000點
                ma.setValue27(NumberUtils.round(ma.getMiddle() * 1.0764, "##.00"));
            }

            Collections.sort(charts, new TurnKDataComparator());

            Map<String, Object> result = new HashMap<String, Object>();
            List<List<Object>> sdatas = new ArrayList<List<Object>>();
            for (TurnKData chart : charts) {
                List<Object> sdata = new ArrayList<Object>();
                sdata.add(chart.getDataTime());
                sdata.add(chart.getOpen());
                sdata.add(chart.getHigh());
                sdata.add(chart.getLow());
                sdata.add(chart.getClose());
                sdatas.add(sdata);
            }
            result.put("sdata", sdatas);
            result.put("sname", stockOptional.get().getStockName());
            result.put("charts", charts);

            return result;
        } catch (Exception e) {
            logger.error("fail : ", e);
        }
        return null;
    }

    public StockInfo findStockInfo(String id) {
        StockInfo info = new StockInfo();

        try {
            Optional<Stocks> stock = stocksRepository.findById(id);
            if (stock.isPresent()) {
                info.setStockId(id);
                info.setStockName(stock.get().getStockName());
                info.setStockType("stock");
            } else {
                Optional<StockWants> wants = stockWantsRepository.findById(id);
                if (wants.isPresent()) {
                    info.setStockId(id);
                    info.setStockName(wants.get().getWarrantAbbr());
                    info.setUnderlyingCode(wants.get().getUnderlyingCode());
                    info.setUnderlyingName(wants.get().getUnderlyingName());
                    info.setStockType("wants");
                }
            }
        } catch (Exception e) {
            logger.error("fail : ", e);
        }

        return info;
    }

    public List<Stocks> findStocks() {
        return stocksRepository.findAll();
    }

    public @ResponseBody Map<String, Object> queryStockTurnData(String stockId, String stockDataType, Integer dataCount) {
        try {
            if (StringUtils.isBlank(stockId)) {
                return null;
            }

            if (dataCount == null) {
                dataCount = Integer.parseInt("150");
            }
            StockDataTypeType dataType = StockDataTypeType.find(stockDataType);
            if (dataType.isUNKNOWN()) {
                dataType = StockDataTypeType.DAYILY;
            }

            // 交易日就去更新最新資料
//            if (bizDateService.isBizDate(LocalDateTime.now(ZoneId.of("Asia/Taipei")).toLocalDate())) {
                forceUpdateStockDatas(stockId, dataType, dataCount);
//            }
            List<ChartData> charts = new ArrayList<ChartData>();

            // 資料第一筆為最新資料
            int findCount = dataCount < 15 ? 15 : dataCount;
            Stocks stock = stocksRepository.findById(stockId).get();
            List<StockData> datas = stockDataRepository.findDataDesc(stockId, dataType.getCode(), findCount);

            // 如果發現沒有資料，強迫做一次更新
            if (datas == null || datas.isEmpty()) {
                forceUpdateStockDatas(stockId, dataType, dataCount);
                datas = stockDataRepository.findDataDesc(stockId, dataType.getCode(), findCount);
            }

            for (int i = 0; i < datas.size() - 6; i++) {
                ChartData chart = new ChartData();

                StockData data = datas.get(i);
                Optional<StockDataMA> maOptional = stockDataMARepository.findById(data.getId());
                Optional<StockPriceLevel> levelOptional = stockPriceLevelRepository.findById(data.getId());
                if (maOptional.isEmpty() || levelOptional.isEmpty() || levelOptional.get().getH12() == null) {
                    logger.error("queryStockTurnData not find h12 " + data.getId().toString() + "-" + data.getDateString());
                    stockCommonService.updateDataMAHL(stockId, dataType.getCode(), dataCount).get();
                    stockCommonService.updateDataLevel(stockId, dataType.getCode(), dataCount).get();
                    stockCommonService.updateDataTurn(stockId, dataType.getCode(), dataCount).get();
                    maOptional = stockDataMARepository.findById(data.getId());
                    levelOptional = stockPriceLevelRepository.findById(data.getId());
                }

                BeanUtils.copyProperties(chart, data);
                if (maOptional.isPresent()) {
                    BeanUtils.copyProperties(chart, maOptional.get());
                } else {
                    logger.error("==> copyProperties ma error : {}", data.getId());
                }
                if (levelOptional.isPresent()) {
                    BeanUtils.copyProperties(chart, levelOptional.get());
                    chart.setTurnPrice(levelOptional.get().getTurn());
                } else {
                    logger.error("==> copyProperties level error : {}", data.getId());
                }
                chart.setStockId(stock.getStockId());
                chart.setStockName(stock.getStockName());
                chart.setDataTime(data.getId().getDataTime());
                chart.setDataType(dataType.getCode());

                charts.add(0, chart);
            }
            // 算出後三天的價格，開高低收都直接拿最後一筆取代
            LocalDateTime baseDate = datas.get(0).getDataDate();
            for (int i = 0; i < 3; i++) {
                LocalDateTime calDate = baseDate;
                if (dataType.isDAYILY()) {
                    calDate = baseDate.plusDays(i + 1);
                } else if (dataType.isWEEK()) {
                    calDate = baseDate.plusDays(7 * (i + 1));
                } else if (dataType.isMONTH()) {
                    calDate = baseDate.plusMonths(i + 1);
                }

                ChartData chart = new ChartData();
                chart.setStockId(datas.get(0).getId().getStockId());
                chart.setDataTime(calDate.atZone(ZoneId.of("Asia/Taipei")).toInstant().toEpochMilli());
                chart.setDataType(datas.get(0).getId().getDataType());
                chart.setOpen(datas.get(0).getOpen());
                chart.setHigh(datas.get(0).getHigh());
                chart.setLow(datas.get(0).getLow());
                chart.setClose(datas.get(0).getClose());
                // 先把暫時產生的放到list中
                charts.add(chart);

                // h12 l12
                Double high1 = 0D;
                Double low1 = 99999D;
                for (int j = 0; j < 11 - i; j++) {
                    StockData data2 = datas.get(j);
                    if (data2.getHigh() >= high1) {
                        high1 = data2.getHigh();
                    }
                    if (data2.getLow() <= low1) {
                        low1 = data2.getLow();
                    }
                }
                // h12 l12
                Double high2 = 0D;
                Double low2 = 99999D;
                for (int j = 0; j < 12 - i; j++) {
                    StockData data2 = datas.get(j);
                    if (data2.getHigh() >= high2) {
                        high2 = data2.getHigh();
                    }
                    if (data2.getLow() <= low2) {
                        low2 = data2.getLow();
                    }
                }

                // 箱幅
                Double box = high1 - low1;
                Double box2 = high2 - low2;

                // 買耗
                chart.setLevel1(NumberUtils.round(high2 + box2 * 0.2, "##.00"));
                // 軋空
                chart.setLevel2(NumberUtils.round(low1 + box * 0.84, "##.00"));
                // 轉強
                chart.setLevel3(NumberUtils.round(low1 + box * 0.666, "##.00"));
                // 中軸
                chart.setLevel4(NumberUtils.round(low1 + box * 0.555, "##.00"));
                // 回撐
                chart.setLevel5(NumberUtils.round(low1 + box * 0.444, "##.00"));
                // 轉弱
                chart.setLevel6(NumberUtils.round(low1 + box * 0.333, "##.00"));
                // 殺多
                chart.setLevel7(NumberUtils.round(low1 + box * 0.16, "##.00"));
                // 賣耗
                chart.setLevel8(NumberUtils.round(low2 - box2 * 0.2, "##.00"));
                // 多空線
                Double level9 = 0D;
                Double averO = 0D;
                Double averH = 0D;
                Double averL = 0D;
                Double averC = 0D;
                for (int j = charts.size() - 1; j > charts.size() - 5; j--) {
                    ChartData data3 = charts.get(j);
                    level9 += data3.getOpen() + data3.getHigh() + data3.getLow() + data3.getClose();
                    averO += data3.getOpen();
                    averH += data3.getHigh();
                    averL += data3.getLow();
                    averC += data3.getClose();
                }
                double aver = Math.round(averO / 4D) + Math.round(averO / 4D) + Math.round(averO / 4D)
                        + Math.round(averO / 4D);
                chart.setLevel9(NumberUtils.round(level9 / 16D, "##.00"));

                // 轉折價 2*三天前收-六天前收
                double turnPrice = NumberUtils.round(2 * datas.get(2 - i).getClose() - datas.get(5 - i).getClose(), "##.00");
                chart.setTurnPrice(turnPrice);
            }

            Map<String, Object> result = new HashMap<String, Object>();
            List<List<Object>> sdatas = new ArrayList<List<Object>>();
            List<List<Object>> level1s = new ArrayList<List<Object>>();
            List<List<Object>> level2s = new ArrayList<List<Object>>();
            List<List<Object>> level3s = new ArrayList<List<Object>>();
            List<List<Object>> level4s = new ArrayList<List<Object>>();
            List<List<Object>> level5s = new ArrayList<List<Object>>();
            List<List<Object>> level6s = new ArrayList<List<Object>>();
            List<List<Object>> level7s = new ArrayList<List<Object>>();
            List<List<Object>> level8s = new ArrayList<List<Object>>();
            List<List<Object>> level9s = new ArrayList<List<Object>>();
            List<List<Object>> turns = new ArrayList<List<Object>>();
            for (ChartData chart : charts) {
                List<Object> sdata = new ArrayList<Object>();
                sdata.add(chart.getDataTime());
                sdata.add(chart.getOpen());
                sdata.add(chart.getHigh());
                sdata.add(chart.getLow());
                sdata.add(chart.getClose());
                sdatas.add(sdata);

                List<Object> turn = new ArrayList<Object>();
                turn.add(chart.getDataTime());
                turn.add(chart.getTurnPrice());
                turns.add(turn);

                List<Object> level1 = new ArrayList<Object>();
                level1.add(chart.getDataTime());
                level1.add(chart.getLevel1());
                level1s.add(level1);

                List<Object> level2 = new ArrayList<Object>();
                level2.add(chart.getDataTime());
                level2.add(chart.getLevel2());
                level2s.add(level2);

                List<Object> level3 = new ArrayList<Object>();
                level3.add(chart.getDataTime());
                level3.add(chart.getLevel3());
                level3s.add(level3);

                List<Object> level4 = new ArrayList<Object>();
                level4.add(chart.getDataTime());
                level4.add(chart.getLevel4());
                level4s.add(level4);

                List<Object> level5 = new ArrayList<Object>();
                level5.add(chart.getDataTime());
                level5.add(chart.getLevel5());
                level5s.add(level5);

                List<Object> level6 = new ArrayList<Object>();
                level6.add(chart.getDataTime());
                level6.add(chart.getLevel6());
                level6s.add(level6);

                List<Object> level7 = new ArrayList<Object>();
                level7.add(chart.getDataTime());
                level7.add(chart.getLevel7());
                level7s.add(level7);

                List<Object> level8 = new ArrayList<Object>();
                level8.add(chart.getDataTime());
                level8.add(chart.getLevel8());
                level8s.add(level8);

                List<Object> level9 = new ArrayList<Object>();
                level9.add(chart.getDataTime());
                level9.add(chart.getLevel9());
                level9s.add(level9);
            }
            result.put("sdata", sdatas);
            result.put("level1", level1s);
            result.put("level2", level2s);
            result.put("level3", level3s);
            result.put("level4", level4s);
            result.put("level5", level5s);
            result.put("level6", level6s);
            result.put("level7", level7s);
            result.put("level8", level8s);
            result.put("level9", level9s);
            result.put("sname", stock.getStockName());
            result.put("charts", charts);
            result.put("turns", turns);

            // 加上目前close價格在哪種關卡的範圍內
            String up = "";
            String down = "";
            // -4是因為後面三筆為虛擬數字
            ChartData chart = charts.get(charts.size() - 4);
            if (chart != null) {
                if (chart.getLevel1() >= chart.getClose()) {
                    up = "買耗:" + chart.getLevel1();
                    down = "軋空:" + chart.getLevel2();
                } else {
                    down = "買耗:" + chart.getLevel1();
                }
                if (chart.getLevel2() >= chart.getClose()) {
                    up = "軋空:" + chart.getLevel2();
                    down = "轉強:" + chart.getLevel3();
                }
                if (chart.getLevel3() >= chart.getClose()) {
                    up = "轉強:" + chart.getLevel3();
                    down = "中軸:" + chart.getLevel4();
                }
                if (chart.getLevel4() >= chart.getClose()) {
                    up = "中軸:" + chart.getLevel4();
                    down = "回撐:" + chart.getLevel5();
                }
                if (chart.getLevel5() >= chart.getClose()) {
                    up = "回撐:" + chart.getLevel5();
                    down = "轉弱:" + chart.getLevel6();
                }
                if (chart.getLevel6() >= chart.getClose()) {
                    up = "轉弱:" + chart.getLevel6();
                    down = "殺多:" + chart.getLevel7();
                }
                if (chart.getLevel7() >= chart.getClose()) {
                    up = "殺多:" + chart.getLevel7();
                    down = "賣耗:" + chart.getLevel8();
                }
                if (chart.getLevel8() >= chart.getClose()) {
                    up = "賣耗:" + chart.getLevel8();
                }
            }
            result.put(dataType.getCode() + 18, up);
            result.put(dataType.getCode() + 19, down);

            // 組合多空轉折
            result.put("close", datas.get(0).getClose());
            Double close = datas.get(0).getClose();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");
            for (int i = 1; i < 5; i++) {
                chart = charts.get(charts.size() - 5 + i);
                result.put(dataType.getCode() + i, formatter.format(LocalDateTime.ofEpochSecond(chart.getDataTime() / 1000, 0, ZoneId.of("Asia/Taipei").getRules().getOffset(LocalDateTime.now()))));
                result.put(dataType.getCode() + (i + 5), chart.getTurnPrice());
                result.put(dataType.getCode() + (i + 10), (chart.getTurnPrice() >= close) ? "空" : "多");
            }
            // 後第二轉折價>=後第一轉折價為多
            String turn1 = (charts.get(charts.size() - 2).getTurnPrice() < charts.get(charts.size() - 3).getTurnPrice())
                    ? "空"
                    : "多";
            // 後第三轉折價>=後第二轉折價為多
            String turn2 = (charts.get(charts.size() - 1).getTurnPrice() < charts.get(charts.size() - 2).getTurnPrice())
                    ? "空"
                    : "多";
            result.put(dataType.getCode() + 5, turn1);
            result.put(dataType.getCode() + 10, turn2);
            result.put(dataType.getCode() + 15, (StringUtils.equals(turn1, turn2)) ? "不變" : "轉折");
            String text16 = (StringUtils.equals(result.get(dataType.getCode() + 13).toString(),
                    result.get(dataType.getCode() + 14).toString())) ? "" : "留意轉折";
            result.put(dataType.getCode() + 16, text16);

            String text17 = "";
            if (close > charts.get(charts.size() - 3).getTurnPrice()
                    && close > charts.get(charts.size() - 2).getTurnPrice()
                    && close > charts.get(charts.size() - 1).getTurnPrice()) {
                text17 = "隔日收盤在" + datas.get(0).getHigh() + "之上，則可買進";
            } else if (close < charts.get(charts.size() - 3).getTurnPrice()
                    && close < charts.get(charts.size() - 2).getTurnPrice()
                    && close < charts.get(charts.size() - 1).getTurnPrice()) {
                text17 = "隔日收盤跌破" + datas.get(0).getLow() + "，則可放空";
            }
            result.put(dataType.getCode() + 17, text17);

            return result;
        } catch (Exception e) {
            logger.error("fail : ", e);
        }
        return null;
    }

    public String queryStockDataForExcel(String stockId, String stockDataType, Integer dataCount) {
        try {
            if (StringUtils.isBlank(stockId)) {
                return null;
            }

            StockDataTypeType dataType = StockDataTypeType.find(stockDataType);
            if (dataType.isUNKNOWN()) {
                dataType = StockDataTypeType.DAYILY;
            }

            queryStockTurnData(stockId, dataType.getCode(), dataCount);

            if (dataCount == null) {
                dataCount = Integer.parseInt("20");
            }
            // 由新到舊
            List<StockData> datas = findStockData(stockId, dataType.getCode(), dataCount.intValue());

            String result = "";
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            // 資料要倒著給
            for (int i = 0; i < datas.size(); i++) {
                StockData data = datas.get(i);

                if (!StringUtils.isBlank(result)) {
                    result += "%%";
                }
                if (StringUtils.equals(stockId, "FITX")) {
                    result += formatter.format(data.getDataDate()) + "," + data.getOpen() + "," + data.getHigh() + "," + data.getLow()
                            + "," + data.getClose() + "," + data.getVolume() + "," + data.getVolume2();
                } else {
                    result += formatter.format(data.getDataDate()) + "," + data.getOpen() + "," + data.getHigh() + "," + data.getLow()
                            + "," + data.getClose() + "," + data.getVolume();
                }
            }
            return result;
        } catch (Exception e) {
            logger.error("err {}", e);
        }

        return null;
    }

    public void updateStockData(Integer dataCount, String stockId, String dataType, String startId, String endId) {
        try {
            List<String> dataTypeList = new ArrayList<String>();
            if (StringUtils.isNotBlank(dataType)) {
                dataTypeList.add(dataType.trim().toUpperCase());
            } else {
                dataTypeList.add(StockDataTypeType.DAYILY.getCode());
                dataTypeList.add(StockDataTypeType.WEEK.getCode());
                dataTypeList.add(StockDataTypeType.MONTH.getCode());
            }

            List<Stocks> updateList = new ArrayList<Stocks>();
            List<Stocks> stocksList = stocksRepository.findAll();
            if (StringUtils.isNotBlank(stockId) &&
                    stocksRepository.findById(stockId).isPresent()) {
                stocksList = new ArrayList<Stocks>();
                stocksList.add(stocksRepository.findById(stockId).get());
            }
            for (Stocks s : stocksList) {
                String id = s.getStockId();

                boolean needUpdate = true;
                if ((StringUtils.isNotBlank(startId) && id.compareTo(startId) < 0)
                        || (StringUtils.isNotBlank(endId) && id.compareTo(endId) > 0)) {
                    needUpdate = false;
                    continue;
                }
                if (needUpdate) {
                    updateList.add(s);
                }
            }

            int splitSize = 6;
            int size = updateList.size() / splitSize;
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
            for (int i = 0; i <= size; i++) {
                int toIndex = splitSize * (i + 1);
                if (toIndex > updateList.size()) {
                    toIndex = updateList.size();
                }
                List<Stocks> subStocks = updateList.subList(i * splitSize, toIndex);
                List<Future<CallbackResult>> result = new ArrayList<Future<CallbackResult>>();

                for (Stocks s2 : subStocks) {
                    for (String dataTypeString : dataTypeList) {
                        result.add(stockCommonService.updateStockData(s2.getStockId(), StockDataTypeType.find(dataTypeString), dataCount));
                    }
                }

                List<Future<CallbackResult>> result2 = new ArrayList<Future<CallbackResult>>();
                for (Future<CallbackResult> r : result) {
                    CallbackResult cr = r.get();
                    if (cr.isResult()) {
                        logger.info("===> init data finish " + cr.getStockId() + "-" + cr.getDataType() + " on "
                                + formatter.format(LocalDateTime.now(ZoneId.of("Asia/Taipei"))));
                        // 更新所有ma hl gap
                        result2.add(stockCommonService.updateDataMAHL(cr.getStockId(), cr.getDataType(), dataCount));
                        result2.add(stockCommonService.updateDataLevel(cr.getStockId(), cr.getDataType(), dataCount));
                        result2.add(stockCommonService.updateDataTurn(cr.getStockId(), cr.getDataType(), dataCount));
                        for (Future<CallbackResult> r2 : result2) {
                            r2.get();
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void syncStockData() {
        List<Stocks> stocks = findStocks();

        int batchSize = 6;
        for (int i = 0; i < stocks.size(); i += batchSize) {
            int end = Math.min(i + batchSize, stocks.size());
            List<Stocks> batch = stocks.subList(i, end);

            List<Future<CallbackResult>> dailyFutures = new ArrayList<>();

            // 第一步：先跑所有股票的 DAYILY
            for (Stocks stock : batch) {
                Future<CallbackResult> future = stockCommonService.updateStockData(stock.getStockId(), StockDataTypeType.DAYILY, 365);
                dailyFutures.add(future);
            }

            // 等待所有 Daily 結果，成功的才跑 Week 和 Month
            for (int j = 0; j < dailyFutures.size(); j++) {
                Future<CallbackResult> future = dailyFutures.get(j);
                try {
                    CallbackResult result = future.get(); // 可加 timeout

                    if (result.isResult()) {
                        Stocks stock = batch.get(j);
                        // 只有 Daily 成功才進行後續
                        List<Future<CallbackResult>> nextFutures = new ArrayList<>();
                        nextFutures.add(stockCommonService.updateStockData(stock.getStockId(), StockDataTypeType.WEEK, 208));
                        nextFutures.add(stockCommonService.updateStockData(stock.getStockId(), StockDataTypeType.MONTH, 48));

                        for (Future<CallbackResult> f : nextFutures) {
                            f.get(); // 等待完成
                        }
                    } else {
                        logger.warn("Stock {} daily update failed, skipping week/month", batch.get(j).getStockId());
                    }
                } catch (Exception e) {
                    logger.error("Stock {} daily update error: {}", batch.get(j).getStockId(), e.getMessage(), e);
                }
            }
            // 等待15秒
            try {
                TimeUnit.SECONDS.sleep(15);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        logger.info("=== All stock data update finished ===");
    }

    private void forceUpdateStockDatas(String stockId, StockDataTypeType dataType, Integer dataCount) {
        try {
            // 先更新當時最新資料再回傳
            CallbackResult cr = stockCommonService.updateStockData(stockId, dataType, dataCount).get();

            // 成功才跑
            if (!cr.isResult()) {
                return;
            }
            List<Future<CallbackResult>> result2 = new ArrayList<Future<CallbackResult>>();
            result2.add(stockCommonService.updateDataMAHL(stockId, dataType.getCode(), dataCount));
            result2.add(stockCommonService.updateDataLevel(stockId, dataType.getCode(), dataCount));
            result2.add(stockCommonService.updateDataTurn(stockId, dataType.getCode(), dataCount));
            for (Future<CallbackResult> r : result2) {
                r.get();
            }
        } catch (Exception e) {
            logger.error("fail : ", e);
        }
    }
}