package com.mryu.signin.service;

import com.mryu.signin.config.SigninLimits;
import com.mryu.signin.data.RewardEntry;
import com.mryu.signin.data.RewardItemEntry;
import com.mryu.signin.util.RewardItemDataUtil;
import com.mryu.signin.util.RewardItemResolver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RewardValidationService {
	private RewardValidationService() {
	}

	public static List<RewardEntry> normalizeAndValidate(List<RewardEntry> rewards, int expectedDays) {
		if (expectedDays < 1 || expectedDays > SigninLimits.MAX_REWARD_DAYS_PER_YEAR) {
			return null;
		}
		if (rewards == null || rewards.size() != expectedDays) {
			return null;
		}

		Set<Integer> daySet = new HashSet<>();
		List<RewardEntry> normalized = new ArrayList<>(rewards.size());

		for (RewardEntry reward : rewards) {
			if (reward == null) {
				return null;
			}

			RewardEntry fixed = reward.normalized();
			if (fixed.day() < 1 || fixed.day() > expectedDays) {
				return null;
			}
			if (!daySet.add(fixed.day())) {
				return null;
			}
			if (fixed.items().size() > SigninLimits.MAX_ITEM_TYPES_PER_REWARD) {
				return null;
			}

			for (RewardItemEntry item : fixed.items()) {
				if (item.itemCount() <= 0 || item.itemCount() > SigninLimits.MAX_REWARD_ITEM_COUNT) {
					return null;
				}
				if (!RewardItemResolver.isValidItemId(item.itemId())) {
					return null;
				}
				if (item.hasData() && !RewardItemDataUtil.isItemDataSyntaxValid(item.itemData())) {
					return null;
				}
			}

			normalized.add(fixed);
		}

		normalized.sort(Comparator.comparingInt(RewardEntry::day));
		return normalized;
	}
}
