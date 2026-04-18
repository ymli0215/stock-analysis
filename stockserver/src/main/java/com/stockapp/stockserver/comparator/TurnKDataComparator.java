package com.stockapp.stockserver.comparator;

import java.util.Comparator;

import com.stockapp.stockserver.model.TurnKData;

public class TurnKDataComparator implements Comparator<TurnKData> {

	@Override
	public int compare(TurnKData o1, TurnKData o2) {
		return o1.getDataTime().compareTo(o2.getDataTime());
	}

}
