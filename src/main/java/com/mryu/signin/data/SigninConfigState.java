package com.mryu.signin.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mryu.signin.Signin;
import com.mryu.signin.config.SigninLimits;
import com.mryu.signin.service.RewardValidationService;
import com.mryu.signin.util.CalendarDayUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class SigninConfigState {
	private static final String CONFIG_DIR = "signin";
	private static final String FILE_PREFIX = "rewards-";
	private static final String FILE_SUFFIX = ".json";
	private static final int SCHEMA_VERSION = 1;

	private static final Gson GSON = new GsonBuilder()
		.setPrettyPrinting()
		.disableHtmlEscaping()
		.create();

	private static final Map<MinecraftServer, SigninConfigState> INSTANCES =
		Collections.synchronizedMap(new WeakHashMap<>());

	public static final int REWARD_CYCLE_DAYS = SigninLimits.REWARD_CYCLE_DAYS;
	private static final List<RewardEntry> BASE_DEFAULT_REWARDS = List.of(
		new RewardEntry(1, 20, List.of(), 0),
		new RewardEntry(2, 30, List.of(), 0),
		new RewardEntry(3, 40, List.of(new RewardItemEntry("minecraft:bread", 3, "")), 0),
		new RewardEntry(4, 50, List.of(new RewardItemEntry("minecraft:iron_ingot", 2, "")), 0),
		new RewardEntry(5, 60, List.of(new RewardItemEntry("minecraft:gold_ingot", 2, "")), 0),
		new RewardEntry(6, 80, List.of(new RewardItemEntry("minecraft:diamond", 1, "")), 0),
		new RewardEntry(7, 120, List.of(new RewardItemEntry("minecraft:emerald", 4, "")), 1)
	);

	private final Path configDirectory;
	private final Map<Integer, YearRewardData> yearCache = new HashMap<>();

	private SigninConfigState() {
		this.configDirectory = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_DIR);
	}

	public static SigninConfigState get(MinecraftServer server) {
		synchronized (INSTANCES) {
			return INSTANCES.computeIfAbsent(server, key -> new SigninConfigState());
		}
	}

	public synchronized RewardEntry getRewardForDate(LocalDate date) {
		YearRewardData data = getOrLoadYearData(date.getYear());
		return data.rewardForDay(date.getDayOfYear());
	}

	public synchronized YearRewardsSnapshot getSnapshotForDate(LocalDate date) {
		YearRewardData data = getOrLoadYearData(date.getYear());
		return data.snapshot();
	}

	public synchronized List<RewardEntry> getRewardsForYear(int year) {
		return getOrLoadYearData(year).snapshot().rewards();
	}

	public synchronized boolean updateRewardsForYear(int year, List<RewardEntry> updatedRewards) {
		int expectedDays = CalendarDayUtil.daysInYear(year);
		List<RewardEntry> normalized = RewardValidationService.normalizeAndValidate(updatedRewards, expectedDays);
		if (normalized == null) {
			return false;
		}

		YearRewardData data = getOrLoadYearData(year);
		YearRewardsSnapshot previous = data.snapshot();
		data.setRewards(normalized);
		if (!writeYearFile(data)) {
			data.setRewards(previous.rewards());
			return false;
		}
		return true;
	}

	public static List<RewardEntry> defaultRewardsForYear(int year) {
		return buildDefaultRewards(year);
	}

	public static List<RewardEntry> defaultRewards() {
		return List.copyOf(BASE_DEFAULT_REWARDS);
	}

	private YearRewardData getOrLoadYearData(int year) {
		YearRewardData cached = yearCache.get(year);
		if (cached != null) {
			return cached;
		}

		YearRewardData loaded = loadYearData(year);
		yearCache.put(year, loaded);
		return loaded;
	}

	private YearRewardData loadYearData(int year) {
		Path filePath = configDirectory.resolve(FILE_PREFIX + year + FILE_SUFFIX);
		ReadRewardsResult readResult = readRewardsFromFile(filePath);
		List<RewardEntry> normalized = normalizeLooseRewards(readResult.rewards(), year);

		YearRewardData data = new YearRewardData(year, filePath);
		data.setRewards(normalized);
		if (readResult.status() == ReadStatus.MISSING || readResult.status() == ReadStatus.LOADED && !normalized.equals(readResult.rewards())) {
			writeYearFile(data);
		} else if (readResult.status() == ReadStatus.INVALID) {
			boolean backedUp = backupBrokenFile(filePath);
			if (backedUp) {
				writeYearFile(data);
			}
		}
		return data;
	}

	private ReadRewardsResult readRewardsFromFile(Path path) {
		if (!Files.exists(path)) {
			return new ReadRewardsResult(ReadStatus.MISSING, null);
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			RewardConfigFile file = GSON.fromJson(reader, RewardConfigFile.class);
			if (file == null || file.rewards == null) {
				Signin.LOGGER.warn("Sign-in reward file {} has no valid rewards array. It will be backed up before defaults are written.", path);
				return new ReadRewardsResult(ReadStatus.INVALID, null);
			}
			return new ReadRewardsResult(ReadStatus.LOADED, file.rewards);
		} catch (Exception exception) {
			Signin.LOGGER.warn("Failed to load sign-in reward file {}. It will be backed up before defaults are written.", path, exception);
			return new ReadRewardsResult(ReadStatus.INVALID, null);
		}
	}

	private boolean writeYearFile(YearRewardData data) {
		try {
			Files.createDirectories(configDirectory);
			RewardConfigFile file = new RewardConfigFile();
			file.schemaVersion = SCHEMA_VERSION;
			file.year = data.year();
			file.daysInYear = data.daysInYear();
			file.rewards = data.snapshot().rewards();
			try (Writer writer = Files.newBufferedWriter(
				data.filePath(),
				StandardCharsets.UTF_8,
				StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING,
				StandardOpenOption.WRITE
			)) {
				GSON.toJson(file, writer);
			}
			return true;
		} catch (IOException exception) {
			Signin.LOGGER.error("Failed to save sign-in reward file {}", data.filePath(), exception);
			return false;
		}
	}

	private boolean backupBrokenFile(Path path) {
		if (!Files.exists(path)) {
			return true;
		}
		Path backupPath = path.resolveSibling(path.getFileName() + ".broken-" + System.currentTimeMillis());
		try {
			Files.move(path, backupPath, StandardCopyOption.REPLACE_EXISTING);
			Signin.LOGGER.warn("Backed up broken sign-in reward file {} to {}.", path, backupPath);
			return true;
		} catch (IOException exception) {
			Signin.LOGGER.error("Failed to back up broken sign-in reward file {}. Defaults will not overwrite it.", path, exception);
			return false;
		}
	}

	private static List<RewardEntry> normalizeLooseRewards(List<RewardEntry> input, int year) {
		int daysInYear = CalendarDayUtil.daysInYear(year);
		RewardEntry[] rewardsByDay = new RewardEntry[daysInYear];
		for (int day = 1; day <= daysInYear; day++) {
			rewardsByDay[day - 1] = defaultRewardForDay(day);
		}

		if (input != null) {
			for (RewardEntry reward : input) {
				if (reward == null) {
					continue;
				}
				RewardEntry normalized = reward.normalized();
				int day = normalized.day();
				if (day < 1 || day > daysInYear) {
					continue;
				}
				rewardsByDay[day - 1] = new RewardEntry(day, normalized.xp(), normalized.items(), normalized.makeupCardReward());
			}
		}

		List<RewardEntry> result = new ArrayList<>(daysInYear);
		Collections.addAll(result, rewardsByDay);
		return List.copyOf(result);
	}

	private static List<RewardEntry> buildDefaultRewards(int year) {
		int daysInYear = CalendarDayUtil.daysInYear(year);
		List<RewardEntry> rewards = new ArrayList<>(daysInYear);
		for (int day = 1; day <= daysInYear; day++) {
			rewards.add(defaultRewardForDay(day));
		}
		return List.copyOf(rewards);
	}

	private static RewardEntry defaultRewardForDay(int day) {
		RewardEntry base = BASE_DEFAULT_REWARDS.get((day - 1) % BASE_DEFAULT_REWARDS.size());
		return new RewardEntry(day, base.xp(), base.items(), base.makeupCardReward());
	}

	public record YearRewardsSnapshot(int year, int daysInYear, List<RewardEntry> rewards) {
		public YearRewardsSnapshot {
			rewards = List.copyOf(rewards);
		}
	}

	private static final class YearRewardData {
		private final int year;
		private final int daysInYear;
		private final Path filePath;
		private List<RewardEntry> rewards = List.of();
		private RewardEntry[] rewardsByDay = new RewardEntry[0];
		private YearRewardsSnapshot snapshot = new YearRewardsSnapshot(1970, 365, List.of());

		private YearRewardData(int year, Path filePath) {
			this.year = year;
			this.daysInYear = CalendarDayUtil.daysInYear(year);
			this.filePath = filePath;
		}

		private int year() {
			return year;
		}

		private int daysInYear() {
			return daysInYear;
		}

		private Path filePath() {
			return filePath;
		}

		private YearRewardsSnapshot snapshot() {
			return snapshot;
		}

		private RewardEntry rewardForDay(int dayOfYear) {
			int safeDay = CalendarDayUtil.clampDayOfYear(year, dayOfYear);
			return rewardsByDay[safeDay - 1];
		}

		private void setRewards(List<RewardEntry> normalizedRewards) {
			this.rewards = List.copyOf(normalizedRewards);
			this.rewardsByDay = new RewardEntry[daysInYear];
			for (int i = 0; i < daysInYear; i++) {
				RewardEntry source = i < rewards.size() ? rewards.get(i).normalized() : defaultRewardForDay(i + 1);
				RewardEntry canonical = source.day() == i + 1
					? source
					: new RewardEntry(i + 1, source.xp(), source.items(), source.makeupCardReward());
				this.rewardsByDay[i] = canonical;
			}
			List<RewardEntry> ordered = new ArrayList<>(daysInYear);
			Collections.addAll(ordered, this.rewardsByDay);
			this.snapshot = new YearRewardsSnapshot(year, daysInYear, ordered);
		}
	}

	private static final class RewardConfigFile {
		private int schemaVersion = SCHEMA_VERSION;
		private int year;
		private int daysInYear;
		private List<RewardEntry> rewards = List.of();
	}

	private enum ReadStatus {
		MISSING,
		LOADED,
		INVALID
	}

	private record ReadRewardsResult(ReadStatus status, List<RewardEntry> rewards) {
	}
}
