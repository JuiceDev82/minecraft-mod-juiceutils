package com.basetracer;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;

/**
 * Changes apply immediately and are saved when the screen closes.
 */
public class BaseTracerConfigScreen extends OptionsSubScreen {
	private static final int MIN_LINE_WIDTH = 1;
	private static final int MAX_LINE_WIDTH = 10;
	private static final int MIN_FREE_LOOK_SPEED = 5;
	private static final int MAX_FREE_LOOK_SPEED = 100;
	private static final int VALID_COLOR = 0xFFE0E0E0;
	private static final int INVALID_COLOR = 0xFFFF5555;

	public BaseTracerConfigScreen(Screen lastScreen) {
		super(lastScreen, Minecraft.getInstance().options, Component.translatable("options.basetracer.title"));
	}

	@Override
	protected void addOptions() {
		BaseTracerConfig config = BaseTracerClient.CONFIG;

		this.list.addBig(OptionInstance.createBoolean("options.basetracer.enabled", config.enabled, value -> config.enabled = value));
		this.list.addSmall(
				Button.builder(Component.translatable("options.basetracer.export"), button -> this.exportSettings()).build(),
				Button.builder(
						Component.translatable("options.basetracer.import"),
						button -> this.minecraft.gui.setScreen(new SettingsImportScreen(this, this.lastScreen))
				).build()
		);

		this.addSection("options.basetracer.section.bases");
		this.list.addSmall(
				OptionInstance.createBoolean("options.basetracer.showChunkHighlights", config.showChunkHighlights, value -> config.showChunkHighlights = value),
				OptionInstance.createBoolean("options.basetracer.showBlockHighlights", config.showBlockHighlights, value -> config.showBlockHighlights = value)
		);
		this.list.addBig(new OptionInstance<>(
				"options.basetracer.fillOpacity",
				OptionInstance.noTooltip(),
				(caption, value) -> Options.genericValueLabel(caption, Component.translatable("options.basetracer.percent", value)),
				new OptionInstance.IntRange(0, 100, true),
				config.fillOpacity,
				value -> config.fillOpacity = value
		));
		this.list.addBig(new OptionInstance<>(
				"options.basetracer.maxBases",
				OptionInstance.noTooltip(),
				(caption, value) -> Options.genericValueLabel(caption, value > BaseTracerConfig.MAX_BASES_LIMIT
						? Component.translatable("options.basetracer.maxBases.all")
						: Component.literal(value.toString())),
				new OptionInstance.IntRange(1, BaseTracerConfig.MAX_BASES_LIMIT + 1, true),
				config.maxBases,
				value -> config.maxBases = value
		));
		this.list.addBig(Button.builder(
				Component.translatable("options.basetracer.rules"),
				button -> this.minecraft.gui.setScreen(new BaseTracerRulesScreen(this))
		).build());

		this.addSection("options.basetracer.section.tracers");
		this.list.addBig(OptionInstance.createBoolean("options.basetracer.showTracers", config.showTracers, value -> config.showTracers = value));
		this.list.addBig(CycleButton.builder(TracerMode::label, config.tracerMode)
				.withValues(TracerMode.values())
				.create(Component.translatable("options.basetracer.tracerMode"), (button, value) -> config.tracerMode = value));

		this.addSection("options.basetracer.section.labels");
		this.list.addBig(OptionInstance.createBoolean("options.basetracer.showLabels", config.showLabels, value -> config.showLabels = value));
		this.list.addSmall(
				OptionInstance.createBoolean("options.basetracer.showLabelDistance", config.showLabelDistance, value -> config.showLabelDistance = value),
				OptionInstance.createBoolean("options.basetracer.showLabelCoordinates", config.showLabelCoordinates, value -> config.showLabelCoordinates = value)
		);

		this.addSection("options.basetracer.section.mobs");
		this.list.addBig(OptionInstance.createBoolean("options.basetracer.showHostileMobs", config.showHostileMobs, value -> config.showHostileMobs = value));
		this.list.addBig(Button.builder(
				Component.translatable("options.basetracer.mobs"),
				button -> this.minecraft.gui.setScreen(new BaseTracerMobsScreen(this))
		).build());
		this.list.addSmall(
				OptionInstance.createBoolean("options.basetracer.mobWarning", config.mobWarning, value -> config.mobWarning = value),
				new OptionInstance<>(
						"options.basetracer.mobWarningDistance",
						OptionInstance.noTooltip(),
						(caption, value) -> Options.genericValueLabel(caption, Component.translatable("options.basetracer.blocks", value)),
						new OptionInstance.IntRange(BaseTracerConfig.MIN_MOB_WARNING_DISTANCE, BaseTracerConfig.MAX_MOB_WARNING_DISTANCE, true),
						config.mobWarningDistance,
						value -> config.mobWarningDistance = value
				)
		);
		addColorField(this.list, this.font, "options.basetracer.hostileMobColor", config.hostileMobColor, value -> config.hostileMobColor = value);

		this.addSection("options.basetracer.section.items");
		this.list.addSmall(
				CycleButton.onOffBuilder(config.showItems).create(Component.translatable("options.basetracer.showItems"), (button, value) -> config.showItems = value),
				CycleButton.builder(ItemMode::label, config.itemMode)
						.withValues(ItemMode.values())
						.create(Component.translatable("options.basetracer.itemMode"), (button, value) -> config.itemMode = value)
		);
		this.list.addBig(OptionInstance.createBoolean("options.basetracer.showItemLabels", config.showItemLabels, value -> config.showItemLabels = value));
		addColorField(this.list, this.font, "options.basetracer.itemColor", config.itemColor, value -> config.itemColor = value);

		this.addSection("options.basetracer.section.players");
		this.list.addSmall(
				OptionInstance.createBoolean("options.basetracer.showPlayers", config.showPlayers, value -> config.showPlayers = value),
				OptionInstance.createBoolean("options.basetracer.showPlayerTracers", config.showPlayerTracers, value -> config.showPlayerTracers = value)
		);
		this.list.addBig(OptionInstance.createBoolean("options.basetracer.playerAlerts", config.playerAlerts, value -> config.playerAlerts = value));
		addColorField(this.list, this.font, "options.basetracer.playerColor", config.playerColor, value -> config.playerColor = value);

		this.addSection("options.basetracer.section.xray");
		this.list.addSmall(
				CycleButton.onOffBuilder(config.xrayEnabled).create(Component.translatable("options.basetracer.xrayEnabled"), (button, value) -> config.xrayEnabled = value),
				Button.builder(
						Component.translatable("options.basetracer.xrayBlocks"),
						button -> this.minecraft.gui.setScreen(new BaseTracerXRayBlocksScreen(this))
				).build()
		);

		this.addSection("options.basetracer.section.nameTags");
		this.list.addSmall(
				OptionInstance.createBoolean("options.basetracer.nameTags", config.nameTags, value -> config.nameTags = value),
				new OptionInstance<>(
						"options.basetracer.nameTagRange",
						OptionInstance.noTooltip(),
						(caption, value) -> Options.genericValueLabel(caption, Component.translatable("options.basetracer.blocks", value)),
						new OptionInstance.IntRange(BaseTracerConfig.MIN_NAME_TAG_RANGE, BaseTracerConfig.MAX_NAME_TAG_RANGE, true),
						config.nameTagRange,
						value -> config.nameTagRange = value
				)
		);
		this.list.addSmall(
				Button.builder(
						Component.translatable("options.basetracer.nameTagEntities"),
						button -> this.minecraft.gui.setScreen(new NameTagEntitiesScreen(this))
				).build(),
				Button.builder(
						Component.translatable("options.basetracer.nameTagBlocks"),
						button -> this.minecraft.gui.setScreen(new NameTagBlocksScreen(this))
				).build()
		);

		this.addSection("options.basetracer.section.tools");
		this.addSubsection("options.basetracer.subsection.mining");
		this.list.addSmall(
				OptionInstance.createBoolean("options.basetracer.smartTool", config.smartTool, value -> config.smartTool = value),
				new OptionInstance<>(
						"options.basetracer.smartToolDelay",
						OptionInstance.noTooltip(),
						(caption, value) -> Options.genericValueLabel(caption, Component.translatable("options.basetracer.seconds", String.format(java.util.Locale.ROOT, "%.2f", value / 20.0))),
						new OptionInstance.IntRange(0, BaseTracerConfig.MAX_SMART_TOOL_DELAY, true),
						config.smartToolDelay,
						value -> config.smartToolDelay = value
				)
		);
		this.addSubsection("options.basetracer.subsection.food");
		this.list.addSmall(
				OptionInstance.createBoolean("options.basetracer.autoEat", config.autoEat, value -> config.autoEat = value),
				new OptionInstance<>(
						"options.basetracer.autoEatHunger",
						OptionInstance.noTooltip(),
						(caption, value) -> Options.genericValueLabel(caption, Component.translatable("options.basetracer.hunger", value)),
						new OptionInstance.IntRange(1, BaseTracerConfig.MAX_AUTO_EAT_HUNGER, true),
						config.autoEatHunger,
						value -> config.autoEatHunger = value
				)
		);
		this.list.addBig(new OptionInstance<>(
				"options.basetracer.autoEatHealth",
				OptionInstance.noTooltip(),
				(caption, value) -> Options.genericValueLabel(caption, value == 0
						? Component.translatable("options.off")
						: Component.translatable("options.basetracer.percent", value)),
				new OptionInstance.IntRange(0, BaseTracerConfig.MAX_AUTO_EAT_HEALTH, true),
				config.autoEatHealth,
				value -> config.autoEatHealth = value
		));
		this.list.addBig(OptionInstance.createBoolean(
				"options.basetracer.autoEatResupply",
				OptionInstance.cachedConstantTooltip(Component.translatable("options.basetracer.autoEatResupply.tooltip")),
				config.autoEatResupply,
				value -> config.autoEatResupply = value
		));
		this.addSubsection("options.basetracer.subsection.combat");
		this.list.addSmall(
				OptionInstance.createBoolean(
						"options.basetracer.autoWeapon",
						OptionInstance.cachedConstantTooltip(Component.translatable("options.basetracer.autoWeapon.tooltip")),
						config.autoWeapon,
						value -> config.autoWeapon = value
				),
				new OptionInstance<>(
						"options.basetracer.autoWeaponReturn",
						OptionInstance.noTooltip(),
						(caption, value) -> Options.genericValueLabel(caption, value == 0
								? Component.translatable("options.basetracer.autoWeaponReturn.never")
								: Component.translatable("options.basetracer.seconds", String.format(java.util.Locale.ROOT, "%.2f", value / 20.0))),
						new OptionInstance.IntRange(0, BaseTracerConfig.MAX_AUTO_WEAPON_RETURN, true),
						config.autoWeaponReturn,
						value -> config.autoWeaponReturn = value
				)
		);
		this.list.addSmall(
				OptionInstance.createBoolean("options.basetracer.autoTotem", config.autoTotem, value -> config.autoTotem = value),
				new OptionInstance<>(
						"options.basetracer.autoTotemDelay",
						OptionInstance.noTooltip(),
						(caption, value) -> Options.genericValueLabel(caption, value == 0
								? Component.translatable("options.basetracer.takeAllDelay.instant")
								: Component.translatable("options.basetracer.seconds", String.format(java.util.Locale.ROOT, "%.2f", value / 20.0))),
						new OptionInstance.IntRange(0, BaseTracerConfig.MAX_AUTO_TOTEM_DELAY, true),
						config.autoTotemDelay,
						value -> config.autoTotemDelay = value
				)
		);
		this.list.addBig(new OptionInstance<>(
				"options.basetracer.autoTotemHealth",
				OptionInstance.cachedConstantTooltip(Component.translatable("options.basetracer.autoTotemHealth.tooltip")),
				(caption, value) -> Options.genericValueLabel(caption, value == 0
						? Component.translatable("options.off")
						: Component.translatable("options.basetracer.hearts", value)),
				new OptionInstance.IntRange(0, BaseTracerConfig.MAX_AUTO_TOTEM_HEALTH, true),
				config.autoTotemHealth,
				value -> config.autoTotemHealth = value
		));
		// The Quick Use key itself is set in Controls, with the others.
		this.list.addSmall(
				OptionInstance.createBoolean(
						"options.basetracer.autoArmor",
						OptionInstance.cachedConstantTooltip(Component.translatable("options.basetracer.autoArmor.tooltip")),
						config.autoArmor,
						value -> config.autoArmor = value
				).createButton(this.minecraft.options),
				CycleButton.builder(QuickUseAction::label, config.quickUseAction)
						.withValues(QuickUseAction.values())
						.create(Component.translatable("options.basetracer.quickUseAction"), (button, value) -> config.quickUseAction = value)
		);
		this.addSubsection("options.basetracer.subsection.storage");
		this.list.addSmall(
				OptionInstance.createBoolean("options.basetracer.takeAll", config.takeAll, value -> config.takeAll = value),
				new OptionInstance<>(
						"options.basetracer.takeAllDelay",
						OptionInstance.noTooltip(),
						(caption, value) -> Options.genericValueLabel(caption, value == 0
								? Component.translatable("options.basetracer.takeAllDelay.instant")
								: Component.translatable("options.basetracer.seconds", String.format(java.util.Locale.ROOT, "%.2f", value / 20.0))),
						new OptionInstance.IntRange(0, BaseTracerConfig.MAX_TAKE_ALL_DELAY, true),
						config.takeAllDelay,
						value -> config.takeAllDelay = value
				)
		);
		this.list.addBig(OptionInstance.createBoolean("options.basetracer.putAllHotbar", config.putAllHotbar, value -> config.putAllHotbar = value));
		this.list.addSmall(
				CycleButton.builder(PutAllFilter::label, config.putAllFilter)
						.withValues(PutAllFilter.values())
						.create(Component.translatable("options.basetracer.putAllFilter"), (button, value) -> config.putAllFilter = value),
				Button.builder(
						Component.translatable("options.basetracer.putAllItems"),
						button -> this.minecraft.gui.setScreen(new PutAllItemsScreen(this))
				).build()
		);
		this.list.addSmall(
				OptionInstance.createBoolean(
						"options.basetracer.sortButtons",
						OptionInstance.cachedConstantTooltip(Component.translatable("options.basetracer.sortButtons.tooltip")),
						config.sortButtons,
						value -> config.sortButtons = value
				).createButton(this.minecraft.options),
				CycleButton.builder(SortMode::label, config.sortMode)
						.withValues(SortMode.values())
						.create(Component.translatable("options.basetracer.sortMode"), (button, value) -> config.sortMode = value)
		);
		this.list.addSmall(
				new OptionInstance<>(
						"options.basetracer.sortDelay",
						OptionInstance.noTooltip(),
						(caption, value) -> Options.genericValueLabel(caption, value == 0
								? Component.translatable("options.basetracer.takeAllDelay.instant")
								: Component.translatable("options.basetracer.seconds", String.format(java.util.Locale.ROOT, "%.2f", value / 20.0))),
						new OptionInstance.IntRange(0, BaseTracerConfig.MAX_SORT_DELAY, true),
						config.sortDelay,
						value -> config.sortDelay = value
				).createButton(this.minecraft.options),
				Button.builder(
						Component.translatable("options.basetracer.sortPriorities"),
						button -> this.minecraft.gui.setScreen(new SortPriorityScreen(this))
				).tooltip(Tooltip.create(Component.translatable("options.basetracer.sortPriorities.tooltip"))).build()
		);
		this.list.addBig(Button.builder(
				Component.translatable("options.basetracer.sortGroups"),
				button -> this.minecraft.gui.setScreen(new SortGroupsScreen(this))
		).tooltip(Tooltip.create(Component.translatable("options.basetracer.sortGroups.tooltip"))).build());

		this.addSection("options.basetracer.section.appearance");
		this.list.addBig(new OptionInstance<>(
				"options.basetracer.lineWidth",
				OptionInstance.noTooltip(),
				(caption, value) -> Options.genericValueLabel(caption, Component.literal(value.toString())),
				new OptionInstance.IntRange(MIN_LINE_WIDTH, MAX_LINE_WIDTH, true),
				Mth.clamp(Math.round(config.lineWidth), MIN_LINE_WIDTH, MAX_LINE_WIDTH),
				value -> config.lineWidth = value
		));

		this.addSection("options.basetracer.section.freeLook");
		this.list.addBig(new OptionInstance<>(
				"options.basetracer.freeLookSpeed",
				OptionInstance.noTooltip(),
				(caption, value) -> Options.genericValueLabel(caption, Component.translatable("options.basetracer.freeLookSpeed.value", value)),
				new OptionInstance.IntRange(MIN_FREE_LOOK_SPEED, MAX_FREE_LOOK_SPEED, true),
				Mth.clamp(Math.round(config.freeLookSpeed), MIN_FREE_LOOK_SPEED, MAX_FREE_LOOK_SPEED),
				value -> config.freeLookSpeed = value
		));
	}

