package com.stockapp.stockserver.utils;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class RocDateFormat extends SimpleDateFormat {
	static final long serialVersionUID = -8125123834729963328L;

	public RocDateFormat(String pattern) {
		super(pattern);
	}

	static public DateFormat getInstance(Locale locale) {
		return new RocDateFormat("yy/MM/dd");
	}

	public StringBuffer format(Date date, StringBuffer toAppendTo, FieldPosition fieldPosition) {
		int yearLength = 0;
		int yearIndex = -1;
		String pattern = toPattern();

		for (int i = 0; i < pattern.length(); i++) {
			if (pattern.charAt(i) == 'y' || pattern.charAt(i) == 'Y') {
				if (yearIndex == -1) {
					yearIndex = i;
				}
				yearLength++;
			} else if (yearIndex != -1) {
				break;
			}
		}

		if (yearLength == 0) {
			return super.format(date, toAppendTo, fieldPosition);
		}

		Calendar calendar = this.getCalendar();
		calendar.setTime(date);
		int year = calendar.get(Calendar.YEAR) - 1911;

		// 顯示
		try {
			if (yearIndex > 0) {
				applyPattern(pattern.substring(0, yearIndex));
				super.format(date, toAppendTo, fieldPosition).append(getYear(year, yearLength));
				applyPattern(pattern.substring(yearIndex + yearLength));
				return super.format(date, toAppendTo, fieldPosition);
			} else {
				applyPattern(pattern.substring(yearIndex + yearLength));
				return super.format(date, toAppendTo, fieldPosition).insert(0, getYear(year, yearLength));
			}
		} finally {
			applyPattern(pattern);
		}
	}

	public Date parse(String source, ParsePosition pos) {
		int yearLength = 0;
		int yearIndex = -1;
		String pattern = toPattern();

		for (int i = 0; i < pattern.length(); i++) {
			if (pattern.charAt(i) == 'y' || pattern.charAt(i) == 'Y') {
				if (yearIndex == -1) {
					yearIndex = i;
				}
				yearLength++;
			} else if (yearIndex != -1) {
				break;
			}
		}

		if (yearLength == 0) {
			return super.parse(source, pos);
		}

		String yearString = source.substring(yearIndex, yearIndex + yearLength);
		yearString = getYear(Integer.parseInt(yearString, 10) + 1911, 4);
		String newSource = source.substring(0, yearIndex) + yearString + source.substring(yearIndex + yearLength);
		try {
			applyPattern(pattern.substring(0, yearIndex) + "yyyy" + pattern.substring(yearIndex + yearLength));
			return super.parse(newSource, pos);
		} finally {
			applyPattern(pattern);
		}
	}

	private String getYear(int year, int length) {
		DecimalFormat nf = (DecimalFormat) DecimalFormat.getInstance();
		nf.setMinimumIntegerDigits(length);
		nf.setMaximumIntegerDigits(length);
		nf.setMinimumFractionDigits(0);
		nf.setMaximumFractionDigits(0);
		nf.setGroupingUsed(false);
		return nf.format(year);
	}
}
