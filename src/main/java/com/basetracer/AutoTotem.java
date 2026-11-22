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
 * the player takes it out. Low on health, a totem goes in at once, shield or not; and once the player has fully
 * recovered, with full health and a full hunger bar, and at least 30 seconds have gone by, the shield goes back. A totem
 * the player put there themselves stays.
 */
public final class AutoTotem {
	/** The button that swaps a slot with the offhand, as the offhand key does in the inventory screen. */
	private static final int OFFHAND_BUTTON = Inventory.SLOT_OFFHAND;

	private static int ticks;
	/** The tick the offhand last stopped holding a totem, or -1 while it holds one. */
	private static int missingSince = -1;
	/** Whether the offhand held a totem until it went, so that running out is said once, as the last one pops. */
	private static boolean lostTotem;
	/** Fully recovered for this long after a shield was swapped out, at least, before it goes back: 30 seconds. */
	public static final int SHIELD_BACK_TICKS = 600;
	/** The tick a shield was swapped out for a totem on low health, or -1 when there's no shield to put back. */
	private static int shieldOutSince = -1;
	/** The shield that was swapped out, to find it again over any other shield. */
	private static ItemStack shieldOut = ItemStack.EMPTY;

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
			shieldOutSince = -1;
			return;
		}

		if (shieldOutSince >= 0 && putShieldBack(client, player)) {
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

		ItemStack replaced = player.getOffhandItem().copy();
		client.gameMode.handleContainerInput(player.inventoryMenu.containerId, menuSlot(slot), OFFHAND_BUTTON, ContainerInput.SWAP, player);
		InventoryClicks.finish(client, player);

		if (lowHealth && isShield(replaced)) {
			shieldOutSince = ticks;
			shieldOut = replaced;
		}

		int left = player.getInventory().countItem(Items.TOTEM_OF_UNDYING) - 1;
		player.sendOverlayMessage(Component.translatable("message.basetracer.autoTotem.equipped", left).withStyle(ChatFormatting.GOLD));
		missingSince = -1;
	}

	/**
	 * Puts back a shield swapped out on low health, once the player is fully recovered and the time is up. Returns
	 * whether it did. Gives up on it when the offhand has been given something else, or the shield is gone.
	 */
	private static boolean putShieldBack(Minecraft client, LocalPlayer player) {
		ItemStack offhand = player.getOffhandItem();

		if (!offhand.isEmpty() && !offhand.is(Items.TOTEM_OF_UNDYING)) {
			shieldOutSince = -1;
			return false;
		}

		if (ticks - shieldOutSince < SHIELD_BACK_TICKS || player.getHealth() < player.getMaxHealth()
				|| player.getFoodData().getFoodLevel() < 20 || !canSwap(client, player)) {
			return false;
		}

		int slot = findShield(player.getInventory());
		shieldOutSince = -1;

		if (slot < 0) {
			return false;
		}

		client.gameMode.handleContainerInput(player.inventoryMenu.containerId, menuSlot(slot), OFFHAND_BUTTON, ContainerInput.SWAP, player);
		InventoryClicks.finish(client, player);
		player.sendOverlayMessage(Component.translatable("message.basetracer.autoTotem.shieldBack").withStyle(ChatFormatting.GOLD));
		missingSince = -1;
		lostTotem = false;
		return true;
	}

	/** The slot of the shield that was swapped out, or failing that any shield, the main inventory before the hotbar. */
	private static int findShield(Inventory inventory) {
		int any = -1;

		for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
			int slot = (i + Inventory.getSelectionSize()) % Inventory.INVENTORY_SIZE;
			ItemStack stack = inventory.getItem(slot);

			if (ItemStack.isSameItemSameComponents(stack, shieldOut)) {
				return slot;
			}

			if (any < 0 && isShield(stack)) {
				any = slot;
			}
		}

		return any;
	}

	/** Whether a shield swapped out on low health is waiting to go back. */
	public static boolean isShieldOut() {
		return shieldOutSince >= 0;
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
