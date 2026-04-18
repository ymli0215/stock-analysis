package com.stockapp.stockserver.comparator;

import java.util.Comparator;

import com.stockapp.stockserver.model.FetureData;

public class FetureDataComparator implements Comparator<FetureData> {

	@Override
	public int compare(FetureData o1, FetureData o2) {
		return o1.getDataTime().compareTo(o2.getDataTime());
	}

}
