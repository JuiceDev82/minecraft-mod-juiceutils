package com.juiceclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Shows the armor worn in a row of hotbar slots just outside the offhand slot, helmet to boots, each with roughly how
 * much durability it has left in small numbers above it.
 */
public final class ArmorStatus {
	public static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final Identifier HOTBAR_SPRITE = Identifier.withDefaultNamespace("hud/hotbar");
	private static final int HOTBAR_WIDTH = 182;
	private static final int HOTBAR_HEIGHT = 22;
	private static final int CELL = 20;
	/** The left edge and four cells of the hotbar, and then its right edge. */
	private static final int ROW_WIDTH = 1 + SLOTS.length * CELL + 1;
	/** The offhand slot is this wide, and the row sits this far beyond it. */
	private static final int OFFHAND_WIDTH = 29;
	private static final int GAP = 4;
	private static final float TEXT_SCALE = 0.5F;

	private ArmorStatus() {
	}

	/** Whether the armor bar above the hearts is left out. */
	public static boolean replacesArmorBar() {
		return JuiceClient.CONFIG.enabled && JuiceClient.CONFIG.armorStatus;
	}

	public static void render(GuiGraphicsExtractor graphics) {
		Minecraft client = Minecraft.getInstance();
		Player player = client.player;

		if (!replacesArmorBar() || player == null || player.isSpectator()) {
			return;
		}

		boolean anyWorn = false;

		for (EquipmentSlot slot : SLOTS) {
			anyWorn |= !player.getItemBySlot(slot).isEmpty();
		}

		if (!anyWorn) {
			return;
		}

		// Beyond the offhand slot, on whichever side the game puts it.
		int center = graphics.guiWidth() / 2;
		int left = player.getMainArm() == HumanoidArm.LEFT
				? center + 91 + OFFHAND_WIDTH + GAP
				: center - 91 - OFFHAND_WIDTH - GAP - ROW_WIDTH;
		int top = graphics.guiHeight() - HOTBAR_HEIGHT;

		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SPRITE, HOTBAR_WIDTH, HOTBAR_HEIGHT, 0, 0, left, top, ROW_WIDTH - 1, HOTBAR_HEIGHT);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SPRITE, HOTBAR_WIDTH, HOTBAR_HEIGHT, HOTBAR_WIDTH - 1, 0, left + ROW_WIDTH - 1, top, 1, HOTBAR_HEIGHT);

		Font font = client.font;

		for (int i = 0; i < SLOTS.length; i++) {
			ItemStack stack = player.getItemBySlot(SLOTS[i]);

			if (stack.isEmpty()) {
				continue;
			}

			int x = left + 3 + i * CELL;
			int y = top + 3;
			graphics.item(player, stack, x, y, i);
			graphics.itemDecorations(font, stack, x, y);
			int percent = percentLeft(stack);

			if (percent >= 0) {
				String text = percent + "%";
				graphics.pose().pushMatrix();
				graphics.pose().translate(x + 8 - font.width(text) * TEXT_SCALE / 2, top - 5);
				graphics.pose().scale(TEXT_SCALE);
				graphics.text(font, text, 0, 0, 0xFF000000 | stack.getBarColor(), true);
				graphics.pose().popMatrix();
			}
		}
	}

	/** Roughly how much durability an item has left, from 0 to 100, or -1 for items that don't wear out. */
	public static int percentLeft(ItemStack stack) {
		if (!stack.isDamageableItem()) {
			return -1;
		}

		int max = stack.getMaxDamage();
		int left = max - stack.getDamageValue();
		int percent = Math.round(left * 100F / max);
		// Only a new item shows 100, and only a broken one 0.
		return left == max ? 100 : left <= 0 ? 0 : Math.clamp(percent, 1, 99);
	}
}
