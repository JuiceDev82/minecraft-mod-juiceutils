package com.basetracer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;

/**
 * Uses an item from another hotbar slot as if right-clicked, then goes back to the slot that was selected. Items
 * that take time, like food and potions, have the use key held for them until they're done.
 */
public final class HeldUse {
	private static boolean active;
	private static int heldSlot;
	private static int previousSlot;

	private HeldUse() {
	}

	/** Whether an item is being used, for the mixin that holds the use key down meanwhile. */
	public static boolean isActive() {
		return active;
	}

	/**
	 * Switches to a slot and uses its item. Something thrown, like an ender pearl, is gone back from right away;
	 * something eaten or drunk once it's done. Returns whether the item was used.
	 */
	public static boolean use(Minecraft client, int slot) {
		LocalPlayer player = client.player;

		if (active || player == null || client.gameMode == null) {
			return false;
		}

		int previous = player.getInventory().getSelectedSlot();
		player.getInventory().setSelectedSlot(slot);
		// This tells the server of the slot before using it.
		boolean used = client.gameMode.useItem(player, InteractionHand.MAIN_HAND).consumesAction();

		if (player.isUsingItem()) {
			active = true;
			heldSlot = slot;
			previousSlot = previous;
			return true;
		}

		player.getInventory().setSelectedSlot(previous);
		return used;
	}

	/** Called each client tick. Returns whether an item finished being used, or was interrupted, this tick. */
	public static boolean tick(Minecraft client) {
		if (!active) {
			return false;
		}

		LocalPlayer player = client.player;

		// Done, or interrupted: by a screen opening or the player picking another slot.
		if (player == null || client.gui.screen() != null || player.getInventory().getSelectedSlot() != heldSlot || !player.isUsingItem()) {
			stop(client);
			return true;
		}

		return false;
	}

	private static void stop(Minecraft client) {
		active = false;
		LocalPlayer player = client.player;

		if (player == null) {
			return;
		}

		if (player.isUsingItem() && client.gameMode != null) {
			client.gameMode.releaseUsingItem(player);
		}

		if (player.getInventory().getSelectedSlot() == heldSlot) {
			player.getInventory().setSelectedSlot(previousSlot);
		}
	}
}
