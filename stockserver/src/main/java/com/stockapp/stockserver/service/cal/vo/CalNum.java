package com.stockapp.stockserver.service.cal.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class CalNum implements Serializable {
	// 平均數
	private BigDecimal avg;
	// 評分 從1開始 對應S+往下 例如 2:S 4:A+
	private int level;

	public BigDecimal getAvg() {
		return avg;
	}

	public void setAvg(BigDecimal avg) {
		this.avg = avg.setScale(2, RoundingMode.HALF_UP);
	}

	public int getLevel() {
		return level;
	}

	public void setLevel(int level) {
		this.level = level;
	}
}
