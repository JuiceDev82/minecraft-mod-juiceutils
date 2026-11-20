package com.basetracer;

import java.util.Set;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Eats the best food in the hotbar once hunger or health drops to a threshold, then goes back to the slot that was
 * selected. When hurt, golden apples come first, for their Regeneration. With Auto Resupply Food, once the hotbar has no
 * food left, the best food from the rest of the inventory is moved into it.
 */
public final class AutoEat {
	/** Foods that harm or teleport, and enchanted golden apples, too rare to eat just for hunger. */
	private static final Set<Item> AVOIDED = Set.of(Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.POISONOUS_POTATO, Items.PUFFERFISH,
			Items.CHICKEN, Items.SUSPICIOUS_STEW, Items.CHORUS_FRUIT, Items.ENCHANTED_GOLDEN_APPLE);
	/** The hunger bar is updated by the server, so after a meal it's given time to catch up before eating again. */
	private static final int COOLDOWN_TICKS = 10;
	/** Ahead of any food's score, for when health is low. */
	private static final float GOLDEN_APPLE_BONUS = 1000F;
	/** A golden apple's score when resupplying, so that it only comes down when there's no other food. */
	private static final float GOLDEN_APPLE_LAST = 0.01F;
	/** And an enchanted golden apple's, below that, so it only comes down when there's no other food at all. */
	private static final float ENCHANTED_APPLE_LAST = 0.001F;

	private static boolean eating;
	private static int cooldown;
	private static boolean warnedNoFood;
	/** The hotbar slot last eaten from, where new food goes when it's empty. */
	private static int lastFoodSlot = -1;

	private AutoEat() {
	}

	/** Whether auto eat is eating. */
	public static boolean isEating() {
		return eating && HeldUse.isActive();
	}

	/** The hotbar slot with the best food, or -1 if there's none worth eating. */
	public static int pick(Inventory inventory) {
		return pick(inventory, false, false, false);
	}

	/**
	 * The hotbar slot with the best food for now, or -1. When {@code hurt}, golden apples come first, unless
	 * Regeneration is already {@code healing}. With a {@code full} hunger bar only food that can always be eaten,
	 * like golden apples, can be.
	 */
	public static int pick(Inventory inventory, boolean hurt, boolean full, boolean healing) {
		int best = -1;
		float bestScore = 0F;

		for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			float score = score(stack);
			boolean goldenApple = stack.is(Items.GOLDEN_APPLE);
			FoodProperties food = stack.get(DataComponents.FOOD);

			if ((full && (food == null || !food.canAlwaysEat())) || (goldenApple && hurt && healing)) {
				continue;
			}

			if (hurt && goldenApple) {
				score += GOLDEN_APPLE_BONUS;
			}

			if (score > bestScore) {
				best = slot;
				bestScore = score;
			}
		}

