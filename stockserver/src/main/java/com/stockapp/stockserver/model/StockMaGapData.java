package com.stockapp.stockserver.model;

import java.util.List;

import com.stockapp.stockserver.entity.BaseEntity;
import com.stockapp.stockserver.entity.StockGap;

import lombok.Data;

@Data
public class StockMaGapData extends BaseEntity {
	// 目前id使用http://histock.tw/的代碼(No)
	private String stockId;

	private String stockName;

	// 紀錄資料為data的time值(long)，皆以當天早上8:00為時間標準
	private Long dataTime;

	private Double open;

	private Double close;

	private Double high;

	private Double low;

	private Double k9;

	private Double d9;

	private Double rsi6;

	private Double rsi12;

	private Integer volume;

	private Double ma3;

	private Double ma6;

	private Double ma13;

	private Double ma5;

	private Double ma10;

	private Double ma20;

	private Double ma60;

	private Double ma120;

	private Double ma240;

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

	// 買權未平倉量
	private Integer bCallOI;

	// 買權未平倉均價
	private Double bCallAvgPrice;

	// 賣權未平倉量
	private Integer bPutOI;

	// 賣權未平倉均價
	private Double bPutAvgPrice;
	
	// 缺口資料
	private List<StockGap> gaps;

}
