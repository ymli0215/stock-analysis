package com.stockapp.stockserver.model;

import java.io.Serializable;

public class CallbackResult implements Serializable {
	private boolean result;
	private String stockId;
	private String dataType;
	public boolean isResult() {
		return result;
	}
	public void setResult(boolean result) {
		this.result = result;
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
	
}
