package com.mryu.signin.client.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public final class UiRender {
	private UiRender() {
	}

	public static void drawSoftBlurBackdrop(DrawContext context, int width, int height) {
		context.fillGradient(0, 0, width, height, UiTheme.BACKDROP_TOP, UiTheme.BACKDROP_BOTTOM);
		for (int i = 0; i < 6; i++) {
			int alpha = 18 - i * 3;
			if (alpha <= 0) {
				break;
			}
			int inset = i * 2;
			context.fill(inset, inset, width - inset, height - inset, (alpha << 24) | 0x00FFF9F2);
		}
	}

	public static void drawPanelShadow(DrawContext context, int x1, int y1, int x2, int y2, int radius) {
		for (int i = 6; i >= 1; i--) {
			int alpha = 10 + i * 2;
			drawRoundedRect(context, x1 - i, y1 - i, x2 + i, y2 + i, radius + i, (alpha << 24) | UiTheme.SHADOW_BASE);
		}
	}

	public static void drawRoundedRect(DrawContext context, int x1, int y1, int x2, int y2, int radius, int color) {
		if (x2 <= x1 || y2 <= y1) {
			return;
		}
		int r = Math.max(0, Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2)));
		if (r <= 0) {
			context.fill(x1, y1, x2, y2, color);
			return;
		}

		context.fill(x1, y1 + r, x2, y2 - r, color);
		for (int i = 0; i < r; i++) {
			int inset = (int) Math.ceil(Math.sqrt(r * r - (i + 0.5) * (i + 0.5)));
			context.fill(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, color);
			context.fill(x1 + inset, y2 - i - 1, x2 - inset, y2 - i, color);
		}
	}

	public static String ellipsize(TextRenderer textRenderer, String text, int maxWidth) {
		if (textRenderer.getWidth(text) <= maxWidth) {
			return text;
		}
		String suffix = "...";
		int suffixWidth = textRenderer.getWidth(suffix);
		int end = text.length();
		while (end > 0 && textRenderer.getWidth(text.substring(0, end)) + suffixWidth > maxWidth) {
			end--;
		}
		return end <= 0 ? suffix : text.substring(0, end) + suffix;
	}

	public static List<Text> wrapTooltipLines(TextRenderer textRenderer, String text, int maxWidth) {
		if (text == null || text.isBlank()) {
			return List.of(Text.empty());
		}

		List<Text> lines = new ArrayList<>();
		for (String paragraph : text.split("\\R")) {
			String remaining = paragraph;
			if (remaining.isBlank()) {
				lines.add(Text.empty());
				continue;
			}

			while (!remaining.isBlank()) {
				int cut = remaining.length();
				while (cut > 1 && textRenderer.getWidth(remaining.substring(0, cut)) > maxWidth) {
					cut--;
				}

				if (cut <= 0) {
					break;
				}

				String line = remaining.substring(0, cut);
				lines.add(Text.literal(line));

				if (cut >= remaining.length()) {
					break;
				}
				remaining = remaining.substring(cut).stripLeading();
			}
		}
		return lines;
	}
}
