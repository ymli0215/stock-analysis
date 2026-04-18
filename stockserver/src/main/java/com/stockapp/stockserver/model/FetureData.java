package com.stockapp.stockserver.model;

import java.io.Serializable;
import java.util.Date;

import org.apache.commons.lang3.time.DateFormatUtils;

import com.stockapp.stockserver.enums.StockDataTypeType;

import lombok.Data;

@Data
public class FetureData implements Serializable {
	// 目前id使用http://histock.tw/的代碼(No)
	private String stockId;

	private String dataType = StockDataTypeType.UNKNOWN.getCode();

	// 紀錄資料為data的time值(long)，皆以當天早上8:00為時間標準
	private Long dataTime;

	// 紀錄資料為data的time值(long)，皆以當天早上8:00為時間標準
	private String dataString;

	private String stockName;

	private Double open;

	private Double close;

	private Double high;

	private Double low;

	private Integer volume;

	// 買前五大交易人合計
	private Integer buyMonthTop5;

	// 買前十大交易人合計
	private Integer buyMonthTop10;

	// 買前五大交易人合計 特定法人
	private Integer buyMonthTop5S;

	// 買前十大交易人合計 特定法人
	private Integer buyMonthTop10S;

	// 買前五大交易人合計 所有契約
	private Integer buyAllTop5;

	// 買前十大交易人合計 所有契約
	private Integer buyAllTop10;

	// 買前五大交易人合計 特定法人 所有契約
	private Integer buyAllTop5S;

	// 買前十大交易人合計 特定法人 所有契約
	private Integer buyAllTop10S;

	// 賣前五大交易人合計
	private Integer sellMonthTop5;

	// 賣前十大交易人合計
	private Integer sellMonthTop10;

	// 賣前五大交易人合計 特定法人
	private Integer sellMonthTop5S;

	// 賣前十大交易人合計 特定法人
	private Integer sellMonthTop10S;

	// 賣前五大交易人合計 所有契約
	private Integer sellAllTop5;

	// 賣前十大交易人合計 所有契約
	private Integer sellAllTop10;

	// 賣前五大交易人合計 特定法人 所有契約
	private Integer sellAllTop5S;

	// 賣前十大交易人合計 特定法人 所有契約
	private Integer sellAllTop10S;

	// 全市場未沖銷部位數 月
	private Integer monthTotalOpen;

	// 全市場未沖銷部位數 所有
	private Integer allTotalOpen;

	// 多方 自營 未平倉
	private Integer buyOpens1;

	// 空方 自營 未平倉
	private Integer sellOpens1;

	// 多方 投信 未平倉
	private Integer buyOpens2;

	// 空方 投信 未平倉
	private Integer sellOpens2;

	// 多方 外資 未平倉
	private Integer buyOpens3;

	// 空方 外資 未平倉
	private Integer sellOpens3;

	// 多方 自營 未平倉 小型
	private Integer buyOpens1Small;

	// 空方 自營 未平倉 小型
	private Integer sellOpens1Small;

	// 多方 投信 未平倉 小型
	private Integer buyOpens2Small;

	// 空方 投信 未平倉 小型
	private Integer sellOpens2Small;

	// 多方 外資 未平倉 小型
	private Integer buyOpens3Small;

	// 空方 外資 未平倉 小型
	private Integer sellOpens3Small;

	public String getDataString() {
		if (dataTime == null) {
			return "";
		}
		return DateFormatUtils.format(new Date(dataTime), "yyyy/MM/dd");
	}

	public void setDataString(String dataString) {
		this.dataString = dataString;
	}

}
