package com.stockapp.stockserver.service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import com.stockapp.stockserver.entity.StockWants;
import com.stockapp.stockserver.repo.StockWantsRepository;

@Service
@EnableAsync
public class StockWantsService extends AbstractService {
	// 初始化 SLF4J Logger
	private static final Logger logger = LoggerFactory.getLogger(StockWantsService.class);

	private static final String CSV_URL = "https://www.tpex.org.tw/web/extend/warrant/wmndty/wntmand_result.php?l=zh-tw&o=data";

	private static final String CSV_URL2 = "https://www.twse.com.tw/rwd/zh/stock/warrantStock?response=csv";

	@Autowired
	StockWantsRepository stockWantsRepository;

	public StockWants queryStockwants(String warrantCode) {
		try {
			Optional<StockWants> wants = stockWantsRepository.findById(warrantCode);

			return wants.isPresent() ? wants.get() : new StockWants();
		} catch (Exception e) {
			logger.error("{}", e);
		}

		return null;
	}

	public void importFromCsv() {

		// 1️⃣ 先 TRUNCATE
		stockWantsRepository.truncateTable();

		doFile1();
		doFile2();

		return;
	}

	private void doFile1() {
		List<StockWants> result = new ArrayList<>();

		try (InputStream is = new URL(CSV_URL).openStream();
				Reader reader = new InputStreamReader(is, Charset.forName("UTF-8"));) {

			CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader);

			for (CSVRecord record : parser) {
				StockWants entity = mapToEntity(record);
				result.add(entity);
			}

			stockWantsRepository.saveAll(result);

		} catch (Exception e) {
			throw new RuntimeException("匯入權證 CSV 失敗", e);
		}
	}

	private void doFile2() {
		try (InputStream is = new URL(CSV_URL2).openStream();
				InputStreamReader isr = new InputStreamReader(is, "MS950");
				BufferedReader reader = new BufferedReader(isr)) {
			// 1️⃣ 手動讀掉前兩列（垃圾說明列）
			reader.readLine(); // 標題
			reader.readLine(); // 欄位群組說明

			// 2️⃣ 用第三列當 header
			CSVParser parser = CSVFormat.DEFAULT
					.builder()
					.setHeader()
					.setAllowMissingColumnNames(true)
					.setSkipHeaderRecord(true)
					.build().parse(reader);

			List<StockWants> list = new ArrayList<>();

			for (CSVRecord record : parser) {
				StockWants entity = mapToEntity2(record);
				list.add(entity);
			}

			stockWantsRepository.saveAll(list);

		} catch (Exception e) {
			throw new RuntimeException("匯入上市權證 CSV 失敗", e);
		}
	}

	// ======================
	// 核心 mapping 邏輯
	// ======================
	private StockWants mapToEntity(CSVRecord r) {

		StockWants e = new StockWants();

		e.setWarrantCode(clean(r.get("代號")));
		e.setWarrantAbbr(clean(r.get("名稱")));
		e.setUnderlyingCode(clean(r.get("標的代號")));
		e.setUnderlyingName(clean(r.get("名稱")));
		e.setWarrantType(clean(r.get("種類")));
		e.setExerciseMethod(clean(r.get("美式或歐式")));

		e.setListingDate(convertRocDate(r.get("上櫃日期")));
		e.setStartDate(convertRocDate(r.get("上櫃日期")));
		e.setEndTradeDate(convertRocDate(r.get("到期日")));
		e.setDueDate(convertRocDate(r.get("到期日")));

		e.setExerciseRatio(parseDecimal(r.get("最新行使比例")));
		e.setExercisePrice(parseDecimal(r.get("最新履約價")));

		return e;
	}
	
	private StockWants mapToEntity2(CSVRecord r) {
		StockWants e = new StockWants();

	    e.setWarrantCode(clean(r.get("權證代號")));
	    e.setWarrantAbbr(clean(r.get("權證簡稱")));

	    e.setUnderlyingCode(clean(r.get("標的代號")));
	    e.setUnderlyingName(clean(r.get("標的名稱")));

	    e.setWarrantType(clean(r.get("權證類型")));
	    e.setExerciseMethod(clean(r.get("履約方式")));

	    e.setListingDate(convertRocDate(r.get("上市日期")));
	    e.setStartDate(convertRocDate(r.get("履約開始日")));
	    e.setEndTradeDate(convertRocDate(r.get("最後交易日")));
	    e.setDueDate(convertRocDate(r.get("履約截止日")));

	    e.setExerciseRatio(parseDecimal(r.get("行使比例")));
	    e.setExercisePrice(parseDecimal(r.get("履約價格(元)/點數")));

	    return e;
	}

	// ======================
	// 工具方法
	// ======================

	private String clean(String v) {
		if (v == null)
			return null;
		return v.replace("=", "").replace("\"", "").trim();
	}

	/**
	 * 民國年：114年07月31日 → 2025-07-31
	 */
	private String convertRocDate(String v) {
		if (v == null || v.isBlank())
			return null;

		Pattern p = Pattern.compile("(\\d+)年(\\d+)月(\\d+)日");
		Matcher m = p.matcher(v);
		if (!m.find())
			return null;

		int rocYear = Integer.parseInt(m.group(1));
		int year = rocYear + 1911;

		String month = String.format("%02d", Integer.parseInt(m.group(2)));
		String day = String.format("%02d", Integer.parseInt(m.group(3)));

		return year + "-" + month + "-" + day;
	}

	private BigDecimal parseDecimal(String v) {
		if (v == null || v.isBlank())
			return null;

		return new BigDecimal(v.replace(",", "").trim());
	}
}
