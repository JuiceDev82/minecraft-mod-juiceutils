package com.juiceclient;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Saves a long fall: with a water bucket, cobweb or hay bale in hand or anywhere in the hotbar, in that order of choice,
 * it's placed on the block about to be landed on as soon as that block comes within reach. A hay bale only softens the
 * fall.
 */
public final class AutoClutch {
	/** How far below the feet the ground is looked for, to tell how far the whole fall will be. */
	private static final double GROUND_SEARCH = 384.0;
	/** Kept clear of the very edge of reach, which the server measures a little differently. */
	private static final double REACH_MARGIN = 0.25;
	/** For blocks, which the server lets reach a block further, so a bigger margin still reaches further. */
	private static final double BLOCK_REACH_MARGIN = 0.5;
	/** How far above the ground the feet have to be still for a hay bale to fit under them. */
	private static final double HAY_CLEARANCE = 1.1;
	/** After placing, so the same fall isn't clutched twice. */
	private static final int COOLDOWN_TICKS = 20;
	private static int cooldown;

	private AutoClutch() {
	}

	/** Looking down and holding the item, to place it next tick. */
	private static boolean prepared;
	private static InteractionHand preparedHand = InteractionHand.MAIN_HAND;
	/** The slot to go back to, or -1 when the item was already in hand. */
	private static int returnSlot = -1;
	private static float returnXRot;

