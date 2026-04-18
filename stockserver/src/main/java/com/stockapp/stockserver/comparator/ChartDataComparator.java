package com.stockapp.stockserver.comparator;

import java.util.Comparator;

import com.stockapp.stockserver.model.ChartData;

public class ChartDataComparator implements Comparator<ChartData> {

	@Override
	public int compare(ChartData o1, ChartData o2) {
		return o1.getDataTime().compareTo(o2.getDataTime());
	}

}
