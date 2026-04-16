package com.mryu.signin.data;

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
			Math.max(0, xp),
			normalizedItems,
			Math.max(0, makeupCardReward)
		);
	}

	public boolean hasItems() {
		return !items.isEmpty();
	}
}
