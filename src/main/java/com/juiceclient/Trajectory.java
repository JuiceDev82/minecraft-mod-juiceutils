package com.juiceclient;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ExperienceBottleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.WindChargeItem;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Works out where something the player can throw or shoot would go, tick by tick, as the game moves it: snowballs,
 * eggs, ender pearls, splash and lingering potions, bottles o' enchanting and wind charges in hand, arrows from a bow
 * being drawn or a loaded crossbow, and a trident being readied. Inaccuracy is left out, as it's random.
 */
public final class Trajectory {
	/** How each kind moves in a tick, which differs in order as well as in numbers. */
	private enum Motion {
		/** Gravity, then drag, then the move, as thrown items do. */
		THROWN,
		/** The move, then drag, then gravity, as arrows and tridents do. Slowed in water before the move instead. */
		ARROW,
		/** Straight on, as wind charges and crossbow fireworks go. */
		STRAIGHT
	}

	/** How it leaves the player: its speed, its tilt upwards in degrees, how fast it falls, and how water slows it. */
	private record Shot(Motion motion, float speed, float tilt, double gravity, double waterDrag) {
	}

	/**
	 * The points it passes through, a tick apart, ending where it lands; and what it hits there, if anything: the block
	 * and the side of it, or the entity.
	 */
	public record Path(List<Vec3> points, @Nullable Vec3 hit, @Nullable Direction face, @Nullable Entity entity) {
	}

	private static final float DEGREES = Mth.DEG_TO_RAD;
	private static final double AIR_DRAG = 0.99;
	private static final double THROWN_WATER_DRAG = 0.8;
	/** Long enough for any throw to come down, even from high up. */
	private static final int MAX_TICKS = 400;
	/** Straight shots go on forever, so they stop at a distance the client has blocks for. */
	private static final int MAX_STRAIGHT_TICKS = 100;
	/** How much wider than its box an entity is to a projectile. */
	private static final float ENTITY_MARGIN = 0.3F;

	private Trajectory() {
	}

	/** The path of what the player would throw or shoot now, or null when there's nothing to. */
	public static @Nullable Path predict(Player player, float partialTick) {
		Shot shot = shotFor(player);
		return shot == null ? null : simulate(player.level(), player, shot, partialTick);
	}

	/**
	 * What the player is about to throw or shoot: a bow or trident only while being drawn or readied, so the path shows
	 * as they aim; anything else as soon as it's in hand, the main hand first since that's the one used.
	 */
	private static @Nullable Shot shotFor(Player player) {
		if (player.isUsingItem()) {
			ItemStack using = player.getUseItem();

			if (using.is(Items.BOW)) {
				float power = BowItem.getPowerForTime(player.getTicksUsingItem());
				// The bow doesn't shoot at all below this.
				return power < 0.1F ? null : new Shot(Motion.ARROW, power * 3F, 0F, 0.05, 0.6);
			}

			if (using.is(Items.TRIDENT)) {
				// With Riptide, the player goes instead of the trident.
				return EnchantmentHelper.getTridentSpinAttackStrength(using, player) > 0F ? null : new Shot(Motion.ARROW, 2.5F, 0F, 0.05, 0.99);
			}
		}

		Shot main = held(player.getMainHandItem());
		return main != null ? main : held(player.getOffhandItem());
	}

	private static @Nullable Shot held(ItemStack stack) {
		if (stack.getItem() instanceof SnowballItem || stack.getItem() instanceof EggItem || stack.getItem() instanceof EnderpearlItem) {
			return new Shot(Motion.THROWN, 1.5F, 0F, 0.03, THROWN_WATER_DRAG);
		}

		if (stack.getItem() instanceof ThrowablePotionItem) {
			return new Shot(Motion.THROWN, 0.5F, -20F, 0.05, THROWN_WATER_DRAG);
		}

		if (stack.getItem() instanceof ExperienceBottleItem) {
			return new Shot(Motion.THROWN, 0.7F, -20F, 0.07, THROWN_WATER_DRAG);
		}

		if (stack.getItem() instanceof WindChargeItem) {
			return new Shot(Motion.STRAIGHT, 1.5F, 0F, 0, 1);
		}

		if (stack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(stack)) {
			ChargedProjectiles loaded = stack.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
			return loaded.contains(Items.FIREWORK_ROCKET) ? new Shot(Motion.STRAIGHT, 1.6F, 0F, 0, 1) : new Shot(Motion.ARROW, 3.15F, 0F, 0.05, 0.6);
		}

		return null;
	}

	private static Path simulate(Level level, Player player, Shot shot, float partialTick) {
		// Projectiles start just below the eyes, heading where the player looks, carried along by their movement.
		Vec3 pos = player.getEyePosition(partialTick).subtract(0, 0.1, 0);
		float xRot = player.getViewXRot(partialTick);
		float yRot = player.getViewYRot(partialTick);
		Vec3 velocity = new Vec3(
				-Mth.sin(yRot * DEGREES) * Mth.cos(xRot * DEGREES),
				-Mth.sin((xRot + shot.tilt()) * DEGREES),
				Mth.cos(yRot * DEGREES) * Mth.cos(xRot * DEGREES)
		).normalize().scale(shot.speed());
		Vec3 movement = player.getKnownMovement();
		velocity = velocity.add(movement.x, player.onGround() ? 0 : movement.y, movement.z);

		List<Vec3> points = new ArrayList<>();
		points.add(pos);
		int maxTicks = shot.motion() == Motion.STRAIGHT ? MAX_STRAIGHT_TICKS : MAX_TICKS;

		for (int tick = 0; tick < maxTicks && pos.y > level.getMinY() - 64; tick++) {
			boolean inWater = level.getFluidState(BlockPos.containing(pos)).is(FluidTags.WATER);

			if (shot.motion() == Motion.THROWN) {
				velocity = velocity.subtract(0, shot.gravity(), 0).scale(inWater ? shot.waterDrag() : AIR_DRAG);
			} else if (shot.motion() == Motion.ARROW && inWater) {
				velocity = velocity.scale(shot.waterDrag());
			}

			Vec3 next = pos.add(velocity);
			BlockHitResult block = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			Vec3 end = block.getType() == HitResult.Type.MISS ? next : block.getLocation();
			EntityHitResult entity = ProjectileUtil.getEntityHitResult(level, player, pos, end, new AABB(pos, end).inflate(1),
					target -> target != player && !target.isSpectator() && target.canBeHitByProjectile(), ENTITY_MARGIN);

			if (entity != null) {
				points.add(entity.getLocation());
				return new Path(points, entity.getLocation(), null, entity.getEntity());
			}

			points.add(end);

			if (block.getType() != HitResult.Type.MISS) {
				return new Path(points, end, block.getDirection(), null);
			}

			pos = next;

			if (shot.motion() == Motion.ARROW) {
				velocity = (inWater ? velocity : velocity.scale(AIR_DRAG)).subtract(0, shot.gravity(), 0);
			}
		}

		return new Path(points, null, null, null);
	}
}
