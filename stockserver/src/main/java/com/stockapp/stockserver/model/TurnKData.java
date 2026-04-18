package com.stockapp.stockserver.model;

import java.io.Serializable;
import java.util.Date;

import org.apache.commons.lang3.time.DateFormatUtils;

import com.stockapp.stockserver.enums.StockDataTypeType;

import jakarta.persistence.Column;
import lombok.Data;

@Data
public class TurnKData implements Serializable {
	// 目前id使用http://histock.tw/的代碼(No)
	private String stockId;

	private String dataType = StockDataTypeType.UNKNOWN.getCode();

	// 紀錄資料為data的time值(long)，皆以當天早上8:00為時間標準
	private Long dataTime;

	// 紀錄資料為data的time值(long)，皆以當天早上8:00為時間標準
	private String dataString;

	private String stockName;

	private Double open;

	private Double close;

	private Double high;

	private Double low;

	// 日多空中軸
	@Column
	private Double middle;

	// DD開盤破站不上空
	@Column
	private Double value1;

	// 易多空線
	@Column
	private Double value2;

	// 超強
	@Column
	private Double value3;

	// 乖離短賣
	@Column
	private Double value4;

	// 高控多停利
	@Column
	private Double value5;

	// 低控空回補
	@Column
	private Double value6;

	// 嘎空點
	@Column
	private Double value7;

	// D殺多
	@Column
	private Double value8;

	// 末跌
	@Column
	private Double value9;

	// 日續跌
	@Column
	private Double value10;

	// 破續跌續空
	@Column
	private Double value11;

	// 碰主跌=搶短點
	@Column
	private Double value12;

	// 碰主跌3
	@Column
	private Double value14;

	// 彈仍跌(放空)
	@Column
	private Double value15;

	// 回跌
	@Column
	private Double value16;

	// 回檔買點/反彈賣點
	@Column
	private Double value17;

	// 時K反彈不該過中軸
	@Column
	private Double value18;

	// 盤跌
	@Column
	private Double value19;

	// T盤漲
	@Column
	private Double value20;

	// 反彈峰B
	@Column
	private Double value21;

	// 續漲-不破續多
	@Column
	private Double value22;

	// 過起漲3
	@Column
	private Double value23;

	// 過起漲
	@Column
	private Double value24;

	// 日續漲
	@Column
	private Double value25;

	// 主升
	@Column
	private Double value26;

	// 強波2000點
	@Column
	private Double value27;

	// 超跌17
	@Column
	private Double value29;

	// 超跌16
	@Column
	private Double value30;

	// 超跌15
	@Column
	private Double value31;

	public String getDataString() {
		if (dataTime == null) {
			return "";
		}
		return DateFormatUtils.format(new Date(dataTime), "yyyy/MM/dd");
	}
}
