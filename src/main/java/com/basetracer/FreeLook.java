package com.basetracer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Detaches the camera from the player and flies it around. Entirely client-side: the player stands still and
 * nothing about the camera is sent to the server. Only touched on the client thread.
 */
public final class FreeLook {
	private static final float MOUSE_SCALE = 0.15F;
	private static final float SPRINT_MULTIPLIER = 3.0F;
	/** Limits how far one slow frame can move the camera. */
	private static final float MAX_FRAME_SECONDS = 0.1F;

	private static boolean active;
	private static LocalPlayer player;
	private static ClientInput playerInput;
	private static double x;
	private static double y;
	private static double z;
	private static float yaw;
	private static float pitch;
	private static long lastFrameNanos;
	private static int lastHurtTime;
	private static float lastHealth;

	private FreeLook() {
	}

	public static boolean isActive() {
		return active;
	}

	public static void toggle(Minecraft client) {
		if (active) {
			stop();
		} else if (client.player != null) {
			start(client.player);
		}
	}

	private static void start(LocalPlayer localPlayer) {
		active = true;
		player = localPlayer;
		Vec3 eyes = localPlayer.getEyePosition();
		x = eyes.x;
		y = eyes.y;
		z = eyes.z;
		yaw = localPlayer.getYRot();
		pitch = localPlayer.getXRot();
		lastFrameNanos = System.nanoTime();
		lastHurtTime = localPlayer.hurtTime;
		lastHealth = localPlayer.getHealth();

		// An empty input leaves the player standing still while the movement keys steer the camera.
		playerInput = localPlayer.input;
		localPlayer.input = new ClientInput();
	}

	private static void stop() {
		player.input = playerInput;
		active = false;
		player = null;
		playerInput = null;
	}

	/** Runs before the game handles its keybinds, so these actions never reach the player. */
	public static void tick(Minecraft client) {
		if (!active) {
			return;
		}

		// Respawning and changing dimension create a new player, and that one already has its own input.
		if (client.level == null || client.player != player) {
			active = false;
			player = null;
			playerInput = null;
			return;
		}

		// Comparing against the last tick ignores a hit from before free look started, and healing.
		boolean tookDamage = player.hurtTime > lastHurtTime || player.getHealth() < lastHealth;
		lastHurtTime = player.hurtTime;
		lastHealth = player.getHealth();

		if (tookDamage) {
			player.sendOverlayMessage(Component.translatable("message.basetracer.freeLook.damaged"));
			stop();
			return;
		}

		Options options = client.options;
		options.keyAttack.setDown(false);
		options.keyUse.setDown(false);
		options.keyPickItem.setDown(false);

		while (options.keyAttack.consumeClick()) {
		}

		while (options.keyUse.consumeClick()) {
		}

		while (options.keyPickItem.consumeClick()) {
		}

		while (options.keyDrop.consumeClick()) {
		}

		while (options.keySwapOffhand.consumeClick()) {
		}
	}

	public static void turn(double mouseX, double mouseY) {
		yaw += (float) mouseX * MOUSE_SCALE;
		pitch = Mth.clamp(pitch + (float) mouseY * MOUSE_SCALE, -90.0F, 90.0F);
	}

	/** Moves by real time rather than game ticks, so it stays smooth at any frame rate. */
	public static void advance(Minecraft client) {
		long now = System.nanoTime();
		float seconds = Math.min((now - lastFrameNanos) / 1.0E9F, MAX_FRAME_SECONDS);
		lastFrameNanos = now;

		if (client.gui.screen() != null) {
			return;
		}

		Options options = client.options;
		float forward = axis(options.keyUp.isDown(), options.keyDown.isDown());
		float strafe = axis(options.keyRight.isDown(), options.keyLeft.isDown());
		float vertical = axis(options.keyJump.isDown(), options.keyShift.isDown());

		if (forward == 0F && strafe == 0F && vertical == 0F) {
			return;
		}

		float yawRadians = yaw * Mth.DEG_TO_RAD;
		float pitchRadians = pitch * Mth.DEG_TO_RAD;
		float cosPitch = Mth.cos(pitchRadians);
		// Forward follows the view, including up and down. Strafing stays level.
		float moveX = -Mth.sin(yawRadians) * cosPitch * forward - Mth.cos(yawRadians) * strafe;
		float moveY = -Mth.sin(pitchRadians) * forward + vertical;
		float moveZ = Mth.cos(yawRadians) * cosPitch * forward - Mth.sin(yawRadians) * strafe;
		float length = Mth.sqrt(moveX * moveX + moveY * moveY + moveZ * moveZ);

		if (length > 0F) {
			float speed = BaseTracerClient.CONFIG.freeLookSpeed * (options.keySprint.isDown() ? SPRINT_MULTIPLIER : 1F);
			float distance = speed * seconds / length;
			x += moveX * distance;
			y += moveY * distance;
			z += moveZ * distance;
		}
	}

	private static float axis(boolean positive, boolean negative) {
		return (positive ? 1F : 0F) - (negative ? 1F : 0F);
	}

	public static Vec3 position() {
		return new Vec3(x, y, z);
	}

	public static float yaw() {
		return yaw;
	}

	public static float pitch() {
		return pitch;
	}
}
