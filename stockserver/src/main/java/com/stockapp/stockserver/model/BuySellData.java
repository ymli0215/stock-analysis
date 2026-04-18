package com.stockapp.stockserver.model;

import java.io.Serializable;
import java.util.Date;

import org.apache.commons.lang3.time.DateFormatUtils;

import com.stockapp.stockserver.enums.StockDataTypeType;

import lombok.Data;

@Data
public class BuySellData implements Serializable {
	// 目前id使用http://histock.tw/的代碼(No)
	private String stockId;

	private String dataType = StockDataTypeType.UNKNOWN.getCode();

	// 紀錄資料為data的time值(long)，皆以當天早上8:00為時間標準
	private Long dataTime;

	// 紀錄資料為data的time值(long)，皆以當天早上8:00為時間標準
	private String dataString;

	private String stockName;

	/** 買點1 */
	private int buy1;

	/** 買點2 */
	private int buy2;

	/** 買點3 */
	private int buy3;

	/** 買點4 */
	private int buy4;

	/** 賣點1 */
	private int sell1;

	/** 賣點2 */
	private int sell2;

	/** 賣點3 */
	private int sell3;

	/** 賣點4 */
	private int sell4;

	/** 多頭排列 */
	private int u1 = 0;

	/** 7、13金叉 */
	private int u2 = 0;

	/** 7、21金叉 */
	private int u3 = 0;

	/** 7、53金叉 */
	private int u4 = 0;

	/** 13、21金叉 */
	private int u5 = 0;

	/** 13、53金叉 */
	private int u6 = 0;

	/** 21、53金叉 */
	private int u7 = 0;

	/** 空頭排列 */
	private int d1 = 0;

	/** 7、13死叉 */
	private int d2 = 0;

	/** 7、21死叉 */
	private int d3 = 0;

	/** 7、53死叉 */
	private int d4 = 0;

	/** 13、21死叉 */
	private int d5 = 0;

	/** 13、53死叉 */
	private int d6 = 0;

	/** 21、53死叉 */
	private int d7 = 0;

	public String getDataString() {
		if (dataTime == null) {
			return "";
		}
		return DateFormatUtils.format(new Date(dataTime), "yyyy/MM/dd");
	}

	public void setDataString(String dataString) {
		this.dataString = dataString;
	}

}
