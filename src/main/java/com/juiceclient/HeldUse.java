package com.juiceclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;

/**
 * Uses an item from another hotbar slot as if right-clicked, then goes back to the slot that was selected. Items
 * that take time, like food and potions, have the use key held for them until they're done.
 */
public final class HeldUse {
	/** How long after going back the slot is watched, in case the server puts the used item's slot back. */
	private static final int WATCH_TICKS = 20;
	/** How many times the slot is gone back to before leaving it to the server. */
	private static final int MAX_RETRIES = 3;
	/** How long past an item's use time it's held for, in case the server is slow to say it's done, before letting go. */
	private static final int GRACE_TICKS = 10;
	private static boolean active;
	private static int heldSlot;
	private static int previousSlot;
	/** Ticks left watching the slot gone back to, or 0. */
	private static int watchTicks;
	private static int retries;

	private HeldUse() {
	}

	/** Whether an item is being used for the player. */
	public static boolean isActive() {
		return active;
	}

	/**
	 * Whether the use key should read as held: only while the one item is still being used. Once it's done the key is
	 * let go, so that the next piece of food isn't started on and the player isn't left slowed down, eating.
	 */
	public static boolean holdsUseKey(LocalPlayer player) {
		return active && player != null && player.isUsingItem() && !overdue(player);
	}

	/** Whether the item has been used for longer than it takes, as when the server never says it's done. */
	private static boolean overdue(LocalPlayer player) {
		return player.getTicksUsingItem() > player.getUseItem().getUseDuration(player) + GRACE_TICKS;
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

		// A new press from the slot gone back to ends the watch on the last one.
		watchTicks = 0;

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
		watch(slot, previous);
		return used;
	}

	private static void watch(int usedSlot, int previous) {
		if (usedSlot != previous) {
			heldSlot = usedSlot;
			previousSlot = previous;
			watchTicks = WATCH_TICKS;
			retries = 0;
		}
	}

	/**
	 * Some servers put the slot of an item just used back in hand, such as after an ender pearl is thrown. If that
	 * happens soon after going back, go back again.
	 */
	private static void tickWatch(Minecraft client) {
		if (watchTicks <= 0) {
			return;
		}

		watchTicks--;
		LocalPlayer player = client.player;

		if (player == null || client.gui.screen() != null) {
			watchTicks = 0;
			return;
		}

		int selected = player.getInventory().getSelectedSlot();

		if (selected == previousSlot) {
			return;
		}

		// The player picked some other slot themselves.
		if (selected != heldSlot) {
			watchTicks = 0;
			return;
		}

		if (retries >= MAX_RETRIES) {
			JuiceClient.LOGGER.info("The server kept putting hotbar slot {} back after a quick use; leaving it", heldSlot + 1);
			watchTicks = 0;
			return;
		}

		retries++;
		JuiceClient.LOGGER.info("The server put hotbar slot {} back after a quick use; going back to slot {} again", heldSlot + 1, previousSlot + 1);
		player.getInventory().setSelectedSlot(previousSlot);
		watchTicks = WATCH_TICKS;
	}

	/** Called each client tick. Returns whether an item finished being used, or was interrupted, this tick. */
	public static boolean tick(Minecraft client) {
		if (!active) {
			tickWatch(client);
			return false;
		}

		LocalPlayer player = client.player;

		// Done, or interrupted: by a screen opening or the player picking another slot. Or taking too long.
		if (player == null || client.gui.screen() != null || player.getInventory().getSelectedSlot() != heldSlot || !player.isUsingItem()
				|| overdue(player)) {
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
			watch(heldSlot, previousSlot);
		}
	}
}
