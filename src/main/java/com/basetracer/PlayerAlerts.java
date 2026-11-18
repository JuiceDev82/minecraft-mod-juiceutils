package com.basetracer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;

/**
 * Says, just above the hotbar and with a ding, when another player comes into range and when one leaves it. In range
 * means the server is sending that player to this client, the same players the player highlights show.
 */
public final class PlayerAlerts {
	/** Citizens-style NPCs use version 2 UUIDs, which real accounts never have. */
	private static final int NPC_UUID_VERSION = 2;
	private static final float ARRIVE_PITCH = 1.8F;
	private static final float LEAVE_PITCH = 1.0F;

	/** The players in range at the last tick, by UUID, with their names. */
	private final Map<UUID, String> inRange = new LinkedHashMap<>();
	private @Nullable ClientLevel level;
	private @Nullable Component lastMessage;

	public void tick(Minecraft client) {
		LocalPlayer self = client.player;
		BaseTracerConfig config = BaseTracerClient.CONFIG;

		if (client.level == null || self == null || !config.enabled || !config.playerAlerts) {
			this.inRange.clear();
			this.level = null;
			return;
		}

		Map<UUID, String> now = new LinkedHashMap<>();

		for (Player player : client.level.players()) {
			if (player != self && player.getUUID().version() != NPC_UUID_VERSION) {
				now.put(player.getUUID(), player.getName().getString());
			}
		}

		// A new world or dimension: everyone left behind didn't walk off, so start over without a message.
		if (client.level != this.level) {
			this.level = client.level;
			this.inRange.clear();
		}

		boolean someoneArrived = now.keySet().stream().anyMatch(uuid -> !this.inRange.containsKey(uuid));
		Component message = this.update(now);

		if (message != null) {
			this.lastMessage = message;
			self.sendOverlayMessage(message);
			// Higher for arriving, lower for only leaving, so they can be told apart without looking.
			client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), someoneArrived ? ARRIVE_PITCH : LEAVE_PITCH, 0.6F));
		}
	}

	/** Takes the players now in range, and says who arrived and who left since the last call, or null for no change. */
	public @Nullable Component update(Map<UUID, String> now) {
		List<String> entered = new ArrayList<>();
		List<String> left = new ArrayList<>();

		for (Map.Entry<UUID, String> player : now.entrySet()) {
			if (!this.inRange.containsKey(player.getKey())) {
				entered.add(player.getValue());
			}
		}

		for (Map.Entry<UUID, String> player : this.inRange.entrySet()) {
			if (!now.containsKey(player.getKey())) {
				left.add(player.getValue());
			}
		}

		this.inRange.clear();
		this.inRange.putAll(now);
		return message(entered, left);
	}

	public static @Nullable Component message(List<String> entered, List<String> left) {
		if (entered.isEmpty() && left.isEmpty()) {
			return null;
		}

		MutableComponent message = Component.empty();

		if (!entered.isEmpty()) {
			message.append(Component.translatable("message.basetracer.playerAlert.entered", String.join(", ", entered)).withStyle(ChatFormatting.YELLOW));
		}

		if (!left.isEmpty()) {
			if (!entered.isEmpty()) {
				message.append(Component.literal("  ·  ").withStyle(ChatFormatting.DARK_GRAY));
			}

			message.append(Component.translatable("message.basetracer.playerAlert.left", String.join(", ", left)).withStyle(ChatFormatting.GRAY));
		}

		return message;
	}

	/** The last alert shown, for tests. */
	public @Nullable Component lastMessage() {
		return this.lastMessage;
	}
}
