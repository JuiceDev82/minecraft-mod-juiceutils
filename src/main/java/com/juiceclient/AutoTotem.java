package com.juiceclient;

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
 * recovered, with full health and a full hunger bar, and at least 15 seconds have gone by, the shield goes back. A totem
 * the player put there themselves stays. Should the server undo the swap, the totem goes in again.
 */
public final class AutoTotem {
	/** The button that swaps a slot with the offhand, as the offhand key does in the inventory screen. */
	private static final int OFFHAND_BUTTON = Inventory.SLOT_OFFHAND;

	private static int ticks;
	/** The tick the offhand last stopped holding a totem, or -1 while it holds one. */
	private static int missingSince = -1;
	/** Whether the offhand held a totem until it went, so that running out is said once, as the last one pops. */
	private static boolean lostTotem;
	/** Fully recovered for this long after a shield was swapped out, at least, before it goes back: 15 seconds. */
	public static final int SHIELD_BACK_TICKS = 300;
	/** The tick a shield was swapped out for a totem on low health, or -1 when there's no shield to put back. */
	private static int shieldOutSince = -1;
	/** The shield that was swapped out, to find it again over any other shield. */
	private static ItemStack shieldOut = ItemStack.EMPTY;
	/** The last tick the player could have moved the offhand themselves: the offhand key held, or an inventory open. */
	private static int lastPlayerSwap = Integer.MIN_VALUE / 2;
	/** A shield turning up in the offhand this soon after that was put there by the player. */
	private static final int PLAYER_SWAP_TICKS = 10;
	/** How many times the server has undone the swap for the shield waiting to go back. */
	private static int undoneCount;
	/** Tries before giving up on a server that keeps undoing the swap. */
	public static final int MAX_UNDONE = 3;
	/** The tick the shield was put back, watched for this long in case the server undoes that too, or -1. */
	private static int backSince = -1;
	public static final int BACK_CHECK_TICKS = 100;
	/** How many times the server has undone putting the shield back. */
	private static int backUndone;
	/** The wait before trying again to put back a shield the server took out. */
	public static final int BACK_RETRY_TICKS = 20;

	private AutoTotem() {
	}