	/**
	 * The damage the fall under way will do on landing, before armor and enchantments, so a little more than it may be;
	 * 0 when not falling, or landing in water or lava.
	 */
	public static float fallDamage(LocalPlayer player, ClientLevel level) {
		if (!isFalling(player)) {
			return 0F;
		}

		BlockHitResult ground = ground(level, player);

		if (ground == null) {
			return 0F;
		}

		double fall = player.fallDistance + player.getY() - ground.getLocation().y;
		double safe = player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
		return (float) Math.max(0.0, Math.ceil((fall - safe) * player.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER)));
	}

	/** The ground straight below the feet, if it's a block's top, rather than water or lava. */
	private static @Nullable BlockHitResult ground(ClientLevel level, LocalPlayer player) {
		BlockHitResult ground = below(level, player);

		if (ground.getType() != HitResult.Type.BLOCK || ground.getDirection() != Direction.UP || !level.getFluidState(ground.getBlockPos()).isEmpty()) {
			return null;
		}

		return ground;
	}

	/** Whatever is straight below the feet, a block's top or the top of water or lava. */
	private static BlockHitResult below(ClientLevel level, LocalPlayer player) {
		Vec3 feet = player.position();
		return level.clip(new ClipContext(feet, feet.subtract(0.0, GROUND_SEARCH, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
	}

	/** Whether the fall under way ends in lava, from any height, without Fire Resistance to survive it. */
	public static boolean fallsIntoLava(LocalPlayer player, ClientLevel level) {
		if (!isFalling(player) || player.hasEffect(MobEffects.FIRE_RESISTANCE)) {
			return false;
		}

		BlockHitResult below = below(level, player);
		return below.getType() == HitResult.Type.BLOCK && level.getFluidState(below.getBlockPos()).is(FluidTags.LAVA);
	}

	/**
	 * Called each client tick, after the player has moved and told the server. Placing takes two ticks, as a player
	 * would: one to hold the item and look straight down, which the server is told of with the next move, and the next
	 * to place it. Servers that check where the player is looking turn down an item used while looking elsewhere.
	 */
	public static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		ClientLevel level = client.level;

		if (prepared) {
			prepared = false;

			if (player != null && level != null && client.gameMode != null) {
				place(client, player, level);
			}

			return;
		}

		if (cooldown > 0) {
			cooldown--;
			return;
		}

		JuiceClientConfig config = JuiceClient.CONFIG;

		if (player == null || level == null || client.gameMode == null || !config.enabled || !config.autoClutch || !isFalling(player)) {
			return;
		}

		BlockHitResult ground = ground(level, player);

		if (ground == null) {
			return;
		}

		double height = player.getY() - ground.getLocation().y;
		// Where the feet will be after the next move, when the item is placed.
		double nextHeight = height + player.getDeltaMovement().y;

		// Shorter falls too, when they could kill, as on low health.
		boolean deadly = fallDamage(player, level) >= player.getHealth() + player.getAbsorptionAmount();

		if (player.fallDistance + height < config.autoClutchHeight && !deadly) {
			return;
		}

		boolean waterWorks = !level.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, ground.getBlockPos().above());
		Item item = null;
		InteractionHand hand = null;
		int hotbarSlot = -1;

		// The first of these to be found, in a hand or anywhere in the hotbar.
		for (Item candidate : waterWorks ? List.of(Items.WATER_BUCKET, Items.COBWEB, Items.HAY_BLOCK) : List.of(Items.COBWEB, Items.HAY_BLOCK)) {
			hand = hand(player, candidate);
			hotbarSlot = hand == null ? hotbarSlot(player, candidate) : -1;

			if (hand != null || hotbarSlot >= 0) {
				item = candidate;
				break;
			}
		}

		if (item == null) {
			return;
		}

		// The bucket aims where the player looks, within reach. A block is placed by clicking the ground, which the server
		// lets reach a block further. A hay bale has to be down before the feet pass its top.
		double reach = item == Items.WATER_BUCKET ? player.blockInteractionRange() - REACH_MARGIN : player.blockInteractionRange() + 1.0 - BLOCK_REACH_MARGIN;
		double lowest = item == Items.HAY_BLOCK ? HAY_CLEARANCE : 0.0;
		boolean inReachNext = nextHeight > lowest && nextHeight + player.getEyeHeight() <= reach;
		// Landing before next tick: placed now, without first looking down, as the last chance.
		boolean lastChance = !inReachNext && nextHeight <= lowest && height > lowest && height + player.getEyeHeight() <= reach;

		if (!inReachNext && !lastChance) {
			return;
		}

		returnSlot = -1;

		if (hand == null) {
			returnSlot = player.getInventory().getSelectedSlot();
			hand = InteractionHand.MAIN_HAND;
			player.getInventory().setSelectedSlot(hotbarSlot);
		}

		preparedHand = hand;
		returnXRot = player.getXRot();
		player.setXRot(90F);
		cooldown = COOLDOWN_TICKS;

		if (lastChance) {
			place(client, player, level);
		} else {
			prepared = true;
		}
	}

	/** Places the item held for it on the ground below, then goes back to the slot and view from before. */
	private static void place(Minecraft client, LocalPlayer player, ClientLevel level) {
		ItemStack stack = player.getItemInHand(preparedHand);
		BlockHitResult ground = ground(level, player);
		boolean clutches = stack.is(Items.WATER_BUCKET) || stack.is(Items.COBWEB) || stack.is(Items.HAY_BLOCK);

		// Still falling towards solid ground, with the item still in hand.
		if (ground != null && clutches && isFalling(player)) {
			Component name = stack.getHoverName();

			if (stack.is(Items.WATER_BUCKET)) {
				client.gameMode.useItem(player, preparedHand);
			} else {
				Vec3 hit = new Vec3(player.getX(), ground.getLocation().y, player.getZ());
				client.gameMode.useItemOn(player, preparedHand, new BlockHitResult(hit, Direction.UP, ground.getBlockPos(), false));
			}

			double fall = player.fallDistance + player.getY() - ground.getLocation().y;
			JuiceClient.LOGGER.info("Auto Clutch placed {} {} blocks into a fall", name.getString(), Math.round(fall));
			player.sendOverlayMessage(Component.translatable("message.juiceclient.autoClutch", name).withStyle(ChatFormatting.AQUA));
		}

		player.setXRot(returnXRot);

		if (returnSlot >= 0) {
			// The server is told as the game mode ticks next.
			player.getInventory().setSelectedSlot(returnSlot);
			returnSlot = -1;
		}
	}

	/** Falling in a way that hurts on landing, with nothing else to break the fall. */
	private static boolean isFalling(LocalPlayer player) {
		return !player.onGround() && player.getDeltaMovement().y < 0.0 && player.isAlive() && !player.isSpectator()
				&& !player.getAbilities().instabuild && !player.getAbilities().flying && !player.isFallFlying() && !player.isPassenger()
				&& !player.isInWater() && !player.isInLava() && !player.hasEffect(MobEffects.SLOW_FALLING) && !player.onClimbable();
	}

	/** The hand holding the item, the main hand first; or null. */
	private static @Nullable InteractionHand hand(LocalPlayer player, Item item) {
		for (InteractionHand hand : InteractionHand.values()) {
			if (player.getItemInHand(hand).is(item)) {
				return hand;
			}
		}

		return null;
	}

	/** The first hotbar slot holding the item, or -1. */
	private static int hotbarSlot(LocalPlayer player, Item item) {
		for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
			if (player.getInventory().getItem(slot).is(item)) {
				return slot;
			}
		}

		return -1;
	}
}
