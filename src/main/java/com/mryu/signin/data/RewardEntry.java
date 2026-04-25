package com.mryu.signin.data;

import com.mryu.signin.config.SigninLimits;

import java.util.ArrayList;
import java.util.List;

public record RewardEntry(
	int day,
	int xp,
	List<RewardItemEntry> items,
	int makeupCardReward
) {
	public RewardEntry {
		items = items == null ? List.of() : List.copyOf(items);
	}

	public RewardEntry normalized() {
		List<RewardItemEntry> normalizedItems = new ArrayList<>();
		for (RewardItemEntry item : items) {
			if (item == null) {
				continue;
			}
			RewardItemEntry fixed = item.normalized();
			if (!fixed.itemId().isBlank() && fixed.itemCount() > 0) {
				normalizedItems.add(fixed);
			}
		}
		return new RewardEntry(
			Math.max(1, day),
			clamp(xp, SigninLimits.MIN_REWARD_XP, SigninLimits.MAX_REWARD_XP),
			normalizedItems,
			clamp(makeupCardReward, SigninLimits.MIN_MAKEUP_CARD_REWARD, SigninLimits.MAX_MAKEUP_CARD_REWARD)
		);
	}

	public boolean hasItems() {
		return !items.isEmpty();
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(value, max));
	}
}
