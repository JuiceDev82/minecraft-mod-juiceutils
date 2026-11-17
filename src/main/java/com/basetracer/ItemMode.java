package com.basetracer;

import net.minecraft.network.chat.Component;

/** Which items on the ground are highlighted. */
public enum ItemMode {
	ALL("all"),
	MOB_DROPS("mob_drops");

	private final Component label;

	ItemMode(String key) {
		this.label = Component.translatable("options.basetracer.itemMode." + key);
	}

	public Component label() {
		return this.label;
	}
}
