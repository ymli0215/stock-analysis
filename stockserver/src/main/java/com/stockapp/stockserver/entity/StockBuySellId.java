package com.stockapp.stockserver.entity;

import com.stockapp.stockserver.enums.StockDataTypeType;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class StockBuySellId extends BaseEntity {
	//目前id使用http://histock.tw/的代碼(No)
	@Column(name = "stockId", nullable = false, length = 50)
	private String stockId;

	//紀錄資料為data的time值(long)，皆以當天早上8:00為時間標準
	@Column(name = "dataTime", nullable = false)
	private Long dataTime;
	
	@Column(name = "dataType", nullable = false, length = 2)
	private String dataType = StockDataTypeType.UNKNOWN.getCode();
	
	public StockBuySellId() {
		
	}

	public String getStockId() {
		return stockId;
	}

	public void setStockId(String stockId) {
		this.stockId = stockId;
	}

	public Long getDataTime() {
		return dataTime;
	}

	public void setDataTime(Long dataTime) {
		this.dataTime = dataTime;
	}

	public String getDataType() {
		return dataType;
	}

	public void setDataType(String dataType) {
		this.dataType = dataType;
	}
	
	public String getIdString() {
		return this.getStockId()+"-"+this.getDataType()+"-"+this.getDataTime();
	}
	
}
