package com.mryu.signin.config;

public final class SigninLimits {
	public static final int REWARD_CYCLE_DAYS = 7;
	public static final int MAX_REWARD_DAYS_PER_YEAR = 366;
	public static final int MIN_REWARD_XP = 0;
	public static final int MAX_REWARD_XP = 1_000_000;
	public static final int MIN_MAKEUP_CARD_REWARD = 0;
	public static final int MAX_MAKEUP_CARD_REWARD = 1_000;
	public static final int MAX_MAKEUP_CARDS_STORED = 1_000_000;
	public static final int MIN_REWARD_ITEM_COUNT = 1;
	public static final int MAX_ITEM_TYPES_PER_REWARD = 16;
	public static final int MAX_REWARD_ITEM_COUNT = 4096;

	public static final int MAX_REWARD_PACKET_ENTRIES = 400;
	public static final int MAX_ITEM_ID_LENGTH = 256;
	public static final int MAX_ITEM_DATA_LENGTH = 32767;

	private SigninLimits() {
	}
}
