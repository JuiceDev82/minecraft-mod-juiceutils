package com.juiceclient;

import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Previews a hex color: solid on the left, and with its transparency over a checkerboard on the right.
 */
public class ColorSwatchWidget extends AbstractWidget {
	private static final int BORDER_COLOR = 0xFFA0A0A0;
	private static final int INVALID_BORDER_COLOR = 0xFFFF5555;
	private static final int INVALID_BACKGROUND_COLOR = 0xFF000000;
	private static final int CHECKER_LIGHT = 0xFFCCCCCC;
	private static final int CHECKER_DARK = 0xFF777777;
	private static final int CHECKER_SIZE = 5;
	private static final Component INVALID = Component.translatable("options.juiceclient.invalidColor");

	private final Supplier<String> hex;

	public ColorSwatchWidget(int width, int height, Component message, Supplier<String> hex) {
		super(0, 0, width, height, message);
		this.hex = hex;
		this.active = false;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = this.getX();
		int y = this.getY();
		int right = this.getRight();
		int bottom = this.getBottom();
		String value = this.hex.get();

		if (!JuiceClientConfig.isValidColor(value)) {
			graphics.fill(x, y, right, bottom, INVALID_BACKGROUND_COLOR);
			graphics.outline(x, y, this.width, this.height, INVALID_BORDER_COLOR);
			graphics.centeredText(Minecraft.getInstance().font, INVALID, x + this.width / 2, y + (this.height - 8) / 2, INVALID_BORDER_COLOR);
			return;
		}

		int color = Integer.parseUnsignedInt(value, 16);
		int middle = x + this.width / 2;

		graphics.fill(x, y, middle, bottom, color | 0xFF000000);

		for (int cellY = y; cellY < bottom; cellY += CHECKER_SIZE) {
			for (int cellX = middle; cellX < right; cellX += CHECKER_SIZE) {
				boolean light = ((cellX - middle) / CHECKER_SIZE + (cellY - y) / CHECKER_SIZE) % 2 == 0;
				graphics.fill(cellX, cellY, Math.min(cellX + CHECKER_SIZE, right), Math.min(cellY + CHECKER_SIZE, bottom), light ? CHECKER_LIGHT : CHECKER_DARK);
			}
		}

		graphics.fill(middle, y, right, bottom, color);
		graphics.outline(x, y, this.width, this.height, BORDER_COLOR);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, this.getMessage());
	}
}