	/** Styled apart from the headers above color fields, which use the same kind of row. */
	/** A group within a section, quieter than a section's title. */
	private void addSubsection(String key) {
		this.list.addHeader(Component.translatable(key).withStyle(ChatFormatting.GRAY, ChatFormatting.UNDERLINE));
	}

	/** A section's title, with the key that toggles it beside it, if it has one, so shortcuts can be seen at a glance. */
	private void addSection(String key) {
		MutableComponent header = Component.translatable(key).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
		KeyMapping shortcut = sectionKey(this.minecraft.options, key);

		if (shortcut != null) {
			header.append(Component.literal("  ").append(sectionKeyLabel(shortcut)).withStyle(style -> style.withBold(false)));
		}

		this.list.addHeader(header);
	}

	/** The key that toggles a section, from the section's title key, or null for a section with none. */
	public static @Nullable KeyMapping sectionKey(Options options, String sectionKey) {
		String id = sectionKey.substring(sectionKey.lastIndexOf('.') + 1);
		String name = switch (id) {
			case "xray" -> "key.basetracer.xray";
			case "freeLook" -> "key.basetracer.freeLook";
			default -> "key.basetracer.section." + id;
		};

		for (KeyMapping mapping : options.keyMappings) {
			if (mapping.getName().equals(name)) {
				return mapping;
			}
		}

		return null;
	}

