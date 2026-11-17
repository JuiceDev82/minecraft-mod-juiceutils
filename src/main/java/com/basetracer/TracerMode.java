package com.basetracer;

import net.minecraft.network.chat.Component;

public enum TracerMode {
	EVERY_SOURCE("every_source"),
	NEAREST_PER_CHUNK("nearest_per_chunk"),
	NEAREST_CHUNK("nearest_chunk"),
	MOST_SOURCES("most_sources");

	private final Component label;

	TracerMode(String key) {
		this.label = Component.translatable("options.basetracer.tracerMode." + key);
	}

	public Component label() {
		return this.label;
	}
}
