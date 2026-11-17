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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Eats the best food in the hotbar once hunger or health drops to a threshold, then goes back to the slot that was
 * selected. When hurt, golden apples come first, for their Regeneration.
 */
public final class AutoEat {
	/** Foods that harm or teleport, and enchanted golden apples, too rare to eat just for hunger. */
	private static final Set<Item> AVOIDED = Set.of(Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.POISONOUS_POTATO, Items.PUFFERFISH,
			Items.CHICKEN, Items.SUSPICIOUS_STEW, Items.CHORUS_FRUIT, Items.ENCHANTED_GOLDEN_APPLE);
	/** The hunger bar is updated by the server, so after a meal it's given time to catch up before eating again. */
	private static final int COOLDOWN_TICKS = 10;
	/** Ahead of any food's score, for when health is low. */
	private static final float GOLDEN_APPLE_BONUS = 1000F;

	private static boolean eating;
	private static int cooldown;
	private static boolean warnedNoFood;

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
		HeldUse.use(client, slot);

		if (HeldUse.isActive()) {
			eating = true;
		} else {
			cooldown = COOLDOWN_TICKS;
		}
	}
}
