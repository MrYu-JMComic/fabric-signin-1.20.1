package com.mryu.signin.data;

public record RewardItemEntry(
	String itemId,
	int itemCount,
	String itemData
) {
	public RewardItemEntry normalized() {
		String normalizedItemId = itemId == null ? "" : itemId.trim();
		String normalizedItemData = itemData == null ? "" : itemData.trim();
		int normalizedCount = Math.max(0, itemCount);
		if (normalizedItemId.isBlank()) {
			normalizedCount = 0;
			normalizedItemData = "";
		}
		return new RewardItemEntry(normalizedItemId, normalizedCount, normalizedItemData);
	}

	public boolean hasData() {
		return itemData != null && !itemData.isBlank();
	}
}
