package com.stockapp.stockserver.service.cal.vo;

import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CalResult {
	Logger logger = LoggerFactory.getLogger(CalResult.class);

	public enum ITEM {
		V1, // ROE
		V2, // EPS
		V3, // 每股淨值
		V4, // 毛利率
		V5, // 營利率
		V6, // 淨利率
		V7, // 流動比
		V8, // 速動比
		V9, // 負債比
		V10, // 現金流量比
		V11// 營運現金流/稅後淨利
	}

	private Map<ITEM, CalNum> data = new TreeMap<>();

	public void add(CalNum val, ITEM key) {
		if (logger.isTraceEnabled()) {
			logger.trace(String.format("add key:%-20s  val:%s", key, val));
		}
		data.put(key, val);
	}

	public CalNum getVal(ITEM key) {
		return data.get(key);
	}
}
