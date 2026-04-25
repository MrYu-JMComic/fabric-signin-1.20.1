package com.mryu.signin.client.gui;

import com.mryu.signin.client.ui.RewardDisplayUtil;
import com.mryu.signin.client.ui.UiLayout;
import com.mryu.signin.client.ui.UiRender;
import com.mryu.signin.client.ui.UiTheme;
import com.mryu.signin.config.SigninLimits;
import com.mryu.signin.data.RewardItemEntry;
import com.mryu.signin.util.RewardItemResolver;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RewardItemsEditorScreen extends Screen {
	private static final int MIN_PANEL_WIDTH = 520;
	private static final int MAX_PANEL_WIDTH = 1080;
	private static final int MIN_PANEL_HEIGHT = 280;
	private static final int MAX_PANEL_HEIGHT = 620;
	private static final int PANEL_MARGIN = 18;

	private static final int HEADER_HEIGHT = 30;
	private static final int STATUS_HEIGHT = 18;
	private static final int ROW_HEIGHT = 58;
	private static final int MAX_DATA_LEN = SigninLimits.MAX_ITEM_DATA_LENGTH;
	private static final int MAX_ITEM_TYPES = SigninLimits.MAX_ITEM_TYPES_PER_REWARD;

	private final RewardEditorScreen parent;
	private final int day;
	private final List<RewardItemEntry> draftItems = new ArrayList<>();
	private final List<RowWidgets> rows = new ArrayList<>();
	private final Map<Integer, RowPreviewCache> rowPreviewCache = new HashMap<>();

	private int panelLeft;
	private int panelTop;
	private int panelWidth;
	private int panelHeight;
	private int rowScroll;
	private int rowIdX;
	private int rowCountX;
	private int rowDataX;
	private int lastScreenWidth = -1;
	private int lastScreenHeight = -1;
	private boolean layoutDirty = true;

	private ButtonWidget addButton;
	private ButtonWidget saveButton;
	private ButtonWidget backButton;
	private Text statusText = Text.empty();
	private int statusColor = UiTheme.TEXT_MUTED;

	public RewardItemsEditorScreen(RewardEditorScreen parent, int day, List<RewardItemEntry> initialItems) {
		this(parent, day, initialItems, 0, Text.empty(), UiTheme.TEXT_MUTED);
	}

	public RewardItemsEditorScreen(
		RewardEditorScreen parent,
		int day,
		List<RewardItemEntry> initialItems,
		int initialScroll,
		Text statusText,
		int statusColor
	) {
		super(Text.translatable("gui.reward_items.title", day));
		this.parent = parent;
		this.day = day;
		this.draftItems.addAll(initialItems == null ? List.of() : initialItems);
		this.rowScroll = Math.max(0, initialScroll);
		this.statusText = statusText == null ? Text.empty() : statusText;
		this.statusColor = statusColor;
	}

	@Override
	protected void init() {
		if (draftItems.isEmpty()) {
			draftItems.add(new RewardItemEntry("", 1, ""));
		}
		buildRows();
		buildButtons();
		ensureLayout(true);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		ensureLayout(false);

		UiRender.drawSoftBlurBackdrop(context, width, height);
		UiRender.drawPanelShadow(context, panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, 8);
		UiRender.drawRoundedRect(context, panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, 8, UiTheme.PANEL_BG);
		UiRender.drawRoundedRect(context, panelLeft, panelTop, panelLeft + panelWidth, panelTop + HEADER_HEIGHT, 8, UiTheme.PANEL_HEADER);
		int contentTop = getRowsTop() - 16;
		UiRender.drawRoundedRect(
			context,
			panelLeft + 10,
			contentTop,
			panelLeft + panelWidth - 10,
			panelTop + panelHeight - 42,
			6,
			UiTheme.PANEL_CONTENT
		);

		context.drawCenteredTextWithShadow(textRenderer, title.copy().formatted(Formatting.BOLD), width / 2, panelTop + 9, 0xFF5A412C);
		context.drawTextWithShadow(textRenderer, Text.translatable("gui.reward_items.subtitle"), panelLeft + 14, panelTop + HEADER_HEIGHT + 2, UiTheme.TEXT_MUTED);
		context.drawTextWithShadow(textRenderer, Text.translatable("gui.reward_items.types_count", rows.size(), MAX_ITEM_TYPES), panelLeft + panelWidth - 110, panelTop + HEADER_HEIGHT + 2, UiTheme.TEXT_NOTICE);
		if (rows.size() > getVisibleRows()) {
			context.drawTextWithShadow(
				textRenderer,
				Text.translatable("gui.common.scroll_page", rowScroll + 1, Math.max(1, rows.size() - getVisibleRows() + 1)),
				panelLeft + panelWidth - 110,
				panelTop + HEADER_HEIGHT + 14,
				UiTheme.TEXT_NOTICE
			);
		}
		drawStatusChip(context);
		drawHeader(context);
		drawRowCards(context, mouseX, mouseY);

		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		int contentLeft = panelLeft + 10;
		int contentRight = panelLeft + panelWidth - 10;
		int contentTop = getRowsTop() - 16;
		int contentBottom = panelTop + panelHeight - 42;
		if (mouseX < contentLeft || mouseX > contentRight || mouseY < contentTop || mouseY > contentBottom) {
			return super.mouseScrolled(mouseX, mouseY, amount);
		}
		int maxScroll = Math.max(0, rows.size() - getVisibleRows());
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

	private void buildRows() {
		rows.clear();
		rowPreviewCache.clear();
		for (int i = 0; i < draftItems.size(); i++) {
			RewardItemEntry item = draftItems.get(i);
			TextFieldWidget itemIdField = addDrawableChild(new TextFieldWidget(textRenderer, 0, 0, 150, 16, Text.translatable("gui.reward_items.header.item_id")));
			itemIdField.setMaxLength(SigninLimits.MAX_ITEM_ID_LENGTH);
			itemIdField.setText(item.itemId());

			TextFieldWidget countField = addDrawableChild(new TextFieldWidget(textRenderer, 0, 0, 48, 16, Text.translatable("gui.reward_items.header.count")));
			countField.setMaxLength(5);
			countField.setText(String.valueOf(Math.max(1, item.itemCount())));

			TextFieldWidget dataField = addDrawableChild(new TextFieldWidget(textRenderer, 0, 0, 240, 16, Text.translatable("gui.reward_items.header.nbt_data")));
			dataField.setMaxLength(MAX_DATA_LEN);
			dataField.setText(item.itemData());

			int index = i;
			ButtonWidget pickButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.reward_items.button.pick"), button -> openPicker(index))
				.dimensions(0, 0, 44, 20)
				.build());
			ButtonWidget heldButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.reward_items.button.held"), button -> applyHeld(index))
				.dimensions(0, 0, 44, 20)
				.build());
			ButtonWidget delButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.reward_items.button.delete"), button -> deleteRow(index))
				.dimensions(0, 0, 44, 20)
				.build());

			rows.add(new RowWidgets(index, itemIdField, countField, dataField, pickButton, heldButton, delButton));
		}
	}

	private void buildButtons() {
		addButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.reward_items.button.add"), button -> addRow())
			.dimensions(0, 0, 86, 20)
			.build());
		saveButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.reward_items.button.apply", day), button -> saveAndBack())
			.dimensions(0, 0, 130, 20)
			.build());
		backButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.common.back"), button -> close())
			.dimensions(0, 0, 70, 20)
			.build());
	}

	private void drawStatusChip(DrawContext context) {
		if (statusText.getString().isBlank()) {
			return;
		}
		int chipX = panelLeft + 12;
		int chipY = panelTop + HEADER_HEIGHT + 16;
		int chipW = panelWidth - 24;
		UiRender.drawRoundedRect(context, chipX, chipY, chipX + chipW, chipY + STATUS_HEIGHT, 5, UiTheme.STATUS_CHIP);
		context.drawText(
			textRenderer,
			Text.literal(UiRender.ellipsize(textRenderer, statusText.getString(), chipW - 10)),
			chipX + 5,
			chipY + 5,
			0xFF000000 | (statusColor & 0x00FFFFFF),
			false
		);
	}

	private void drawHeader(DrawContext context) {
		int y = getRowsTop() - 22;
		context.drawTextWithShadow(textRenderer, Text.translatable("gui.reward_items.header.item_id"), rowIdX + 2, y, UiTheme.TEXT_MUTED);
		context.drawTextWithShadow(textRenderer, Text.translatable("gui.reward_items.header.count"), rowCountX + 2, y, UiTheme.TEXT_MUTED);
		context.drawTextWithShadow(textRenderer, Text.translatable("gui.reward_items.header.nbt_data_optional"), rowDataX + 2, y + 10, UiTheme.TEXT_MUTED);
	}

	private void drawRowCards(DrawContext context, int mouseX, int mouseY) {
		List<Text> hoverTooltip = null;
		for (RowWidgets row : rows) {
			if (!row.visible) {
				continue;
			}
			int rowTop = row.rowTop;
			UiRender.drawRoundedRect(context, panelLeft + 12, rowTop - 1, panelLeft + panelWidth - 12, rowTop + ROW_HEIGHT - 2, 4, 0x72FFE9D2);

			RowPreview previewData = getOrBuildPreview(row);
			RewardItemEntry previewEntry = previewData.entry();
			ItemStack preview = previewData.stack();
			if (preview.getItem() == Items.BARRIER && !previewEntry.itemId().isBlank()) {
				context.drawItem(new ItemStack(Items.BARRIER), panelLeft + 18, rowTop + 18);
			} else {
				context.drawItem(preview, panelLeft + 18, rowTop + 18);
				context.drawItemInSlot(textRenderer, preview, panelLeft + 18, rowTop + 18);
			}

			boolean inRewardIcon = mouseX >= panelLeft + 18 && mouseX <= panelLeft + 34 && mouseY >= rowTop + 18 && mouseY <= rowTop + 34;
			if (inRewardIcon) {
				hoverTooltip = new ArrayList<>();
				hoverTooltip.addAll(UiRender.wrapTooltipLines(textRenderer, previewData.localizedSummary(), Math.max(220, panelWidth - 130)));
				hoverTooltip.addAll(UiRender.wrapTooltipLines(
					textRenderer,
					Text.translatable(
						"gui.reward_items.tooltip.id",
						previewEntry.itemId().isBlank() ? Text.translatable("text.signin.none") : previewEntry.itemId()
					).getString(),
					Math.max(220, panelWidth - 130)
				));
				hoverTooltip.add(Text.literal(previewData.dataHint()));
			}
		}

		if (hoverTooltip != null && !hoverTooltip.isEmpty()) {
			context.drawTooltip(textRenderer, hoverTooltip, mouseX, mouseY);
		}
	}

	private void addRow() {
		captureDraftFromWidgets();
		if (draftItems.size() >= MAX_ITEM_TYPES) {
			setStatus(Text.translatable("gui.reward_items.error.max_types", MAX_ITEM_TYPES).getString(), 0xFFFF7B7B);
			rebuild();
			return;
		}
		draftItems.add(new RewardItemEntry("", 1, ""));
		rebuild();
	}

	private void deleteRow(int index) {
		captureDraftFromWidgets();
		if (index >= 0 && index < draftItems.size()) {
			draftItems.remove(index);
		}
		if (draftItems.isEmpty()) {
			draftItems.add(new RewardItemEntry("", 1, ""));
		}
		rebuild();
	}

	private void openPicker(int index) {
		if (client == null || index < 0 || index >= rows.size()) {
			return;
		}
		RowWidgets row = rows.get(index);
		client.setScreen(new RewardItemPickerScreen(
			this,
			Text.translatable("gui.reward_items.picker_title", day, index + 1).getString(),
			itemId -> row.itemIdField.setText(itemId)
		));
	}

	private void applyHeld(int index) {
		if (client == null || client.player == null || index < 0 || index >= rows.size()) {
			return;
		}
		ItemStack held = client.player.getMainHandStack();
		RowWidgets row = rows.get(index);
		if (held.isEmpty() || held.getItem() == Items.AIR) {
			row.itemIdField.setText("");
			row.countField.setText("1");
			row.dataField.setText("");
			return;
		}

		Identifier id = Registries.ITEM.getId(held.getItem());
		row.itemIdField.setText(id.toString());
		row.countField.setText(String.valueOf(Math.max(1, held.getCount())));
		row.dataField.setText(held.hasNbt() && held.getNbt() != null ? held.getNbt().toString() : "");
	}

	private void saveAndBack() {
		List<RewardItemEntry> result = new ArrayList<>();
		for (RowWidgets row : rows) {
			String itemId = row.itemIdField.getText().trim();
			String countText = row.countField.getText().trim();
			String itemData = row.dataField.getText().trim();

			if (itemId.isEmpty()) {
				continue;
			}

			Identifier itemIdentifier = Identifier.tryParse(itemId);
			if (itemIdentifier == null || !RewardItemResolver.isValidItemId(itemId)) {
				setStatus(Text.translatable("gui.reward_items.error.invalid_item", itemId).getString(), 0xFFFF7B7B);
				return;
			}

			int count;
			try {
				count = Integer.parseInt(countText);
				if (count <= 0) {
					setStatus(Text.translatable("gui.reward_items.error.count_positive").getString(), 0xFFFF7B7B);
					return;
				}
				if (count > SigninLimits.MAX_REWARD_ITEM_COUNT) {
					setStatus(Text.translatable("gui.reward_items.error.count_too_large", SigninLimits.MAX_REWARD_ITEM_COUNT).getString(), 0xFFFF7B7B);
					return;
				}
			} catch (NumberFormatException exception) {
				setStatus(Text.translatable("gui.reward_items.error.count_integer").getString(), 0xFFFF7B7B);
				return;
			}

			if (!itemData.isEmpty() && !RewardDisplayUtil.isItemDataSyntaxValid(itemData)) {
				setStatus(Text.translatable("gui.reward_items.error.nbt_invalid", itemId).getString(), 0xFFFF7B7B);
				return;
			}

			result.add(new RewardItemEntry(itemId, count, itemData));
		}

		parent.applyDayItems(day, result);
	}

	private void captureDraftFromWidgets() {
		if (rows.isEmpty()) {
			return;
		}

		List<RewardItemEntry> snapshot = new ArrayList<>(rows.size());
		for (RowWidgets row : rows) {
			String itemId = row.itemIdField.getText().trim();
			int count = parsePositiveOrOne(row.countField.getText());
			String itemData = row.dataField.getText().trim();
			if (itemId.isEmpty()) {
				snapshot.add(new RewardItemEntry("", 1, ""));
			} else {
				snapshot.add(new RewardItemEntry(itemId, count, itemData));
			}
		}
		draftItems.clear();
		draftItems.addAll(snapshot);
	}

	private int parsePositiveOrOne(String text) {
		try {
			return Math.max(1, Math.min(Integer.parseInt(text.trim()), SigninLimits.MAX_REWARD_ITEM_COUNT));
		} catch (NumberFormatException exception) {
			return 1;
		}
	}

	private void setStatus(String message, int color) {
		statusText = Text.literal(message);
		statusColor = color;
		layoutDirty = true;
	}

	private int getVisibleRows() {
		return Math.max(1, (getRowsBottom() - getRowsTop()) / ROW_HEIGHT);
	}

	private int getRowsTop() {
		int top = panelTop + HEADER_HEIGHT + 52;
		if (hasStatusText()) {
			top += STATUS_HEIGHT + 8;
		}
		return top;
	}

	private int getRowsBottom() {
		return panelTop + panelHeight - 44;
	}

	private boolean hasStatusText() {
		return statusText != null && !statusText.getString().isBlank();
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

		int visibleRows = getVisibleRows();
		int maxScroll = Math.max(0, rows.size() - visibleRows);
		if (rowScroll > maxScroll) {
			rowScroll = maxScroll;
		}

		int rightX = panelLeft + panelWidth - 12;
		int delW = 44;
		int heldW = 44;
		int pickW = 44;
		int delX = rightX - delW;
		int heldX = delX - 4 - heldW;
		int pickX = heldX - 4 - pickW;
		int idX = panelLeft + 42;
		int countW = 52;
		int countX = pickX - 6 - countW;
		int idWidth = Math.max(96, countX - 6 - idX);
		int dataX = idX;
		int dataWidth = Math.max(120, rightX - dataX);

		rowIdX = idX;
		rowCountX = countX;
		rowDataX = dataX;

		for (int i = 0; i < rows.size(); i++) {
			RowWidgets row = rows.get(i);
			boolean visible = i >= rowScroll && i < rowScroll + visibleRows;
			row.visible = visible;
			int rowTop = getRowsTop() + (i - rowScroll) * ROW_HEIGHT;
			row.rowTop = rowTop;

			if (visible) {
				int line1Y = rowTop + 7;
				int line2Y = rowTop + 31;

				row.itemIdField.setPosition(idX, line1Y);
				row.itemIdField.setWidth(idWidth);

				row.countField.setPosition(countX, line1Y);
				row.countField.setWidth(countW);

				row.dataField.setPosition(dataX, line2Y);
				row.dataField.setWidth(dataWidth);

				row.pickButton.setPosition(pickX, line1Y);
				row.heldButton.setPosition(heldX, line1Y);
				row.delButton.setPosition(delX, line1Y);
			}

			row.itemIdField.visible = visible;
			row.countField.visible = visible;
			row.dataField.visible = visible;
			row.pickButton.visible = visible;
			row.heldButton.visible = visible;
			row.delButton.visible = visible;
			row.itemIdField.setEditable(visible);
			row.countField.setEditable(visible);
			row.dataField.setEditable(visible);
			row.pickButton.active = visible;
			row.heldButton.active = visible;
			row.delButton.active = visible;
		}

		addButton.setPosition(panelLeft + 12, panelTop + panelHeight - 28);
		backButton.setPosition(panelLeft + 104, panelTop + panelHeight - 28);
		saveButton.setPosition(panelLeft + panelWidth - 146, panelTop + panelHeight - 28);

		layoutDirty = false;
	}

	private void rebuild() {
		if (client != null) {
			client.setScreen(new RewardItemsEditorScreen(
				parent,
				day,
				new ArrayList<>(draftItems),
				rowScroll,
				statusText,
				statusColor
			));
		}
	}

	private RowPreview getOrBuildPreview(RowWidgets row) {
		String itemId = row.itemIdField.getText().trim();
		int count = parsePositiveOrOne(row.countField.getText());
		String itemData = row.dataField.getText().trim();

		RowPreviewCache cache = rowPreviewCache.get(row.index);
		if (cache != null && cache.matches(itemId, count, itemData)) {
			return cache.preview();
		}

		RewardItemEntry entry = new RewardItemEntry(itemId, count, itemData);
		ItemStack preview = RewardDisplayUtil.resolveItemStack(entry);
		String localizedSummary = RewardDisplayUtil.localizedItemText(entry.itemId(), entry.itemCount(), entry.itemData(), preview);
		String dataHint = entry.itemData().isBlank()
			? Text.translatable("gui.reward_items.data_hint.none").getString()
			: Text.translatable("gui.reward_items.data_hint.has").getString();

		RowPreview rowPreview = new RowPreview(entry, preview, localizedSummary, dataHint);
		rowPreviewCache.put(row.index, new RowPreviewCache(itemId, count, itemData, rowPreview));
		return rowPreview;
	}

	private static final class RowWidgets {
		private final int index;
		private final TextFieldWidget itemIdField;
		private final TextFieldWidget countField;
		private final TextFieldWidget dataField;
		private final ButtonWidget pickButton;
		private final ButtonWidget heldButton;
		private final ButtonWidget delButton;

		private boolean visible;
		private int rowTop;

		private RowWidgets(
			int index,
			TextFieldWidget itemIdField,
			TextFieldWidget countField,
			TextFieldWidget dataField,
			ButtonWidget pickButton,
			ButtonWidget heldButton,
			ButtonWidget delButton
		) {
			this.index = index;
			this.itemIdField = itemIdField;
			this.countField = countField;
			this.dataField = dataField;
			this.pickButton = pickButton;
			this.heldButton = heldButton;
			this.delButton = delButton;
		}
	}

	private record RowPreview(RewardItemEntry entry, ItemStack stack, String localizedSummary, String dataHint) {
	}

	private record RowPreviewCache(String itemId, int count, String itemData, RowPreview preview) {
		private boolean matches(String itemId, int count, String itemData) {
			return this.count == count && this.itemId.equals(itemId) && this.itemData.equals(itemData);
		}
	}
}
