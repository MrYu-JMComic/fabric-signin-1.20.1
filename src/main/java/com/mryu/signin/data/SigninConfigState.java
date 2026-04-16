package com.mryu.signin.data;

import com.mryu.signin.config.SigninLimits;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SigninConfigState extends PersistentState {
	private static final String SAVE_KEY = "signin_config";
	private static final String REWARDS_KEY = "rewards";
	private static final String ITEMS_KEY = "items";

	private static final String DAY_KEY = "day";
	private static final String XP_KEY = "xp";
	private static final String ITEM_ID_KEY = "itemId";
	private static final String ITEM_COUNT_KEY = "itemCount";
	private static final String ITEM_DATA_KEY = "itemData";
	private static final String CARD_REWARD_KEY = "cardReward";

	public static final int REWARD_CYCLE_DAYS = SigninLimits.REWARD_CYCLE_DAYS;
	private static final List<RewardEntry> DEFAULT_REWARDS = List.of(
		new RewardEntry(1, 20, List.of(), 0),
		new RewardEntry(2, 30, List.of(), 0),
		new RewardEntry(3, 40, List.of(new RewardItemEntry("minecraft:bread", 3, "")), 0),
		new RewardEntry(4, 50, List.of(new RewardItemEntry("minecraft:iron_ingot", 2, "")), 0),
		new RewardEntry(5, 60, List.of(new RewardItemEntry("minecraft:gold_ingot", 2, "")), 0),
		new RewardEntry(6, 80, List.of(new RewardItemEntry("minecraft:diamond", 1, "")), 0),
		new RewardEntry(7, 120, List.of(new RewardItemEntry("minecraft:emerald", 4, "")), 1)
	);

	private final List<RewardEntry> rewards = new ArrayList<>();
	private final RewardEntry[] rewardsByDay = new RewardEntry[REWARD_CYCLE_DAYS];
	private List<RewardEntry> rewardSnapshot = List.of();
	private boolean cacheDirty = true;

	public SigninConfigState() {
		rewards.addAll(DEFAULT_REWARDS);
	}

	public static SigninConfigState get(MinecraftServer server) {
		PersistentStateManager stateManager = server.getOverworld().getPersistentStateManager();
		return stateManager.getOrCreate(SigninConfigState::fromNbt, SigninConfigState::new, SAVE_KEY);
	}

	public static SigninConfigState fromNbt(NbtCompound nbt) {
		SigninConfigState state = new SigninConfigState();
		state.rewards.clear();

		NbtList rewardList = nbt.getList(REWARDS_KEY, NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < rewardList.size(); i++) {
			NbtCompound rewardNbt = rewardList.getCompound(i);
			List<RewardItemEntry> items = readItemList(rewardNbt);
			RewardEntry reward = new RewardEntry(
				rewardNbt.getInt(DAY_KEY),
				rewardNbt.getInt(XP_KEY),
				items,
				rewardNbt.getInt(CARD_REWARD_KEY)
			).normalized();
			state.rewards.add(reward);
		}

		state.ensureDefaults();
		state.refreshRewardCache();
		return state;
	}

	@Override
	public synchronized NbtCompound writeNbt(NbtCompound nbt) {
		ensureDefaults();
		refreshRewardCache();

		NbtList rewardList = new NbtList();
		for (RewardEntry reward : rewardSnapshot) {
			NbtCompound rewardNbt = new NbtCompound();
			rewardNbt.putInt(DAY_KEY, reward.day());
			rewardNbt.putInt(XP_KEY, reward.xp());
			rewardNbt.putInt(CARD_REWARD_KEY, reward.makeupCardReward());
			rewardNbt.put(ITEMS_KEY, writeItemList(reward.items()));
			rewardList.add(rewardNbt);
		}
		nbt.put(REWARDS_KEY, rewardList);
		return nbt;
	}

	public synchronized RewardEntry getRewardForStreak(int streak) {
		ensureDefaults();
		refreshRewardCache();
		int day = ((Math.max(1, streak) - 1) % REWARD_CYCLE_DAYS) + 1;
		return rewardsByDay[day - 1];
	}

	public synchronized List<RewardEntry> getRewards() {
		ensureDefaults();
		refreshRewardCache();
		return rewardSnapshot;
	}

	public synchronized void updateRewards(List<RewardEntry> updatedRewards) {
		rewards.clear();
		if (updatedRewards != null) {
			for (RewardEntry reward : updatedRewards) {
				if (reward != null) {
					rewards.add(reward.normalized());
				}
			}
		}
		cacheDirty = true;
		ensureDefaults();
		refreshRewardCache();
		markDirty();
	}

	public static List<RewardEntry> defaultRewards() {
		return DEFAULT_REWARDS;
	}

	private void ensureDefaults() {
		if (rewards.size() == REWARD_CYCLE_DAYS) {
			boolean changed = false;
			List<RewardEntry> sorted = new ArrayList<>(rewards);
			sorted.sort(Comparator.comparingInt(RewardEntry::day));
			if (!sorted.equals(rewards)) {
				rewards.clear();
				rewards.addAll(sorted);
				changed = true;
			}

			for (int i = 0; i < rewards.size(); i++) {
				RewardEntry normalized = rewards.get(i).normalized();
				RewardEntry canonical = normalized.day() == i + 1
					? normalized
					: new RewardEntry(i + 1, normalized.xp(), normalized.items(), normalized.makeupCardReward());
				if (!canonical.equals(rewards.get(i))) {
					rewards.set(i, canonical);
					changed = true;
				}
			}

			if (changed) {
				cacheDirty = true;
			}
			if (changed) {
				markDirty();
			}
			return;
		}

		rewards.clear();
		rewards.addAll(DEFAULT_REWARDS);
		cacheDirty = true;
		markDirty();
	}

	private void refreshRewardCache() {
		if (!cacheDirty) {
			return;
		}

		List<RewardEntry> ordered = new ArrayList<>(REWARD_CYCLE_DAYS);
		for (int i = 0; i < REWARD_CYCLE_DAYS; i++) {
			RewardEntry source = i < rewards.size() ? rewards.get(i).normalized() : DEFAULT_REWARDS.get(i);
			RewardEntry reward = source.day() == i + 1
				? source
				: new RewardEntry(i + 1, source.xp(), source.items(), source.makeupCardReward());
			rewardsByDay[i] = reward;
			ordered.add(reward);
		}

		if (!rewards.equals(ordered)) {
			rewards.clear();
			rewards.addAll(ordered);
			markDirty();
		}

		rewardSnapshot = List.copyOf(ordered);
		cacheDirty = false;
	}

	private static List<RewardItemEntry> readItemList(NbtCompound rewardNbt) {
		List<RewardItemEntry> items = new ArrayList<>();

		if (rewardNbt.contains(ITEMS_KEY, NbtElement.LIST_TYPE)) {
			NbtList itemList = rewardNbt.getList(ITEMS_KEY, NbtElement.COMPOUND_TYPE);
			for (int j = 0; j < itemList.size(); j++) {
				NbtCompound itemNbt = itemList.getCompound(j);
				RewardItemEntry entry = new RewardItemEntry(
					itemNbt.getString(ITEM_ID_KEY),
					itemNbt.getInt(ITEM_COUNT_KEY),
					itemNbt.contains(ITEM_DATA_KEY, NbtElement.STRING_TYPE) ? itemNbt.getString(ITEM_DATA_KEY) : ""
				).normalized();
				if (!entry.itemId().isBlank() && entry.itemCount() > 0) {
					items.add(entry);
				}
			}
			return items;
		}

		// Backward compatibility with single-item schema.
		String legacyItemId = rewardNbt.getString(ITEM_ID_KEY);
		int legacyItemCount = rewardNbt.getInt(ITEM_COUNT_KEY);
		String legacyItemData = rewardNbt.contains(ITEM_DATA_KEY, NbtElement.STRING_TYPE) ? rewardNbt.getString(ITEM_DATA_KEY) : "";
		RewardItemEntry legacy = new RewardItemEntry(legacyItemId, legacyItemCount, legacyItemData).normalized();
		if (!legacy.itemId().isBlank() && legacy.itemCount() > 0) {
			items.add(legacy);
		}
		return items;
	}

	private static NbtList writeItemList(List<RewardItemEntry> items) {
		NbtList itemList = new NbtList();
		for (RewardItemEntry item : items) {
			NbtCompound itemNbt = new NbtCompound();
			itemNbt.putString(ITEM_ID_KEY, item.itemId());
			itemNbt.putInt(ITEM_COUNT_KEY, item.itemCount());
			itemNbt.putString(ITEM_DATA_KEY, item.itemData());
			itemList.add(itemNbt);
		}
		return itemList;
	}
}
