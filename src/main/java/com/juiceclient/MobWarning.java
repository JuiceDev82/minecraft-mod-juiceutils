package com.juiceclient;

import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.phys.Vec3;

/**
 * Warns, with a message and a ding, when a listed mob comes closer while the player stands still. Uses the same
 * mob list as the highlights, whether or not those are on.
 */
public final class MobWarning {
	/** How often mobs are checked, and so how far back "getting closer" looks. */
	private static final int CHECK_INTERVAL_TICKS = 5;
	/** Standing still for this long counts as not moving. */
	private static final int STILL_TICKS = 20;
	/** Less than this per tick is standing still, so being nudged or bobbing in water doesn't count as moving. */
	private static final double STILL_DISTANCE = 0.05;
	/** A mob has to come this much closer between checks, so one milling about in place doesn't count. */
	private static final double APPROACH_DISTANCE = 0.1;
	/** Dings at most this often, however many mobs come. */
	private static final long DING_COOLDOWN_MILLIS = 3000L;

	private Vec3 lastPlayerPos;
	private int stillTicks;
	private int tickCount;
	/** Each nearby mob's distance at the last check. */
	private final Int2DoubleMap lastDistances = new Int2DoubleOpenHashMap();
	/** Mobs already dinged for, until they leave the range or the player moves. */
	private final IntSet warned = new IntOpenHashSet();
	private long lastDing;
	private int dings;
	private Component lastMessage;

	public void tick(Minecraft client) {
		LocalPlayer player = client.player;
		JuiceClientConfig config = JuiceClient.CONFIG;

		if (client.level == null || player == null || !config.enabled || !config.mobWarning) {
			this.reset();
			return;
		}

		Vec3 pos = player.position();
		boolean moved = this.lastPlayerPos != null && pos.distanceToSqr(this.lastPlayerPos) > STILL_DISTANCE * STILL_DISTANCE;
		this.lastPlayerPos = pos;

		if (moved) {
			this.stillTicks = 0;
			this.lastDistances.clear();
			this.warned.clear();
			return;
		}

		this.stillTicks++;

		if (++this.tickCount % CHECK_INTERVAL_TICKS != 0) {
			return;
		}

		double range = config.mobWarningDistance;
		Int2DoubleMap distances = new Int2DoubleOpenHashMap();
		Entity nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		int approaching = 0;
		boolean newMob = false;

		for (Entity entity : client.level.entitiesForRendering()) {
			if (!(entity instanceof LivingEntity living) || !living.isAlive() || !config.isMobHighlighted(entity.getType()) || isPassive(entity)) {
				continue;
			}

			double distance = entity.position().distanceTo(pos);

			if (distance > range) {
				continue;
			}

			distances.put(entity.getId(), distance);

			if (this.stillTicks >= STILL_TICKS && isApproaching(this.lastDistances.getOrDefault(entity.getId(), Double.NaN), distance)) {
				approaching++;
				newMob |= this.warned.add(entity.getId());

				if (distance < nearestDistance) {
					nearestDistance = distance;
					nearest = entity;
				}
			}
		}

		this.lastDistances.clear();
		this.lastDistances.putAll(distances);
		// Mobs that left the range warn again if they come back.
		this.warned.removeIf(id -> !distances.containsKey(id));

		if (nearest == null) {
			return;
		}

		this.lastMessage = message(nearest.getDisplayName(), (int) Math.round(nearestDistance), approaching - 1);
		player.sendOverlayMessage(this.lastMessage);

		long now = Util.getMillis();

		if (newMob && now - this.lastDing >= DING_COOLDOWN_MILLIS) {
			this.lastDing = now;
			this.dings++;
			// A deep, slowed down Warden heartbeat, so a mob coming close sounds like a threat rather than a notification.
			client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 0.6F, 1.0F));
		}
	}

	/** Whether a mob came closer since the last check. A mob not seen at the last check hasn't yet. */
	/**
	 * Piglins and zombified piglins leave the player alone unless angered, so only count once they are. Whether a mob
	 * is attacking is all the server tells the client of its mood: it's what raises their arms.
	 */
	public static boolean isPassive(Entity entity) {
		return (entity instanceof Piglin || entity instanceof ZombifiedPiglin) && !((Mob) entity).isAggressive();
	}

	public static boolean isApproaching(double lastDistance, double distance) {
		return !Double.isNaN(lastDistance) && lastDistance - distance >= APPROACH_DISTANCE;
	}

	public static Component message(Component mob, int distance, int others) {
		Component text = others > 0
				? Component.translatable("message.juiceclient.mobWarning.more", mob, distance, others)
				: Component.translatable("message.juiceclient.mobWarning", mob, distance);
		return text.copy().withStyle(ChatFormatting.RED);
	}

	/** How many times it has dinged, for tests. */
	public int dings() {
		return this.dings;
	}

	/** The last warning shown, for tests. */
	public Component lastMessage() {
		return this.lastMessage;
	}

	private void reset() {
		this.lastPlayerPos = null;
		this.stillTicks = 0;
		this.lastDistances.clear();
		this.warned.clear();
	}
}