	public static void tick(Minecraft client) {
		ticks++;
		LocalPlayer player = client.player;
		JuiceClientConfig config = JuiceClient.CONFIG;

		if (!config.enabled || !config.autoTotem) {
			missingSince = -1;
			lostTotem = false;

			if (shieldOutSince >= 0) {
				dropShieldOut("Auto Totem was turned off");
			}

			return;
		}

		// The shield keeps waiting through a moment without a player, as a server moving the player to another of its
		// servers makes. Should it be gone afterwards, as after dying, it's let go once it can't be found.
		if (player == null || client.gameMode == null || player.isSpectator() || player.isCreative() || !player.isAlive()) {
			missingSince = -1;
			lostTotem = false;
			return;
		}

		if (client.options.keySwapOffhand.isDown() || client.gui.screen() instanceof AbstractContainerScreen<?>) {
			lastPlayerSwap = ticks;
		}

		// Putting the shield back can be undone just the same: the totem turns up in the offhand again. Then it's tried again.
		if (backSince >= 0) {
			if (ticks - backSince > BACK_CHECK_TICKS) {
				backSince = -1;
				backUndone = 0;
			} else if (player.getOffhandItem().is(Items.TOTEM_OF_UNDYING) && ticks - lastPlayerSwap > PLAYER_SWAP_TICKS) {
				backSince = -1;

				if (backUndone >= MAX_UNDONE) {
					player.sendOverlayMessage(Component.translatable("message.juiceclient.autoTotem.undone").withStyle(ChatFormatting.RED));
					JuiceClient.LOGGER.info("Auto Totem: the server undid putting the shield back {} times, giving up", backUndone);
					backUndone = 0;
				} else {
					backUndone++;
					JuiceClient.LOGGER.info("Auto Totem: the server undid putting the shield back, trying again");
					shieldOutSince = ticks - SHIELD_BACK_TICKS + BACK_RETRY_TICKS;
				}
			}
		}

		// A server can turn down the swap without saying so, such as one done while eating: the client shows the totem
		// until the server next sends the inventory, often as a meal ends, and the shield is back. Unless the player put
		// it there, the totem goes back in, just as if health were still low.
		boolean undone = shieldOutSince >= 0 && isShield(player.getOffhandItem()) && ticks - lastPlayerSwap > PLAYER_SWAP_TICKS;

		if (undone && undoneCount >= MAX_UNDONE) {
			player.sendOverlayMessage(Component.translatable("message.juiceclient.autoTotem.undone").withStyle(ChatFormatting.RED));
			dropShieldOut("the server undid the swap " + undoneCount + " times");
			undone = false;
		} else if (undone && missingSince < 0) {
			JuiceClient.LOGGER.info("Auto Totem: the server undid the totem swap, putting the totem back in");
		}

		if (shieldOutSince >= 0 && putShieldBack(client, player, undone)) {
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
		if (!lowHealth && !undone && isShield(player.getOffhandItem())) {
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
				player.sendOverlayMessage(Component.translatable("message.juiceclient.autoTotem.noneLeft").withStyle(ChatFormatting.RED));
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

		if (undone) {
			undoneCount++;
		} else if (lowHealth && isShield(replaced)) {
			shieldOutSince = ticks;
			shieldOut = replaced;
			undoneCount = 0;
			JuiceClient.LOGGER.info("Auto Totem: low on health ({}), swapped the shield out for a totem", player.getHealth());
		}

		int left = player.getInventory().countItem(Items.TOTEM_OF_UNDYING) - 1;
		player.sendOverlayMessage(Component.translatable("message.juiceclient.autoTotem.equipped", left).withStyle(ChatFormatting.GOLD));
		missingSince = -1;
	}

	/**
	 * Puts back a shield swapped out on low health, once the player is fully recovered and the time is up. Returns
	 * whether it did. Gives up on it when the offhand has been given something else, or the shield is gone; but not
	 * when the shield is back only because the server {@code undone} the swap.
	 */
	private static boolean putShieldBack(Minecraft client, LocalPlayer player, boolean undone) {
		ItemStack offhand = player.getOffhandItem();

		if (!offhand.isEmpty() && !offhand.is(Items.TOTEM_OF_UNDYING)) {
			if (!undone) {
				dropShieldOut("the offhand now holds " + offhand.getItem());
			}

			return false;
		}

		if (ticks - shieldOutSince < SHIELD_BACK_TICKS) {
			return false;
		}

		// With no hurry, it also waits for the player to stand still, as many anticheats turn down inventory clicks made
		// while moving.
		if (player.getHealth() < player.getMaxHealth() || player.getFoodData().getFoodLevel() < 20 || isMoving(player) || !canSwap(client, player)) {
			// Said once a minute, for finding out why a shield isn't going back.
			if ((ticks - shieldOutSince) % 1200 == 0) {
				JuiceClient.LOGGER.info("Auto Totem: shield waiting for health {}/{}, food {}/20, moving {}, swap allowed {}", player.getHealth(),
						player.getMaxHealth(), player.getFoodData().getFoodLevel(), isMoving(player), canSwap(client, player));
			}

			return false;
		}

		int slot = findShield(player.getInventory());

		if (slot < 0) {
			dropShieldOut("there's no shield in the inventory");
			return false;
		}

		shieldOutSince = -1;
		undoneCount = 0;
		backSince = ticks;
		JuiceClient.LOGGER.info("Auto Totem: fully recovered, put the shield back in the offhand");

		client.gameMode.handleContainerInput(player.inventoryMenu.containerId, menuSlot(slot), OFFHAND_BUTTON, ContainerInput.SWAP, player);
		InventoryClicks.finish(client, player);
		player.sendOverlayMessage(Component.translatable("message.juiceclient.autoTotem.shieldBack").withStyle(ChatFormatting.GOLD));
		missingSince = -1;
		lostTotem = false;
		return true;
	}

	/** Whether the player is moving across the ground or through the air, rather than standing still. */
	/** Whether the player is walking or being pushed sideways, rather than standing still. */
	static boolean isMoving(LocalPlayer player) {
		return player.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4;
	}

	/** Stops waiting to put the shield back, saying why in the game log. */
	private static void dropShieldOut(String why) {
		JuiceClient.LOGGER.info("Auto Totem: no longer putting the shield back, as {}", why);
		shieldOutSince = -1;
		undoneCount = 0;
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
	public static boolean isLowHealth(LocalPlayer player, JuiceClientConfig config) {
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
