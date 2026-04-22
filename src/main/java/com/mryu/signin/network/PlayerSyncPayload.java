package com.mryu.signin.network;

import com.mryu.signin.data.PlayerSigninRecord;
import com.mryu.signin.data.RewardEntry;
import net.minecraft.text.Text;

import java.util.List;

public record PlayerSyncPayload(
	boolean op,
	long todayEpochDay,
	int rewardYear,
	int daysInYear,
	int todayDayOfYear,
	PlayerSigninRecord record,
	long nextRewardEpochDay,
	int nextRewardDay,
	boolean canMakeup,
	long makeupTargetEpochDay,
	List<Integer> signedDaysOfYear,
	List<RewardEntry> rewards,
	int noticeEvent,
	Text notice
) {
	public PlayerSyncPayload {
		signedDaysOfYear = signedDaysOfYear == null ? List.of() : List.copyOf(signedDaysOfYear);
		rewards = List.copyOf(rewards);
		notice = notice == null ? Text.empty() : notice;
	}
}
