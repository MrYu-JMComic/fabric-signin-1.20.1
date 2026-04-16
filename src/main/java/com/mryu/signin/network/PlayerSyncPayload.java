package com.mryu.signin.network;

import com.mryu.signin.data.PlayerSigninRecord;
import com.mryu.signin.data.RewardEntry;
import net.minecraft.text.Text;

import java.util.List;

public record PlayerSyncPayload(
	boolean op,
	long todayEpochDay,
	PlayerSigninRecord record,
	int nextRewardDay,
	List<RewardEntry> rewards,
	Text notice
) {
	public PlayerSyncPayload {
		rewards = List.copyOf(rewards);
		notice = notice == null ? Text.empty() : notice;
	}
}