		return best;
	}

	/**
	 * With no food in the hotbar, moves the best from the rest of the inventory into it: to the slot last eaten from if
	 * that's empty, otherwise the first empty one. Returns whether it moved something. Golden apples only come down
	 * when there's no other food, being worth saving, and enchanted golden apples only when there's nothing else at all.
	 */
	private static boolean resupply(Minecraft client, LocalPlayer player) {
		Inventory inventory = player.getInventory();

		if (hasHotbarFood(inventory) || client.gui.screen() != null || !player.inventoryMenu.getCarried().isEmpty() || player.isUsingItem()) {
			return false;
		}

		int from = pickStored(inventory);
		int to = resupplySlot(inventory, lastFoodSlot);

		if (from < 0 || to < 0) {
			return false;
		}

		ItemStack food = inventory.getItem(from).copy();
		// The main inventory has the same slot numbers in the inventory menu; the button is the hotbar slot.
		client.gameMode.handleContainerInput(player.inventoryMenu.containerId, from, to, ContainerInput.SWAP, player);
		InventoryClicks.finish(client, player);
		player.sendOverlayMessage(Component.translatable("message.basetracer.autoEat.resupplied", food.getHoverName()).withStyle(ChatFormatting.GREEN));
		cooldown = COOLDOWN_TICKS;
		return true;
	}

	/** Whether the hotbar holds any food worth eating, counting an enchanted golden apple, so that it isn't refilled. */
	public static boolean hasHotbarFood(Inventory inventory) {
		for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);

			if (score(stack) > 0F || stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
				return true;
			}
		}

		return false;
	}

	/** The main inventory slot of the best food to bring down to the hotbar, golden apples last, or -1. */
	public static int pickStored(Inventory inventory) {
		int best = -1;
		float bestScore = 0F;

		for (int slot = Inventory.getSelectionSize(); slot < Inventory.INVENTORY_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			float score = score(stack);

			// Still above nothing, but below any other food.
			if (stack.is(Items.GOLDEN_APPLE) && score > 0F) {
				score = GOLDEN_APPLE_LAST;
			} else if (stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
				score = ENCHANTED_APPLE_LAST;
			}

			if (score > bestScore) {
				best = slot;
				bestScore = score;
			}
		}

		return best;
	}

	/** Where resupplied food goes: the slot last eaten from if it's empty, otherwise the first empty one, or -1. */
	public static int resupplySlot(Inventory inventory, int preferred) {
		if (preferred >= 0 && preferred < Inventory.getSelectionSize() && inventory.getItem(preferred).isEmpty()) {
			return preferred;
		}

		for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
			if (inventory.getItem(slot).isEmpty()) {
				return slot;
			}
		}

		return -1;
	}

	/** Hunger and saturation restored, or 0 if it isn't food or is avoided. */
	private static float score(ItemStack stack) {
		FoodProperties food = stack.get(DataComponents.FOOD);

		if (food == null || AVOIDED.contains(stack.getItem())) {
			return 0F;
		}

		return food.nutrition() + food.saturation();
	}

	public static void tick(Minecraft client) {
		LocalPlayer player = client.player;

		if (eating) {
			if (HeldUse.isActive()) {
				return;
			}

			eating = false;
			cooldown = COOLDOWN_TICKS;
		}

		// Something else is being used for the player, by Quick Use.
		if (HeldUse.isActive()) {
			return;
		}

		if (cooldown > 0) {
			cooldown--;
			return;
		}

		BaseTracerConfig config = BaseTracerClient.CONFIG;

		if (player == null || client.gameMode == null || !config.enabled || !config.autoEat || player.isSpectator() || player.getAbilities().instabuild) {
			return;
		}

		if (config.autoEatResupply && resupply(client, player)) {
			return;
		}

		int foodLevel = player.getFoodData().getFoodLevel();
		boolean hungry = foodLevel <= config.autoEatHunger;
		boolean hurt = config.autoEatHealth > 0 && player.getHealth() < player.getMaxHealth() * config.autoEatHealth / 100F;

		if (!hungry) {
			warnedNoFood = false;
		}

		if (!hungry && !hurt) {
			return;
		}

		// Never in the middle of something else: a menu, mining, drawing a bow or blocking. Nor while Smart Tool
		// holds a tool, so that both go back to the same slot.
		if (client.gui.screen() != null || player.isUsingItem() || client.gameMode.isDestroying() || SmartTool.isHoldingTool()) {
			return;
		}

		int slot = pick(player.getInventory(), hurt, foodLevel >= 20, player.hasEffect(MobEffects.REGENERATION));

		if (slot < 0) {
			// Only for hunger: when hurt with a full bar there's often just nothing that can be eaten.
			if (hungry && !warnedNoFood) {
				warnedNoFood = true;
				player.sendOverlayMessage(Component.translatable("message.basetracer.autoEat.noFood").withStyle(ChatFormatting.RED));
			}

			return;
		}

		warnedNoFood = false;
		lastFoodSlot = slot;
		HeldUse.use(client, slot);

		if (HeldUse.isActive()) {
			eating = true;
		} else {
			cooldown = COOLDOWN_TICKS;
		}
	}
}
