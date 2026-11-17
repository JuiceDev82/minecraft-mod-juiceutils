package com.basetracer;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * When the player starts mining a block, selects the best tool of the right kind in the hotbar: a pickaxe for stone
 * and ores, a shovel for dirt and sand, an axe for wood and a hoe for sculk. A worse one of the same kind in hand is swapped too.
 * Ender chests and glass, which break into nothing useful otherwise, prefer a tool with Silk Touch. Boats get an axe.
 */
public final class SmartTool {
	private record Kind(Predicate<BlockState> blocks, TagKey<Item> tools) {
		Kind(TagKey<Block> blocks, TagKey<Item> tools) {
			this(state -> state.is(blocks), tools);
		}
	}

	/**
	 * Hoes only for sculk: they also mine leaves and hay, which are as often hit by a sword or a bare hand and would
	 * switch away from them.
	 */
	private static final Set<Block> SCULK = Set.of(Blocks.SCULK, Blocks.SCULK_VEIN, Blocks.SCULK_CATALYST, Blocks.SCULK_SHRIEKER,
			Blocks.SCULK_SENSOR, Blocks.CALIBRATED_SCULK_SENSOR);

	private static final List<Kind> KINDS = List.of(
			new Kind(state -> SCULK.contains(state.getBlock()), ItemTags.HOES),
			new Kind(BlockTags.MINEABLE_WITH_PICKAXE, ItemTags.PICKAXES),
			new Kind(BlockTags.MINEABLE_WITH_SHOVEL, ItemTags.SHOVELS),
			new Kind(BlockTags.MINEABLE_WITH_AXE, ItemTags.AXES)
	);
	/** Getting the block's drops matters more than any speed, like an iron pickaxe over a gold one on diamond ore. */
	private static final float DROPS_BONUS = 10_000F;
	/** And getting the block itself back matters more still. */
	private static final float SILK_BONUS = 100_000F;

	/** An item's level of an enchantment, 0 without it. */
	@FunctionalInterface
	public interface Enchants {
		int level(ResourceKey<Enchantment> enchantment, ItemStack stack);
	}

	private SmartTool() {
	}

	/**
	 * The hotbar slot to switch to before mining this block, or -1 to keep the current one. The tool in hand is kept
	 * when nothing is better, so equal tools aren't swapped back and forth.
	 */
	public static int pick(Inventory inventory, BlockState state, Enchants enchants) {
		Kind kind = null;

		for (Kind candidate : KINDS) {
			if (candidate.blocks().test(state)) {
				kind = candidate;
				break;
			}
		}

		boolean silk = wantsSilkTouch(state);

		if (kind == null && !silk) {
			return -1;
		}

		int current = inventory.getSelectedSlot();
		int best = -1;
		float bestScore = score(inventory.getSelectedItem(), state, kind, silk, enchants);

		for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
			if (slot == current) {
				continue;
			}

			float score = score(inventory.getItem(slot), state, kind, silk, enchants);

			if (score > bestScore) {
				best = slot;
				bestScore = score;
			}
		}

