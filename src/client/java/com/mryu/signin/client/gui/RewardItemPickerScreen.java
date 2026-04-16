package com.mryu.signin.client.gui;

import com.mryu.signin.client.ui.UiLayout;
import com.mryu.signin.client.ui.UiRender;
import com.mryu.signin.client.ui.UiTheme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class RewardItemPickerScreen extends Screen {
	private static final int MIN_PANEL_WIDTH = 360;
	private static final int MAX_PANEL_WIDTH = 760;
	private static final int MIN_PANEL_HEIGHT = 240;
	private static final int MAX_PANEL_HEIGHT = 430;
	private static final int PANEL_MARGIN = 20;

	private static final int CELL_SIZE = 20;
	private static final int CELL_GAP = 2;
	private static final int HEADER_HEIGHT = 28;

	private static List<CatalogItem> catalogCache;

	private final Screen parent;
	private final Consumer<String> onSelect;
	private final String pickerTitle;
	private final List<CatalogItem> filteredItems = new ArrayList<>();

	private int panelLeft;
	private int panelTop;
	private int panelWidth;
	private int panelHeight;

	private int gridX;
	private int gridY;
	private int gridWidth;
	private int gridHeight;
	private int columns;
	private int visibleRows;
	private int scrollRows;

	private int lastScreenWidth = -1;
	private int lastScreenHeight = -1;
	private boolean layoutDirty = true;

	private TextFieldWidget searchField;
	private ButtonWidget clearButton;
	private ButtonWidget backButton;

	public RewardItemPickerScreen(Screen parent, String pickerTitle, Consumer<String> onSelect) {
		super(Text.translatable("gui.item_picker.title"));
		this.parent = parent;
		this.pickerTitle = pickerTitle == null || pickerTitle.isBlank()
			? Text.translatable("gui.item_picker.title").getString()
			: pickerTitle;
		this.onSelect = onSelect;
	}

	@Override
	protected void init() {
		ensureCatalogLoaded();

		searchField = addDrawableChild(new TextFieldWidget(textRenderer, 0, 0, 260, 16, Text.translatable("gui.item_picker.search_placeholder")));
		searchField.setMaxLength(120);
		searchField.setChangedListener(value -> {
			applyFilter(value);
			scrollRows = 0;
			layoutDirty = true;
		});
		setFocused(searchField);

		clearButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.item_picker.button.clear"), button -> selectAndBack(""))
			.dimensions(0, 0, 46, 18)
			.build());

		backButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.common.back"), button -> close())
			.dimensions(0, 0, 46, 18)
			.build());

		applyFilter("");
		ensureLayout(true);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		ensureLayout(false);

		UiRender.drawSoftBlurBackdrop(context, width, height);
		UiRender.drawPanelShadow(context, panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, 8);
		UiRender.drawRoundedRect(context, panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, 8, UiTheme.PANEL_BG);
		UiRender.drawRoundedRect(context, panelLeft, panelTop, panelLeft + panelWidth, panelTop + HEADER_HEIGHT, 8, UiTheme.PANEL_HEADER);
		UiRender.drawRoundedRect(
			context,
			panelLeft + 10,
			panelTop + 56,
			panelLeft + panelWidth - 10,
			panelTop + panelHeight - 24,
			6,
			UiTheme.PANEL_CONTENT
		);

		context.drawCenteredTextWithShadow(
			textRenderer,
			Text.literal(pickerTitle).formatted(Formatting.BOLD),
			width / 2,
			panelTop + 9,
			0xFF5A412C
		);
		context.drawTextWithShadow(textRenderer, Text.translatable("gui.item_picker.search_label"), panelLeft + 18, panelTop + 34, UiTheme.TEXT_MUTED);
		context.drawTextWithShadow(textRenderer, Text.translatable("gui.item_picker.hint"), panelLeft + 18, panelTop + panelHeight - 18, UiTheme.TEXT_NOTICE);
		context.drawTextWithShadow(textRenderer, Text.translatable("gui.item_picker.total", filteredItems.size()), panelLeft + panelWidth - 74, panelTop + panelHeight - 18, UiTheme.TEXT_NOTICE);

		super.render(context, mouseX, mouseY, delta);

		int maxScrollRows = Math.max(0, getTotalRows() - visibleRows);
		if (scrollRows > maxScrollRows) {
			scrollRows = maxScrollRows;
		}

		int hoveredIndex = -1;
		for (int row = 0; row < visibleRows; row++) {
			for (int col = 0; col < columns; col++) {
				int index = (scrollRows + row) * columns + col;
				if (index >= filteredItems.size()) {
					continue;
				}

				int x = gridX + col * (CELL_SIZE + CELL_GAP);
				int y = gridY + row * (CELL_SIZE + CELL_GAP);
				int color = 0x70FFE7CA;
				if (isMouseInCell(mouseX, mouseY, x, y)) {
					color = 0xBCFFC692;
					hoveredIndex = index;
				}

				UiRender.drawRoundedRect(context, x, y, x + CELL_SIZE, y + CELL_SIZE, 3, color);
				context.drawItem(new ItemStack(filteredItems.get(index).item()), x + 2, y + 2);
			}
		}

		if (hoveredIndex >= 0) {
			CatalogItem hovered = filteredItems.get(hoveredIndex);
			String tooltip = Text.translatable("gui.item_picker.tooltip_item", hovered.localizedName(), hovered.id()).getString();
			context.drawTooltip(textRenderer, Text.literal(tooltip), mouseX, mouseY);
		}

		if (getTotalRows() > visibleRows) {
			context.drawTextWithShadow(
				textRenderer,
				Text.translatable("gui.common.scroll_page", scrollRows + 1, Math.max(1, getTotalRows() - visibleRows + 1)),
				panelLeft + panelWidth - 130,
				panelTop + panelHeight - 18,
				UiTheme.TEXT_NOTICE
			);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && isInGrid(mouseX, mouseY)) {
			int col = (int) ((mouseX - gridX) / (CELL_SIZE + CELL_GAP));
			int row = (int) ((mouseY - gridY) / (CELL_SIZE + CELL_GAP));
			int index = (scrollRows + row) * columns + col;
			if (index >= 0 && index < filteredItems.size()) {
				selectAndBack(filteredItems.get(index).id());
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (!isInGrid(mouseX, mouseY)) {
			return super.mouseScrolled(mouseX, mouseY, amount);
		}
		int maxScrollRows = Math.max(0, getTotalRows() - visibleRows);
		if (maxScrollRows <= 0) {
			return super.mouseScrolled(mouseX, mouseY, amount);
		}
		scrollRows -= (int) Math.signum(amount);
		if (scrollRows < 0) {
			scrollRows = 0;
		}
		if (scrollRows > maxScrollRows) {
			scrollRows = maxScrollRows;
		}
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

	private void selectAndBack(String itemId) {
		if (onSelect != null) {
			onSelect.accept(itemId == null ? "" : itemId);
		}
		if (client != null) {
			client.setScreen(parent);
		}
	}

	private void ensureCatalogLoaded() {
		if (catalogCache != null) {
			return;
		}

		List<CatalogItem> cache = new ArrayList<>();
		for (Item item : Registries.ITEM) {
			if (item == Items.AIR) {
				continue;
			}
			Identifier id = Registries.ITEM.getId(item);
			String itemId = id.toString();
			String localizedName = new ItemStack(item).getName().getString();
			cache.add(new CatalogItem(item, itemId, itemId.toLowerCase(Locale.ROOT), localizedName, localizedName.toLowerCase(Locale.ROOT)));
		}
		cache.sort(Comparator.comparing(CatalogItem::id));
		catalogCache = List.copyOf(cache);
	}

	private void applyFilter(String query) {
		String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		filteredItems.clear();
		if (normalized.isEmpty()) {
			filteredItems.addAll(catalogCache);
			return;
		}

		for (CatalogItem item : catalogCache) {
			if (item.idLower().contains(normalized) || item.nameLower().contains(normalized)) {
				filteredItems.add(item);
			}
		}
	}

	private int getTotalRows() {
		if (columns <= 0) {
			return 0;
		}
		return (filteredItems.size() + columns - 1) / columns;
	}

	private boolean isInGrid(double mouseX, double mouseY) {
		return mouseX >= gridX && mouseX <= gridX + gridWidth
			&& mouseY >= gridY && mouseY <= gridY + gridHeight;
	}

	private boolean isMouseInCell(int mouseX, int mouseY, int x, int y) {
		return mouseX >= x && mouseX <= x + CELL_SIZE && mouseY >= y && mouseY <= y + CELL_SIZE;
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

		searchField.setPosition(panelLeft + 58, panelTop + 30);
		searchField.setWidth(panelWidth - 180);
		clearButton.setPosition(panelLeft + panelWidth - 114, panelTop + 29);
		backButton.setPosition(panelLeft + panelWidth - 62, panelTop + 29);

		gridX = panelLeft + 14;
		gridY = panelTop + 56;
		gridWidth = panelWidth - 28;
		gridHeight = panelHeight - 82;
		columns = Math.max(4, gridWidth / (CELL_SIZE + CELL_GAP));
		visibleRows = Math.max(1, gridHeight / (CELL_SIZE + CELL_GAP));

		layoutDirty = false;
	}

	private record CatalogItem(
		Item item,
		String id,
		String idLower,
		String localizedName,
		String nameLower
	) {
	}
}
