package com.stockapp.stockserver.comparator;

import java.util.Comparator;

import com.stockapp.stockserver.entity.StockData;

public class StockDataComparator implements Comparator<StockData> {

	@Override
	public int compare(StockData o1, StockData o2) {
		return o1.getId().getDataTime().compareTo(o2.getId().getDataTime());
	}

}
