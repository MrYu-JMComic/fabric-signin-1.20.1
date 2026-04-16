package com.mryu.signin.util;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public final class RewardItemResolver {
	private RewardItemResolver() {
	}

	public static Item resolveNonAirItem(String itemIdText) {
		if (itemIdText == null || itemIdText.isBlank()) {
			return null;
		}
		Identifier itemId = Identifier.tryParse(itemIdText);
		if (itemId == null || !Registries.ITEM.containsId(itemId)) {
			return null;
		}
		Item item = Registries.ITEM.get(itemId);
		return item == Items.AIR ? null : item;
	}

	public static boolean isValidItemId(String itemIdText) {
		return resolveNonAirItem(itemIdText) != null;
	}
}
