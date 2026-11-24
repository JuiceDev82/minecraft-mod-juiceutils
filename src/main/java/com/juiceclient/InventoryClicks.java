package com.juiceclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;

/**
 * Tidies up after moving items in the player's inventory without its screen open. Clicking a slot is something only an
 * open inventory does, so anticheats on many servers take it as the inventory being open, and hold the player in place
 * until it's closed. Closing the inventory screen tells the server so; this sends the same message, as if the
 * inventory had been opened for the move and closed straight after.
 */
public final class InventoryClicks {
	private static int closesSent;

	private InventoryClicks() {
	}

	/** Call after the last click of a move. Does nothing while a container screen is open, since that one closes itself. */
	public static void finish(Minecraft client, LocalPlayer player) {
		if (client.gui.screen() instanceof AbstractContainerScreen<?> || client.getConnection() == null) {
			return;
		}

		client.getConnection().send(new ServerboundContainerClosePacket(player.inventoryMenu.containerId));
		closesSent++;
	}

	/** How many times the inventory was closed after a move, for tests. */
	public static int closesSent() {
		return closesSent;
	}
}
