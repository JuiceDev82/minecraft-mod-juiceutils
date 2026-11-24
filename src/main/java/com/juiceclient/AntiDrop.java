package com.juiceclient;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

/**
 * Stops the items on the Anti Drop list from being thrown out by mistake, with the drop key or by clicking outside a
 * screen. They can still be put in chests and other containers, and blocks can still be placed.
 */
public final class AntiDrop {
	private AntiDrop() {
	}

	/** Whether the stack can't be thrown out. */
	public static boolean protects(ItemStack stack) {
		JuiceClientConfig config = JuiceClient.CONFIG;
		return config.enabled && config.antiDrop && config.isAntiDropped(stack);
	}

	/** Whether the drop key, outside a screen, should be stopped for the item in hand. */
	public static boolean blocksDrop(LocalPlayer player) {
		return block(player, player.getMainHandItem());
	}

	/** Whether a click in a container would throw out a protected item and should be stopped. */
	public static boolean blocksClick(AbstractContainerMenu menu, int slotNum, ContainerInput input, Player player) {
		ItemStack thrown = ItemStack.EMPTY;

		if (input == ContainerInput.THROW && slotNum >= 0 && slotNum < menu.slots.size()) {
			// The drop key over a slot, while nothing is on the cursor.
			thrown = menu.getCarried().isEmpty() ? menu.getSlot(slotNum).getItem() : ItemStack.EMPTY;
		} else if ((input == ContainerInput.PICKUP || input == ContainerInput.THROW) && slotNum == AbstractContainerMenu.SLOT_CLICKED_OUTSIDE) {
			// Clicking outside the screen with something on the cursor.
			thrown = menu.getCarried();
		}

		return block(player, thrown);
	}

	private static boolean block(Player player, ItemStack stack) {
		if (player.isCreative() || !protects(stack)) {
			return false;
		}

		player.sendOverlayMessage(Component.translatable("juiceclient.antiDrop.blocked", stack.getHoverName()));
		return true;
	}
}
