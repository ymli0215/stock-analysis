package com.stockapp.stockserver.utils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.apache.commons.lang3.StringUtils;

public class DateUtils extends org.apache.commons.lang3.time.DateUtils {
	public static String defaultPatten = "yyyy/MM/dd";
	public static String defaultRocPatten = "yyy/MM/dd";
	public static String defaultRocPatten2 = "yyy年MM月dd日";
	public static String defaultRocPatten3 = "yyyMMdd";
	public static String defaultTimePatten = "yyyy/MM/dd HH:mm:ss";
	public static final Date systemMinDate = new Date(0, 0, 1);
	public static final Date systemMaxDate = new Date(8099,11,31);
	
	public static String formatDate(String date, String oldPattern, String pattern) {
		Date d = parse(date, oldPattern);
		if (d != null) {
			return format(d, pattern);
		} else {
			return null;
		}
	}
	
	public static String format(Date date) {
		return format(date, defaultPatten);
	}

	public static String format(Date date, String pattern) {
		try {
			SimpleDateFormat sdf = new SimpleDateFormat(pattern);
			return sdf.format(date);
		} catch (Exception e) {
			return "";
		}
	}

	public static String formatDate(Date date, String pattern) {
		return format(date, pattern);
	}

	public static Date parseDate(String date) {
		return parse(date, defaultPatten);
	}

	public static Date parseDate(String date, String pattern) {
		return parse(date, pattern);
	}

	public static Date parseDate(String date, String pattern, Date defaultValue) {
		try {
			if (StringUtils.isNotEmpty(date) && StringUtils.isNotEmpty(pattern)) {
				SimpleDateFormat sdf = new SimpleDateFormat(pattern);
				return sdf.parse(date);
			}
		} catch (Exception e) {
		}
		return defaultValue;
	}

	public static Date parse(String date, String pattern) {
		try {
			if (StringUtils.isNotEmpty(date) && StringUtils.isNotEmpty(pattern)) {
				SimpleDateFormat sdf = new SimpleDateFormat(pattern);
				return sdf.parse(date);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public static Date trim(Date date, String pattern) {
		return parse(format(date, pattern), pattern);
	}
	
	public static Date getTodayWithoutTime() {
		Date date = new Date();
		return trim(date, defaultPatten);
	}
	
	public static Date truncateTime(Date date) {
		Date newDate = date;
		newDate = DateUtils.setHours(newDate, 0);
		newDate = DateUtils.setMilliseconds(newDate, 0);
		newDate = DateUtils.setMinutes(newDate, 0);
		newDate = DateUtils.setSeconds(newDate, 0);
		
		return newDate;
	}
	
	public static Date setTime(Date date, int hours, int minutes, int seconds) {
		Date newDate = date;
		newDate = DateUtils.setHours(newDate, hours);
		newDate = DateUtils.setMinutes(newDate, minutes);
		newDate = DateUtils.setSeconds(newDate, seconds);
		newDate = DateUtils.setMilliseconds(newDate, 0);
		
		return newDate;
	}
	
	public static Date getAmericanDate() {
		TimeZone zone = TimeZone.getTimeZone("America/New_York");
		Calendar calendar = new GregorianCalendar();
		calendar.setTimeZone(zone);
		
		Date date = new Date();
		date.setYear(calendar.get(Calendar.YEAR)-1900);
		date.setMonth(calendar.get(Calendar.MONTH));
		date.setDate(calendar.get(Calendar.DATE));
		date.setHours(calendar.get(Calendar.HOUR));
		date.setMinutes(calendar.get(Calendar.MINUTE));
		date.setSeconds(calendar.get(Calendar.SECOND));
		
		return date;
	}
	
	public static Date findWeekDate(Date now, int weekDay) {
		if(now == null) {
			return null;
		}
		
		Calendar cal = Calendar.getInstance();
		cal.setTime(now);
		cal.set(Calendar.DAY_OF_WEEK, weekDay);
		
		return truncateTime(cal.getTime());
	}
	
	public static Date parseRocDate(String date, String pattern) {
		try {
			if (StringUtils.isNotEmpty(date) && StringUtils.isNotEmpty(pattern)) {
				RocDateFormat sdf = new RocDateFormat(pattern);
				return sdf.parse(date);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
}
