package com.juiceclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * Drops the items on the Auto Drop list as soon as they're in the inventory, so junk that's picked up never stays.
 * Each stack goes once the player has stood still for half a second with it, and half a second after the last one
 * went. Armor being worn is left alone.
 */
public final class AutoDrop {
	/** Half a second before each stack is dropped. */
	public static final int DROP_DELAY_TICKS = 10;
	private static int ticks;
	/** The tick a listed item was found, or after one was dropped, the tick it was; -1 while there's nothing to do. */
	private static int waitingSince = -1;
	private static final int OFFHAND_SLOT = Inventory.SLOT_OFFHAND;

	private AutoDrop() {
	}

	public static void tick(Minecraft client) {
		ticks++;
		LocalPlayer player = client.player;
		JuiceClientConfig config = JuiceClient.CONFIG;

		if (player == null || client.gameMode == null || !config.enabled || !config.autoDrop || !config.hasAutoDropItems()
				|| player.isSpectator() || player.isCreative() || !player.isAlive()) {
			waitingSince = -1;
			return;
		}

		// Not while a screen is open, so items being moved by hand aren't fought over, and not with something held on
		// the cursor.
		if (client.gui.screen() != null || !player.inventoryMenu.getCarried().isEmpty()) {
			return;
		}

		int slot = find(player.getInventory(), config);

		if (slot < 0) {
			waitingSince = -1;
			return;
		}

		// The half second starts over while moving.
		if (waitingSince < 0 || AutoTotem.isMoving(player)) {
			waitingSince = ticks;
		}

		if (ticks - waitingSince < DROP_DELAY_TICKS) {
			return;
		}

		// Button 1 throws the whole stack.
		client.gameMode.handleContainerInput(player.inventoryMenu.containerId, menuSlot(slot), 1, ContainerInput.THROW, player);
		InventoryClicks.finish(client, player);
		waitingSince = ticks;
	}

	/** The first slot, hotbar, main inventory or offhand, holding an item on the list, or -1. */
	public static int find(Inventory inventory, JuiceClientConfig config) {
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			if (config.isAutoDropped(inventory.getItem(slot))) {
				return slot;
			}
		}

		return config.isAutoDropped(inventory.getItem(OFFHAND_SLOT)) ? OFFHAND_SLOT : -1;
	}

	private static int menuSlot(int inventorySlot) {
		if (inventorySlot == OFFHAND_SLOT) {
			return InventoryMenu.SHIELD_SLOT;
		}

		return inventorySlot < Inventory.getSelectionSize() ? InventoryMenu.USE_ROW_SLOT_START + inventorySlot : inventorySlot;
	}
}
