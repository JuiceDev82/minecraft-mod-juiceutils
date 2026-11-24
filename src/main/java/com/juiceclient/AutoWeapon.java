package com.juiceclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * When the player hits a mob or another player, switches to the best sword in the hotbar, or the best axe if there's no
 * sword. Enchantments count: Sharpness always, Smite on the undead and Bane of Arthropods on spiders and the like. A
 * mace, trident or spear in hand is kept, being chosen on purpose. After the chosen time without hitting anything, the
 * slot from before comes back.
 */
public final class AutoWeapon {
	/** Sharpness adds half a heart a level, plus half. */
	private static final float SHARPNESS_PER_LEVEL = 0.5F;
	/** Smite and Bane of Arthropods add 2.5 a level against the mobs they're for. */
	private static final float SPECIAL_PER_LEVEL = 2.5F;
	/** Ahead of any axe, so that a sword is used whenever there is one. */
	private static final float SWORD_BONUS = 1000F;
	private static final float TIEBREAK = 0.01F;

	private static int ticks;
	private static int lastHit;
	/** What was in hand before the switch, gone back to after a while without hitting; -1 when nothing's switched. */
	private static int returnSlot = -1;
	private static int weaponSlot;

	private AutoWeapon() {
	}

	/** Called as the player hits an entity, before the game tells the server which slot is selected. */
	public static void attack(LocalPlayer player, Entity target) {
		JuiceClientConfig config = JuiceClient.CONFIG;

		if (!config.enabled || !config.autoWeapon || player.isSpectator() || player.getAbilities().instabuild || HeldUse.isActive()
				|| !(target instanceof LivingEntity) || target instanceof ArmorStand) {
			return;
		}

		Inventory inventory = player.getInventory();
		lastHit = ticks;

		if (isChosenWeapon(inventory.getSelectedItem())) {
			return;
		}

		int slot = pick(inventory, target, AutoArmor.enchants(player));

		if (slot < 0 || slot == inventory.getSelectedSlot()) {
			return;
		}

		if (returnSlot < 0) {
			returnSlot = inventory.getSelectedSlot();
		}

		weaponSlot = slot;
		inventory.setSelectedSlot(slot);
	}

	/** Weapons picked on purpose, which are never switched away from. */
	private static boolean isChosenWeapon(ItemStack stack) {
		return stack.is(Items.MACE) || stack.is(Items.TRIDENT) || stack.is(ItemTags.SPEARS);
	}

	/**
	 * The hotbar slot of the best weapon against this target, or -1 to keep what's in hand: when there's no sword or axe,
	 * or the one in hand is already the best.
	 */
	public static int pick(Inventory inventory, Entity target, SmartTool.Enchants enchants) {
		int selected = inventory.getSelectedSlot();
		float bestScore = score(inventory.getItem(selected), target, enchants);
		int best = -1;

		for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
			float score = score(inventory.getItem(slot), target, enchants);

			if (score > bestScore) {
				bestScore = score;
				best = slot;
			}
		}

		return best;
	}

	/** How well a stack hits this target, swords ahead of axes, or 0 if it's neither or nearly broken. */
	public static float score(ItemStack stack, Entity target, SmartTool.Enchants enchants) {
		boolean sword = stack.is(ItemTags.SWORDS);

		if (!sword && !stack.is(ItemTags.AXES) || stack.isDamageableItem() && stack.getMaxDamage() - stack.getDamageValue() <= 1) {
			return 0F;
		}

		ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		float damage = (float) modifiers.compute(Attributes.ATTACK_DAMAGE, 0, EquipmentSlot.MAINHAND);
		int sharpness = enchants.level(Enchantments.SHARPNESS, stack);

		if (sharpness > 0) {
			damage += sharpness * SHARPNESS_PER_LEVEL + SHARPNESS_PER_LEVEL;
		}

		if (target.getType().builtInRegistryHolder().is(EntityTypeTags.SENSITIVE_TO_SMITE)) {
			damage += enchants.level(Enchantments.SMITE, stack) * SPECIAL_PER_LEVEL;
		}

		if (target.getType().builtInRegistryHolder().is(EntityTypeTags.SENSITIVE_TO_BANE_OF_ARTHROPODS)) {
			damage += enchants.level(Enchantments.BANE_OF_ARTHROPODS, stack) * SPECIAL_PER_LEVEL;
		}

		damage += enchants.level(Enchantments.FIRE_ASPECT, stack) * TIEBREAK;
		float durability = stack.isDamageableItem() ? (stack.getMaxDamage() - stack.getDamageValue()) / (float) stack.getMaxDamage() : 1F;
		return damage + durability * TIEBREAK + (sword ? SWORD_BONUS : 0F);
	}

	/** Goes back to the slot from before, once the player has stopped hitting for the chosen time. */
	public static void tick(Minecraft client) {
		ticks++;

		if (returnSlot < 0) {
			return;
		}

		LocalPlayer player = client.player;
		int delay = JuiceClient.CONFIG.autoWeaponReturn;

		if (player == null || player.getInventory().getSelectedSlot() != weaponSlot) {
			// The player picked another slot themselves, which is kept.
			returnSlot = -1;
		} else if (delay > 0 && ticks - lastHit >= delay && !client.options.keyAttack.isDown()) {
			player.getInventory().setSelectedSlot(returnSlot);
			returnSlot = -1;
		}
	}

	/** Whether a weapon is in hand only until the player stops fighting. */
	public static boolean isHoldingWeapon() {
		return returnSlot >= 0;
	}
}
