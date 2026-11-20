package com.basetracer;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import net.minecraft.network.chat.Component;

/**
 * A key, unbound at first, that turns a whole section of the settings on or off. Off remembers which of the section's
 * switches were on, so that on brings back just those rather than everything.
 */
public enum SectionToggle {
	BASES("bases",
			flag(config -> config.showChunkHighlights, (config, value) -> config.showChunkHighlights = value),
			flag(config -> config.showBlockHighlights, (config, value) -> config.showBlockHighlights = value)),
	TRACERS("tracers",
			flag(config -> config.showTracers, (config, value) -> config.showTracers = value)),
	LABELS("labels",
			flag(config -> config.showLabels, (config, value) -> config.showLabels = value)),
	MOBS("mobs",
			flag(config -> config.showHostileMobs, (config, value) -> config.showHostileMobs = value),
			flag(config -> config.mobWarning, (config, value) -> config.mobWarning = value)),
	PLAYERS("players",
			flag(config -> config.showPlayers, (config, value) -> config.showPlayers = value),
			flag(config -> config.playerAlerts, (config, value) -> config.playerAlerts = value)),
	ITEMS("items",
			flag(config -> config.showItems, (config, value) -> config.showItems = value)),
	NAME_TAGS("nameTags",
			flag(config -> config.nameTags, (config, value) -> config.nameTags = value)),
	TOOLS("tools",
			flag(config -> config.smartTool, (config, value) -> config.smartTool = value),
			flag(config -> config.autoEat, (config, value) -> config.autoEat = value),
			flag(config -> config.autoTotem, (config, value) -> config.autoTotem = value),
			flag(config -> config.autoArmor, (config, value) -> config.autoArmor = value),
			flag(config -> config.autoWeapon, (config, value) -> config.autoWeapon = value),
			flag(config -> config.takeAll, (config, value) -> config.takeAll = value));

	private record Flag(Predicate<BaseTracerConfig> get, BiConsumer<BaseTracerConfig, Boolean> set) {
	}

	private final String id;
	private final List<Flag> flags;
	/** Which switches were on when the section was last turned off. */
	private boolean[] remembered;

	SectionToggle(String id, Flag... flags) {
		this.id = id;
		this.flags = List.of(flags);
	}

	private static Flag flag(Predicate<BaseTracerConfig> get, BiConsumer<BaseTracerConfig, Boolean> set) {
		return new Flag(get, set);
	}

	public String keyName() {
		return "key.basetracer.section." + this.id;
	}

	public Component label() {
		return Component.translatable("options.basetracer.section." + this.id);
	}

	/** Whether any of the section's switches is on. */
	public boolean isOn(BaseTracerConfig config) {
		return this.flags.stream().anyMatch(flag -> flag.get().test(config));
	}

	/** Turns the section off if any of it is on, otherwise back on. Returns whether it's now on. */
	public boolean toggle(BaseTracerConfig config) {
		if (this.isOn(config)) {
			this.remembered = new boolean[this.flags.size()];

			for (int i = 0; i < this.flags.size(); i++) {
				this.remembered[i] = this.flags.get(i).get().test(config);
				this.flags.get(i).set().accept(config, false);
			}

			return false;
		}

		for (int i = 0; i < this.flags.size(); i++) {
			// Nothing remembered, such as after a restart: everything on.
			this.flags.get(i).set().accept(config, this.remembered == null || this.remembered[i]);
		}

		this.remembered = null;
		return true;
	}

	/** Forgets what each section had on, for tests. */
	public static void forgetAll() {
		for (SectionToggle section : values()) {
			section.remembered = null;
		}
	}
}
