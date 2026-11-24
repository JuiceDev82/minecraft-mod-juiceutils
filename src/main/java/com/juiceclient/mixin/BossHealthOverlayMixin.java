package com.juiceclient.mixin;

import java.util.Collection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;

import com.juiceclient.BossBarStack;

@Mixin(BossHealthOverlay.class)
public class BossHealthOverlayMixin {
	// Bars with the same name are drawn as one.
	@ModifyExpressionValue(method = "extractRenderState", at = @At(value = "INVOKE", target = "Ljava/util/Map;values()Ljava/util/Collection;"))
	private Collection<LerpingBossEvent> juiceclient$stack(Collection<LerpingBossEvent> bars) {
		return BossBarStack.stack(bars);
	}

	// That one says how many it stands for.
	@ModifyExpressionValue(method = "extractRenderState",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/LerpingBossEvent;getName()Lnet/minecraft/network/chat/Component;"))
	private Component juiceclient$count(Component name, @Local LerpingBossEvent bar) {
		return BossBarStack.name(bar, name);
	}
}
