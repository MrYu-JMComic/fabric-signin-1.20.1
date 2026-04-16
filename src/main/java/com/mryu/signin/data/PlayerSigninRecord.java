package com.mryu.signin.data;

public record PlayerSigninRecord(
	boolean signedToday,
	boolean signedYesterday,
	int streak,
	int totalDays,
	int makeupCards,
	long lastSignDay
) {
	public static PlayerSigninRecord empty() {
		return new PlayerSigninRecord(false, false, 0, 0, 0, -1L);
	}
}
