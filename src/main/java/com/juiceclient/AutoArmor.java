package com.juiceclient;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.equipment.Equippable;

/**
 * Wears the best armor in the inventory. Each piece is scored by its armor, toughness and knockback resistance, plus its
 * protection enchantments, so diamond with Protection IV beats bare netherite. A worn piece is only swapped for a
 * better one. Things worn that aren't armor, such as an elytra or a carved pumpkin, are left alone, and so is anything
 * with Curse of Binding. Each piece goes on half a second after the last, or after a better one turns up.
 */
public final class AutoArmor {
	/** Head to feet, the order the inventory menu has them in. */
	private static final List<EquipmentSlot> SLOTS = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
	/** Half a second before each piece goes on, one piece at a time, rather than all at once. */
	public static final int SWAP_DELAY_TICKS = 10;
	/** Protection reduces all damage by 4% a level; an armor point is worth about that. */
	private static final float PROTECTION_PER_LEVEL = 1F;
	/** The specialized protections are stronger but only against one kind of damage. */
	private static final float SPECIAL_PROTECTION_PER_LEVEL = 0.5F;
	/** Netherite's 0.1 knockback resistance counts like an armor point. */
	private static final float KNOCKBACK_RESISTANCE_WEIGHT = 10F;
	/** Small enough to only break ties between otherwise equal pieces. */
	private static final float TIEBREAK_PER_LEVEL = 0.05F;

	private static int ticks;
	/** The tick a better piece was found, or after one went on, the tick it did; -1 while there's nothing to do. */
	private static int waitingSince = -1;

	private AutoArmor() {
	}

	public static void tick(Minecraft client) {
		ticks++;
		LocalPlayer player = client.player;
		JuiceClientConfig config = JuiceClient.CONFIG;

		if (player == null || client.gameMode == null || !config.enabled || !config.autoArmor
				|| player.isSpectator() || player.isCreative() || !player.isAlive()) {
			waitingSince = -1;
			return;
		}

		// Not while any screen is open, so that rearranging armor by hand isn't fought over, and not with something
		// held on the cursor.
		if (client.gui.screen() != null || !player.inventoryMenu.getCarried().isEmpty()) {
			return;
		}

		SmartTool.Enchants enchants = enchants(player);
		Inventory inventory = player.getInventory();

		for (int i = 0; i < SLOTS.size(); i++) {
			EquipmentSlot slot = SLOTS.get(i);
			int best = pick(inventory, player.getItemBySlot(slot), slot, enchants);

			if (best < 0) {
				continue;
			}

			if (waitingSince < 0) {
				waitingSince = ticks;
			}

			if (ticks - waitingSince < SWAP_DELAY_TICKS) {
				return;
			}

			ItemStack chosen = inventory.getItem(best).copy();
			int containerId = player.inventoryMenu.containerId;
			int from = menuSlot(best);
			int armorSlot = InventoryMenu.ARMOR_SLOT_START + i;
			// Picks the piece up, puts it on, and puts whatever was worn where the piece was.
			client.gameMode.handleContainerInput(containerId, from, 0, ContainerInput.PICKUP, player);
			client.gameMode.handleContainerInput(containerId, armorSlot, 0, ContainerInput.PICKUP, player);

			if (!player.inventoryMenu.getCarried().isEmpty()) {
				client.gameMode.handleContainerInput(containerId, from, 0, ContainerInput.PICKUP, player);
			}

			InventoryClicks.finish(client, player);

			player.sendOverlayMessage(Component.translatable("message.juiceclient.autoArmor.equipped", chosen.getHoverName()).withStyle(ChatFormatting.AQUA));
			waitingSince = ticks;
			return;
		}

		waitingSince = -1;
	}

	/**
	 * The inventory slot of the best piece to wear in this slot, or -1 to keep what's worn: when nothing is better, or
	 * when what's worn isn't armor or can't be taken off.
	 */
	public static int pick(Inventory inventory, ItemStack worn, EquipmentSlot slot, SmartTool.Enchants enchants) {
		float bestScore;

		if (worn.isEmpty()) {
			bestScore = Float.NEGATIVE_INFINITY;
		} else {
			if (!isArmor(worn, slot) || enchants.level(Enchantments.BINDING_CURSE, worn) > 0) {
				return -1;
			}

			bestScore = score(worn, slot, enchants);
		}

		int best = -1;

		for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
			ItemStack stack = inventory.getItem(i);

			if (!isArmor(stack, slot) || enchants.level(Enchantments.BINDING_CURSE, stack) > 0) {
				continue;
			}

			float score = score(stack, slot, enchants);

			if (score > bestScore) {
				bestScore = score;
				best = i;
			}
		}

		return best;
	}

	/** Whether a stack is worn in this slot and gives armor there, which leaves out elytras, pumpkins and heads. */
	public static boolean isArmor(ItemStack stack, EquipmentSlot slot) {
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);

		if (equippable == null || equippable.slot() != slot) {
			return false;
		}

		ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		return modifiers.compute(Attributes.ARMOR, 0, slot) > 0;
	}

	/** How much a piece protects: its armor, toughness and knockback resistance, and its enchantments. */
	public static float score(ItemStack stack, EquipmentSlot slot, SmartTool.Enchants enchants) {
		ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		float score = (float) modifiers.compute(Attributes.ARMOR, 0, slot)
				+ (float) modifiers.compute(Attributes.ARMOR_TOUGHNESS, 0, slot)
				+ (float) modifiers.compute(Attributes.KNOCKBACK_RESISTANCE, 0, slot) * KNOCKBACK_RESISTANCE_WEIGHT;

		score += enchants.level(Enchantments.PROTECTION, stack) * PROTECTION_PER_LEVEL;

		for (ResourceKey<Enchantment> special : List.of(Enchantments.FIRE_PROTECTION, Enchantments.BLAST_PROTECTION, Enchantments.PROJECTILE_PROTECTION)) {
			score += enchants.level(special, stack) * SPECIAL_PROTECTION_PER_LEVEL;
		}

		if (slot == EquipmentSlot.FEET) {
			score += enchants.level(Enchantments.FEATHER_FALLING, stack) * SPECIAL_PROTECTION_PER_LEVEL;
		}

		for (ResourceKey<Enchantment> extra : List.of(Enchantments.UNBREAKING, Enchantments.MENDING, Enchantments.THORNS)) {
			score += enchants.level(extra, stack) * TIEBREAK_PER_LEVEL;
		}

		// A piece about to break is worth a little less than the same piece whole.
		if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
			score -= TIEBREAK_PER_LEVEL * stack.getDamageValue() / stack.getMaxDamage();
		}

		return score;
	}

	/** The player's enchantment levels, looked up through the world's registry. */
	public static SmartTool.Enchants enchants(LocalPlayer player) {
		var enchantments = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		return (enchantment, stack) -> enchantments.get(enchantment)
				.map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, stack))
				.orElse(0);
	}

	/** The inventory menu's index for an inventory slot: the hotbar sits after the main inventory there. */
	private static int menuSlot(int inventorySlot) {
		return inventorySlot < Inventory.getSelectionSize() ? InventoryMenu.USE_ROW_SLOT_START + inventorySlot : inventorySlot;
	}
}
