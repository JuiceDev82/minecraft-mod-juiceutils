package com.basetracer;

import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Keeps a totem of undying in the offhand. Once the offhand stops holding one, such as when it pops, it waits the
 * chosen time and then swaps in a totem from anywhere in the inventory, the same swap as pressing the offhand key over
 * it in the inventory screen. Whatever was in the offhand goes where the totem was, except a shield: that stays until
 * the player takes it out. Low on health, a totem goes in at once, shield or not.
 */
public final class AutoTotem {
	/** The button that swaps a slot with the offhand, as the offhand key does in the inventory screen. */
	private static final int OFFHAND_BUTTON = Inventory.SLOT_OFFHAND;

	private static int ticks;
	/** The tick the offhand last stopped holding a totem, or -1 while it holds one. */
	private static int missingSince = -1;
	/** Whether the offhand held a totem until it went, so that running out is said once, as the last one pops. */
	private static boolean lostTotem;

	private AutoTotem() {
	}

	public static void tick(Minecraft client) {
		ticks++;
		LocalPlayer player = client.player;
		BaseTracerConfig config = BaseTracerClient.CONFIG;

		if (player == null || client.gameMode == null || !config.enabled || !config.autoTotem
				|| player.isSpectator() || player.isCreative() || !player.isAlive()) {
			missingSince = -1;
			lostTotem = false;
			return;
		}

		if (player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
			missingSince = -1;
			lostTotem = true;
			return;
		}

		boolean lowHealth = isLowHealth(player, config);

		// A shield the player put in the offhand stays until they take it out, or it breaks. Then the wait starts.
		// Unless health is low: then the totem matters more.
		if (!lowHealth && isShield(player.getOffhandItem())) {
			missingSince = -1;
			lostTotem = false;
			return;
		}

		if (missingSince < 0) {
			missingSince = ticks;
		}

		int slot = find(player.getInventory());

		if (slot < 0) {
			if (lostTotem) {
				player.sendOverlayMessage(Component.translatable("message.basetracer.autoTotem.noneLeft").withStyle(ChatFormatting.RED));
			}

			lostTotem = false;
			return;
		}

		// Low on health, a shield or whatever else is in the offhand goes without waiting. After a pop the chosen wait
		// still applies.
		boolean skipWait = lowHealth && !lostTotem;

		if (!skipWait && ticks - missingSince < config.autoTotemDelay || !canSwap(client, player)) {
			return;
		}

		client.gameMode.handleContainerInput(player.inventoryMenu.containerId, menuSlot(slot), OFFHAND_BUTTON, ContainerInput.SWAP, player);
		InventoryClicks.finish(client, player);
		int left = player.getInventory().countItem(Items.TOTEM_OF_UNDYING) - 1;
		player.sendOverlayMessage(Component.translatable("message.basetracer.autoTotem.equipped", left).withStyle(ChatFormatting.GOLD));
		missingSince = -1;
	}

	/** Whether health is at or below the chosen number of hearts, when that's on. */
	public static boolean isLowHealth(LocalPlayer player, BaseTracerConfig config) {
		return config.autoTotemHealth > 0 && player.getHealth() <= config.autoTotemHealth * 2F;
	}

	/** Shields, including modded ones that use the common shield tag. */
	public static boolean isShield(ItemStack stack) {
		return stack.is(Items.SHIELD) || stack.is(ConventionalItemTags.SHIELD_TOOLS);
	}

	/** The inventory slot of a totem, from the main inventory before the hotbar so the hotbar stays as it was. */
	public static int find(Inventory inventory) {
		for (int slot = Inventory.getSelectionSize(); slot < Inventory.INVENTORY_SIZE; slot++) {
			if (inventory.getItem(slot).is(Items.TOTEM_OF_UNDYING)) {
				return slot;
			}
		}

		for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
			if (inventory.getItem(slot).is(Items.TOTEM_OF_UNDYING)) {
				return slot;
			}
		}

		return -1;
	}

	/** The inventory menu's index for an inventory slot: the hotbar sits after the main inventory there. */
	private static int menuSlot(int inventorySlot) {
		return inventorySlot < Inventory.getSelectionSize() ? InventoryMenu.USE_ROW_SLOT_START + inventorySlot : inventorySlot;
	}

	/**
	 * Only through the player's own inventory menu, so not while a chest or the like is open, nor with an item held on
	 * the cursor, nor while using something in the offhand.
	 */
	private static boolean canSwap(Minecraft client, LocalPlayer player) {
		if (client.gui.screen() instanceof AbstractContainerScreen<?> && !(client.gui.screen() instanceof InventoryScreen)) {
			return false;
		}

		if (!player.inventoryMenu.getCarried().isEmpty()) {
			return false;
		}

		return !(player.isUsingItem() && player.getUsedItemHand() == InteractionHand.OFF_HAND);
	}
}
