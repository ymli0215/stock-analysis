package com.stockapp.stockserver.utils;

import java.text.DecimalFormat;
import java.text.ParseException;

import org.apache.commons.lang3.StringUtils;

public class NumberUtils extends org.apache.commons.lang3.math.NumberUtils {

	/**
	 * 將數字針對格式進行四捨五入
	 * 
	 * @param number
	 *            long
	 * @param pattern
	 *            String
	 * @return String
	 */
	public static double round(double number, String pattern) {
		DecimalFormat df = (DecimalFormat) DecimalFormat.getInstance();
		df.applyPattern(pattern);
		return Double.parseDouble(df.format(number));
	}
	
	/**
	 * 將數字格式化為 pattern 的字串
	 * 
	 * @param number
	 *            double
	 * @param pattern
	 *            String
	 * @return String
	 */
	public static String format(double number, String pattern) {
		DecimalFormat df = (DecimalFormat) DecimalFormat.getInstance();
		df.applyPattern(pattern);
		return df.format(number);
	}

	/**
	 * 將數字格式化為 pattern 的字串
	 * 
	 * @param number
	 *            long
	 * @param pattern
	 *            String
	 * @return String
	 */
	public static String format(long number, String pattern) {
		DecimalFormat df = (DecimalFormat) DecimalFormat.getInstance();
		df.applyPattern(pattern);
		return df.format(number);
	}

	/**
	 * 將數字格式化為 pattern 的字串
	 * 
	 * @param number
	 *            Object
	 * @param pattern
	 *            String
	 * @return String
	 */
	public static String format(Object number, String pattern) {
		DecimalFormat df = (DecimalFormat) DecimalFormat.getInstance();
		df.applyPattern(pattern);
		if (number == null) {
			number = new Integer(0);
		}
		if (number instanceof String) {
			String s = (String) number;

			if (s.matches("\\d+(\\.\\d+)?")) {
				number = new Double(s);
			} else if (s.matches("\\d{1,3}(,\\d{3})*(\\\\.\\\\d+)?")) {
				try {
					number = parseNumber(s, ",##0.############");
				} catch (ParseException e) {
				}
			} else {
				return s;
			}
		}
		return df.format(number);
	}

	public static Number parseNumber(String number, String pattern, Double defaultValue) throws ParseException {
		if (number == null || pattern == null) {
			return defaultValue;
		}
		try {
			DecimalFormat df = (DecimalFormat) DecimalFormat.getInstance();
			df.applyPattern(pattern);
			return df.parse(number);
		} catch (Throwable e) {
			return defaultValue;
		}
	}

	public static Number parseNumber(String number, String pattern) throws ParseException {
		if (number == null || pattern == null) {
			return null;
		}
		DecimalFormat df = (DecimalFormat) DecimalFormat.getInstance();
		df.applyPattern(pattern);
		return df.parse(number);
	}

	public static double parseDouble(String number, String pattern) throws ParseException {
		return parseNumber(number, pattern).doubleValue();
	}

	public static int parseInteger(String number, String pattern) throws ParseException {
		return parseNumber(number, pattern).intValue();
	}

	public static int parseInteger(String number) throws ParseException {
		Number result = parseNumber(number, "0.###");
		return result != null ? result.intValue() : 0;
	}

	public static long parseLong(String number, String pattern) throws ParseException {
		return parseNumber(number, pattern).longValue();
	}

	public static double parseDouble(String number, String pattern, double defauleValue) {
		try {
			return parseNumber(number, pattern).doubleValue();
		} catch (ParseException e) {
			return defauleValue;
		}
	}

	public static int parseInteger(String number, int defauleValue) throws ParseException {
		return parseInteger(number);
	}

	public static long parseLong(String number, String pattern, long defauleValue) {
		try {
			return parseNumber(number, pattern).longValue();
		} catch (ParseException e) {
			return defauleValue;
		}
	}

	public static long parseLong(String number, long defaultValue) {
		return parseNumber(number, defaultValue).longValue();
	}

	public static long parseLong(String number) {
		return parseLong(number, 0);
	}

	/**
	 * 將byte array 轉成 int
	 * 
	 * @param bytes
	 *            byte[]
	 * @return int
	 */
	public static int toInt(byte[] bytes) {
		int result = 0;
		int length = (bytes.length < 4 ? bytes.length : 4);
		for (int i = 0; i < length; i++) {
			result = (result << 8) + (bytes[i] & 0xff);
			if (result > Integer.MAX_VALUE) {
				result -= Integer.MAX_VALUE;
			}
		}
		return result;
	}

	/**
	 * 將 byte array 轉成 long
	 * 
	 * @param bytes
	 *            byte[]
	 * @return long
	 */
	public static long toLong(byte[] bytes) {
		long result = 0;
		int length = (bytes.length < 8 ? bytes.length : 8);
		for (int i = 0; i < length; i++) {
			result = (result << 8) + (bytes[i] & 0xff);
			if (result > Long.MAX_VALUE) {
				result -= Long.MAX_VALUE;
			}
		}
		return result;
	}

	/**
	 * 將 long 轉成 byte array
	 * 
	 * @param value
	 *            long
	 * @return byte[]
	 */
	public static byte[] toBytes(long value) {
		byte[] result = new byte[8];
		for (int i = 8; i > 0;) {
			result[--i] = (byte) (value & 0xff);
			value = value >>> 8;
		}
		return result;
	}

	/**
	 * 將 int 轉成 byte array
	 * 
	 * @param value
	 *            int
	 * @return byte[]
	 */
	public static byte[] toBytes(int value) {
		byte[] result = new byte[4];
		for (int i = 4; i > 0;) {
			result[--i] = (byte) (value & 0xff);
			value = value >>> 8;
		}
		return result;
	}

	/**
	 * 將 byte array 轉成十六進位字串
	 * 
	 * @param bytes
	 *            byte[]
	 * @return String
	 */
	public static String toHexString(byte[] bytes) {
		if (bytes == null || bytes.length == 0) {
			return "";
		}
		StringBuffer buff = new StringBuffer(bytes.length * 2);
		for (int i = 0; i < bytes.length; i++) {
			buff.append(Character.forDigit((int) ((bytes[i] >> 4) & 0x0f), 16));
			buff.append(Character.forDigit((int) (bytes[i] & 0x0f), 16));
		}
		return buff.toString();
	}

	public static Number parseNumber(String number, Number defaultValue) {
		String[] patterns = { ",##0.############", "#####################0.############" };
		if (StringUtils.isNotEmpty(number)) {
			for (int i = 0; i < patterns.length; i++) {
				try {
					return new DecimalFormat(patterns[i]).parse(number);
				} catch (ParseException e) {
				}
			}
		}
		return defaultValue;
	}
}
