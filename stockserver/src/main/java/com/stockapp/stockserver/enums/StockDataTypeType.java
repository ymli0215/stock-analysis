package com.stockapp.stockserver.enums;

import org.apache.commons.lang3.StringUtils;

public enum StockDataTypeType implements IEnum {
	DAYILY("D"),
	WEEK("W"),
	MONTH("M"),
	UNKNOWN(UNKNOWN_STR_CODE);
	
	private String code;
	
	StockDataTypeType(String code) {
		this.code = code;
		
	}
	public static StockDataTypeType find(String code) {
		for (StockDataTypeType value : StockDataTypeType.values()) {
			if (value.getCode().equals(code)) {
				return value;
			}
		}

		return StockDataTypeType.UNKNOWN;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}
	
	public boolean isUNKNOWN() {
		return StringUtils.equals(this.getCode(), UNKNOWN.getCode());
	}
	
	public boolean isDAYILY() {
		return StringUtils.equals(this.getCode(), DAYILY.getCode());
	}
	
	public boolean isWEEK() {
		return StringUtils.equals(this.getCode(), WEEK.getCode());
	}
	
	public boolean isMONTH() {
		return StringUtils.equals(this.getCode(), MONTH.getCode());
	}
}
