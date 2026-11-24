package com.juiceclient;

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
public class JuiceClientConfigScreen extends OptionsSubScreen {
	private static final int MIN_LINE_WIDTH = 1;
	private static final int MAX_LINE_WIDTH = 10;
	private static final int MIN_FREE_LOOK_SPEED = 5;
	private static final int MAX_FREE_LOOK_SPEED = 100;
	private static final int VALID_COLOR = 0xFFE0E0E0;
	private static final int INVALID_COLOR = 0xFFFF5555;

	public JuiceClientConfigScreen(Screen lastScreen) {
		super(lastScreen, Minecraft.getInstance().options, Component.translatable("options.juiceclient.title"));
	}

	@Override
	protected void addOptions() {
		JuiceClientConfig config = JuiceClient.CONFIG;

		this.list.addBig(OptionInstance.createBoolean("options.juiceclient.enabled", config.enabled, value -> config.enabled = value));
		this.list.addSmall(
				Button.builder(Component.translatable("options.juiceclient.export"), button -> this.exportSettings()).build(),
				Button.builder(
						Component.translatable("options.juiceclient.import"),
						button -> this.minecraft.gui.setScreen(new SettingsImportScreen(this, this.lastScreen))
				).build()
		);

		this.addSection("options.juiceclient.section.bases");
		this.list.addSmall(
				OptionInstance.createBoolean("options.juiceclient.showChunkHighlights", config.showChunkHighlights, value -> config.showChunkHighlights = value),
				OptionInstance.createBoolean("options.juiceclient.showBlockHighlights", config.showBlockHighlights, value -> config.showBlockHighlights = value)
		);
		this.list.addBig(new OptionInstance<>(
				"options.juiceclient.fillOpacity",
				OptionInstance.noTooltip(),
				(caption, value) -> Options.genericValueLabel(caption, Component.translatable("options.juiceclient.percent", value)),
				new OptionInstance.IntRange(0, 100, true),
				config.fillOpacity,
				value -> config.fillOpacity = value
		));
		this.list.addBig(new OptionInstance<>(
				"options.juiceclient.maxBases",
				OptionInstance.noTooltip(),
				(caption, value) -> Options.genericValueLabel(caption, value > JuiceClientConfig.MAX_BASES_LIMIT
						? Component.translatable("options.juiceclient.maxBases.all")
						: Component.literal(value.toString())),
				new OptionInstance.IntRange(1, JuiceClientConfig.MAX_BASES_LIMIT + 1, true),
				config.maxBases,
				value -> config.maxBases = value
		));
		this.list.addBig(Button.builder(
				Component.translatable("options.juiceclient.rules"),
				button -> this.minecraft.gui.setScreen(new JuiceClientRulesScreen(this))
		).build());

		this.addSection("options.juiceclient.section.tracers");
		this.list.addBig(OptionInstance.createBoolean("options.juiceclient.showTracers", config.showTracers, value -> config.showTracers = value));
		this.list.addBig(CycleButton.builder(TracerMode::label, config.tracerMode)
				.withValues(TracerMode.values())
				.create(Component.translatable("options.juiceclient.tracerMode"), (button, value) -> config.tracerMode = value));

		this.addSection("options.juiceclient.section.labels");
		this.list.addBig(OptionInstance.createBoolean("options.juiceclient.showLabels", config.showLabels, value -> config.showLabels = value));
		this.list.addSmall(
				OptionInstance.createBoolean("options.juiceclient.showLabelDistance", config.showLabelDistance, value -> config.showLabelDistance = value),
				OptionInstance.createBoolean("options.juiceclient.showLabelCoordinates", config.showLabelCoordinates, value -> config.showLabelCoordinates = value)
		);

		this.addSection("options.juiceclient.section.mobs");
		this.list.addBig(OptionInstance.createBoolean("options.juiceclient.showHostileMobs", config.showHostileMobs, value -> config.showHostileMobs = value));
		this.list.addBig(Button.builder(
				Component.translatable("options.juiceclient.mobs"),
				button -> this.minecraft.gui.setScreen(new JuiceClientMobsScreen(this))
		).build());
		this.list.addSmall(
				OptionInstance.createBoolean("options.juiceclient.mobWarning", config.mobWarning, value -> config.mobWarning = value),
				new OptionInstance<>(
						"options.juiceclient.mobWarningDistance",
						OptionInstance.noTooltip(),
						(caption, value) -> Options.genericValueLabel(caption, Component.translatable("options.juiceclient.blocks", value)),
						new OptionInstance.IntRange(JuiceClientConfig.MIN_MOB_WARNING_DISTANCE, JuiceClientConfig.MAX_MOB_WARNING_DISTANCE, true),
						config.mobWarningDistance,
						value -> config.mobWarningDistance = value
				)
		);
		addColorField(this.list, this.font, "options.juiceclient.hostileMobColor", config.hostileMobColor, value -> config.hostileMobColor = value);

		this.addSection("options.juiceclient.section.items");
		this.list.addSmall(
				CycleButton.onOffBuilder(config.showItems).create(Component.translatable("options.juiceclient.showItems"), (button, value) -> config.showItems = value),
				CycleButton.builder(ItemMode::label, config.itemMode)
						.withValues(ItemMode.values())
						.create(Component.translatable("options.juiceclient.itemMode"), (button, value) -> config.itemMode = value)
		);
		this.list.addBig(OptionInstance.createBoolean("options.juiceclient.showItemLabels", config.showItemLabels, value -> config.showItemLabels = value));
		addColorField(this.list, this.font, "options.juiceclient.itemColor", config.itemColor, value -> config.itemColor = value);

		this.addSection("options.juiceclient.section.players");
		this.list.addSmall(
				OptionInstance.createBoolean("options.juiceclient.showPlayers", config.showPlayers, value -> config.showPlayers = value),
				OptionInstance.createBoolean("options.juiceclient.showPlayerTracers", config.showPlayerTracers, value -> config.showPlayerTracers = value)
		);
		this.list.addBig(OptionInstance.createBoolean("options.juiceclient.playerAlerts", config.playerAlerts, value -> config.playerAlerts = value));
		addColorField(this.list, this.font, "options.juiceclient.playerColor", config.playerColor, value -> config.playerColor = value);

		this.addSection("options.juiceclient.section.xray");
		this.list.addSmall(
				CycleButton.onOffBuilder(config.xrayEnabled).create(Component.translatable("options.juiceclient.xrayEnabled"), (button, value) -> config.xrayEnabled = value),
				Button.builder(
						Component.translatable("options.juiceclient.xrayBlocks"),
						button -> this.minecraft.gui.setScreen(new JuiceClientXRayBlocksScreen(this))
				).build()
		);

		this.addSection("options.juiceclient.section.nameTags");
		this.list.addSmall(
				OptionInstance.createBoolean("options.juiceclient.nameTags", config.nameTags, value -> config.nameTags = value),
				new OptionInstance<>(
						"options.juiceclient.nameTagRange",
						OptionInstance.noTooltip(),
						(caption, value) -> Options.genericValueLabel(caption, Component.translatable("options.juiceclient.blocks", value)),
						new OptionInstance.IntRange(JuiceClientConfig.MIN_NAME_TAG_RANGE, JuiceClientConfig.MAX_NAME_TAG_RANGE, true),
						config.nameTagRange,
						value -> config.nameTagRange = value
				)
		);
		this.list.addSmall(
				Button.builder(
						Component.translatable("options.juiceclient.nameTagEntities"),
						button -> this.minecraft.gui.setScreen(new NameTagEntitiesScreen(this))
				).build(),
				Button.builder(
						Component.translatable("options.juiceclient.nameTagBlocks"),
						button -> this.minecraft.gui.setScreen(new NameTagBlocksScreen(this))
				).build()
		);

		this.addSection("options.juiceclient.section.tools");
		this.addSubsection("options.juiceclient.subsection.mining");
		this.list.addSmall(
				OptionInstance.createBoolean("options.juiceclient.smartTool", config.smartTool, value -> config.smartTool = value),
				new OptionInstance<>(
						"options.juiceclient.smartToolDelay",
						OptionInstance.noTooltip(),
						(caption, value) -> Options.genericValueLabel(caption, Component.translatable("options.juiceclient.seconds", String.format(java.util.Locale.ROOT, "%.2f", value / 20.0))),
						new OptionInstance.IntRange(0, JuiceClientConfig.MAX_SMART_TOOL_DELAY, true),
						config.smartToolDelay,
						value -> config.smartToolDelay = value
				)
		);
		this.addSubsection("options.juiceclient.subsection.food");
		this.list.addSmall(
				OptionInstance.createBoolean("options.juiceclient.autoEat", config.autoEat, value -> config.autoEat = value),
				new OptionInstance<>(
						"options.juiceclient.autoEatHunger",
						OptionInstance.noTooltip(),
						(caption, value) -> Options.genericValueLabel(caption, Component.translatable("options.juiceclient.hunger", value)),
						new OptionInstance.IntRange(1, JuiceClientConfig.MAX_AUTO_EAT_HUNGER, true),
						config.autoEatHunger,
						value -> config.autoEatHunger = value
				)
		);
		this.list.addBig(new OptionInstance<>(
				"options.juiceclient.autoEatHealth",
				OptionInstance.noTooltip(),
				(caption, value) -> Options.genericValueLabel(caption, value == 0
						? Component.translatable("options.off")
						: Component.translatable("options.juiceclient.percent", value)),
				new OptionInstance.IntRange(0, JuiceClientConfig.MAX_AUTO_EAT_HEALTH, true),
				config.autoEatHealth,
				value -> config.autoEatHealth = value
		));
		this.list.addBig(OptionInstance.createBoolean(
				"options.juiceclient.autoEatResupply",
				OptionInstance.cachedConstantTooltip(Component.translatable("options.juiceclient.autoEatResupply.tooltip")),
				config.autoEatResupply,
				value -> config.autoEatResupply = value
		));
		this.addSubsection("options.juiceclient.subsection.combat");
		this.list.addSmall(
				OptionInstance.createBoolean(
						"options.juiceclient.autoWeapon",
						OptionInstance.cachedConstantTooltip(Component.translatable("options.juiceclient.autoWeapon.tooltip")),
						config.autoWeapon,
						value -> config.autoWeapon = value
				),
				new OptionInstance<>(
						"options.juiceclient.autoWeaponReturn",
						OptionInstance.noTooltip(),
						(caption, value) -> Options.genericValueLabel(caption, value == 0
								? Component.translatable("options.juiceclient.autoWeaponReturn.never")
								: Component.translatable("options.juiceclient.seconds", String.format(java.util.Locale.ROOT, "%.2f", value / 20.0))),
						new OptionInstance.IntRange(0, JuiceClientConfig.MAX_AUTO_WEAPON_RETURN, true),
						config.autoWeaponReturn,
						value -> config.autoWeaponReturn = value
				)
		);
		this.list.addSmall(
				OptionInstance.createBoolean("options.juiceclient.autoTotem", config.autoTotem, value -> config.autoTotem = value),
				new OptionInstance<>(
						"options.juiceclient.autoTotemDelay",
						OptionInstance.noTooltip(),
						(caption, value) -> Options.genericValueLabel(caption, value == 0
								? Component.translatable("options.juiceclient.takeAllDelay.instant")
								: Component.translatable("options.juiceclient.seconds", String.format(java.util.Locale.ROOT, "%.2f", value / 20.0))),
						new OptionInstance.IntRange(0, JuiceClientConfig.MAX_AUTO_TOTEM_DELAY, true),
						config.autoTotemDelay,
						value -> config.autoTotemDelay = value
				)
		);
		this.list.addBig(new OptionInstance<>(
				"options.juiceclient.autoTotemHealth",
				OptionInstance.cachedConstantTooltip(Component.translatable("options.juiceclient.autoTotemHealth.tooltip")),
				(caption, value) -> Options.genericValueLabel(caption, value == 0
						? Component.translatable("options.off")
						: Component.translatable("options.juiceclient.hearts", value)),
				new OptionInstance.IntRange(0, JuiceClientConfig.MAX_AUTO_TOTEM_HEALTH, true),
				config.autoTotemHealth,
				value -> config.autoTotemHealth = value
		));
		// The Quick Use key itself is set in Controls, with the others.
		this.list.addSmall(
				OptionInstance.createBoolean(
						"options.juiceclient.autoArmor",
						OptionInstance.cachedConstantTooltip(Component.translatable("options.juiceclient.autoArmor.tooltip")),
						config.autoArmor,
						value -> config.autoArmor = value
				).createButton(this.minecraft.options),
				CycleButton.builder(QuickUseAction::label, config.quickUseAction)
						.withValues(QuickUseAction.values())
						.create(Component.translatable("options.juiceclient.quickUseAction"), (button, value) -> config.quickUseAction = value)
		);
		this.addSubsection("options.juiceclient.subsection.storage");
		this.list.addSmall(
				OptionInstance.createBoolean("options.juiceclient.takeAll", config.takeAll, value -> config.takeAll = value),
				new OptionInstance<>(
						"options.juiceclient.takeAllDelay",
						OptionInstance.noTooltip(),
						(caption, value) -> Options.genericValueLabel(caption, value == 0
								? Component.translatable("options.juiceclient.takeAllDelay.instant")
								: Component.translatable("options.juiceclient.seconds", String.format(java.util.Locale.ROOT, "%.2f", value / 20.0))),
						new OptionInstance.IntRange(0, JuiceClientConfig.MAX_TAKE_ALL_DELAY, true),
						config.takeAllDelay,
						value -> config.takeAllDelay = value
				)
		);
		this.list.addBig(OptionInstance.createBoolean("options.juiceclient.putAllHotbar", config.putAllHotbar, value -> config.putAllHotbar = value));
		this.list.addSmall(
				CycleButton.builder(PutAllFilter::label, config.putAllFilter)
						.withValues(PutAllFilter.values())
						.create(Component.translatable("options.juiceclient.putAllFilter"), (button, value) -> config.putAllFilter = value),
				Button.builder(
						Component.translatable("options.juiceclient.putAllItems"),
						button -> this.minecraft.gui.setScreen(new PutAllItemsScreen(this))
				).build()
		);
		this.list.addSmall(
				OptionInstance.createBoolean(
						"options.juiceclient.sortButtons",
						OptionInstance.cachedConstantTooltip(Component.translatable("options.juiceclient.sortButtons.tooltip")),
						config.sortButtons,
						value -> config.sortButtons = value
				).createButton(this.minecraft.options),
				CycleButton.builder(SortMode::label, config.sortMode)
						.withValues(SortMode.values())
						.create(Component.translatable("options.juiceclient.sortMode"), (button, value) -> config.sortMode = value)
		);
		this.list.addSmall(
				new OptionInstance<>(
						"options.juiceclient.sortDelay",
						OptionInstance.noTooltip(),
						(caption, value) -> Options.genericValueLabel(caption, value == 0
								? Component.translatable("options.juiceclient.takeAllDelay.instant")
								: Component.translatable("options.juiceclient.seconds", String.format(java.util.Locale.ROOT, "%.2f", value / 20.0))),
						new OptionInstance.IntRange(0, JuiceClientConfig.MAX_SORT_DELAY, true),
						config.sortDelay,
						value -> config.sortDelay = value
				).createButton(this.minecraft.options),
				Button.builder(
						Component.translatable("options.juiceclient.sortPriorities"),
						button -> this.minecraft.gui.setScreen(new SortPriorityScreen(this))
				).tooltip(Tooltip.create(Component.translatable("options.juiceclient.sortPriorities.tooltip"))).build()
		);
		this.list.addBig(Button.builder(
				Component.translatable("options.juiceclient.sortGroups"),
				button -> this.minecraft.gui.setScreen(new SortGroupsScreen(this))
		).tooltip(Tooltip.create(Component.translatable("options.juiceclient.sortGroups.tooltip"))).build());
		this.addSubsection("options.juiceclient.subsection.aiming");
		this.list.addBig(OptionInstance.createBoolean("options.juiceclient.trajectories",
				OptionInstance.cachedConstantTooltip(Component.translatable("options.juiceclient.trajectories.tooltip")), config.trajectories,
				value -> config.trajectories = value));
		addColorField(this.list, this.font, "options.juiceclient.trajectoryColor", config.trajectoryColor, value -> config.trajectoryColor = value);

		this.addSection("options.juiceclient.section.appearance");
		this.list.addBig(new OptionInstance<>(
				"options.juiceclient.lineWidth",
				OptionInstance.noTooltip(),
				(caption, value) -> Options.genericValueLabel(caption, Component.literal(value.toString())),
				new OptionInstance.IntRange(MIN_LINE_WIDTH, MAX_LINE_WIDTH, true),
				Mth.clamp(Math.round(config.lineWidth), MIN_LINE_WIDTH, MAX_LINE_WIDTH),
				value -> config.lineWidth = value
		));
		this.list.addBig(OptionInstance.createBoolean("options.juiceclient.stackBossBars",
				OptionInstance.cachedConstantTooltip(Component.translatable("options.juiceclient.stackBossBars.tooltip")), config.stackBossBars,
				value -> config.stackBossBars = value));
		this.list.addBig(OptionInstance.createBoolean("options.juiceclient.armorStatus",
				OptionInstance.cachedConstantTooltip(Component.translatable("options.juiceclient.armorStatus.tooltip")), config.armorStatus,
				value -> config.armorStatus = value));

		this.addSection("options.juiceclient.section.freeLook");
		this.list.addBig(new OptionInstance<>(
				"options.juiceclient.freeLookSpeed",
				OptionInstance.noTooltip(),
				(caption, value) -> Options.genericValueLabel(caption, Component.translatable("options.juiceclient.freeLookSpeed.value", value)),
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
			case "xray" -> "key.juiceclient.xray";
			case "freeLook" -> "key.juiceclient.freeLook";
			default -> "key.juiceclient.section." + id;
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
				? Component.translatable("options.juiceclient.sectionKey.none").withStyle(ChatFormatting.DARK_GRAY)
				: Component.translatable("options.juiceclient.sectionKey", mapping.getTranslatedKeyMessage()).withStyle(ChatFormatting.GRAY);
	}

	static void addColorField(OptionsList list, Font font, String key, String initialValue, Consumer<String> setter) {
		Component label = Component.translatable(key);
		EditBox box = new EditBox(font, 150, 20, label);
		box.setMaxLength(8);
		box.setValue(initialValue);
		box.setResponder(value -> {
			boolean valid = JuiceClientConfig.isValidColor(value);
			box.setTextColor(valid ? VALID_COLOR : INVALID_COLOR);

			if (valid) {
				setter.accept(value);
				JuiceClient.CONFIG.refreshColors();
			}
		});

		list.addHeader(label);
		list.addSmall(box, new ColorSwatchWidget(150, 20, label, box::getValue));
	}

	private void exportSettings() {
		JuiceClient.CONFIG.save();

		try {
			java.nio.file.Path file = SettingsTransfer.export();
			net.minecraft.client.gui.components.toasts.SystemToast.addOrUpdate(this.minecraft.gui.toastManager(),
					net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
					Component.translatable("options.juiceclient.export.done"), Component.literal(file.getFileName().toString()));
		} catch (java.io.IOException e) {
			JuiceClient.LOGGER.error("Failed to export settings", e);
			net.minecraft.client.gui.components.toasts.SystemToast.addOrUpdate(this.minecraft.gui.toastManager(),
					net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
					Component.translatable("options.juiceclient.export.failed").withStyle(ChatFormatting.RED), Component.literal(String.valueOf(e.getMessage())));
		}
	}

	@Override
	public void removed() {
		super.removed();
		JuiceClient.CONFIG.save();
	}
}
