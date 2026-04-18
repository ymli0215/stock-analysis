package com.stockapp.stockserver.model;

import com.stockapp.stockserver.enums.StockDataTypeType;

import lombok.Data;

@Data
public class StockHighLowData {
	// 目前id使用http://histock.tw/的代碼(No)
	private String stockId;

	private String dataType = StockDataTypeType.UNKNOWN.getCode();

	// 紀錄資料為data的time值(long)，皆以當天早上8:00為時間標準
	private Long dataTime;
	
	private String stockName;
	
	private boolean high3;
	
	private boolean high5;
	
	private boolean high8;
	
	private boolean high13;
	
	private boolean high21;
	
	private boolean high34;
	
	private boolean high55;
	
	private boolean high89;
	
	private boolean high144;
	
	private boolean high233;
	
	private boolean low3;
	
	private boolean low5;
	
	private boolean low8;
	
	private boolean low13;
	
	private boolean low21;
	
	private boolean low34;
	
	private boolean low55;
	
	private boolean low89;
	
	private boolean low144;
	
	private boolean low233;

}
