package com.mryu.signin.network;

import com.mryu.signin.Signin;
import com.mryu.signin.config.SigninLimits;
import com.mryu.signin.data.PlayerSigninRecord;
import com.mryu.signin.data.RewardEntry;
import com.mryu.signin.data.RewardItemEntry;
import com.mryu.signin.data.SigninResult;
import com.mryu.signin.service.SigninNoticeEvent;
import com.mryu.signin.service.SigninService;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class SigninNetworking {
	private static final int MAX_REWARD_PACKET_ENTRIES = SigninLimits.MAX_REWARD_PACKET_ENTRIES;
	private static final int MAX_ITEM_ENTRIES_PER_REWARD = SigninLimits.MAX_ITEM_TYPES_PER_REWARD;
	private static final int MAX_ITEM_ID_LENGTH = SigninLimits.MAX_ITEM_ID_LENGTH;
	private static final int MAX_ITEM_DATA_LENGTH = SigninLimits.MAX_ITEM_DATA_LENGTH;

	public static final Identifier C2S_REQUEST_SYNC = id("request_sync");
	public static final Identifier C2S_SIGN = id("sign_today");
	public static final Identifier C2S_MAKEUP_LATEST_MISSED = id("makeup_latest_missed");
	public static final Identifier C2S_SAVE_REWARDS = id("save_rewards");
	public static final Identifier S2C_SYNC = id("sync");

	private SigninNetworking() {
	}

	public static void registerServerReceivers() {
		ServerPlayNetworking.registerGlobalReceiver(C2S_REQUEST_SYNC, (server, player, handler, buf, responseSender) ->
			server.execute(() -> sendSync(player, false, Text.empty()))
		);

		ServerPlayNetworking.registerGlobalReceiver(C2S_SIGN, (server, player, handler, buf, responseSender) ->
			server.execute(() -> {
				SigninResult result = SigninService.signToday(server, player);
				sendSync(
					player,
					false,
					result.message(),
					result.success() ? SigninNoticeEvent.SIGN_SUCCESS : SigninNoticeEvent.NONE
				);
			})
		);

		ServerPlayNetworking.registerGlobalReceiver(C2S_MAKEUP_LATEST_MISSED, (server, player, handler, buf, responseSender) ->
			server.execute(() -> {
				SigninResult result = SigninService.makeupLatestMissedDay(server, player);
				sendSync(
					player,
					false,
					result.message(),
					result.success() ? SigninNoticeEvent.MAKEUP_SUCCESS : SigninNoticeEvent.NONE
				);
			})
		);

		ServerPlayNetworking.registerGlobalReceiver(C2S_SAVE_REWARDS, (server, player, handler, buf, responseSender) -> {
			List<RewardEntry> rewards;
			try {
				rewards = readRewards(buf);
			} catch (RuntimeException exception) {
				Signin.LOGGER.warn("Rejected invalid reward save packet from {}", player.getName().getString(), exception);
				server.execute(() -> sendSync(player, false, Text.translatable("message.signin.invalid_rewards")));
				return;
			}
			server.execute(() -> {
				SigninResult result = SigninService.saveRewards(server, player, rewards);
				sendSync(player, false, result.message());
			});
		});
	}

	public static void openMainScreen(ServerPlayerEntity player) {
		sendSync(player, true, Text.empty());
	}

	public static void sendSync(ServerPlayerEntity player, boolean openScreen, Text notice) {
		sendSync(player, openScreen, notice, SigninNoticeEvent.NONE);
	}

	public static void sendSync(ServerPlayerEntity player, boolean openScreen, Text notice, int noticeEvent) {
		if (player.getServer() == null) {
			return;
		}
		PlayerSyncPayload payload = SigninService.buildSyncPayload(player.getServer(), player, notice, noticeEvent);
		PacketByteBuf out = PacketByteBufs.create();
		writeSyncPacket(out, openScreen, payload);
		ServerPlayNetworking.send(player, S2C_SYNC, out);
	}

	public static void writeSyncPacket(PacketByteBuf buf, boolean openScreen, PlayerSyncPayload payload) {
		buf.writeBoolean(openScreen);
		buf.writeBoolean(payload.op());
		buf.writeLong(payload.todayEpochDay());
		buf.writeInt(payload.rewardYear());
		buf.writeInt(payload.daysInYear());
		buf.writeInt(payload.todayDayOfYear());

		buf.writeBoolean(payload.record().signedToday());
		buf.writeBoolean(payload.record().signedYesterday());
		buf.writeInt(payload.record().streak());
		buf.writeInt(payload.record().totalDays());
		buf.writeInt(payload.record().makeupCards());
		buf.writeLong(payload.record().lastSignDay());
		buf.writeLong(payload.nextRewardEpochDay());
		buf.writeInt(payload.nextRewardDay());
		buf.writeBoolean(payload.canMakeup());
		buf.writeLong(payload.makeupTargetEpochDay());
		buf.writeInt(payload.signedDaysOfYear().size());
		for (Integer dayOfYear : payload.signedDaysOfYear()) {
			buf.writeInt(dayOfYear == null ? 0 : dayOfYear);
		}
		buf.writeInt(payload.noticeEvent());
		buf.writeText(payload.notice());

		writeRewards(buf, payload.rewards());
	}

	public static ReceivedSyncPacket readSyncPacket(PacketByteBuf buf) {
		boolean openScreen = buf.readBoolean();
		boolean op = buf.readBoolean();
		long todayEpochDay = buf.readLong();
		int rewardYear = buf.readInt();
		int daysInYear = buf.readInt();
		int todayDayOfYear = buf.readInt();

		boolean signedToday = buf.readBoolean();
		boolean signedYesterday = buf.readBoolean();
		int streak = buf.readInt();
		int totalDays = buf.readInt();
		int makeupCards = buf.readInt();
		long lastSignDay = buf.readLong();
		long nextRewardEpochDay = buf.readLong();
		int nextRewardDay = buf.readInt();
		boolean canMakeup = buf.readBoolean();
		long makeupTargetEpochDay = buf.readLong();
		int signedDaysSize = buf.readInt();
		if (signedDaysSize < 0 || signedDaysSize > SigninLimits.MAX_REWARD_DAYS_PER_YEAR) {
			throw new IllegalArgumentException("Invalid signed day list size: " + signedDaysSize);
		}
		List<Integer> signedDaysOfYear = new ArrayList<>(signedDaysSize);
		for (int i = 0; i < signedDaysSize; i++) {
			signedDaysOfYear.add(buf.readInt());
		}
		int noticeEvent = buf.readInt();
		Text notice = buf.readText();

		List<RewardEntry> rewards = readRewards(buf);

		return new ReceivedSyncPacket(
			openScreen,
			new PlayerSyncPayload(
				op,
				todayEpochDay,
				rewardYear,
				daysInYear,
				todayDayOfYear,
				new PlayerSigninRecord(
					signedToday,
					signedYesterday,
					streak,
					totalDays,
					makeupCards,
					lastSignDay
				),
				nextRewardEpochDay,
				nextRewardDay,
				canMakeup,
				makeupTargetEpochDay,
				signedDaysOfYear,
				rewards,
				noticeEvent,
				notice
			)
		);
	}

	public static void writeRewards(PacketByteBuf buf, List<RewardEntry> rewards) {
		List<RewardEntry> safeRewards = rewards == null ? List.of() : rewards;
		int count = Math.min(safeRewards.size(), MAX_REWARD_PACKET_ENTRIES);
		buf.writeVarInt(count);
		for (int i = 0; i < count; i++) {
			RewardEntry reward = safeRewards.get(i);
			buf.writeInt(reward.day());
			buf.writeInt(reward.xp());
			buf.writeInt(reward.makeupCardReward());

			List<RewardItemEntry> items = reward.items() == null ? List.of() : reward.items();
			int itemCount = Math.min(items.size(), MAX_ITEM_ENTRIES_PER_REWARD);
			buf.writeVarInt(itemCount);
			for (int j = 0; j < itemCount; j++) {
				RewardItemEntry item = items.get(j);
				buf.writeString(item.itemId(), MAX_ITEM_ID_LENGTH);
				buf.writeInt(item.itemCount());
				buf.writeString(item.itemData(), MAX_ITEM_DATA_LENGTH);
			}
		}
	}

	public static List<RewardEntry> readRewards(PacketByteBuf buf) {
		int size = buf.readVarInt();
		if (size < 0 || size > MAX_REWARD_PACKET_ENTRIES) {
			throw new IllegalArgumentException("Invalid reward packet size: " + size);
		}

		List<RewardEntry> rewards = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			int day = buf.readInt();
			int xp = buf.readInt();
			int makeupCard = buf.readInt();

			int itemSize = buf.readVarInt();
			if (itemSize < 0 || itemSize > MAX_ITEM_ENTRIES_PER_REWARD) {
				throw new IllegalArgumentException("Invalid reward item packet size: " + itemSize);
			}
			List<RewardItemEntry> items = new ArrayList<>(itemSize);
			for (int j = 0; j < itemSize; j++) {
				items.add(new RewardItemEntry(
					buf.readString(MAX_ITEM_ID_LENGTH),
					buf.readInt(),
					buf.readString(MAX_ITEM_DATA_LENGTH)
				));
			}

			rewards.add(new RewardEntry(day, xp, items, makeupCard));
		}
		return rewards;
	}

	private static Identifier id(String path) {
		return new Identifier(Signin.MOD_ID, path);
	}

	public record ReceivedSyncPacket(boolean openScreen, PlayerSyncPayload payload) {
	}
}
