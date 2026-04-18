package com.stockapp.stockserver.model;

import java.io.Serializable;
import java.util.Date;

import org.apache.commons.lang3.time.DateFormatUtils;

import com.stockapp.stockserver.enums.StockDataTypeType;

import lombok.Data;

@Data
public class ChartData implements Serializable {
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

	private Double ma3;

	private Double ma5;

	private Double ma8;

	private Double ma10;

	private Double ma13;

	private Double ma21;

	private Double ma34;

	private Double ma55;

	private Double ma89;

	private Double ma144;

	private Double ma233;

	// 買耗
	private Double level1;

	// 軋空
	private Double level2;

	// 轉強
	private Double level3;

	// 中軸
	private Double level4;

	// 回撐
	private Double level5;

	// 轉弱
	private Double level6;

	// 殺多
	private Double level7;

	// 賣耗
	private Double level8;

	// 多空線
	private Double level9;

	// 轉折價
	private Double turnPrice;

	private Double ema3;

	private Double ema5;

	private Double ema7;

	private Double ema8;

	private Double ema10;

	private Double ema13;

	private Double ema21;

	private Double ema34;

	private Double ema53;

	private Double ema55;

	private Double ema89;

	private Double ema144;

	private Double ema233;

	// 買權未平倉量
	private Integer bCallOI;

	// 買權未平倉均價
	private Double bCallAvgPrice;

	// 賣權未平倉量
	private Integer bPutOI;

	// 賣權未平倉均價
	private Double bPutAvgPrice;

	// 多空買賣金額差距
	private Integer bsDiffAmont;


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
