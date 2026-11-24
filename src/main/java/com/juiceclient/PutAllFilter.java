package com.juiceclient;

import net.minecraft.network.chat.Component;

/** Which items Put All moves, going by the Put All item list. */
public enum PutAllFilter {
	OFF("off"),
	ONLY_LISTED("only"),
	EXCEPT_LISTED("except");

	private final Component label;

	PutAllFilter(String key) {
		this.label = Component.translatable("options.juiceclient.putAllFilter." + key);
	}

	public Component label() {
		return this.label;
	}

	public boolean allows(boolean listed) {
		return switch (this) {
			case OFF -> true;
			case ONLY_LISTED -> listed;
			case EXCEPT_LISTED -> !listed;
		};
	}
}
