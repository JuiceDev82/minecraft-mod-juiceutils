package com.basetracer;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Briefly shows what's switched on, a column per section, when joining a world with the mod on, and when the mod is turned on. Changing a
 * module or Free Look doesn't show it. Shown like the game's own overlay messages, just above where they appear, and
 * fades the same way. Turning the mod on in the settings shows it once they close.
 */
public final class ModuleSplash {
	private static final long SHOW_MILLIS = 5000L;
	private static final long FADE_MILLIS = 1000L;
	/** The game's overlay message sits 68 pixels above the bottom, so this stays clear of it. */
	private static final int BOTTOM_OFFSET = 88;
	private static final int SIDE_MARGIN = 20;
	private static final int LINE_HEIGHT = 11;
	/** The sections are drawn smaller than the title, so that they fit side by side. */
	private static final float SCALE = 0.65F;
	private static final int COLUMN_GAP = 24;
	private static final int ROW_GAP = 6;
	private static final int TITLE_GAP = 3;
	private static final int PADDING = 5;
	private static final int BACKGROUND_ALPHA = 0x90;
	private static final Component TITLE = Component.translatable("options.basetracer.splash.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
	private static final Component NONE = Component.translatable("options.basetracer.splash.none").withStyle(ChatFormatting.GRAY);

	/** Null outside a world, so that joining one counts as turning the mod on. */
	private Boolean lastEnabled;
	private List<Group> shown = List.of();
	private boolean showing;
	private long shownAt;

	public void tick(Minecraft client) {
		// Starts over outside a world, so that joining one shows what's on.
		if (client.level == null) {
			this.lastEnabled = null;
			this.shown = List.of();
			this.showing = false;
			return;
		}

		if (client.gui.screen() != null) {
			return;
		}

		BaseTracerConfig config = BaseTracerClient.CONFIG;

		// Waits for the loading screen to close on joining, as for any other screen.
		if (config.enabled && !Boolean.TRUE.equals(this.lastEnabled)) {
			this.shown = groups(config, FreeLook.isActive());
			this.showing = true;
			this.shownAt = Util.getMillis();
		} else if (!config.enabled) {
			this.shown = List.of();
			this.showing = false;
		}

		this.lastEnabled = config.enabled;
	}

	public boolean isShowing() {
		return this.showing && Util.getMillis() - this.shownAt < SHOW_MILLIS;
	}

	/** A column of the splash: a section's title over what's on in it. */
	public record Group(Component title, List<Component> entries) {
	}

	/** What's on, by section, leaving out sections with nothing on. Empty when nothing at all is on. */
	public static List<Group> groups(BaseTracerConfig config, boolean freeLook) {
		List<Group> groups = new ArrayList<>();

		List<Component> bases = new ArrayList<>();
		addIf(bases, config.showChunkHighlights, Component.translatable("options.basetracer.showChunkHighlights"));
		addIf(bases, config.showBlockHighlights, Component.translatable("options.basetracer.showBlockHighlights"));
		addIf(bases, config.showTracers, Component.translatable("options.basetracer.showTracers"));
		addIf(bases, config.showLabels, Component.translatable("options.basetracer.showLabels"));
		addGroup(groups, "bases", bases, ChatFormatting.GREEN);

		if (!bases.isEmpty()) {
			List<Component> rules = new ArrayList<>();

			for (BaseRule rule : config.rules) {
				// A rule with nothing chosen yet, like Custom at first, isn't looking for anything.
				if (rule.enabled && (rule.lightSources || !rule.blocks().isEmpty() || rule.hasEntities())) {
					rules.add(Component.literal(rule.name).withColor(rule.colorArgb() & 0xFFFFFF));
				}
			}

			if (rules.isEmpty()) {
				rules.add(Component.translatable("options.basetracer.splash.noRules").withStyle(ChatFormatting.GRAY));
			}

			addGroup(groups, "rules", rules, null);
		}

		List<Component> mobs = new ArrayList<>();
		addIf(mobs, config.showHostileMobs, Component.translatable("options.basetracer.showHostileMobs"));
		addIf(mobs, config.mobWarning, Component.translatable("options.basetracer.mobWarning"));
		addIf(mobs, config.showPlayers, Component.translatable(config.showPlayerTracers ? "options.basetracer.splash.playersAndTracers" : "options.basetracer.showPlayers"));
		addIf(mobs, config.playerAlerts, Component.translatable("options.basetracer.playerAlerts"));
		addGroup(groups, "mobs", mobs, ChatFormatting.GREEN);

		List<Component> items = new ArrayList<>();
		addIf(items, config.showItems, Component.translatable("options.basetracer.showItems").append(" (").append(config.itemMode.label()).append(")"));
		addIf(items, config.nameTags && (!config.nameTagEntities().isEmpty() || !config.nameTagBlocks().isEmpty()),
				Component.translatable("options.basetracer.section.nameTags"));
		addGroup(groups, "items", items, ChatFormatting.GREEN);

		List<Component> view = new ArrayList<>();
		addIf(view, config.xrayEnabled, Component.translatable("options.basetracer.section.xray"));
		addIf(view, freeLook, Component.translatable("options.basetracer.section.freeLook"));
		addGroup(groups, "view", view, ChatFormatting.GREEN);

		List<Component> tools = new ArrayList<>();
		addIf(tools, config.smartTool, Component.translatable("options.basetracer.smartTool"));
		addIf(tools, config.autoEat, Component.translatable("options.basetracer.autoEat"));
		addIf(tools, config.autoTotem, Component.translatable("options.basetracer.autoTotem"));
		addIf(tools, config.autoArmor, Component.translatable("options.basetracer.autoArmor"));
		addIf(tools, config.autoWeapon, Component.translatable("options.basetracer.autoWeapon"));
		addIf(tools, config.sortButtons, Component.translatable("options.basetracer.sort.button"));
		addGroup(groups, "tools", tools, ChatFormatting.GREEN);

		return groups;
	}

	private static void addIf(List<Component> entries, boolean on, Component entry) {
		if (on) {
			entries.add(entry);
		}
	}

	private static void addGroup(List<Group> groups, String id, List<Component> entries, ChatFormatting style) {
		if (entries.isEmpty()) {
			return;
		}

		List<Component> styled = entries.stream().map(entry -> style == null ? entry : (Component) entry.copy().withStyle(style)).toList();
		groups.add(new Group(Component.translatable("options.basetracer.splash.group." + id).withStyle(ChatFormatting.YELLOW, ChatFormatting.UNDERLINE), styled));
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
		int maxWidth = Math.round((graphics.guiWidth() - SIDE_MARGIN * 2) / SCALE);

		// Columns side by side, starting a new row of them when the screen is too narrow. Sizes here are unscaled.
		List<List<Group>> rows = new ArrayList<>();
		List<int[]> rowSizes = new ArrayList<>();
		List<Group> row = new ArrayList<>();
		int rowWidth = 0;
		int rowLines = 0;

		for (Group group : this.shown) {
			int width = columnWidth(font, group);

			if (!row.isEmpty() && rowWidth + COLUMN_GAP + width > maxWidth) {
				rows.add(row);
				rowSizes.add(new int[] {rowWidth, rowLines});
				row = new ArrayList<>();
				rowWidth = 0;
				rowLines = 0;
			}

			rowWidth += (row.isEmpty() ? 0 : COLUMN_GAP) + width;
			rowLines = Math.max(rowLines, group.entries().size() + 1);
			row.add(group);
		}

		if (!row.isEmpty()) {
			rows.add(row);
			rowSizes.add(new int[] {rowWidth, rowLines});
		}

		int blockWidth = font.width(TITLE);
		int blockHeight = LINE_HEIGHT + TITLE_GAP;

		for (int[] size : rowSizes) {
			blockWidth = Math.max(blockWidth, Math.round(size[0] * SCALE));
			blockHeight += Math.round(size[1] * LINE_HEIGHT * SCALE) + ROW_GAP;
		}

		if (this.shown.isEmpty()) {
			blockWidth = Math.max(blockWidth, font.width(NONE));
			blockHeight += LINE_HEIGHT;
		} else {
			blockHeight -= ROW_GAP;
		}

		int centerX = graphics.guiWidth() / 2;
		// Sits just above the overlay message.
		int top = graphics.guiHeight() - BOTTOM_OFFSET - blockHeight;
		int backgroundAlpha = Math.round(BACKGROUND_ALPHA * opacity);
		graphics.fill(centerX - blockWidth / 2 - PADDING, top - PADDING, centerX + (blockWidth + 1) / 2 + PADDING, top + blockHeight + PADDING, backgroundAlpha << 24);

		graphics.centeredText(font, TITLE, centerX, top, color);
		int y = top + LINE_HEIGHT + TITLE_GAP;

		if (this.shown.isEmpty()) {
			graphics.centeredText(font, NONE, centerX, y, color);
			return;
		}

		for (int r = 0; r < rows.size(); r++) {
			int[] size = rowSizes.get(r);
			graphics.pose().pushMatrix();
			graphics.pose().translate(centerX - size[0] * SCALE / 2F, y);
			graphics.pose().scale(SCALE);
			int x = 0;

			for (Group group : rows.get(r)) {
				graphics.text(font, group.title(), x, 0, color);
				int lineY = LINE_HEIGHT;

				for (Component entry : group.entries()) {
					graphics.text(font, entry, x, lineY, color);
					lineY += LINE_HEIGHT;
				}

				x += columnWidth(font, group) + COLUMN_GAP;
			}

			graphics.pose().popMatrix();
			y += Math.round(size[1] * LINE_HEIGHT * SCALE) + ROW_GAP;
		}
	}

	private static int columnWidth(Font font, Group group) {
		int width = font.width(group.title());

		for (Component entry : group.entries()) {
			width = Math.max(width, font.width(entry));
		}

		return width;
	}
}
