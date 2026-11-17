package com.basetracer;

import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Leaves only the chosen blocks in the world's meshes, fully lit. Every other block is drawn as air, so the chosen
 * ones show through it from any distance. Only drawing changes: the world itself, and what the player can walk
 * through or hit, stay the same.
 */
public final class XRay {
	// Read by the threads that build chunk meshes.
	private static volatile boolean active;
	private static volatile Set<?> shown = Set.of();

	private XRay() {
	}

	public static boolean isActive() {
		return active;
	}

	/** Whether a block is drawn as air. */
	public static boolean hides(BlockState state) {
		return active && !state.isAir() && !shown.contains(state.getBlock());
	}

	/** Rebuilds every chunk's mesh when X-Ray turns on or off, or its blocks change. */
	public static void update(Minecraft client) {
		BaseTracerConfig config = BaseTracerClient.CONFIG;
		boolean nowActive = config.enabled && config.xrayEnabled;
		Set<?> nowShown = config.xrayBlocks();

		if (nowActive != active || nowActive && nowShown != shown) {
			shown = nowShown;
			active = nowActive;

			if (client.level != null) {
				client.levelExtractor.allChanged();
			}
		}
	}
}
