package com.juiceclient.mixin;

import java.util.Map;
import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;

/** The boss bars the game has, for tests. */
@Mixin(BossHealthOverlay.class)
public interface BossHealthOverlayAccessor {
	@Accessor("events")
	Map<UUID, LerpingBossEvent> juiceclient$events();
}
