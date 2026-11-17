package com.basetracer;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;

/**
 * Briefly lists what's switched on when joining a world with the mod on, and when the mod is turned on. Changing a
 * module or Free Look doesn't show it. Shown like the game's own overlay messages, just above where they appear, and
 * fades the same way. Turning the mod on in the settings shows it once they close.
 */
public final class ModuleSplash {
	private static final long SHOW_MILLIS = 5000L;
	private static final long FADE_MILLIS = 1000L;
	/** The game's overlay message sits 68 pixels above the bottom, so this stays clear of it. */
	private static final int BOTTOM_OFFSET = 88;
	private static final int SIDE_MARGIN = 40;
	private static final int LINE_HEIGHT = 12;
	/** Between the modules and the rules. */
	private static final int GROUP_GAP = 8;
	private static final Component SEPARATOR = Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY);

	/** Null outside a world, so that joining one counts as turning the mod on. */
	private Boolean lastEnabled;
	private List<Component> shown = List.of();
	private long shownAt;

	public void tick(Minecraft client) {
		// Starts over outside a world, so that joining one shows what's on.
		if (client.level == null) {
			this.lastEnabled = null;
			this.shown = List.of();
			return;
		}

		if (client.gui.screen() != null) {
			return;
		}

		BaseTracerConfig config = BaseTracerClient.CONFIG;

		// Waits for the loading screen to close on joining, as for any other screen.
		if (config.enabled && !Boolean.TRUE.equals(this.lastEnabled)) {
			this.shown = lines(config, FreeLook.isActive());
			this.shownAt = Util.getMillis();
		} else if (!config.enabled) {
			this.shown = List.of();
		}

		this.lastEnabled = config.enabled;
	}

	public boolean isShowing() {
		return !this.shown.isEmpty() && Util.getMillis() - this.shownAt < SHOW_MILLIS;
	}

	/** One line of the modules that are on, and one of the rules they're finding things with. */
	public static List<Component> lines(BaseTracerConfig config, boolean freeLook) {
		List<Component> modules = new ArrayList<>();

		if (config.showChunkHighlights) {
			modules.add(Component.translatable("options.basetracer.showChunkHighlights"));
		}

		if (config.showBlockHighlights) {
			modules.add(Component.translatable("options.basetracer.showBlockHighlights"));
		}

		if (config.showTracers) {
			modules.add(Component.translatable("options.basetracer.showTracers").append(" (").append(config.tracerMode.label()).append(")"));
		}

		if (config.showLabels) {
			modules.add(Component.translatable("options.basetracer.showLabels"));
		}

		if (config.showHostileMobs) {
			modules.add(Component.translatable("options.basetracer.showHostileMobs"));
		}

		if (config.mobWarning) {
			modules.add(Component.translatable("options.basetracer.mobWarning"));
		}

		if (config.showItems) {
			modules.add(Component.translatable("options.basetracer.showItems").append(" (").append(config.itemMode.label()).append(")"));
		}

		if (config.showPlayers) {
			modules.add(Component.translatable(config.showPlayerTracers ? "options.basetracer.splash.playersAndTracers" : "options.basetracer.showPlayers"));
		}

		if (config.xrayEnabled) {
			modules.add(Component.translatable("options.basetracer.section.xray"));
		}

		if (config.smartTool) {
			modules.add(Component.translatable("options.basetracer.smartTool"));
		}

		if (config.autoEat) {
			modules.add(Component.translatable("options.basetracer.autoEat"));
		}

		if (freeLook) {
			modules.add(Component.translatable("options.basetracer.section.freeLook"));
		}

		MutableComponent moduleLine = Component.translatable("options.basetracer.splash.title").withStyle(ChatFormatting.YELLOW).append(": ");

		if (modules.isEmpty()) {
			moduleLine.append(Component.translatable("options.basetracer.splash.none").withStyle(ChatFormatting.GRAY));
		} else {
			moduleLine.append(joined(modules, ChatFormatting.GREEN));
		}

		List<Component> lines = new ArrayList<>(List.of(moduleLine));

		if (config.showChunkHighlights || config.showBlockHighlights || config.showTracers || config.showLabels) {
			List<Component> rules = new ArrayList<>();

			for (BaseRule rule : config.rules) {
				// A rule with nothing chosen yet, like Custom at first, isn't looking for anything.
				if (rule.enabled && (rule.lightSources || !rule.blocks().isEmpty() || rule.hasEntities())) {
					rules.add(Component.literal(rule.name).withColor(rule.colorArgb() & 0xFFFFFF));
				}
			}

			lines.add(rules.isEmpty()
					? Component.translatable("options.basetracer.splash.noRules").withStyle(ChatFormatting.GRAY)
					: Component.translatable("options.basetracer.splash.rules").withStyle(ChatFormatting.GRAY).append(" ").append(joined(rules, null)));
		}

		return lines;
	}

	private static Component joined(List<Component> parts, ChatFormatting style) {
		MutableComponent joined = Component.empty();

		for (int i = 0; i < parts.size(); i++) {
			if (i > 0) {
				joined.append(SEPARATOR);
			}

			joined.append(style == null ? parts.get(i) : parts.get(i).copy().withStyle(style));
		}

		return joined;
	}

	public void render(GuiGraphicsExtractor graphics) {
		if (!this.isShowing()) {
			return;
		}

		long remaining = SHOW_MILLIS - (Util.getMillis() - this.shownAt);
		float opacity = Math.min(1F, remaining / (float) FADE_MILLIS);
		// Text below a small alpha isn't drawn at all, so it fades to that rather than to nothing.
		int color = Math.max(5, Math.round(255 * opacity)) << 24 | 0xFFFFFF;
		Font font = Minecraft.getInstance().font;
		List<List<FormattedCharSequence>> groups = new ArrayList<>();
		int height = -GROUP_GAP;

		for (Component line : this.shown) {
			List<FormattedCharSequence> wrapped = font.split(line, graphics.guiWidth() - SIDE_MARGIN * 2);
			groups.add(wrapped);
			height += wrapped.size() * LINE_HEIGHT + GROUP_GAP;
		}

		// Stacked upward, so the last line sits just above the overlay message.
		int y = graphics.guiHeight() - BOTTOM_OFFSET - height;

		for (List<FormattedCharSequence> group : groups) {
			for (FormattedCharSequence line : group) {
				graphics.centeredText(font, line, graphics.guiWidth() / 2, y, color);
				y += LINE_HEIGHT;
			}

			y += GROUP_GAP;
		}
	}
}
