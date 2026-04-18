package com.stockapp.stockserver.model;

import java.io.Serializable;

import com.stockapp.stockserver.entity.StockData;
import com.stockapp.stockserver.entity.StockDataEMA;

import lombok.Data;

@Data
public class StockCandle implements Serializable {
	// 今天資料
	private StockData s;
	// 前一筆資料
	private StockData s1;

	// 紅k
	private boolean whiteLine;
	// 黑k
	private boolean blackLine;
	// 高低差
	private double height;
	// 實體上緣
	private double highBody;
	// 實體下緣
	private double lowBody;
	// 上引線
	private double upShowad;
	// 下引線
	private double downShowad;

	public StockCandle(StockData s, StockData s1) {
		this.s = s;
		this.s1 = s1;

		prepare();
	}

	private void prepare() {
		if (s.getClose().compareTo(s.getOpen()) > 0) {
			whiteLine = true;
		} else if (s.getClose().compareTo(s.getOpen()) < 0) {
			blackLine = true;
		}

		height = s.getHigh().doubleValue() - s.getLow().doubleValue();

		highBody = Math.max(s.getClose().doubleValue(), s.getOpen().doubleValue());
		lowBody = Math.min(s.getClose().doubleValue(), s.getOpen().doubleValue());

		upShowad = s.getHigh().doubleValue() - highBody;
		downShowad = lowBody - s.getLow().doubleValue();
	}

	//長紅
	public boolean isLongRed() {
		if (whiteLine && ((downShowad + upShowad) < height * 0.2)
				&& (height > s1.getClose().shortValue() * 0.015 || (height > s1.getClose().shortValue() * 0.01
						&& s.getClose().doubleValue() > s1.getClose().doubleValue() * 1.02))) {
			return true;
		}
		return false;
	}

	//中紅
	public boolean isMiddleRed() {
		if (whiteLine && ((downShowad + upShowad) < height * 0.2)
				&& (height > s1.getClose().shortValue() * 0.005 || (height > s1.getClose().shortValue() * 0.03
						&& s.getClose().doubleValue() > s1.getClose().doubleValue() * 1.007))) {
			return true;
		}
		return false;
	}

	//長黑
	public boolean isLongBlack() {
		if (blackLine && ((downShowad + upShowad) < height * 0.2)
				&& (height > s1.getClose().shortValue() * 0.015 || (height > s1.getClose().shortValue() * 0.01
						&& s.getClose().doubleValue() < s1.getClose().doubleValue() * 0.98))) {
			return true;
		}
		return false;
	}

	//中黑
	public boolean isMiddleBlack() {
		if (blackLine && ((downShowad + upShowad) < height * 0.2)
				&& (height > s1.getClose().shortValue() * 0.005 || (height > s1.getClose().shortValue() * 0.03
						&& s.getClose().doubleValue() < s1.getClose().doubleValue() * 0.993))) {
			return true;
		}
		return false;
	}
	
	//順勢突破
	public boolean isTradeUp() {
		return isLongRed() || isMiddleRed();
	}
	
	//順勢突破
	public boolean isTradeDown() {
		return isLongBlack() || isMiddleBlack();
	}
	
	//金叉
	public boolean isCrossOver() {
		StockDataEMA ema = s.getEma();
		StockDataEMA ema1 = s1.getEma();
		if(ema.getEma7().doubleValue() > ema.getEma13().doubleValue() &&
				ema.getEma13().doubleValue() > ema.getEma21().doubleValue() &&
				ema.getEma21().doubleValue() > ema.getEma53().doubleValue() &&
				ema1.getEma7().doubleValue() > ema1.getEma13().doubleValue() &&
				ema1.getEma13().doubleValue() > ema1.getEma21().doubleValue() &&
				ema1.getEma21().doubleValue() < ema1.getEma53().doubleValue()) {
			return true;
		}
		
		return false;
	}
	
	//金叉
	public boolean isCrossUnder() {
		StockDataEMA ema = s.getEma();
		StockDataEMA ema1 = s1.getEma();
		if(ema.getEma7().doubleValue() < ema.getEma13().doubleValue() &&
				ema.getEma13().doubleValue() < ema.getEma21().doubleValue() &&
				ema.getEma21().doubleValue() < ema.getEma53().doubleValue() &&
				ema1.getEma7().doubleValue() < ema1.getEma13().doubleValue() &&
				ema1.getEma13().doubleValue() < ema1.getEma21().doubleValue() &&
				ema1.getEma21().doubleValue() > ema1.getEma53().doubleValue()) {
			return true;
		}
		
		return false;
	}
}
