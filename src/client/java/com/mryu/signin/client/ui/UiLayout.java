package com.mryu.signin.client.ui;

public final class UiLayout {
	private UiLayout() {
	}

	public static PanelBounds centeredPanel(
		int screenWidth,
		int screenHeight,
		int minWidth,
		int maxWidth,
		int minHeight,
		int maxHeight,
		int margin
	) {
		int targetWidth = screenWidth - margin;
		if (targetWidth < minWidth) {
			targetWidth = Math.max(220, screenWidth - 6);
		}
		int panelWidth = Math.min(maxWidth, targetWidth);

		int targetHeight = screenHeight - margin;
		if (targetHeight < minHeight) {
			targetHeight = Math.max(180, screenHeight - 6);
		}
		int panelHeight = Math.min(maxHeight, targetHeight);

		return new PanelBounds(
			(screenWidth - panelWidth) / 2,
			(screenHeight - panelHeight) / 2,
			panelWidth,
			panelHeight
		);
	}

	public record PanelBounds(int left, int top, int width, int height) {
		public int right() {
			return left + width;
		}

		public int bottom() {
			return top + height;
		}
	}
}
