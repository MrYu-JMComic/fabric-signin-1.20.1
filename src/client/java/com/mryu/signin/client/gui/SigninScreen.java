package com.mryu.signin.client.gui;

import com.mryu.signin.client.network.SigninClientActions;
import com.mryu.signin.client.state.ClientSigninState;
import com.mryu.signin.client.ui.RewardDisplayUtil;
import com.mryu.signin.client.ui.UiLayout;
import com.mryu.signin.client.ui.UiRender;
import com.mryu.signin.client.ui.UiTheme;
import com.mryu.signin.data.PlayerSigninRecord;
import com.mryu.signin.data.RewardEntry;
import com.mryu.signin.data.RewardItemEntry;
import com.mryu.signin.network.PlayerSyncPayload;
import com.mryu.signin.util.CalendarDayUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SigninScreen extends Screen {
	private static final int MIN_PANEL_WIDTH = 320;
	private static final int MAX_PANEL_WIDTH = 860;
	private static final int MIN_PANEL_HEIGHT = 240;
	private static final int MAX_PANEL_HEIGHT = 420;
	private static final int PANEL_MARGIN = 16;
	private static final int HEADER_HEIGHT = 30;
	private static final int STATUS_HEIGHT = 18;
	private static final int REWARD_ROW_TALL = 22;
	private static final int REWARD_ROW_SMALL = 20;
	private static final int ROW_COLOR_SIGNED = 0x8CCAF3D0;
	private static final int ROW_COLOR_MISSED = 0x8CF8CDC3;
	private static final int ROW_COLOR_UNSIGNED = 0x76FFEBD2;

	private ButtonWidget signButton;
	private ButtonWidget makeupButton;
	private ButtonWidget refreshButton;
	private ButtonWidget editButton;
	private ButtonWidget closeButton;
	private ButtonWidget prevMonthButton;
	private ButtonWidget nextMonthButton;

	private int panelLeft;
	private int panelTop;
	private int panelWidth;
	private int panelHeight;
	private int rewardScroll;
	private int displayYear;
	private int displayMonth = 1;
	private int pendingFocusDay = -1;
	private boolean monthInitialized;

	private int lastScreenWidth = -1;
	private int lastScreenHeight = -1;

	private List<RewardEntry> rewardCacheSource = List.of();
	private List<RewardVisual> rewardVisualCache = List.of();

	public SigninScreen() {
		super(Text.translatable("gui.signin.title"));
	}

	@Override
	protected void init() {
		signButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.signin.button.sign"), button -> SigninClientActions.signToday())
			.dimensions(0, 0, 120, 20)
			.build());
		makeupButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.signin.button.makeup"), button -> SigninClientActions.makeupYesterday())
			.dimensions(0, 0, 120, 20)
			.build());
		refreshButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.signin.button.refresh"), button -> SigninClientActions.requestSync())
			.dimensions(0, 0, 120, 20)
			.build());
		editButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.signin.button.op_edit"), button -> {
			if (client != null) {
				client.setScreen(new RewardEditorScreen(this));
			}
		}).dimensions(0, 0, 120, 20).build());
		closeButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.common.close"), button -> close())
			.dimensions(0, 0, 62, 18)
			.build());
		prevMonthButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.common.prev"), button -> switchMonth(-1))
			.dimensions(0, 0, 20, 18)
			.build());
		nextMonthButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.common.next"), button -> switchMonth(1))
			.dimensions(0, 0, 20, 18)
			.build());

		ensureLayout(true);
		refreshButtons();
	}

	public void refreshButtons() {
		if (signButton == null || makeupButton == null || refreshButton == null || editButton == null || prevMonthButton == null || nextMonthButton == null) {
			return;
		}

		PlayerSyncPayload payload = ClientSigninState.get();
		if (payload == null) {
			signButton.active = false;
			makeupButton.active = false;
			refreshButton.active = true;
			editButton.visible = false;
			prevMonthButton.active = false;
			nextMonthButton.active = false;
			return;
		}

		PlayerSigninRecord record = payload.record();
		signButton.active = !record.signedToday();
		makeupButton.active = payload.canMakeup() && record.makeupCards() > 0;
		refreshButton.active = true;
		editButton.visible = payload.op();
		editButton.active = payload.op();
		prevMonthButton.active = displayMonth > 1;
		nextMonthButton.active = displayMonth < 12;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		ensureLayout(false);

		UiRender.drawSoftBlurBackdrop(context, width, height);
		UiRender.drawPanelShadow(context, panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, 9);
		UiRender.drawRoundedRect(context, panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, 8, UiTheme.PANEL_BG);
		UiRender.drawRoundedRect(context, panelLeft, panelTop, panelLeft + panelWidth, panelTop + HEADER_HEIGHT, 8, UiTheme.PANEL_HEADER);

		int dividerX = panelLeft + getLeftSectionWidth();
		UiRender.drawRoundedRect(
			context,
			panelLeft + 8,
			panelTop + HEADER_HEIGHT + 30,
			panelLeft + panelWidth - 8,
			panelTop + panelHeight - 10,
			6,
			UiTheme.PANEL_CONTENT
		);
		context.fill(dividerX, panelTop + HEADER_HEIGHT + 30, dividerX + 1, panelTop + panelHeight - 10, 0x6A9E846C);

		Text titleText = Text.translatable("gui.signin.title").formatted(Formatting.BOLD);
		context.drawText(textRenderer, titleText, (width - textRenderer.getWidth(titleText)) / 2, panelTop + 10, 0xFF5A422B, false);

		PlayerSyncPayload payload = ClientSigninState.get();
		syncMonthState(payload);
		drawStatusChip(context, payload);

		if (payload == null) {
			context.drawText(textRenderer, Text.translatable("gui.signin.waiting_server"), panelLeft + 14, panelTop + HEADER_HEIGHT + 56, UiTheme.TEXT_MUTED, false);
			refreshButtons();
			super.render(context, mouseX, mouseY, delta);
			return;
		}

		refreshRewardVisualCache(payload.rewards());
		drawStatus(context, payload, payload.record());
		drawRewards(context, payload, mouseX, mouseY);

		refreshButtons();
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		PlayerSyncPayload payload = ClientSigninState.get();
		if (payload == null) {
			return super.mouseScrolled(mouseX, mouseY, amount);
		}

		int listX = panelLeft + getLeftSectionWidth() + 14;
		int listY = panelTop + HEADER_HEIGHT + 34;
		int listWidth = panelWidth - getLeftSectionWidth() - 28;
		int listStartY = listY + 18;
		int listEndY = panelTop + panelHeight - 32;
		if (mouseX < listX || mouseX > listX + listWidth || mouseY < listStartY || mouseY > listEndY) {
			return super.mouseScrolled(mouseX, mouseY, amount);
		}

		CalendarDayUtil.MonthRange monthRange = CalendarDayUtil.monthRange(displayYear, displayMonth);
		int monthRows = Math.max(0, monthRange.endDay() - monthRange.startDay() + 1);
		int rowHeight = getRewardRowHeight();
		int visibleRows = Math.max(1, (listEndY - listStartY) / rowHeight);
		int maxScroll = Math.max(0, monthRows - visibleRows);
		if (maxScroll <= 0) {
			return super.mouseScrolled(mouseX, mouseY, amount);
		}

		rewardScroll -= (int) Math.signum(amount);
		if (rewardScroll < 0) {
			rewardScroll = 0;
		}
		if (rewardScroll > maxScroll) {
			rewardScroll = maxScroll;
		}
		return true;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private void switchMonth(int delta) {
		int next = Math.max(1, Math.min(12, displayMonth + delta));
		if (next == displayMonth) {
			return;
		}
		displayMonth = next;
		rewardScroll = 0;
		pendingFocusDay = -1;
	}

	private void syncMonthState(PlayerSyncPayload payload) {
		if (payload == null) {
			monthInitialized = false;
			return;
		}

		boolean changedYear = !monthInitialized || displayYear != payload.rewardYear();
		displayYear = payload.rewardYear();
		if (changedYear) {
			displayMonth = CalendarDayUtil.monthFromDayOfYear(displayYear, payload.todayDayOfYear());
			pendingFocusDay = payload.todayDayOfYear();
			monthInitialized = true;
		}
	}

	private void drawStatus(DrawContext context, PlayerSyncPayload payload, PlayerSigninRecord record) {
		int x = panelLeft + 14;
		int y = panelTop + HEADER_HEIGHT + 34;
		String lastSignDate = record.lastSignDay() >= 0
			? LocalDate.ofEpochDay(record.lastSignDay()).toString()
			: Text.translatable("text.signin.none").getString();

		Text todayText = payload.op()
			? Text.translatable(
				"gui.signin.status.today_with_op",
				record.signedToday()
					? Text.translatable("gui.signin.status.signed")
					: Text.translatable("gui.signin.status.unsigned")
			)
			: Text.translatable(
				"gui.signin.status.today",
				record.signedToday()
					? Text.translatable("gui.signin.status.signed")
					: Text.translatable("gui.signin.status.unsigned")
			);

		LocalDate nextRewardDate = LocalDate.ofEpochDay(payload.nextRewardEpochDay());
		String nextDateLabel = nextRewardDate.getMonthValue() + "." + nextRewardDate.getDayOfMonth();
		context.drawText(textRenderer, todayText, x, y, UiTheme.TEXT_PRIMARY, false);
		context.drawText(textRenderer, Text.translatable("gui.signin.status.streak", record.streak()), x, y + 12, UiTheme.TEXT_MUTED, false);
		context.drawText(textRenderer, Text.translatable("gui.signin.status.total", record.totalDays()), x, y + 24, UiTheme.TEXT_MUTED, false);
		context.drawText(textRenderer, Text.translatable("gui.signin.status.cards", record.makeupCards()), x, y + 36, UiTheme.TEXT_NOTICE, false);
		context.drawText(textRenderer, Text.translatable("gui.signin.status.last", lastSignDate), x, y + 48, UiTheme.TEXT_MUTED, false);
		context.drawText(textRenderer, Text.translatable("gui.signin.status.next_reward_date", nextDateLabel), x, y + 60, 0xFFAA6A36, false);
	}

	private void drawRewards(DrawContext context, PlayerSyncPayload payload, int mouseX, int mouseY) {
		int listX = panelLeft + getLeftSectionWidth() + 14;
		int listY = panelTop + HEADER_HEIGHT + 34;
		int listWidth = panelWidth - getLeftSectionWidth() - 28;
		int listBottom = panelTop + panelHeight - 32;
		int rowHeight = getRewardRowHeight();
		int rowInnerHeight = rowHeight - 2;

		CalendarDayUtil.MonthRange monthRange = CalendarDayUtil.monthRange(displayYear, displayMonth);
		List<RewardVisual> monthRewards = filterMonthRewards(monthRange.startDay(), monthRange.endDay());
		LocalDate nextRewardDate = LocalDate.ofEpochDay(payload.nextRewardEpochDay());
		int nextRewardDay = nextRewardDate.getYear() == displayYear ? nextRewardDate.getDayOfYear() : -1;

		int visibleRows = Math.max(1, (listBottom - (listY + 18)) / rowHeight);
		int maxScroll = Math.max(0, monthRewards.size() - visibleRows);
		if (rewardScroll > maxScroll) {
			rewardScroll = maxScroll;
		}

		if (pendingFocusDay >= monthRange.startDay() && pendingFocusDay <= monthRange.endDay()) {
			int targetIndex = pendingFocusDay - monthRange.startDay();
			rewardScroll = Math.max(0, Math.min(maxScroll, targetIndex - visibleRows / 2));
		}
		pendingFocusDay = -1;

		context.drawText(textRenderer, Text.translatable("gui.signin.reward_preview.title_month", displayYear, displayMonth).formatted(Formatting.BOLD), listX, listY, UiTheme.TEXT_PRIMARY, false);
		if (maxScroll > 0) {
			int pageX = listX + Math.max(0, listWidth - 150);
			context.drawText(
				textRenderer,
				Text.translatable("gui.common.scroll_page", rewardScroll + 1, maxScroll + 1),
				pageX,
				listY + 10,
				UiTheme.TEXT_NOTICE,
				false
			);
		}

		int start = rewardScroll;
		int end = Math.min(monthRewards.size(), start + visibleRows);
		Set<Integer> signedDays = new HashSet<>(payload.signedDaysOfYear());
		int todayDay = payload.todayDayOfYear();
		List<Text> hoverTooltip = null;
		for (int i = start; i < end; i++) {
			RewardVisual visual = monthRewards.get(i);
			int drawIndex = i - start;
			int rowTop = listY + 18 + drawIndex * rowHeight;
			int color = resolveRowColor(visual.day(), todayDay, signedDays);
			UiRender.drawRoundedRect(context, listX, rowTop, listX + listWidth, rowTop + rowInnerHeight, 4, color);
			if (visual.day() == nextRewardDay) {
				context.fill(listX, rowTop, listX + 2, rowTop + rowInnerHeight, 0xFFAA6A36);
			}

			context.drawItem(visual.stack(), listX + 4, rowTop + 1);
			context.drawItemInSlot(textRenderer, visual.stack(), listX + 4, rowTop + 1);
			String dayLabel = CalendarDayUtil.monthDayLabel(displayYear, visual.day());

			String line = Text.translatable(
				"gui.signin.reward_preview.row",
				dayLabel,
				visual.xp(),
				visual.itemText(),
				visual.makeupCard()
			).getString();
			context.drawText(
				textRenderer,
				Text.literal(UiRender.ellipsize(textRenderer, line, listWidth - 30)),
				listX + 24,
				rowTop + 6,
				UiTheme.TEXT_PRIMARY,
				false
			);

			boolean inRewardIcon = mouseX >= listX + 4 && mouseX <= listX + 20 && mouseY >= rowTop + 1 && mouseY <= rowTop + 17;
			if (inRewardIcon) {
				hoverTooltip = buildRewardTooltipLines(visual, Math.max(220, panelWidth - 90));
			}
		}

		if (hoverTooltip != null && !hoverTooltip.isEmpty()) {
			context.drawTooltip(textRenderer, hoverTooltip, mouseX, mouseY);
		}
	}

	private List<RewardVisual> filterMonthRewards(int startDay, int endDay) {
		List<RewardVisual> filtered = new ArrayList<>(endDay - startDay + 1);
		for (RewardVisual visual : rewardVisualCache) {
			if (visual.day() >= startDay && visual.day() <= endDay) {
				filtered.add(visual);
			}
		}
		return filtered;
	}

	private void drawStatusChip(DrawContext context, PlayerSyncPayload payload) {
		int x = panelLeft + 12;
		int y = panelTop + HEADER_HEIGHT + 8;
		int w = panelWidth - 24;
		UiRender.drawRoundedRect(context, x, y, x + w, y + STATUS_HEIGHT, 5, UiTheme.STATUS_CHIP);

		String message;
		if (payload == null) {
			message = Text.translatable("gui.signin.status.syncing").getString();
		} else if (!payload.notice().getString().isBlank()) {
			message = payload.notice().getString();
		} else if (payload.op()) {
			message = Text.translatable("gui.signin.status.op_tip").getString();
		} else {
			message = Text.translatable("gui.signin.status.normal_tip").getString();
		}

		context.drawText(
			textRenderer,
			Text.literal(UiRender.ellipsize(textRenderer, message, w - 10)),
			x + 5,
			y + 5,
			UiTheme.TEXT_NOTICE,
			false
		);
	}

	private void refreshRewardVisualCache(List<RewardEntry> rewards) {
		if (rewards.equals(rewardCacheSource)) {
			return;
		}

		rewardCacheSource = List.copyOf(rewards);
		List<RewardVisual> visuals = new ArrayList<>(rewardCacheSource.size());
		for (RewardEntry reward : rewardCacheSource) {
			RewardItemEntry first = reward.items().isEmpty() ? null : reward.items().get(0);
			ItemStack stack = RewardDisplayUtil.resolveItemStack(first);
			String localized = RewardDisplayUtil.localizedItemSummary(reward.items(), 2);
			List<String> itemLines = RewardDisplayUtil.localizedItemLines(reward.items());
			visuals.add(new RewardVisual(reward.day(), reward.xp(), reward.makeupCardReward(), stack, localized, itemLines));
		}
		rewardVisualCache = visuals;
		rewardScroll = 0;
	}

	private List<Text> buildRewardTooltipLines(RewardVisual visual, int maxWidth) {
		List<Text> lines = new ArrayList<>();
		lines.add(Text.translatable("gui.signin.reward_preview.tooltip_head", CalendarDayUtil.monthDayLabel(displayYear, visual.day()), visual.xp(), visual.makeupCard()));
		lines.add(Text.translatable("gui.signin.reward_preview.tooltip_items"));
		if (visual.itemLines().isEmpty()) {
			lines.add(Text.translatable("gui.signin.reward_preview.tooltip_empty"));
			return lines;
		}

		for (int i = 0; i < visual.itemLines().size(); i++) {
			String line = Text.translatable("text.signin.indexed_line", i + 1, visual.itemLines().get(i)).getString();
			lines.addAll(UiRender.wrapTooltipLines(textRenderer, line, maxWidth));
		}
		return lines;
	}

	private void ensureLayout(boolean force) {
		if (!force && width == lastScreenWidth && height == lastScreenHeight) {
			return;
		}
		lastScreenWidth = width;
		lastScreenHeight = height;

		UiLayout.PanelBounds bounds = UiLayout.centeredPanel(
			width,
			height,
			MIN_PANEL_WIDTH,
			MAX_PANEL_WIDTH,
			MIN_PANEL_HEIGHT,
			MAX_PANEL_HEIGHT,
			PANEL_MARGIN
		);
		panelLeft = bounds.left();
		panelTop = bounds.top();
		panelWidth = bounds.width();
		panelHeight = bounds.height();

		if (signButton == null || makeupButton == null || refreshButton == null || editButton == null || closeButton == null || prevMonthButton == null || nextMonthButton == null) {
			return;
		}

		int leftSectionWidth = getLeftSectionWidth();
		int buttonX = panelLeft + 12;
		int buttonWidth = Math.max(94, leftSectionWidth - 20);
		int buttonHeight = panelHeight < 250 ? 18 : 20;
		int buttonGap = panelHeight < 250 ? 3 : 5;
		int buttonBlockHeight = 4 * buttonHeight + 3 * buttonGap;

		int statusBlockTop = panelTop + HEADER_HEIGHT + 34;
		int statusBlockBottom = statusBlockTop + 5 * 12 + 10;
		int preferredActionStartY = statusBlockBottom + 10;
		int maxActionStartY = panelTop + panelHeight - 12 - buttonBlockHeight;
		int minActionStartY = panelTop + HEADER_HEIGHT + STATUS_HEIGHT + 16;
		int actionStartY = Math.max(minActionStartY, Math.min(preferredActionStartY, maxActionStartY));

		signButton.setPosition(buttonX, actionStartY);
		signButton.setWidth(buttonWidth);
		makeupButton.setPosition(buttonX, actionStartY + (buttonHeight + buttonGap));
		makeupButton.setWidth(buttonWidth);
		refreshButton.setPosition(buttonX, actionStartY + (buttonHeight + buttonGap) * 2);
		refreshButton.setWidth(buttonWidth);
		editButton.setPosition(buttonX, actionStartY + (buttonHeight + buttonGap) * 3);
		editButton.setWidth(buttonWidth);

		closeButton.setPosition(panelLeft + panelWidth - 74, panelTop + panelHeight - 24);

		int listX = panelLeft + getLeftSectionWidth() + 14;
		int listWidth = panelWidth - getLeftSectionWidth() - 28;
		prevMonthButton.setPosition(listX + listWidth - 56, panelTop + HEADER_HEIGHT + 32);
		nextMonthButton.setPosition(listX + listWidth - 32, panelTop + HEADER_HEIGHT + 32);
	}

	private int resolveRowColor(int dayOfYear, int todayDayOfYear, Set<Integer> signedDays) {
		if (signedDays.contains(dayOfYear)) {
			return ROW_COLOR_SIGNED;
		}
		if (dayOfYear < todayDayOfYear) {
			return ROW_COLOR_MISSED;
		}
		return ROW_COLOR_UNSIGNED;
	}

	private int getLeftSectionWidth() {
		int suggested = panelWidth / 3;
		if (suggested < 146) {
			suggested = 146;
		}
		if (suggested > 250) {
			suggested = 250;
		}
		return suggested;
	}

	private int getRewardRowHeight() {
		return panelHeight < 250 ? REWARD_ROW_SMALL : REWARD_ROW_TALL;
	}

	private record RewardVisual(int day, int xp, int makeupCard, ItemStack stack, String itemText, List<String> itemLines) {
	}
}
