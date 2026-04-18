package com.stockapp.stockserver.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class StockFinanId extends BaseEntity {
	// 目前id使用http://histock.tw/的代碼(No)
	@Column(name = "stockId", nullable = false, length = 50)
	private String stockId;

	//資料時間
	@Column(name = "time", nullable = false, length = 12)
	private String time;
	
	//年:Y 季:Q
	@Column(name = "type", nullable = false, length = 1)
	private String type;

	public StockFinanId() {

	}

	public String getStockId() {
		return stockId;
	}

	public void setStockId(String stockId) {
		this.stockId = stockId;
	}

	public String getTime() {
		return time;
	}

	public void setTime(String time) {
		this.time = time;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

}
