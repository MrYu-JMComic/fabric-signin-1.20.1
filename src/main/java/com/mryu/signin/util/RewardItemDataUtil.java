package com.mryu.signin.util;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class RewardItemDataUtil {
	public static final String COMPONENT_PREFIX = "components:";
	private static final int DATA_CACHE_LIMIT = 256;
	private static final int MAX_CACHED_TEXT_LENGTH = 4096;
	private static final ParsedItemData EMPTY_DATA = new ParsedItemData(ItemDataKind.EMPTY, null);
	private static final ParsedItemData COMPONENT_DATA = new ParsedItemData(ItemDataKind.COMPONENT_TEXT, null);
	private static final Map<String, ParsedItemData> PARSED_DATA_CACHE = new LinkedHashMap<>(DATA_CACHE_LIMIT, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, ParsedItemData> eldest) {
			return size() > DATA_CACHE_LIMIT;
		}
	};

	private RewardItemDataUtil() {
	}

	public static ItemDataKind detectKind(String itemDataText) {
		return analyze(itemDataText).kind();
	}

	public static boolean isItemDataSyntaxValid(String itemDataText) {
		return analyze(itemDataText).kind() != ItemDataKind.INVALID;
	}

	public static boolean isComponentDataText(String itemDataText) {
		if (itemDataText == null) {
			return false;
		}
		String trimmed = itemDataText.trim();
		String lower = trimmed.toLowerCase(Locale.ROOT);
		return lower.startsWith(COMPONENT_PREFIX) && trimmed.length() > COMPONENT_PREFIX.length();
	}

	public static NbtCompound parseSnbtOrNull(String itemDataText) {
		ParsedItemData parsed = analyze(itemDataText);
		if (parsed.kind() != ItemDataKind.SNBT || parsed.nbt() == null) {
			return null;
		}
		return parsed.nbt().copy();
	}

	public static String normalizePotentialSnbt(String itemDataText) {
		if (itemDataText == null || itemDataText.isBlank()) {
			return null;
		}
		String trimmed = itemDataText.trim();
		if (trimmed.startsWith("<") && trimmed.endsWith(">") && trimmed.length() > 2) {
			return "{" + trimmed.substring(1, trimmed.length() - 1) + "}";
		}
		return trimmed;
	}

	private static ParsedItemData analyze(String itemDataText) {
		if (itemDataText == null || itemDataText.isBlank()) {
			return EMPTY_DATA;
		}

		String trimmed = itemDataText.trim();
		if (isComponentDataText(trimmed)) {
			return COMPONENT_DATA;
		}

		ParsedItemData cached = getCached(trimmed);
		if (cached != null) {
			return cached;
		}

		String normalized = normalizePotentialSnbt(trimmed);
		if (normalized == null) {
			return ItemDataKind.INVALID.data(null);
		}

		try {
			NbtCompound parsed = StringNbtReader.parse(normalized);
			ParsedItemData result = ItemDataKind.SNBT.data(parsed);
			cache(trimmed, result);
			return result;
		} catch (CommandSyntaxException exception) {
			ParsedItemData result = ItemDataKind.INVALID.data(null);
			cache(trimmed, result);
			return result;
		}
	}

	private static ParsedItemData getCached(String trimmed) {
		if (!isCacheable(trimmed)) {
			return null;
		}
		synchronized (PARSED_DATA_CACHE) {
			return PARSED_DATA_CACHE.get(trimmed);
		}
	}

	private static void cache(String trimmed, ParsedItemData parsed) {
		if (!isCacheable(trimmed)) {
			return;
		}
		synchronized (PARSED_DATA_CACHE) {
			PARSED_DATA_CACHE.put(trimmed, parsed);
		}
	}

	private static boolean isCacheable(String trimmed) {
		return trimmed.length() <= MAX_CACHED_TEXT_LENGTH;
	}

	public enum ItemDataKind {
		EMPTY,
		SNBT,
		COMPONENT_TEXT,
		INVALID;

		private ParsedItemData data(NbtCompound nbt) {
			return new ParsedItemData(this, nbt);
		}
	}

	private record ParsedItemData(ItemDataKind kind, NbtCompound nbt) {
	}
}
