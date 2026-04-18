package com.stockapp.stockserver.enums;


public enum BizDateTypeType implements IEnum{
	HOLIDAY(0),
	WORKDAY(1),
	UNKNOWN(UNKNOWN_INT_CODE);
	
	private int code;
	
	BizDateTypeType(int code) {
		this.code = code;
		
	}
	public static BizDateTypeType find(int code) {
		for (BizDateTypeType value : BizDateTypeType.values()) {
			if (value.getCode() == code) {
				return value;
			}
		}

		return BizDateTypeType.UNKNOWN;
	}

	public int getCode() {
		return code;
	}

	public void setCode(int code) {
		this.code = code;
	}
}
