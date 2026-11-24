package com.juiceclient;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * On a key press, uses the chosen kind of item from the hotbar without having to select it: throws an ender pearl,
 * drinks or throws a potion, or eats an enchanted golden apple. Then the slot from before is back.
 */
public final class QuickUse {
	private QuickUse() {
	}

	/** The leftmost hotbar slot holding the item, or -1. */
	public static int find(Inventory inventory, QuickUseAction action) {
		for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
			if (action.matches(inventory.getItem(slot))) {
				return slot;
			}
		}

		return -1;
	}

	public static void press(Minecraft client) {
		LocalPlayer player = client.player;
		JuiceClientConfig config = JuiceClient.CONFIG;

		if (player == null || client.gameMode == null || client.gui.screen() != null || player.isSpectator()) {
			return;
		}

		if (!config.enabled) {
			player.sendOverlayMessage(Component.translatable("message.juiceclient.quickUse.modOff"));
			return;
		}

		// Already busy with an item: being eaten by auto eat, a potion from an earlier press, or a bow drawn.
		if (HeldUse.isActive() || player.isUsingItem()) {
			return;
		}

		QuickUseAction action = config.quickUseAction;
		int slot = find(player.getInventory(), action);

		if (slot < 0) {
			player.sendOverlayMessage(Component.translatable("message.juiceclient.quickUse.missing", action.label()).withStyle(ChatFormatting.RED));
			return;
		}

		ItemStack stack = player.getInventory().getItem(slot);

		if (player.getCooldowns().isOnCooldown(stack)) {
			player.sendOverlayMessage(Component.translatable("message.juiceclient.quickUse.cooldown", action.label()).withStyle(ChatFormatting.RED));
			return;
		}

		HeldUse.use(client, slot);
	}
}
