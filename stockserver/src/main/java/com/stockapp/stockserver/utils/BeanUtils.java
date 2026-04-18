package com.stockapp.stockserver.utils;

public class BeanUtils {
	public static void copyProperties(Object dest, Object src, String... ignoreProperties) throws Exception {
		try {
			org.springframework.beans.BeanUtils.copyProperties(src, dest, ignoreProperties);
		}
		catch (Exception e) {
			throw new Exception(e.getMessage(), e);
		}
	}
}