		return best;
	}

	/**
	 * Blocks that only drop themselves when mined with Silk Touch, and are worth keeping: ender chests, which otherwise
	 * drop obsidian, and glass and glass panes, which otherwise drop nothing.
	 */
	public static boolean wantsSilkTouch(BlockState state) {
		// The name is checked too, since a server needn't send the convention tags.
		return state.is(Blocks.ENDER_CHEST) || state.is(ConventionalBlockTags.GLASS_BLOCKS) || state.is(ConventionalBlockTags.GLASS_PANES)
				|| BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().contains("glass");
	}

	/**
	 * How good a stack is at mining the block, or 0 if it shouldn't be used: a tool of the wrong kind, or for a block
	 * that needs no tool, anything without Silk Touch.
	 */
	private static float score(ItemStack stack, BlockState state, Kind kind, boolean silk, Enchants enchants) {
		boolean hasSilk = silk && enchants.level(Enchantments.SILK_TOUCH, stack) > 0;

		if (nearlyBroken(stack) || (kind != null ? !stack.is(kind.tools()) : !hasSilk)) {
			return 0F;
		}

		// As mining works out speed: Efficiency adds its level squared plus one to a tool that's already faster.
		float speed = stack.getDestroySpeed(state);
		int level = enchants.level(Enchantments.EFFICIENCY, stack);

		if (speed > 1F && level > 0) {
			speed += level * level + 1;
		}

		// Remaining durability only breaks ties between otherwise equal tools.
		float durability = stack.isDamageableItem() ? (stack.getMaxDamage() - stack.getDamageValue()) / (float) stack.getMaxDamage() : 1F;
		return speed + durability * 0.01F + (kind != null && stack.isCorrectToolForDrops(state) ? DROPS_BONUS : 0F) + (hasSilk ? SILK_BONUS : 0F);
	}

	/** Left alone so that a favourite tool isn't broken by a switch the player didn't make. */
	private static boolean nearlyBroken(ItemStack stack) {
		return stack.isDamageableItem() && stack.getMaxDamage() - stack.getDamageValue() <= 1;
	}

	// A switch waits until the player has kept mining for the delay, so that a misclick doesn't change the tool.
	private static BlockPos pendingPos;
	private static int pendingSince;
	private static int lastMined;
	private static int ticks;
	// What was in hand before the first switch, gone back to once the player stops mining.
	private static int returnSlot = -1;
	private static int toolSlot;
	/** Boats take a few clicks to break, so the axe stays in hand for a second after each hit. */
	private static final int BOAT_HOLD_TICKS = 20;
	private static int lastBoatHit = Integer.MIN_VALUE / 2;

	/** The slot to switch to for this block, or -1 to keep the current one. */
	private static int slotFor(LocalPlayer player, BlockState state) {
		BaseTracerConfig config = BaseTracerClient.CONFIG;

		if (!config.enabled || !config.smartTool || player.isSpectator() || player.getAbilities().instabuild || HeldUse.isActive()) {
			return -1;
		}

		var enchantments = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		return pick(player.getInventory(), state, (enchantment, stack) -> enchantments.get(enchantment)
				.map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, stack))
				.orElse(0));
	}

	/**
	 * Called as mining of a block starts. Returns whether the player was switched to another slot right away, which
	 * only happens with no delay; otherwise the switch waits for {@link #tick}.
	 */
	public static boolean startMining(LocalPlayer player, BlockState state, BlockPos pos) {
		lastMined = ticks;
		int slot = slotFor(player, state);

		if (slot < 0) {
			pendingPos = null;
			return false;
		}

		if (BaseTracerClient.CONFIG.smartToolDelay <= 0) {
			pendingPos = null;
			switchTo(player, slot);
			return true;
		}

		// Mining on from one block to the next keeps counting from the first.
		if (pendingPos == null) {
			pendingSince = ticks;
		}

		pendingPos = pos.immutable();
		return false;
	}

	/**
	 * Called as the player hits an entity: a boat is broken with an axe, switched to right away since each click
	 * is a hit of its own.
	 */
	public static void attack(LocalPlayer player, Entity target) {
		if (!(target instanceof AbstractBoat)) {
			return;
		}

		// Planks stand in for the boat, so the axe is chosen as it would be for wood.
		int slot = slotFor(player, Blocks.OAK_PLANKS.defaultBlockState());
		pendingPos = null;

		if (slot >= 0) {
			switchTo(player, slot);
		}

		if (isHoldingTool()) {
			lastBoatHit = ticks;
		}
	}

	/**
	 * Called each tick mining goes on. Returns whether to hold the block's progress until the switch, when the tool in
	 * hand would waste the block: glass breaks by hand before the delay is up, and a gold pickaxe gets ore but no drops.
	 */
	public static boolean continueMining(LocalPlayer player, BlockState state, BlockPos pos) {
		lastMined = ticks;

		if (pendingPos == null) {
			return false;
		}

		boolean wasted = wantsSilkTouch(state) || (state.requiresCorrectToolForDrops() && !player.getInventory().getSelectedItem().isCorrectToolForDrops(state));
		return pos.equals(pendingPos) && wasted;
	}

	/** Called at the end of each client tick: switches once mining has gone on long enough, or drops a misclick. */
	public static void tick(Minecraft client) {
		if (pendingPos != null) {
			LocalPlayer player = client.player;

			if (player == null || client.level == null || lastMined < ticks) {
				pendingPos = null;
			} else if (ticks - pendingSince >= BaseTracerClient.CONFIG.smartToolDelay) {
				// The server is told of the new slot as mining goes on next tick, which then starts over with the tool.
				int slot = slotFor(player, client.level.getBlockState(pendingPos));

				if (slot >= 0) {
					switchTo(player, slot);
				}

				pendingPos = null;
			}
		}

		if (returnSlot >= 0) {
			LocalPlayer player = client.player;

			if (player == null || player.getInventory().getSelectedSlot() != toolSlot) {
				// The player picked another slot themselves, which is kept.
				returnSlot = -1;
			} else if (!client.options.keyAttack.isDown() && lastMined < ticks && ticks - lastBoatHit > BOAT_HOLD_TICKS) {
				// Released: back to what was in hand. The server is told as the game mode ticks next.
				player.getInventory().setSelectedSlot(returnSlot);
				returnSlot = -1;
			}
		}

		ticks++;
	}

	/** Mining on from one block to another keeps the slot from before the first switch to go back to. */
	private static void switchTo(LocalPlayer player, int slot) {
		if (returnSlot < 0) {
			returnSlot = player.getInventory().getSelectedSlot();
		}

		toolSlot = slot;
		player.getInventory().setSelectedSlot(slot);
	}

	/** Whether a tool is in hand only until mining stops. */
	public static boolean isHoldingTool() {
		return returnSlot >= 0;
	}

	/** Whether a switch is waiting on the delay, for tests. */
	public static boolean isPending() {
		return pendingPos != null;
	}
}
