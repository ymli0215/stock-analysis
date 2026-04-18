package com.stockapp.stockserver.entity;

import com.stockapp.stockserver.enums.StockDataTypeType;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;


@Embeddable
public class StockDataId extends BaseEntity {
	//目前id使用http://histock.tw/的代碼(No)
	@Column(name = "stockId", nullable = false, length = 50)
	private String stockId;
	
	@Column(name = "dataType", nullable = false, length = 2)
	private String dataType = StockDataTypeType.UNKNOWN.getCode();

	//紀錄資料為data的time值(long)，皆以台灣早上8:00為時間標準
	@Column(name = "dataTime", nullable = false)
	private Long dataTime;
	
	public StockDataId() {
		
	}
	
	public String toString() {
		return this.getStockId() + "-" +this.getDataType() + "-" + this.getDataTime();
	}

	public String getStockId() {
		return stockId;
	}

	public void setStockId(String stockId) {
		this.stockId = stockId;
	}

	public String getDataType() {
		return dataType;
	}

	public void setDataType(String dataType) {
		this.dataType = dataType;
	}

	public Long getDataTime() {
		return dataTime;
	}

	public void setDataTime(Long dataTime) {
		this.dataTime = dataTime;
	}
}