	public static Component sectionKeyLabel(KeyMapping mapping) {
		return mapping.isUnbound()
				? Component.translatable("options.basetracer.sectionKey.none").withStyle(ChatFormatting.DARK_GRAY)
				: Component.translatable("options.basetracer.sectionKey", mapping.getTranslatedKeyMessage()).withStyle(ChatFormatting.GRAY);
	}

	static void addColorField(OptionsList list, Font font, String key, String initialValue, Consumer<String> setter) {
		Component label = Component.translatable(key);
		EditBox box = new EditBox(font, 150, 20, label);
		box.setMaxLength(8);
		box.setValue(initialValue);
		box.setResponder(value -> {
			boolean valid = BaseTracerConfig.isValidColor(value);
			box.setTextColor(valid ? VALID_COLOR : INVALID_COLOR);

			if (valid) {
				setter.accept(value);
				BaseTracerClient.CONFIG.refreshColors();
			}
		});

		list.addHeader(label);
		list.addSmall(box, new ColorSwatchWidget(150, 20, label, box::getValue));
	}

	private void exportSettings() {
		BaseTracerClient.CONFIG.save();

		try {
			java.nio.file.Path file = SettingsTransfer.export();
			net.minecraft.client.gui.components.toasts.SystemToast.addOrUpdate(this.minecraft.gui.toastManager(),
					net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
					Component.translatable("options.basetracer.export.done"), Component.literal(file.getFileName().toString()));
		} catch (java.io.IOException e) {
			BaseTracerClient.LOGGER.error("Failed to export settings", e);
			net.minecraft.client.gui.components.toasts.SystemToast.addOrUpdate(this.minecraft.gui.toastManager(),
					net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
					Component.translatable("options.basetracer.export.failed").withStyle(ChatFormatting.RED), Component.literal(String.valueOf(e.getMessage())));
		}
	}

	@Override
	public void removed() {
		super.removed();
		BaseTracerClient.CONFIG.save();
	}
}
