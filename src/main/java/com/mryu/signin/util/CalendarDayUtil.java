package com.mryu.signin.util;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

public final class CalendarDayUtil {
	private CalendarDayUtil() {
	}

	public static LocalDate today() {
		return LocalDate.now(ZoneId.systemDefault());
	}

	public static int daysInYear(int year) {
		return LocalDate.of(year, 1, 1).lengthOfYear();
	}

	public static int clampDayOfYear(int year, int dayOfYear) {
		int max = daysInYear(year);
		return Math.max(1, Math.min(dayOfYear, max));
	}

	public static int monthFromDayOfYear(int year, int dayOfYear) {
		int safeDay = clampDayOfYear(year, dayOfYear);
		return LocalDate.ofYearDay(year, safeDay).getMonthValue();
	}

	public static MonthRange monthRange(int year, int month) {
		int safeMonth = Math.max(1, Math.min(12, month));
		YearMonth yearMonth = YearMonth.of(year, safeMonth);
		LocalDate firstDay = yearMonth.atDay(1);
		LocalDate lastDay = yearMonth.atEndOfMonth();
		return new MonthRange(safeMonth, firstDay.getDayOfYear(), lastDay.getDayOfYear());
	}

	public static LocalDate dateFromDayOfYear(int year, int dayOfYear) {
		return LocalDate.ofYearDay(year, clampDayOfYear(year, dayOfYear));
	}

	public static String monthDayLabel(int year, int dayOfYear) {
		LocalDate date = dateFromDayOfYear(year, dayOfYear);
		return date.getMonthValue() + "." + date.getDayOfMonth();
	}

	public record MonthRange(int month, int startDay, int endDay) {
		public int dayCount() {
			return Math.max(0, endDay - startDay + 1);
		}

		public boolean contains(int dayOfYear) {
			return dayOfYear >= startDay && dayOfYear <= endDay;
		}
	}
}
