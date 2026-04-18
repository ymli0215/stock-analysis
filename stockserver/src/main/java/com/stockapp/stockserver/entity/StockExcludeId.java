package com.stockapp.stockserver.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class StockExcludeId extends BaseEntity {
	//目前id使用http://histock.tw/的代碼(No)
	@Column(name = "stockId", nullable = false, length = 50)
	private String stockId;

	//除權息資料年份
	@Column(name = "year", nullable = false)
	private Integer year;
	
	public StockExcludeId() {
		
	}

	public String getStockId() {
		return stockId;
	}

	public void setStockId(String stockId) {
		this.stockId = stockId;
	}

	public Integer getYear() {
		return year;
	}

	public void setYear(Integer year) {
		this.year = year;
	}

	
}
