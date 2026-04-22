package com.mryu.signin.client.gui;

import com.mryu.signin.client.network.SigninClientActions;
import com.mryu.signin.client.state.ClientSigninState;
import com.mryu.signin.client.ui.RewardDisplayUtil;
import com.mryu.signin.client.ui.UiLayout;
import com.mryu.signin.client.ui.UiRender;
import com.mryu.signin.client.ui.UiTheme;
import com.mryu.signin.data.RewardEntry;
import com.mryu.signin.data.RewardItemEntry;
import com.mryu.signin.data.SigninConfigState;
import com.mryu.signin.network.PlayerSyncPayload;
import com.mryu.signin.util.CalendarDayUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RewardEditorScreen extends Screen {
	private static final int MIN_PANEL_WIDTH = 520;
	private static final int MAX_PANEL_WIDTH = 1260;
	private static final int MIN_PANEL_HEIGHT = 260;
	private static final int MAX_PANEL_HEIGHT = 680;
	private static final int PANEL_MARGIN = 18;

	private static final int ROW_HEIGHT = 46;
	private static final int HEADER_HEIGHT = 32;
	private static final int STATUS_HEIGHT = 18;
	private static final int ROW_A = 0x72FFE9D2;
	private static final int ROW_B = 0x72FFE1C1;
	private static final int ACCENT = 0xFFE4904E;

	private final Screen parent;
	private final List<RowWidgets> rows = new ArrayList<>();
	private final List<RewardEntry> draftRewards = new ArrayList<>();
	private final Map<Integer, RewardRowVisual> rewardVisualCache = new HashMap<>();
	private final int initialDisplayMonth;

	private int panelLeft;
	private int panelTop;
	private int panelWidth;
	private int panelHeight;
	private int rowScroll;
	private int displayYear;
	private int displayMonth;
	private int monthStartDay;
	private int monthEndDay;
	private int pendingFocusDay = -1;

	private int rowDayX;
	private int rowXpX;
	private int rowCardX;
	private int rowFieldWidth;
	private int rowIconX;
	private int rowSummaryX;
	private int rowSummaryWidth;
	private int rowManageX;
	private int rowHeldX;
	private int rowManageWidth;
	private int rowHeldWidth;

	private int lastScreenWidth = -1;
	private int lastScreenHeight = -1;
	private boolean layoutDirty = true;

	private ButtonWidget saveButton;
	private ButtonWidget resetButton;
	private ButtonWidget backButton;
	private ButtonWidget prevMonthButton;
	private ButtonWidget nextMonthButton;
	private Text statusText = Text.empty();
	private int statusColor = 0xFFB4CED4;

	public RewardEditorScreen(Screen parent) {
		this(parent, List.of(), 0, 0, Text.empty(), 0xFFB4CED4);
	}

	public RewardEditorScreen(
		Screen parent,
		List<RewardEntry> initialDraft,
		int initialDisplayMonth,
		int initialRowScroll,
		Text initialStatusText,
		int initialStatusColor
	) {
		super(Text.translatable("gui.reward_editor.title"));
		this.parent = parent;
		this.initialDisplayMonth = initialDisplayMonth;
		this.draftRewards.addAll(initialDraft);
		this.rowScroll = Math.max(0, initialRowScroll);
		this.statusText = initialStatusText == null ? Text.empty() : initialStatusText;
		this.statusColor = initialStatusColor;
	}

	@Override
	protected void init() {
		PlayerSyncPayload payload = ClientSigninState.get();
		if (payload == null || !payload.op()) {
			close();
			return;
		}

		displayYear = payload.rewardYear();
		displayMonth = initialDisplayMonth > 0
			? Math.max(1, Math.min(12, initialDisplayMonth))
			: CalendarDayUtil.monthFromDayOfYear(payload.rewardYear(), payload.todayDayOfYear());
		if (initialDisplayMonth <= 0) {
			pendingFocusDay = payload.todayDayOfYear();
		}

		loadDraftIfNeeded(payload.rewards(), payload.daysInYear(), payload.rewardYear());
		refreshMonthBounds();
		buildRowWidgetsForCurrentMonth();
		buildFooterButtons();
		ensureLayout(true);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		ensureLayout(false);

		UiRender.drawSoftBlurBackdrop(context, width, height);
		UiRender.drawPanelShadow(context, panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, 9);
		UiRender.drawRoundedRect(context, panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, 8, UiTheme.PANEL_BG);
		int contentTop = getRowsTop() - 10;
		UiRender.drawRoundedRect(
			context,
			panelLeft + 8,
			contentTop,
			panelLeft + panelWidth - 8,
			panelTop + panelHeight - 38,
			6,
			UiTheme.PANEL_CONTENT
		);
		UiRender.drawRoundedRect(context, panelLeft, panelTop, panelLeft + panelWidth, panelTop + HEADER_HEIGHT, 8, UiTheme.PANEL_HEADER);

		Text titleText = title.copy().formatted(Formatting.BOLD);
		context.drawText(textRenderer, titleText, (width - textRenderer.getWidth(titleText)) / 2, panelTop + 11, 0xFF5A412C, false);
		int totalPages = Math.max(1, rows.size() - getVisibleRowCount() + 1);
		int monthControlLeft = panelLeft + panelWidth - 108;
		String pageText = totalPages > 1 ? Text.translatable("gui.common.scroll_page", rowScroll + 1, totalPages).getString() : "";
		int pageWidth = pageText.isEmpty() ? 0 : textRenderer.getWidth(pageText);
		String subtitle = Text.translatable("gui.reward_editor.subtitle_month", displayYear, displayMonth).getString();
		int subtitleRight = monthControlLeft - 10;
		if (totalPages > 1) {
			subtitleRight = monthControlLeft - pageWidth - 18;
		}
		int subtitleMaxWidth = Math.max(80, subtitleRight - (panelLeft + 14));
		context.drawText(
			textRenderer,
			Text.literal(UiRender.ellipsize(textRenderer, subtitle, subtitleMaxWidth)),
			panelLeft + 14,
			panelTop + HEADER_HEIGHT + 2,
			UiTheme.TEXT_MUTED,
			false
		);

		if (totalPages > 1) {
			int pageX = monthControlLeft - pageWidth - 8;
			if (pageX < panelLeft + 14) {
				pageX = panelLeft + 14;
			}
			context.drawText(
				textRenderer,
				Text.literal(pageText),
				pageX,
				panelTop + HEADER_HEIGHT + 2,
				ACCENT,
				false
			);
		}

		drawStatusChip(context);
		drawTableHeader(context);
		drawRowCards(context, mouseX, mouseY);

		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		int rowsStartY = getRowsTop() - 2;
		int rowsEndY = getRowsBottom();
		if (mouseY < rowsStartY || mouseY > rowsEndY) {
			return super.mouseScrolled(mouseX, mouseY, amount);
		}

		int maxScroll = Math.max(0, rows.size() - getVisibleRowCount());
		if (maxScroll <= 0) {
			return super.mouseScrolled(mouseX, mouseY, amount);
		}

		rowScroll -= (int) Math.signum(amount);
		if (rowScroll < 0) {
			rowScroll = 0;
		}
		if (rowScroll > maxScroll) {
			rowScroll = maxScroll;
		}
		layoutDirty = true;
		return true;
	}

	@Override
	public void close() {
		if (client != null) {
			client.setScreen(parent);
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	public void onServerPayloadUpdated() {
		PlayerSyncPayload payload = ClientSigninState.get();
		if (payload != null && payload.op() && !payload.notice().getString().isBlank()) {
			setStatus(payload.notice().getString(), 0xFF9CF2D7);
		}
	}

	public void applyDayItems(int day, List<RewardItemEntry> items) {
		captureDraftFromWidgets();
		for (int i = 0; i < draftRewards.size(); i++) {
			RewardEntry reward = draftRewards.get(i);
			if (reward.day() != day) {
				continue;
			}
			RewardEntry updated = new RewardEntry(day, reward.xp(), items, reward.makeupCardReward());
			draftRewards.set(i, updated);
			rewardVisualCache.remove(day);
			setStatus(Text.translatable("gui.reward_editor.status.day_items_updated", day, items.size()).getString(), 0xFF9CF2D7);
			break;
		}
		rebuildEditor(displayMonth, rowScroll, statusText, statusColor);
	}

	private void buildRowWidgetsForCurrentMonth() {
		rows.clear();
		rewardVisualCache.clear();
		for (int day = monthStartDay; day <= monthEndDay; day++) {
			RewardEntry reward = getDraftByDay(day);

			TextFieldWidget xpField = addDrawableChild(new TextFieldWidget(textRenderer, 0, 0, 64, 16, Text.translatable("gui.reward_editor.header.xp")));
			xpField.setMaxLength(8);
			xpField.setText(String.valueOf(reward.xp()));

			TextFieldWidget cardField = addDrawableChild(new TextFieldWidget(textRenderer, 0, 0, 64, 16, Text.translatable("gui.reward_editor.header.card")));
			cardField.setMaxLength(6);
			cardField.setText(String.valueOf(reward.makeupCardReward()));

			final int rewardDay = day;
			ButtonWidget manageItemsButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.reward_editor.button.manage_items"), button -> openItemsEditor(rewardDay))
				.dimensions(0, 0, 94, 20)
				.build());
			ButtonWidget heldAppendButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.reward_editor.button.held_append"), button -> appendHeldItem(rewardDay))
				.dimensions(0, 0, 62, 20)
				.build());

			rows.add(new RowWidgets(rewardDay, xpField, cardField, manageItemsButton, heldAppendButton));
		}
	}

	private void buildFooterButtons() {
		prevMonthButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.common.prev"), button -> switchMonth(-1))
			.dimensions(0, 0, 20, 18)
			.build());
		nextMonthButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.common.next"), button -> switchMonth(1))
			.dimensions(0, 0, 20, 18)
			.build());

		saveButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.reward_editor.button.save"), button -> saveChanges())
			.dimensions(0, 0, 150, 20)
			.build());
		resetButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.reward_editor.button.reset"), button -> resetDefaults())
			.dimensions(0, 0, 150, 20)
			.build());
		backButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.common.back"), button -> close())
			.dimensions(0, 0, 80, 20)
			.build());
	}

	private void drawTableHeader(DrawContext context) {
		int y = getRowsTop() - 12;
		context.drawText(textRenderer, Text.translatable("gui.reward_editor.header.day"), rowDayX, y, UiTheme.TEXT_MUTED, false);
		context.drawText(textRenderer, Text.translatable("gui.reward_editor.header.xp"), rowXpX + 1, y, UiTheme.TEXT_MUTED, false);
		context.drawText(textRenderer, Text.translatable("gui.reward_editor.header.card"), rowCardX + 1, y, UiTheme.TEXT_MUTED, false);
		context.drawText(textRenderer, Text.translatable("gui.reward_editor.header.items"), rowIconX + 22, y, UiTheme.TEXT_MUTED, false);
	}

	private void drawRowCards(DrawContext context, int mouseX, int mouseY) {
		List<Text> hoverTooltip = null;
		for (int index = 0; index < rows.size(); index++) {
			RowWidgets row = rows.get(index);
			if (!row.visible) {
				continue;
			}

			RewardEntry reward = getDraftByDay(row.day);
			int rowTop = row.rowTop;
			int rowColor = (index % 2 == 0) ? ROW_A : ROW_B;
			UiRender.drawRoundedRect(context, panelLeft + 12, rowTop - 1, panelLeft + panelWidth - 12, rowTop + ROW_HEIGHT - 2, 4, rowColor);

			context.drawText(textRenderer, Text.translatable("text.signin.day_label", CalendarDayUtil.monthDayLabel(displayYear, row.day)), rowDayX, rowTop + 16, UiTheme.TEXT_PRIMARY, false);

			RewardRowVisual visual = getRewardRowVisual(reward);
			ItemStack icon = visual.icon();

			int iconX = rowIconX;
			int iconY = rowTop + 12;
			context.drawItem(icon, iconX, iconY);
			context.drawItemInSlot(textRenderer, icon, iconX, iconY);

			context.drawText(
				textRenderer,
				Text.literal(UiRender.ellipsize(textRenderer, visual.summary(), rowSummaryWidth)),
				rowSummaryX,
				rowTop + 16,
				UiTheme.TEXT_PRIMARY,
				false
			);

			boolean inRewardIcon = mouseX >= iconX && mouseX <= iconX + 16 && mouseY >= iconY && mouseY <= iconY + 16;
			if (inRewardIcon) {
				hoverTooltip = buildRewardTooltipLines(row.day, reward, visual.itemLines(), Math.max(220, panelWidth - 120));
			}
		}

		if (hoverTooltip != null && !hoverTooltip.isEmpty()) {
			context.drawTooltip(textRenderer, hoverTooltip, mouseX, mouseY);
		}
	}

	private RewardRowVisual getRewardRowVisual(RewardEntry reward) {
		RewardRowVisual cached = rewardVisualCache.get(reward.day());
		if (cached != null && cached.source().equals(reward)) {
			return cached;
		}

		RewardItemEntry first = reward.items().isEmpty() ? null : reward.items().get(0);
		ItemStack icon = RewardDisplayUtil.resolveItemStack(first);
		if (icon.getItem() == Items.BARRIER && reward.items().isEmpty()) {
			icon = new ItemStack(Items.BARRIER);
		}

		String summary = Text.translatable("gui.reward_editor.summary", reward.items().size(), RewardDisplayUtil.localizedItemSummary(reward.items(), 2)).getString();
		List<String> itemLines = RewardDisplayUtil.localizedItemLines(reward.items());
		RewardRowVisual visual = new RewardRowVisual(reward, icon, summary, itemLines);
		rewardVisualCache.put(reward.day(), visual);
		return visual;
	}

	private List<Text> buildRewardTooltipLines(int day, RewardEntry reward, List<String> itemLines, int maxWidth) {
		List<Text> lines = new ArrayList<>();
		lines.add(Text.translatable("gui.reward_editor.tooltip_head", CalendarDayUtil.monthDayLabel(displayYear, day), reward.xp(), reward.makeupCardReward()));
		for (int i = 0; i < itemLines.size(); i++) {
			lines.addAll(UiRender.wrapTooltipLines(
				textRenderer,
				Text.translatable("text.signin.indexed_line", i + 1, itemLines.get(i)).getString(),
				maxWidth
			));
		}
		return lines;
	}

	private void switchMonth(int delta) {
		int nextMonth = Math.max(1, Math.min(12, displayMonth + delta));
		if (nextMonth == displayMonth) {
			return;
		}
		captureDraftFromWidgets();
		pendingFocusDay = -1;
		rebuildEditor(nextMonth, 0, statusText, statusColor);
	}

	private void openItemsEditor(int day) {
		captureDraftFromWidgets();
		RewardEntry reward = getDraftByDay(day);
		if (client != null) {
			client.setScreen(new RewardItemsEditorScreen(this, day, reward.items()));
		}
	}

	private void appendHeldItem(int day) {
		if (client == null || client.player == null) {
			return;
		}

		ItemStack held = client.player.getMainHandStack();
		if (held.isEmpty() || held.getItem() == Items.AIR) {
			setStatus(Text.translatable("gui.reward_editor.status.no_item_in_hand").getString(), 0xFFFF7B7B);
			return;
		}

		captureDraftFromWidgets();
		for (int i = 0; i < draftRewards.size(); i++) {
			RewardEntry reward = draftRewards.get(i);
			if (reward.day() != day) {
				continue;
			}

			Identifier id = Registries.ITEM.getId(held.getItem());
			int count = Math.max(1, held.getCount());
			String data = held.hasNbt() && held.getNbt() != null ? held.getNbt().toString() : "";

			List<RewardItemEntry> items = new ArrayList<>(reward.items());
			items.add(new RewardItemEntry(id.toString(), count, data));
			draftRewards.set(i, new RewardEntry(day, reward.xp(), items, reward.makeupCardReward()));
			rewardVisualCache.remove(day);
			setStatus(Text.translatable("gui.reward_editor.status.append_held_ok", day).getString(), 0xFF9CF2D7);
			break;
		}
		rebuildEditor(displayMonth, rowScroll, statusText, statusColor);
	}

	private void saveChanges() {
		for (RowWidgets row : rows) {
			int xp = parseNonNegativeInt(row.xpField.getText(), Text.translatable("gui.reward_editor.header.xp").getString());
			if (xp < 0) {
				return;
			}
			int cardCount = parseNonNegativeInt(row.cardField.getText(), Text.translatable("gui.reward_editor.header.card").getString());
			if (cardCount < 0) {
				return;
			}
			updateDraftValue(row.day, xp, cardCount);
		}

		draftRewards.sort(Comparator.comparingInt(RewardEntry::day));
		SigninClientActions.saveRewards(draftRewards);
		setStatus(Text.translatable("gui.reward_editor.status.save_sent").getString(), 0xFF9CF2D7);
	}

	private void resetDefaults() {
		draftRewards.clear();
		draftRewards.addAll(SigninConfigState.defaultRewardsForYear(displayYear));
		setStatus(Text.translatable("gui.reward_editor.status.reset_done").getString(), 0xFF9EC9FF);
		rebuildEditor(displayMonth, 0, statusText, statusColor);
	}

	private void rebuildEditor(int targetMonth, int targetScroll, Text status, int color) {
		if (client != null) {
			client.setScreen(new RewardEditorScreen(
				parent,
				new ArrayList<>(draftRewards),
				targetMonth,
				targetScroll,
				status,
				color
			));
		}
	}

	private void captureDraftFromWidgets() {
		for (RowWidgets row : rows) {
			int xp = parsePositiveOrZero(row.xpField.getText());
			int card = parsePositiveOrZero(row.cardField.getText());
			updateDraftValue(row.day, xp, card);
		}
		draftRewards.sort(Comparator.comparingInt(RewardEntry::day));
	}

	private void updateDraftValue(int day, int xp, int card) {
		for (int i = 0; i < draftRewards.size(); i++) {
			RewardEntry source = draftRewards.get(i);
			if (source.day() != day) {
				continue;
			}
			RewardEntry updated = new RewardEntry(day, Math.max(0, xp), source.items(), Math.max(0, card));
			draftRewards.set(i, updated);
			rewardVisualCache.remove(day);
			return;
		}
		draftRewards.add(new RewardEntry(day, Math.max(0, xp), List.of(), Math.max(0, card)));
	}

	private void loadDraftIfNeeded(List<RewardEntry> rewardsFromServer, int daysInYear, int year) {
		if (draftRewards.isEmpty()) {
			draftRewards.addAll(rewardsFromServer.stream().sorted(Comparator.comparingInt(RewardEntry::day)).toList());
		}
		ensureDraftShape(daysInYear, year);
	}

	private void ensureDraftShape(int daysInYear, int year) {
		List<RewardEntry> defaults = SigninConfigState.defaultRewardsForYear(year);
		Map<Integer, RewardEntry> map = new HashMap<>(daysInYear);
		for (RewardEntry reward : defaults) {
			map.put(reward.day(), reward);
		}
		for (RewardEntry reward : draftRewards) {
			if (reward == null) {
				continue;
			}
			RewardEntry fixed = reward.normalized();
			if (fixed.day() < 1 || fixed.day() > daysInYear) {
				continue;
			}
			map.put(fixed.day(), new RewardEntry(fixed.day(), fixed.xp(), fixed.items(), fixed.makeupCardReward()));
		}

		draftRewards.clear();
		for (int day = 1; day <= daysInYear; day++) {
			draftRewards.add(map.get(day));
		}
		draftRewards.sort(Comparator.comparingInt(RewardEntry::day));
	}

	private void refreshMonthBounds() {
		CalendarDayUtil.MonthRange monthRange = CalendarDayUtil.monthRange(displayYear, displayMonth);
		monthStartDay = monthRange.startDay();
		monthEndDay = monthRange.endDay();
	}

	private RewardEntry getDraftByDay(int day) {
		for (RewardEntry reward : draftRewards) {
			if (reward.day() == day) {
				return reward;
			}
		}
		return new RewardEntry(day, 0, List.of(), 0);
	}

	private int getVisibleRowCount() {
		int availableHeight = getRowsBottom() - getRowsTop();
		return Math.max(1, availableHeight / ROW_HEIGHT);
	}

	private void ensureLayout(boolean force) {
		boolean resized = width != lastScreenWidth || height != lastScreenHeight;
		if (!force && !resized && !layoutDirty) {
			return;
		}

		if (resized || force) {
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
		}

		updateRowWidgetLayout();
		layoutDirty = false;
	}

	private void updateRowWidgetLayout() {
		if (saveButton == null || resetButton == null || backButton == null || prevMonthButton == null || nextMonthButton == null) {
			return;
		}

		int rowsTop = getRowsTop();
		int visibleRows = getVisibleRowCount();
		int maxScroll = Math.max(0, rows.size() - visibleRows);
		if (rowScroll > maxScroll) {
			rowScroll = maxScroll;
		}
		if (pendingFocusDay >= monthStartDay && pendingFocusDay <= monthEndDay) {
			int targetIndex = pendingFocusDay - monthStartDay;
			rowScroll = Math.max(0, Math.min(maxScroll, targetIndex - visibleRows / 2));
			pendingFocusDay = -1;
		}

		int rowLeft = panelLeft + 12;
		int rowRight = panelLeft + panelWidth - 12;

		rowFieldWidth = panelWidth < 760 ? 56 : 68;
		rowManageWidth = panelWidth < 760 ? 96 : 112;
		rowHeldWidth = panelWidth < 760 ? 66 : 78;
		int buttonGap = 6;

		rowHeldX = rowRight - 8 - rowHeldWidth;
		rowManageX = rowHeldX - buttonGap - rowManageWidth;
		rowDayX = rowLeft + 8;
		rowXpX = rowDayX + 42;
		rowCardX = rowXpX + rowFieldWidth + 10;
		rowIconX = rowCardX + rowFieldWidth + 14;
		rowSummaryX = rowIconX + 22;
		rowSummaryWidth = Math.max(36, rowManageX - 10 - rowSummaryX);

		for (int i = 0; i < rows.size(); i++) {
			RowWidgets row = rows.get(i);
			boolean visible = i >= rowScroll && i < rowScroll + visibleRows;
			int rowTop = rowsTop + (i - rowScroll) * ROW_HEIGHT;
			row.visible = visible;
			row.rowTop = rowTop;

			if (visible) {
				int controlY = rowTop + 10;
				int fieldY = rowTop + 11;

				row.xpField.setPosition(rowXpX, fieldY);
				row.xpField.setWidth(rowFieldWidth);
				row.cardField.setPosition(rowCardX, fieldY);
				row.cardField.setWidth(rowFieldWidth);

				row.manageItemsButton.setPosition(rowManageX, controlY);
				row.manageItemsButton.setWidth(rowManageWidth);
				row.heldAppendButton.setPosition(rowHeldX, controlY);
				row.heldAppendButton.setWidth(rowHeldWidth);
			}

			row.xpField.visible = visible;
			row.cardField.visible = visible;
			row.manageItemsButton.visible = visible;
			row.heldAppendButton.visible = visible;
			row.xpField.setEditable(visible);
			row.cardField.setEditable(visible);
			row.manageItemsButton.active = visible;
			row.heldAppendButton.active = visible;
		}

		prevMonthButton.setPosition(panelLeft + panelWidth - 108, panelTop + HEADER_HEIGHT + 1);
		nextMonthButton.setPosition(panelLeft + panelWidth - 84, panelTop + HEADER_HEIGHT + 1);
		prevMonthButton.active = displayMonth > 1;
		nextMonthButton.active = displayMonth < 12;

		saveButton.setPosition(panelLeft + panelWidth - 170, panelTop + panelHeight - 28);
		resetButton.setPosition(panelLeft + panelWidth - 332, panelTop + panelHeight - 28);
		backButton.setPosition(panelLeft + 16, panelTop + panelHeight - 28);
	}

	private int getRowsTop() {
		int top = panelTop + HEADER_HEIGHT + 28;
		if (hasStatusText()) {
			top += STATUS_HEIGHT + 8;
		}
		return top;
	}

	private int getRowsBottom() {
		return panelTop + panelHeight - 40;
	}

	private boolean hasStatusText() {
		return statusText != null && !statusText.getString().isBlank();
	}

	private int parseNonNegativeInt(String text, String label) {
		try {
			int value = Integer.parseInt(text.trim());
			if (value < 0) {
				setStatus(Text.translatable("gui.common.error.non_negative", label).getString(), 0xFFFF7B7B);
				return -1;
			}
			return value;
		} catch (NumberFormatException exception) {
			setStatus(Text.translatable("gui.common.error.integer_required", label).getString(), 0xFFFF7B7B);
			return -1;
		}
	}

	private int parsePositiveOrZero(String text) {
		try {
			return Math.max(0, Integer.parseInt(text.trim()));
		} catch (NumberFormatException exception) {
			return 0;
		}
	}

	private void setStatus(String text, int color) {
		statusText = Text.literal(text);
		statusColor = color;
		layoutDirty = true;
	}

	private void drawStatusChip(DrawContext context) {
		if (statusText.getString().isBlank()) {
			return;
		}
		int chipX = panelLeft + 14;
		int chipY = panelTop + HEADER_HEIGHT + 16;
		int chipW = panelWidth - 28;
		int chipH = STATUS_HEIGHT;

		UiRender.drawRoundedRect(context, chipX, chipY, chipX + chipW, chipY + chipH, 5, UiTheme.STATUS_CHIP);
		context.drawText(
			textRenderer,
			Text.literal(UiRender.ellipsize(textRenderer, statusText.getString(), chipW - 10)),
			chipX + 5,
			chipY + 5,
			0xFF000000 | (statusColor & 0x00FFFFFF),
			false
		);
	}

	private static final class RowWidgets {
		private final int day;
		private final TextFieldWidget xpField;
		private final TextFieldWidget cardField;
		private final ButtonWidget manageItemsButton;
		private final ButtonWidget heldAppendButton;

		private boolean visible;
		private int rowTop;

		private RowWidgets(
			int day,
			TextFieldWidget xpField,
			TextFieldWidget cardField,
			ButtonWidget manageItemsButton,
			ButtonWidget heldAppendButton
		) {
			this.day = day;
			this.xpField = xpField;
			this.cardField = cardField;
			this.manageItemsButton = manageItemsButton;
			this.heldAppendButton = heldAppendButton;
		}
	}

	private record RewardRowVisual(RewardEntry source, ItemStack icon, String summary, List<String> itemLines) {
	}
}
