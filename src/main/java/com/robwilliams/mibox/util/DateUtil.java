package com.robwilliams.mibox.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class DateUtil {
	
	private static SimpleDateFormat getDateFormatter() {
		// set up date formatter, for consistent date->string and string->date conversions
		SimpleDateFormat dfm = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S");
		dfm.setTimeZone(TimeZone.getTimeZone("America/Seattle")); //TODO: use ConfigUtil
		return dfm;
	}
	
	private static SimpleDateFormat getSimplerDateFormatter() {
		// set up date formatter, for consistent date->string and string->date conversions
		SimpleDateFormat dfm = new SimpleDateFormat("yyyy-MM-dd");
		dfm.setTimeZone(TimeZone.getTimeZone("America/Seattle")); //TODO: use ConfigUtil
		return dfm;
	}
	
	public static Date parse(String dateString) throws ParseException {
		SimpleDateFormat dfm = getDateFormatter();
		return dfm.parse(dateString);
	}
	
	public static String dateToString(Date date) {
		SimpleDateFormat dfm = getDateFormatter();
		return dfm.format(date);
	}
	
	public static String dateToSimplerString(Date date) {
		SimpleDateFormat dfm = getSimplerDateFormatter();
		return dfm.format(date);
	}

	
}
