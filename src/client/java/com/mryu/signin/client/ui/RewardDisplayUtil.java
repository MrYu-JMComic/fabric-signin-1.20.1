package com.mryu.signin.client.ui;

import com.mryu.signin.data.RewardItemEntry;
import com.mryu.signin.util.RewardItemDataUtil;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RewardDisplayUtil {
	private static final int STACK_CACHE_LIMIT = 512;
	private static final Pattern JSON_TEXT_PATTERN = Pattern.compile("\"text\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");
	private static final Pattern CUSTOM_NAME_PATTERN = Pattern.compile("custom_name\\s*[:=]\\s*\"((?:\\\\.|[^\"])*)\"", Pattern.CASE_INSENSITIVE);
	private static final Map<StackCacheKey, ItemStack> STACK_CACHE = new LinkedHashMap<>(STACK_CACHE_LIMIT, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<StackCacheKey, ItemStack> eldest) {
			return size() > STACK_CACHE_LIMIT;
		}
	};

	private RewardDisplayUtil() {
	}

	public static ItemStack resolveItemStack(RewardItemEntry item) {
		if (item == null) {
			return new ItemStack(Items.BARRIER);
		}
		return resolveItemStack(item.itemId(), item.itemCount(), item.itemData());
	}

	public static ItemStack resolveItemStack(String itemIdText, int count, String itemDataText) {
		if (itemIdText == null || itemIdText.isBlank() || count <= 0) {
			return new ItemStack(Items.BARRIER);
		}
		String normalizedItemId = itemIdText.trim();
		String normalizedData = itemDataText == null ? "" : itemDataText.trim();
		StackCacheKey key = new StackCacheKey(normalizedItemId, count, normalizedData);
		ItemStack cached = getCachedStack(key);
		if (cached != null) {
			return cached;
		}

		ItemStack resolved = resolveItemStackUncached(normalizedItemId, count, normalizedData);
		putCachedStack(key, resolved);
		return resolved.copy();
	}

	private static ItemStack resolveItemStackUncached(String itemIdText, int count, String itemDataText) {
		Identifier id = Identifier.tryParse(itemIdText);
		if (id == null || !Registries.ITEM.containsId(id)) {
			return new ItemStack(Items.BARRIER);
		}

		Item item = Registries.ITEM.get(id);
		if (item == Items.AIR) {
			return new ItemStack(Items.BARRIER);
		}

		int safeCount = Math.max(1, Math.min(count, Math.max(1, item.getMaxCount())));
		ItemStack stack = new ItemStack(item, safeCount);

		RewardItemDataUtil.ItemDataKind kind = RewardItemDataUtil.detectKind(itemDataText);
		if (kind == RewardItemDataUtil.ItemDataKind.INVALID) {
			return new ItemStack(Items.BARRIER);
		}
		if (kind == RewardItemDataUtil.ItemDataKind.SNBT) {
			NbtCompound data = RewardItemDataUtil.parseSnbtOrNull(itemDataText);
			if (data == null) {
				return new ItemStack(Items.BARRIER);
			}
			stack.setNbt(data.copy());
		}
		return stack;
	}

	private static ItemStack getCachedStack(StackCacheKey key) {
		synchronized (STACK_CACHE) {
			ItemStack stack = STACK_CACHE.get(key);
			return stack == null ? null : stack.copy();
		}
	}

	private static void putCachedStack(StackCacheKey key, ItemStack stack) {
		synchronized (STACK_CACHE) {
			STACK_CACHE.put(key, stack.copy());
		}
	}

	public static String localizedItemSummary(List<RewardItemEntry> items) {
		return localizedItemSummary(items, 1);
	}

	public static String localizedItemSummary(List<RewardItemEntry> items, int previewCount) {
		if (items == null || items.isEmpty()) {
			return I18n.translate("text.signin.none");
		}

		int visibleCount = Math.max(1, Math.min(previewCount, items.size()));
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < visibleCount; i++) {
			RewardItemEntry entry = items.get(i);
			ItemStack stack = resolveItemStack(entry);
			if (i > 0) {
				builder.append(I18n.translate("text.signin.item_summary.separator"));
			}
			builder.append(localizedItemText(entry.itemId(), entry.itemCount(), entry.itemData(), stack));
		}

		if (items.size() > visibleCount) {
			builder.append(I18n.translate("text.signin.item_summary.more_types", items.size()));
		}
		return builder.toString();
	}

	public static String localizedItemTooltipSummary(List<RewardItemEntry> items) {
		if (items == null || items.isEmpty()) {
			return I18n.translate("text.signin.reward_item_list_empty");
		}

		StringBuilder builder = new StringBuilder(I18n.translate("text.signin.reward_item_list_prefix"));
		for (int i = 0; i < items.size(); i++) {
			RewardItemEntry entry = items.get(i);
			ItemStack stack = resolveItemStack(entry);
			if (i > 0) {
				builder.append(I18n.translate("text.signin.reward_item_list.separator"));
			}
			builder.append(I18n.translate(
				"text.signin.indexed_line",
				i + 1,
				localizedItemText(entry.itemId(), entry.itemCount(), entry.itemData(), stack)
			));
		}
		return builder.toString();
	}

	public static List<String> localizedItemLines(List<RewardItemEntry> items) {
		if (items == null || items.isEmpty()) {
			return List.of(I18n.translate("text.signin.none"));
		}

		List<String> lines = new ArrayList<>(items.size());
		for (RewardItemEntry entry : items) {
			ItemStack stack = resolveItemStack(entry);
			lines.add(localizedItemText(entry.itemId(), entry.itemCount(), entry.itemData(), stack));
		}
		return lines;
	}

	public static String localizedItemText(String itemIdText, int count, String itemDataText, ItemStack stack) {
		if (itemIdText == null || itemIdText.isBlank() || count <= 0) {
			return I18n.translate("text.signin.none");
		}
		if (stack.getItem() == Items.BARRIER) {
			return I18n.translate("text.signin.item_invalid", itemIdText, Math.max(1, count));
		}

		String displayName = resolveDisplayName(stack, itemDataText);
		String base = I18n.translate("text.signin.item_count", displayName, Math.max(1, count));
		RewardItemDataUtil.ItemDataKind kind = RewardItemDataUtil.detectKind(itemDataText);
		if (kind == RewardItemDataUtil.ItemDataKind.SNBT) {
			return base + " " + I18n.translate("text.signin.tag_nbt");
		}
		return base;
	}

	public static boolean isItemDataSyntaxValid(String itemDataText) {
		return RewardItemDataUtil.isItemDataSyntaxValid(itemDataText);
	}

	private static String resolveDisplayName(ItemStack stack, String itemDataText) {
		String extracted = extractCustomName(itemDataText);
		if (extracted != null && !extracted.isBlank()) {
			return extracted;
		}
		return stack.getName().getString();
	}

	private static String extractCustomName(String itemDataText) {
		if (itemDataText == null || itemDataText.isBlank()) {
			return null;
		}

		NbtCompound nbt = RewardItemDataUtil.parseSnbtOrNull(itemDataText);
		if (nbt != null && nbt.contains("display", net.minecraft.nbt.NbtElement.COMPOUND_TYPE)) {
			NbtCompound display = nbt.getCompound("display");
			if (display.contains("Name", net.minecraft.nbt.NbtElement.STRING_TYPE)) {
				String raw = display.getString("Name");
				String parsed = parsePotentialJsonText(raw);
				if (parsed != null && !parsed.isBlank()) {
					return parsed;
				}
			}
		}

		String raw = itemDataText.trim();
		Matcher customMatcher = CUSTOM_NAME_PATTERN.matcher(raw);
		if (customMatcher.find()) {
			String parsed = unescapeJson(customMatcher.group(1));
			if (!parsed.isBlank()) {
				return parsed;
			}
		}
		return parsePotentialJsonText(raw);
	}

	private static String parsePotentialJsonText(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}

		String trimmed = raw.trim();
		Matcher matcher = JSON_TEXT_PATTERN.matcher(trimmed);
		if (matcher.find()) {
			return unescapeJson(matcher.group(1));
		}

		if (trimmed.length() >= 2
			&& ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) || (trimmed.startsWith("'") && trimmed.endsWith("'")))) {
			return trimmed.substring(1, trimmed.length() - 1);
		}
		return null;
	}

	private static String unescapeJson(String text) {
		return text
			.replace("\\\"", "\"")
			.replace("\\\\", "\\");
	}

	private record StackCacheKey(String itemId, int count, String itemData) {
	}
}
