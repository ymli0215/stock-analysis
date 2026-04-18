package com.stockapp.stockserver.controller.bean;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

public class StockInfo implements Serializable {
	
	/** 股票代碼 */
	private String stockId;

	/** 股票名稱 */
	private String stockName;

	/** 股票類別 */
	private String stockType;
	
	/** 標的代碼 */
    private String underlyingCode;
	
	/** 標的名稱 */
    private String underlyingName;

	public StockInfo() {
	}

	public String getStockId() {
		return stockId;
	}

	public void setStockId(String stockId) {
		this.stockId = stockId;
	}

	public String getStockName() {
		return stockName;
	}

	public void setStockName(String stockName) {
		this.stockName = stockName;
	}

	public String getStockType() {
		return stockType;
	}

	public void setStockType(String stockType) {
		this.stockType = stockType;
	}

	public String getUnderlyingName() {
		return underlyingName;
	}

	public void setUnderlyingName(String underlyingName) {
		this.underlyingName = underlyingName;
	}

	public String getUnderlyingCode() {
		return underlyingCode;
	}

	public void setUnderlyingCode(String underlyingCode) {
		this.underlyingCode = underlyingCode;
	}
}
