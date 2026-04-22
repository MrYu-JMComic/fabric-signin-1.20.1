package com.mryu.signin.service;

import com.mryu.signin.command.SigninPermissions;
import com.mryu.signin.config.SigninLimits;
import com.mryu.signin.data.PlayerSigninRecord;
import com.mryu.signin.data.RewardEntry;
import com.mryu.signin.data.RewardItemEntry;
import com.mryu.signin.data.SigninConfigState;
import com.mryu.signin.data.SigninPersistentState;
import com.mryu.signin.data.SigninResult;
import com.mryu.signin.network.PlayerSyncPayload;
import com.mryu.signin.util.CalendarDayUtil;
import com.mryu.signin.util.RewardItemDataUtil;
import com.mryu.signin.util.RewardItemResolver;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SigninService {

	private SigninService() {
	}

	public static long todayEpochDay() {
		return todayDate().toEpochDay();
	}

	public static LocalDate todayDate() {
		return CalendarDayUtil.today();
	}

	public static SigninResult signToday(MinecraftServer server, ServerPlayerEntity player) {
		LocalDate todayDate = todayDate();
		long today = todayDate.toEpochDay();
		SigninPersistentState dataState = SigninPersistentState.get(server);
		SigninConfigState configState = SigninConfigState.get(server);

		if (dataState.hasSigned(player.getUuid(), today)) {
			return result(false, Text.translatable("message.signin.already_signed"), dataState, player, today, null);
		}

		dataState.addSignedDay(player.getUuid(), today);
		RewardEntry reward = configState.getRewardForDate(todayDate);
		giveReward(player, dataState, reward);

		Text message = buildRewardMessage("message.signin.sign_success", snapshot(dataState, player, today), reward);
		return result(true, message, dataState, player, today, reward);
	}

	public static SigninResult makeupYesterday(MinecraftServer server, ServerPlayerEntity player) {
		LocalDate todayDate = todayDate();
		long today = todayDate.toEpochDay();
		SigninPersistentState dataState = SigninPersistentState.get(server);
		SigninConfigState configState = SigninConfigState.get(server);

		long targetDay = findLatestMakeupTargetEpochDay(dataState, player, todayDate);
		if (targetDay < 0L) {
			return result(false, Text.translatable("message.signin.makeup_not_needed"), dataState, player, today, null);
		}
		if (!dataState.consumeMakeupCard(player.getUuid())) {
			return result(false, Text.translatable("message.signin.makeup_no_card"), dataState, player, today, null);
		}

		LocalDate targetDate = LocalDate.ofEpochDay(targetDay);
		dataState.addSignedDay(player.getUuid(), targetDay);
		RewardEntry reward = configState.getRewardForDate(targetDate);
		giveReward(player, dataState, reward);

		Text message = buildRewardMessage("message.signin.makeup_success", snapshot(dataState, player, today), reward);
		return result(true, message, dataState, player, today, reward);
	}

	public static PlayerSyncPayload buildSyncPayload(MinecraftServer server, ServerPlayerEntity player, Text notice) {
		return buildSyncPayload(server, player, notice, SigninNoticeEvent.NONE);
	}

	public static PlayerSyncPayload buildSyncPayload(MinecraftServer server, ServerPlayerEntity player, Text notice, int noticeEvent) {
		LocalDate todayDate = todayDate();
		long today = todayDate.toEpochDay();
		SigninPersistentState dataState = SigninPersistentState.get(server);
		SigninConfigState configState = SigninConfigState.get(server);
		SigninConfigState.YearRewardsSnapshot rewardSnapshot = configState.getSnapshotForDate(todayDate);
		PlayerSigninRecord record = snapshot(dataState, player, today);
		int todayDayOfYear = todayDate.getDayOfYear();
		long nextRewardEpochDay = record.signedToday() ? today + 1L : today;
		LocalDate nextRewardDate = LocalDate.ofEpochDay(nextRewardEpochDay);
		int nextRewardDay = nextRewardDate.getDayOfYear();
		long yearStartEpochDay = LocalDate.of(todayDate.getYear(), 1, 1).toEpochDay();
		long yearEndEpochDay = LocalDate.of(todayDate.getYear(), 12, 31).toEpochDay();
		long makeupTargetEpochDay = findLatestMakeupTargetEpochDay(dataState, player, todayDate);
		boolean canMakeup = makeupTargetEpochDay >= 0L;
		List<Integer> signedDaysOfYear = toDayOfYearList(dataState.getSignedDaysInRange(player.getUuid(), yearStartEpochDay, yearEndEpochDay), yearStartEpochDay);

		return new PlayerSyncPayload(
			SigninPermissions.canManageRewards(player),
			today,
			rewardSnapshot.year(),
			rewardSnapshot.daysInYear(),
			todayDayOfYear,
			record,
			nextRewardEpochDay,
			nextRewardDay,
			canMakeup,
			makeupTargetEpochDay,
			signedDaysOfYear,
			rewardSnapshot.rewards(),
			noticeEvent,
			notice == null ? Text.empty() : notice
		);
	}

	public static SigninResult saveRewards(MinecraftServer server, ServerPlayerEntity player, List<RewardEntry> rewards) {
		LocalDate todayDate = todayDate();
		long today = todayDate.toEpochDay();
		SigninPersistentState dataState = SigninPersistentState.get(server);

		if (!SigninPermissions.canManageRewards(player)) {
			return result(false, Text.translatable("message.signin.no_admin_permission"), dataState, player, today, null);
		}

		int rewardYear = todayDate.getYear();
		boolean updated = SigninConfigState.get(server).updateRewardsForYear(rewardYear, rewards);
		if (!updated) {
			return result(false, Text.translatable("message.signin.invalid_rewards"), dataState, player, today, null);
		}

		return result(true, Text.translatable("message.signin.save_rewards_ok"), dataState, player, today, null);
	}

	public static boolean clearPlayerSigninData(MinecraftServer server, ServerPlayerEntity target) {
		return SigninPersistentState.get(server).clearPlayerData(target.getUuid());
	}

	public static Text buildStatusText(PlayerSyncPayload payload) {
		PlayerSigninRecord record = payload.record();
		if (record.totalDays() <= 0) {
			return Text.translatable("message.signin.status.no_record");
		}

		Object lastSignDate = record.lastSignDay() >= 0
			? LocalDate.ofEpochDay(record.lastSignDay()).toString()
			: Text.translatable("text.signin.none");

		return Text.translatable(
			"message.signin.status.summary",
			record.signedToday()
				? Text.translatable("message.signin.status.today_signed")
				: Text.translatable("message.signin.status.today_unsigned"),
			record.streak(),
			record.totalDays(),
			record.makeupCards(),
			lastSignDate
		);
	}

	private static Text buildRewardMessage(String titleKey, PlayerSigninRecord record, RewardEntry reward) {
		int itemTypes = reward.items().size();
		int totalCount = 0;
		for (RewardItemEntry item : reward.items()) {
			totalCount += Math.max(0, item.itemCount());
		}

		List<Text> rewardParts = new ArrayList<>(3);
		if (reward.xp() > 0) {
			rewardParts.add(Text.translatable("message.signin.reward_part.xp", reward.xp()));
		}
		if (itemTypes > 0 && totalCount > 0) {
			rewardParts.add(Text.translatable("message.signin.reward_part.items", itemTypes, totalCount));
		}
		if (reward.makeupCardReward() > 0) {
			rewardParts.add(Text.translatable("message.signin.reward_part.card", reward.makeupCardReward()));
		}

		if (rewardParts.isEmpty()) {
			return Text.translatable(
				"message.signin.reward_result.no_reward",
				Text.translatable(titleKey),
				record.streak(),
				record.totalDays()
			);
		}

		MutableText rewardSummary = Text.empty();
		for (int i = 0; i < rewardParts.size(); i++) {
			if (i > 0) {
				rewardSummary.append(Text.translatable("text.signin.separator.reward_part"));
			}
			rewardSummary.append(rewardParts.get(i));
		}

		return Text.translatable(
			"message.signin.reward_result.with_reward",
			Text.translatable(titleKey),
			rewardSummary,
			record.streak(),
			record.totalDays()
		);
	}

	private static void giveReward(ServerPlayerEntity player, SigninPersistentState dataState, RewardEntry reward) {
		if (reward.xp() > 0) {
			player.addExperience(reward.xp());
		}

		for (RewardItemEntry itemEntry : reward.items()) {
			Item item = RewardItemResolver.resolveNonAirItem(itemEntry.itemId());
			if (item == null || itemEntry.itemCount() <= 0) {
				continue;
			}
			NbtCompound itemData = RewardItemDataUtil.parseSnbtOrNull(itemEntry.itemData());
			giveItemRewardSafely(player, item, itemEntry.itemCount(), itemData);
		}

		if (reward.makeupCardReward() > 0) {
			dataState.addMakeupCards(player.getUuid(), reward.makeupCardReward());
		}
	}

	private static void giveItemRewardSafely(ServerPlayerEntity player, Item item, int requestedCount, NbtCompound itemData) {
		int remaining = Math.min(SigninLimits.MAX_REWARD_ITEM_COUNT, Math.max(0, requestedCount));
		int maxStack = Math.max(1, item.getMaxCount());
		while (remaining > 0) {
			int chunk = Math.min(maxStack, remaining);
			ItemStack stack = new ItemStack(item, chunk);
			if (itemData != null) {
				stack.setNbt(itemData.copy());
			}
			if (!player.giveItemStack(stack)) {
				player.dropItem(stack, false);
			}
			remaining -= chunk;
		}
	}

	private static SigninResult result(
		boolean success,
		Text message,
		SigninPersistentState dataState,
		ServerPlayerEntity player,
		long today,
		RewardEntry reward
	) {
		return new SigninResult(success, message, snapshot(dataState, player, today), reward);
	}

	private static PlayerSigninRecord snapshot(SigninPersistentState dataState, ServerPlayerEntity player, long today) {
		return dataState.getRecordSnapshot(player.getUuid(), today);
	}

	private static long findLatestMakeupTargetEpochDay(SigninPersistentState dataState, ServerPlayerEntity player, LocalDate todayDate) {
		long todayEpochDay = todayDate.toEpochDay();
		long startEpochDay = LocalDate.of(todayDate.getYear(), 1, 1).toEpochDay();
		long endEpochDay = todayEpochDay - 1L;
		if (endEpochDay < startEpochDay) {
			return -1L;
		}
		return dataState.findLatestUnsignedDayInRange(player.getUuid(), startEpochDay, endEpochDay);
	}

	private static List<Integer> toDayOfYearList(List<Long> epochDays, long yearStartEpochDay) {
		if (epochDays == null || epochDays.isEmpty()) {
			return List.of();
		}
		List<Integer> result = new ArrayList<>(epochDays.size());
		Set<Integer> dedupe = new HashSet<>();
		for (long epochDay : epochDays) {
			int dayOfYear = (int) (epochDay - yearStartEpochDay) + 1;
			if (dayOfYear < 1) {
				continue;
			}
			if (dedupe.add(dayOfYear)) {
				result.add(dayOfYear);
			}
		}
		return result;
	}
}
