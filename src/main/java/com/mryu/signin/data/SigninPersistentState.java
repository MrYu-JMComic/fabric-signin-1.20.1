package com.mryu.signin.data;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLong;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SigninPersistentState extends PersistentState {
	private static final String SAVE_KEY = "signin_data";
	private static final String PLAYERS_KEY = "players";

	private static final String UUID_KEY = "uuid";
	private static final String SIGNED_DAYS_KEY = "signedDays";
	private static final String MAKEUP_CARDS_KEY = "makeupCards";

	private final Map<UUID, MutableRecord> records = new HashMap<>();

	public static SigninPersistentState get(MinecraftServer server) {
		PersistentStateManager stateManager = server.getOverworld().getPersistentStateManager();
		return stateManager.getOrCreate(SigninPersistentState::fromNbt, SigninPersistentState::new, SAVE_KEY);
	}

	public static SigninPersistentState fromNbt(NbtCompound nbt) {
		SigninPersistentState state = new SigninPersistentState();
		NbtList players = nbt.getList(PLAYERS_KEY, NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < players.size(); i++) {
			NbtCompound playerNbt = players.getCompound(i);
			if (!playerNbt.containsUuid(UUID_KEY)) {
				continue;
			}

			UUID uuid = playerNbt.getUuid(UUID_KEY);
			MutableRecord record = new MutableRecord();
			record.makeupCards = playerNbt.contains(MAKEUP_CARDS_KEY, NbtElement.INT_TYPE)
				? playerNbt.getInt(MAKEUP_CARDS_KEY)
				: 0;

			if (playerNbt.contains(SIGNED_DAYS_KEY, NbtElement.LIST_TYPE)) {
				NbtList signedDaysNbt = playerNbt.getList(SIGNED_DAYS_KEY, NbtElement.LONG_TYPE);
				for (int dayIndex = 0; dayIndex < signedDaysNbt.size(); dayIndex++) {
					record.addSignedDay(((NbtLong) signedDaysNbt.get(dayIndex)).longValue());
				}
			}

			// Backward compatibility for the first MVP schema.
			if (playerNbt.contains("lastSignDay", NbtElement.LONG_TYPE)) {
				record.addSignedDay(playerNbt.getLong("lastSignDay"));
			}

			state.records.put(uuid, record);
		}
		return state;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		NbtList players = new NbtList();
		for (Map.Entry<UUID, MutableRecord> entry : records.entrySet()) {
			MutableRecord record = entry.getValue();
			NbtCompound playerNbt = new NbtCompound();
			playerNbt.putUuid(UUID_KEY, entry.getKey());
			playerNbt.putInt(MAKEUP_CARDS_KEY, record.makeupCards);

			NbtList signedDaysNbt = new NbtList();
			for (long signedDay : record.signedDays) {
				signedDaysNbt.add(NbtLong.of(signedDay));
			}
			playerNbt.put(SIGNED_DAYS_KEY, signedDaysNbt);
			players.add(playerNbt);
		}
		nbt.put(PLAYERS_KEY, players);
		return nbt;
	}

	public synchronized boolean hasSigned(UUID uuid, long epochDay) {
		return getRecord(uuid).signedDays.contains(epochDay);
	}

	public synchronized void addSignedDay(UUID uuid, long epochDay) {
		MutableRecord record = getRecord(uuid);
		if (record.addSignedDay(epochDay)) {
			markDirty();
		}
	}

	public synchronized int getTotalDays(UUID uuid) {
		return getRecord(uuid).signedDays.size();
	}

	public synchronized int getMakeupCards(UUID uuid) {
		return getRecord(uuid).makeupCards;
	}

	public synchronized void addMakeupCards(UUID uuid, int amount) {
		if (amount <= 0) {
			return;
		}
		MutableRecord record = getRecord(uuid);
		record.makeupCards += amount;
		markDirty();
	}

	public synchronized boolean consumeMakeupCard(UUID uuid) {
		MutableRecord record = getRecord(uuid);
		if (record.makeupCards <= 0) {
			return false;
		}
		record.makeupCards -= 1;
		markDirty();
		return true;
	}

	public synchronized int calculateStreak(UUID uuid, long endEpochDay) {
		Set<Long> signedDays = getRecord(uuid).signedDays;
		return calculateStreak(signedDays, endEpochDay);
	}

	private static int calculateStreak(Set<Long> signedDays, long endEpochDay) {
		int streak = 0;
		long day = endEpochDay;
		while (signedDays.contains(day)) {
			streak++;
			day--;
		}
		return streak;
	}

	public synchronized long getLastSignedDay(UUID uuid) {
		return getRecord(uuid).lastSignedDay();
	}

	public synchronized PlayerSigninRecord getRecordSnapshot(UUID uuid, long todayEpochDay) {
		MutableRecord record = getRecord(uuid);
		boolean signedToday = record.signedDays.contains(todayEpochDay);
		boolean signedYesterday = record.signedDays.contains(todayEpochDay - 1L);
		long streakEndDay = signedToday ? todayEpochDay : todayEpochDay - 1L;
		int streak = calculateStreak(record.signedDays, streakEndDay);
		return new PlayerSigninRecord(
			signedToday,
			signedYesterday,
			streak,
			record.signedDays.size(),
			record.makeupCards,
			record.lastSignedDay()
		);
	}

	public synchronized boolean clearPlayerData(UUID uuid) {
		MutableRecord removed = records.remove(uuid);
		if (removed != null) {
			markDirty();
			return true;
		}
		return false;
	}

	private MutableRecord getRecord(UUID uuid) {
		return records.computeIfAbsent(uuid, key -> new MutableRecord());
	}

	private static final class MutableRecord {
		private final Set<Long> signedDays = new HashSet<>();
		private int makeupCards = 0;
		private long lastSignedDay = -1L;

		private boolean addSignedDay(long epochDay) {
			if (!signedDays.add(epochDay)) {
				return false;
			}
			if (epochDay > lastSignedDay) {
				lastSignedDay = epochDay;
			}
			return true;
		}

		private long lastSignedDay() {
			return lastSignedDay;
		}
	}
}
