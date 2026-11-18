package com.basetracer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Edited through the settings screen and stored in config/basetracer.properties. Colors are ARGB hex strings.
 */
public final class BaseTracerConfig {
	private static final String FILE_NAME = "basetracer.properties";
	public static final int MAX_BASES_LIMIT = 50;
	public static final int MIN_MOB_WARNING_DISTANCE = 3;
	public static final int MAX_SMART_TOOL_DELAY = 40;
	public static final int MAX_AUTO_EAT_HUNGER = 19;
	public static final int MAX_AUTO_EAT_HEALTH = 95;
	public static final int MAX_TAKE_ALL_DELAY = 10;
	public static final int MAX_AUTO_TOTEM_DELAY = 100;
	public static final int MAX_AUTO_TOTEM_HEALTH = 10;
	public static final int MIN_NAME_TAG_RANGE = 8;
	public static final int MAX_NAME_TAG_RANGE = 64;
	public static final int MAX_MOB_WARNING_DISTANCE = 32;
	/** From when this mod was called Light Tracer. */
	private static final String OLD_NAME_FILE_NAME = "lighttracer.properties";
	private static final String LEGACY_FILE_NAME = "lighttracer.json";
	private static final String CUSTOM_RULE_OFFERED = "customRuleOffered";
	private static final String ENTITY_RULES_OFFERED = "entityRulesOffered";
	/** Light-emitting blocks that generate in natural terrain, so finding one says nothing about players. */
	private static final List<String> DEFAULT_EXCLUDED_BLOCKS = List.of(
			"minecraft:amethyst_cluster",
			"minecraft:brown_mushroom",
			"minecraft:cave_vines",
			"minecraft:cave_vines_plant",
			"minecraft:crying_obsidian",
			"minecraft:deepslate_redstone_ore",
			"minecraft:dragon_egg",
			"minecraft:end_gateway",
			"minecraft:end_portal",
			"minecraft:end_portal_frame",
			"minecraft:fire",
			"minecraft:firefly_bush",
			"minecraft:glow_lichen",
			"minecraft:glowstone",
			"minecraft:large_amethyst_bud",
			"minecraft:lava",
			"minecraft:magma_block",
			"minecraft:medium_amethyst_bud",
			"minecraft:nether_portal",
			"minecraft:redstone_ore",
			"minecraft:sculk_catalyst",
			"minecraft:sculk_sensor",
			"minecraft:sea_pickle",
			"minecraft:shroomlight",
			"minecraft:small_amethyst_bud",
			"minecraft:soul_fire",
			"minecraft:trial_spawner",
			"minecraft:vault"
	);

	/** What X-Ray leaves visible at first: every ore, and the raw ore blocks that mark big veins. */
	private static final List<String> DEFAULT_XRAY_BLOCKS = List.of(
			"minecraft:ancient_debris",
			"minecraft:coal_ore",
			"minecraft:copper_ore",
			"minecraft:deepslate_coal_ore",
			"minecraft:deepslate_copper_ore",
			"minecraft:deepslate_diamond_ore",
			"minecraft:deepslate_emerald_ore",
			"minecraft:deepslate_gold_ore",
			"minecraft:deepslate_iron_ore",
			"minecraft:deepslate_lapis_ore",
			"minecraft:deepslate_redstone_ore",
			"minecraft:diamond_ore",
			"minecraft:emerald_ore",
			"minecraft:gold_ore",
			"minecraft:iron_ore",
			"minecraft:lapis_ore",
			"minecraft:nether_gold_ore",
			"minecraft:nether_quartz_ore",
			"minecraft:raw_copper_block",
			"minecraft:raw_gold_block",
			"minecraft:raw_iron_block",
			"minecraft:redstone_ore"
	);

	/** Spawn like monsters without being enemies. A mob's type can't say that itself, only a spawned mob can. */
	private static final Set<EntityType<?>> UNAGGRESSIVE_MONSTERS = Set.of(
			EntityTypes.CAMEL_HUSK,
			EntityTypes.SULFUR_CUBE,
			EntityTypes.ZOMBIE_HORSE,
			EntityTypes.ZOMBIE_NAUTILUS
	);

	public boolean enabled = true;
	public boolean showChunkHighlights = true;
	public boolean showTracers = true;
	public TracerMode tracerMode = TracerMode.NEAREST_PER_CHUNK;
	public boolean showBlockHighlights = true;
	public boolean showLabels = true;
	public boolean showLabelDistance = true;
	public boolean showLabelCoordinates = true;
	public boolean showHostileMobs = false;
	public boolean mobWarning = true;
	public boolean showItems = false;
	public ItemMode itemMode = ItemMode.ALL;
	public boolean showItemLabels = true;
	public String itemColor = "FFFFCC33";
	/** Blocks. */
	public int mobWarningDistance = 10;
	public boolean showPlayers = false;
	public boolean showPlayerTracers = true;
	/** Says above the hotbar when another player comes into range or leaves it. */
	public boolean playerAlerts = true;
	public boolean xrayEnabled = false;
	public boolean smartTool = true;
	/** Ticks of mining before the tool is switched, so that a misclick doesn't switch it. */
	public int smartToolDelay = 10;
	public boolean autoEat = true;
	public QuickUseAction quickUseAction = QuickUseAction.ENDER_PEARL;
	public boolean autoTotem = true;
	public boolean nameTags = true;
	/** Blocks from the camera that name tags reach. */
	public int nameTagRange = 32;
	/** Ticks after the offhand loses its totem before another goes in. */
	public int autoTotemDelay = 5;
	/** Hearts at or below which a totem goes in straight away, even over a shield. 0 is off. */
	public int autoTotemHealth = 4;
	public boolean takeAll = true;
	/** Ticks between the slots Take All shift-clicks. 0 takes everything at once. */
	public int takeAllDelay = 1;
	/** Whether Put All empties the hotbar too, not just the rest of the inventory. */
	public boolean putAllHotbar = false;
	/** Whether Put All moves only the listed items, all but them, or everything. */
	public PutAllFilter putAllFilter = PutAllFilter.OFF;
	/** Eats once the hunger bar is at or below this, out of 20. */
	public int autoEatHunger = 14;
	/** Also eats once health is below this percent of the maximum. 0 turns it off. */
	public int autoEatHealth = 50;
	/** How many bases are shown, the highest priority and then the nearest. Above {@link #MAX_BASES_LIMIT} means all. */
	public int maxBases = 10;
	/** Percent. Applies to both the outline and the fill of player and item boxes. Mobs are outlined solid instead. */
	public int mobOpacity = 50;
	/** Percent. Each rule's color fills its highlight at this opacity, and more for busier chunks. */
	public int fillOpacity = 9;
	public String hostileMobColor = "FFFF3333";
	public String playerColor = "FF55AAFF";
	public float lineWidth = 2.0F;
	/** Blocks per second. */
	public float freeLookSpeed = 20.0F;
	// Kept as ids so that entries from a temporarily removed mod aren't lost.
	private TreeSet<String> excludedBlocks = new TreeSet<>(DEFAULT_EXCLUDED_BLOCKS);
	// Only what differs from the default, so that hostile mobs from a newly added mod are on the list without asking.
	private TreeSet<String> mobOverrides = new TreeSet<>();
	private TreeSet<String> xrayBlockIds = new TreeSet<>(DEFAULT_XRAY_BLOCKS);
	private TreeSet<String> putAllItemIds = new TreeSet<>();
	private TreeSet<String> nameTagEntityIds = new TreeSet<>();
	private TreeSet<String> nameTagBlockIds = new TreeSet<>();
	// Only read when importing the old JSON file, where tracerMode didn't exist yet.
	@SerializedName("tracerPerSource")
	private Boolean legacyTracerPerSource;

	public final transient List<BaseRule> rules = BaseRule.defaults();
	private transient Path file;
	private transient Set<Block> excluded;
	private transient Set<EntityType<?>> mobOverrideTypes;
	/** Replaced rather than changed, so that X-Ray can tell when to rebuild. */
	private transient Set<Block> xrayBlockSet;
	private transient Set<EntityType<?>> nameTagEntitySet;
	private transient Set<Block> nameTagBlockSet;
	/** The blocks of every enabled rule, so that scanning a block doesn't have to walk the rules. */
	private transient Set<Block> trackedBlocks;
	private transient boolean trackLightSources;
	private transient boolean trackEntities;
	private transient boolean rulesDirty;
	private transient int hostileMobArgb;
	private transient int playerArgb;
	private transient int itemArgb;

	public static BaseTracerConfig load(Path configDir) {
		Path file = configDir.resolve(FILE_NAME);
		Path oldNameFile = configDir.resolve(OLD_NAME_FILE_NAME);
		Path legacyFile = configDir.resolve(LEGACY_FILE_NAME);
		BaseTracerConfig config;

		if (Files.exists(file)) {
			config = readProperties(file);
		} else if (Files.exists(oldNameFile)) {
			config = readProperties(oldNameFile);
		} else {
			config = readLegacyJson(legacyFile);
		}

		config.file = file;
		config.refreshColors();
		// Writing back also adds any options introduced since the file was created.
		config.save();

		if (Files.exists(file)) {
			try {
				Files.deleteIfExists(oldNameFile);
				Files.deleteIfExists(legacyFile);
			} catch (IOException e) {
				BaseTracerClient.LOGGER.warn("Failed to delete old settings in {}", configDir, e);
			}
		}

		return config;
	}

	private static BaseTracerConfig readProperties(Path file) {
		BaseTracerConfig config = new BaseTracerConfig();
		Properties properties = new Properties();

		try (Reader reader = Files.newBufferedReader(file)) {
			properties.load(reader);
		} catch (IOException | IllegalArgumentException e) {
			BaseTracerClient.LOGGER.error("Failed to read {}, using defaults", file, e);
			return config;
		}

		config.enabled = Boolean.parseBoolean(properties.getProperty("enabled", String.valueOf(config.enabled)));
		config.showChunkHighlights = Boolean.parseBoolean(properties.getProperty("showChunkHighlights", String.valueOf(config.showChunkHighlights)));
		config.showTracers = Boolean.parseBoolean(properties.getProperty("showTracers", String.valueOf(config.showTracers)));
		config.tracerMode = readTracerMode(properties);
		config.showBlockHighlights = Boolean.parseBoolean(properties.getProperty("showBlockHighlights", String.valueOf(config.showBlockHighlights)));
		config.showLabels = Boolean.parseBoolean(properties.getProperty("showLabels", String.valueOf(config.showLabels)));
		config.showLabelDistance = Boolean.parseBoolean(properties.getProperty("showLabelDistance", String.valueOf(config.showLabelDistance)));
		config.showLabelCoordinates = Boolean.parseBoolean(properties.getProperty("showLabelCoordinates", String.valueOf(config.showLabelCoordinates)));
		config.showPlayers = Boolean.parseBoolean(properties.getProperty("showPlayers", String.valueOf(config.showPlayers)));
		config.showPlayerTracers = Boolean.parseBoolean(properties.getProperty("showPlayerTracers", String.valueOf(config.showPlayerTracers)));
		config.playerAlerts = Boolean.parseBoolean(properties.getProperty("playerAlerts", String.valueOf(config.playerAlerts)));
		config.showHostileMobs = Boolean.parseBoolean(properties.getProperty("showHostileMobs", String.valueOf(config.showHostileMobs)));
		config.mobWarning = Boolean.parseBoolean(properties.getProperty("mobWarning", String.valueOf(config.mobWarning)));
		config.showItems = Boolean.parseBoolean(properties.getProperty("showItems", String.valueOf(config.showItems)));
		config.showItemLabels = Boolean.parseBoolean(properties.getProperty("showItemLabels", String.valueOf(config.showItemLabels)));
		config.itemColor = properties.getProperty("itemColor", config.itemColor);

		try {
			config.itemMode = ItemMode.valueOf(properties.getProperty("itemMode", config.itemMode.name()));
		} catch (IllegalArgumentException e) {
			BaseTracerClient.LOGGER.warn("Ignoring unknown item mode: {}", properties.getProperty("itemMode"));
		}
		config.mobWarningDistance = Mth.clamp(readInt(properties, "mobWarningDistance", config.mobWarningDistance), MIN_MOB_WARNING_DISTANCE, MAX_MOB_WARNING_DISTANCE);
		config.xrayEnabled = Boolean.parseBoolean(properties.getProperty("xrayEnabled", String.valueOf(config.xrayEnabled)));
		config.smartTool = Boolean.parseBoolean(properties.getProperty("smartTool", String.valueOf(config.smartTool)));
		config.smartToolDelay = Mth.clamp(readInt(properties, "smartToolDelay", config.smartToolDelay), 0, MAX_SMART_TOOL_DELAY);
		config.autoEat = Boolean.parseBoolean(properties.getProperty("autoEat", String.valueOf(config.autoEat)));
		config.takeAll = Boolean.parseBoolean(properties.getProperty("takeAll", String.valueOf(config.takeAll)));
		config.autoTotem = Boolean.parseBoolean(properties.getProperty("autoTotem", String.valueOf(config.autoTotem)));
		config.autoTotemDelay = Mth.clamp(readInt(properties, "autoTotemDelay", config.autoTotemDelay), 0, MAX_AUTO_TOTEM_DELAY);
		config.autoTotemHealth = Mth.clamp(readInt(properties, "autoTotemHealth", config.autoTotemHealth), 0, MAX_AUTO_TOTEM_HEALTH);
		config.takeAllDelay = Mth.clamp(readInt(properties, "takeAllDelay", config.takeAllDelay), 0, MAX_TAKE_ALL_DELAY);
		config.putAllHotbar = Boolean.parseBoolean(properties.getProperty("putAllHotbar", String.valueOf(config.putAllHotbar)));
		config.nameTags = Boolean.parseBoolean(properties.getProperty("nameTags", String.valueOf(config.nameTags)));
		config.nameTagRange = Mth.clamp(readInt(properties, "nameTagRange", config.nameTagRange), MIN_NAME_TAG_RANGE, MAX_NAME_TAG_RANGE);
		readIds(properties.getProperty("nameTagEntities", ""), config.nameTagEntityIds);
		readIds(properties.getProperty("nameTagBlocks", ""), config.nameTagBlockIds);

		try {
			config.putAllFilter = PutAllFilter.valueOf(properties.getProperty("putAllFilter", config.putAllFilter.name()));
		} catch (IllegalArgumentException e) {
			BaseTracerClient.LOGGER.warn("Ignoring unknown put all filter: {}", properties.getProperty("putAllFilter"));
		}

		for (String item : properties.getProperty("putAllItems", "").split(",")) {
			if (!item.isBlank()) {
				config.putAllItemIds.add(item.trim());
			}
		}

		try {
			config.quickUseAction = QuickUseAction.valueOf(properties.getProperty("quickUseAction", config.quickUseAction.name()));
		} catch (IllegalArgumentException e) {
			BaseTracerClient.LOGGER.warn("Ignoring unknown quick use action: {}", properties.getProperty("quickUseAction"));
		}
		config.autoEatHunger = Mth.clamp(readInt(properties, "autoEatHunger", config.autoEatHunger), 1, MAX_AUTO_EAT_HUNGER);
		config.autoEatHealth = Mth.clamp(readInt(properties, "autoEatHealth", config.autoEatHealth), 0, MAX_AUTO_EAT_HEALTH);
		config.maxBases = Mth.clamp(readInt(properties, "maxBases", config.maxBases), 1, MAX_BASES_LIMIT + 1);
		config.mobOpacity = Mth.clamp(readInt(properties, "mobOpacity", config.mobOpacity), 0, 100);
		config.fillOpacity = Mth.clamp(readInt(properties, "fillOpacity", config.fillOpacity), 0, 100);
		config.hostileMobColor = properties.getProperty("hostileMobColor", config.hostileMobColor);
		config.playerColor = properties.getProperty("playerColor", config.playerColor);

		try {
			config.lineWidth = Float.parseFloat(properties.getProperty("lineWidth", String.valueOf(config.lineWidth)));
		} catch (NumberFormatException e) {
			BaseTracerClient.LOGGER.warn("Ignoring invalid line width: {}", properties.getProperty("lineWidth"));
		}

		try {
			config.freeLookSpeed = Float.parseFloat(properties.getProperty("freeLookSpeed", String.valueOf(config.freeLookSpeed)));
		} catch (NumberFormatException e) {
			BaseTracerClient.LOGGER.warn("Ignoring invalid free look speed: {}", properties.getProperty("freeLookSpeed"));
		}

		String blocks = properties.getProperty("excludedBlocks");

		if (blocks != null) {
			config.excludedBlocks.clear();

			for (String block : blocks.split(",")) {
				if (!block.isBlank()) {
					config.excludedBlocks.add(block.trim());
				}
			}
		}

		String xrayBlocks = properties.getProperty("xrayBlocks");

		if (xrayBlocks != null) {
			config.xrayBlockIds.clear();

			for (String block : xrayBlocks.split(",")) {
				if (!block.isBlank()) {
					config.xrayBlockIds.add(block.trim());
				}
			}
		}

		for (String mob : properties.getProperty("mobOverrides", "").split(",")) {
			if (!mob.isBlank()) {
				config.mobOverrides.add(mob.trim());
			}
		}

		readRules(config, properties);
		return config;
	}

	private static void readRules(BaseTracerConfig config, Properties properties) {
		int count = readInt(properties, "ruleCount", -1);

		// Files from before rules existed get the default rules.
		if (count < 0) {
			return;
		}

		config.rules.clear();

		for (int i = 0; i < count; i++) {
			String prefix = "rule." + i + ".";
			List<String> blocks = new ArrayList<>();

			for (String block : properties.getProperty(prefix + "blocks", "").split(",")) {
				if (!block.isBlank()) {
					blocks.add(block.trim());
				}
			}

			BaseRule rule = BaseRule.restored(
					Boolean.parseBoolean(properties.getProperty(prefix + "lightSources")),
					properties.getProperty(prefix + "name", "Unnamed Rule"),
					blocks,
					Mth.clamp(readInt(properties, prefix + "minBlocks", 1), 1, BaseRule.MAX_MIN_BLOCKS),
					Mth.clamp(readInt(properties, prefix + "minTypes", 1), 1, BaseRule.MAX_MIN_TYPES)
			);
			rule.enabled = Boolean.parseBoolean(properties.getProperty(prefix + "enabled", "true"));
			rule.showLabel = Boolean.parseBoolean(properties.getProperty(prefix + "showLabel", "true"));
			rule.showTracer = Boolean.parseBoolean(properties.getProperty(prefix + "showTracer", "true"));

			for (String entity : properties.getProperty(prefix + "entities", "").split(",")) {
				if (!entity.isBlank()) {
					rule.setEntity(entity.trim(), true);
				}
			}

			// Rules saved before they had priorities get their default one, matched by name.
			String priority = properties.getProperty(prefix + "priority");

			if (priority != null) {
				rule.priority = Mth.clamp(readInt(properties, prefix + "priority", BaseRule.DEFAULT_PRIORITY), 1, BaseRule.MAX_PRIORITY);
			} else {
				BaseRule.defaults().stream().filter(other -> other.name.equals(rule.name)).findFirst().ifPresent(other -> rule.priority = other.priority);
			}

			// Rules saved before they had colors keep their default one, matched by name.
			String color = properties.getProperty(prefix + "color");

			if (color != null) {
				rule.setColor(color);
			} else {
				BaseRule.defaults().stream().filter(other -> other.name.equals(rule.name)).findFirst().ifPresent(other -> rule.setColor(other.color()));
			}

			config.rules.add(rule);
		}

		// The screens rely on this one always being there.
		if (config.rules.stream().noneMatch(rule -> rule.lightSources)) {
			config.rules.addFirst(BaseRule.lightSources());
		}

		// Added once to files from before this rule existed, so that deleting it afterwards sticks.
		if (properties.getProperty(CUSTOM_RULE_OFFERED) == null && config.rules.stream().noneMatch(rule -> rule.name.equals(BaseRule.custom().name))) {
			config.rules.add(BaseRule.custom());
		}

		// The same for the rules about entities, which go in ahead of Custom as they do in the defaults.
		if (properties.getProperty(ENTITY_RULES_OFFERED) == null) {
			int index = config.rules.size();

			for (int i = 0; i < config.rules.size(); i++) {
				if (config.rules.get(i).name.equals(BaseRule.custom().name)) {
					index = i;
				}
			}

			for (BaseRule added : BaseRule.entityDefaults()) {
				if (config.rules.stream().noneMatch(rule -> rule.name.equals(added.name))) {
					config.rules.add(index++, added);
				}
			}
		}
	}

	private static int readInt(Properties properties, String key, int fallback) {
		String value = properties.getProperty(key);

		if (value == null) {
			return fallback;
		}

		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			BaseTracerClient.LOGGER.warn("Ignoring invalid value for {}: {}", key, value);
			return fallback;
		}
	}

	private static TracerMode readTracerMode(Properties properties) {
		String mode = properties.getProperty("tracerMode");

		if (mode == null) {
			// Before tracerMode there was only an on/off setting for tracing every source.
			return Boolean.parseBoolean(properties.getProperty("tracerPerSource")) ? TracerMode.EVERY_SOURCE : TracerMode.NEAREST_PER_CHUNK;
		}

		try {
			return TracerMode.valueOf(mode);
		} catch (IllegalArgumentException e) {
			BaseTracerClient.LOGGER.warn("Ignoring invalid tracer mode: {}", mode);
			return TracerMode.NEAREST_PER_CHUNK;
		}
	}

	// Settings used to be stored as JSON.
	private static BaseTracerConfig readLegacyJson(Path file) {
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file)) {
				BaseTracerConfig config = new Gson().fromJson(reader, BaseTracerConfig.class);

				if (config != null) {
					if (Boolean.TRUE.equals(config.legacyTracerPerSource)) {
						config.tracerMode = TracerMode.EVERY_SOURCE;
					}

					return config;
				}
			} catch (IOException | JsonParseException e) {
				BaseTracerClient.LOGGER.error("Failed to import {}, using defaults", file, e);
			}
		}

		return new BaseTracerConfig();
	}

	public void save() {
		Properties properties = new Properties();
		properties.setProperty("enabled", String.valueOf(this.enabled));
		properties.setProperty("showChunkHighlights", String.valueOf(this.showChunkHighlights));
		properties.setProperty("showTracers", String.valueOf(this.showTracers));
		properties.setProperty("tracerMode", this.tracerMode.name());
		properties.setProperty("showBlockHighlights", String.valueOf(this.showBlockHighlights));
		properties.setProperty("showLabels", String.valueOf(this.showLabels));
		properties.setProperty("showLabelDistance", String.valueOf(this.showLabelDistance));
		properties.setProperty("showLabelCoordinates", String.valueOf(this.showLabelCoordinates));
		properties.setProperty("showPlayers", String.valueOf(this.showPlayers));
		properties.setProperty("showPlayerTracers", String.valueOf(this.showPlayerTracers));
		properties.setProperty("playerAlerts", String.valueOf(this.playerAlerts));
		properties.setProperty("showHostileMobs", String.valueOf(this.showHostileMobs));
		properties.setProperty("mobWarning", String.valueOf(this.mobWarning));
		properties.setProperty("showItems", String.valueOf(this.showItems));
		properties.setProperty("itemMode", this.itemMode.name());
		properties.setProperty("showItemLabels", String.valueOf(this.showItemLabels));
		properties.setProperty("itemColor", this.itemColor);
		properties.setProperty("mobWarningDistance", String.valueOf(this.mobWarningDistance));
		properties.setProperty("xrayEnabled", String.valueOf(this.xrayEnabled));
		properties.setProperty("smartTool", String.valueOf(this.smartTool));
		properties.setProperty("smartToolDelay", String.valueOf(this.smartToolDelay));
		properties.setProperty("autoEat", String.valueOf(this.autoEat));
		properties.setProperty("quickUseAction", this.quickUseAction.name());
		properties.setProperty("takeAll", String.valueOf(this.takeAll));
		properties.setProperty("autoTotem", String.valueOf(this.autoTotem));
		properties.setProperty("autoTotemDelay", String.valueOf(this.autoTotemDelay));
		properties.setProperty("autoTotemHealth", String.valueOf(this.autoTotemHealth));
		properties.setProperty("takeAllDelay", String.valueOf(this.takeAllDelay));
		properties.setProperty("putAllHotbar", String.valueOf(this.putAllHotbar));
		properties.setProperty("putAllFilter", this.putAllFilter.name());
		properties.setProperty("putAllItems", String.join(",", this.putAllItemIds));
		properties.setProperty("nameTags", String.valueOf(this.nameTags));
		properties.setProperty("nameTagRange", String.valueOf(this.nameTagRange));
		properties.setProperty("nameTagEntities", String.join(",", this.nameTagEntityIds));
		properties.setProperty("nameTagBlocks", String.join(",", this.nameTagBlockIds));
		properties.setProperty("autoEatHunger", String.valueOf(this.autoEatHunger));
		properties.setProperty("autoEatHealth", String.valueOf(this.autoEatHealth));
		properties.setProperty("maxBases", String.valueOf(this.maxBases));
		properties.setProperty("xrayBlocks", String.join(",", this.xrayBlockIds));
		properties.setProperty("mobOpacity", String.valueOf(this.mobOpacity));
		properties.setProperty("fillOpacity", String.valueOf(this.fillOpacity));
		properties.setProperty("hostileMobColor", this.hostileMobColor);
		properties.setProperty("playerColor", this.playerColor);
		properties.setProperty("lineWidth", String.valueOf(this.lineWidth));
		properties.setProperty("freeLookSpeed", String.valueOf(this.freeLookSpeed));
		properties.setProperty("excludedBlocks", String.join(",", this.excludedBlocks));
		properties.setProperty("mobOverrides", String.join(",", this.mobOverrides));
		properties.setProperty("ruleCount", String.valueOf(this.rules.size()));
		properties.setProperty(CUSTOM_RULE_OFFERED, "true");
		properties.setProperty(ENTITY_RULES_OFFERED, "true");

		for (int i = 0; i < this.rules.size(); i++) {
			BaseRule rule = this.rules.get(i);
			String prefix = "rule." + i + ".";
			properties.setProperty(prefix + "name", rule.name);
			properties.setProperty(prefix + "enabled", String.valueOf(rule.enabled));
			properties.setProperty(prefix + "showLabel", String.valueOf(rule.showLabel));
			properties.setProperty(prefix + "showTracer", String.valueOf(rule.showTracer));
			properties.setProperty(prefix + "priority", String.valueOf(rule.priority));
			properties.setProperty(prefix + "entities", String.join(",", rule.entityIds()));
			properties.setProperty(prefix + "color", rule.color());
			properties.setProperty(prefix + "lightSources", String.valueOf(rule.lightSources));
			properties.setProperty(prefix + "minBlocks", String.valueOf(rule.minBlocks));
			properties.setProperty(prefix + "minTypes", String.valueOf(rule.minTypes));
			properties.setProperty(prefix + "blocks", String.join(",", rule.blockIds()));
		}

		try (Writer writer = Files.newBufferedWriter(this.file)) {
			properties.store(writer, "Base Tracer settings. Change these in-game through the settings screen.");
		} catch (IOException e) {
			BaseTracerClient.LOGGER.error("Failed to write {}", this.file, e);
		}
	}

	public void refreshColors() {
		this.hostileMobArgb = parseColor(this.hostileMobColor, 0xFFFF3333);
		this.playerArgb = parseColor(this.playerColor, 0xFF55AAFF);
		this.itemArgb = parseColor(this.itemColor, 0xFFFFCC33);
	}

	public int itemArgb() {
		return this.itemArgb;
	}

	public int fillAlpha() {
		return Math.round(this.fillOpacity * 2.55F);
	}

	public int playerArgb() {
		return this.playerArgb;
	}

	public int hostileMobArgb() {
		return this.hostileMobArgb;
	}

	/** Whether any enabled rule cares about this block, which says nothing yet about whether that rule's thresholds are met. */
	public boolean isTracked(BlockState state) {
		this.resolveTracked();
		return this.trackedBlocks.contains(state.getBlock()) || this.trackLightSources && this.isLightSource(state);
	}

	private void resolveTracked() {
		if (this.trackedBlocks == null) {
			Set<Block> blocks = new ReferenceOpenHashSet<>();
			this.trackLightSources = false;
			this.trackEntities = false;

			for (BaseRule rule : this.rules) {
				if (rule.enabled) {
					blocks.addAll(rule.blocks());
					this.trackLightSources |= rule.lightSources;
					this.trackEntities |= rule.hasEntities();
				}
			}

			this.trackedBlocks = blocks;
		}
	}

	/** Whether any enabled rule looks at entities, so that they needn't be gone through otherwise. */
	public boolean tracksEntities() {
		this.resolveTracked();
		return this.trackEntities;
	}

	public boolean isTracked(BaseTracker.EntityFind entity) {
		for (BaseRule rule : this.rules) {
			if (rule.enabled && this.matches(rule, entity)) {
				return true;
			}
		}

		return false;
	}

	public boolean matches(BaseRule rule, BaseTracker.EntityFind entity) {
		return rule.entities().contains(entity.type()) || entity.named() && rule.matchesNamedMobs() || entity.tamed() && rule.matchesTamedAnimals();
	}

	public boolean matches(BaseRule rule, BlockState state) {
		return rule.lightSources ? this.isLightSource(state) : rule.blocks().contains(state.getBlock());
	}

	private boolean isLightSource(BlockState state) {
		return state.getLightEmission() > 0 && !this.excluded().contains(state.getBlock());
	}

	/** Call after changing anything about a rule. {@link BaseTracerClient#applyRuleChanges} then applies it. */
	public void markRulesChanged() {
		this.trackedBlocks = null;
		this.rulesDirty = true;
	}

	/** Returns whether rules changed since the last call. */
	public boolean consumeRulesChanged() {
		boolean dirty = this.rulesDirty;
		this.rulesDirty = false;
		return dirty;
	}

	public void resetRules() {
		this.rules.clear();
		this.rules.addAll(BaseRule.defaults());
		this.markRulesChanged();
	}

	public boolean isExcluded(Block block) {
		return this.excluded().contains(block);
	}

	public void setExcluded(Block block, boolean isExcluded) {
		String id = BuiltInRegistries.BLOCK.getKey(block).toString();

		if (isExcluded) {
			this.excludedBlocks.add(id);
		} else {
			this.excludedBlocks.remove(id);
		}

		this.excluded = null;
	}

	public void resetExcludedBlocks() {
		this.excludedBlocks = new TreeSet<>(DEFAULT_EXCLUDED_BLOCKS);
		this.excluded = null;
	}

	public void setXrayBlock(Block block, boolean shown) {
		String id = BuiltInRegistries.BLOCK.getKey(block).toString();

		if (shown) {
			this.xrayBlockIds.add(id);
		} else {
			this.xrayBlockIds.remove(id);
		}

		this.xrayBlockSet = null;
	}

	public boolean isPutAllListed(Item item) {
		return this.putAllItemIds.contains(BuiltInRegistries.ITEM.getKey(item).toString());
	}

	public void setPutAllItem(Item item, boolean listed) {
		String id = BuiltInRegistries.ITEM.getKey(item).toString();

		if (listed) {
			this.putAllItemIds.add(id);
		} else {
			this.putAllItemIds.remove(id);
		}
	}

	public void clearPutAllItems() {
		this.putAllItemIds.clear();
	}

	/** Whether Put All moves this item, going by the filter and the list. */
	public boolean putAllMoves(Item item) {
		return this.putAllFilter.allows(this.isPutAllListed(item));
	}

	public void resetXrayBlocks() {
		this.xrayBlockIds = new TreeSet<>(DEFAULT_XRAY_BLOCKS);
		this.xrayBlockSet = null;
	}

	// Resolved on first use because other mods may not have registered their blocks during our init.
	public Set<Block> xrayBlocks() {
		if (this.xrayBlockSet == null) {
			Set<Block> blocks = new ReferenceOpenHashSet<>();

			for (String name : this.xrayBlockIds) {
				Identifier id = Identifier.tryParse(name);

				if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
					blocks.add(BuiltInRegistries.BLOCK.getValue(id));
				}
			}

			this.xrayBlockSet = blocks;
		}

		return this.xrayBlockSet;
	}

	public List<String> xrayBlockIds() {
		return List.copyOf(this.xrayBlockIds);
	}

	/** How many bases to show, or Integer.MAX_VALUE for all. */
	public int baseLimit() {
		return this.maxBases > MAX_BASES_LIMIT ? Integer.MAX_VALUE : this.maxBases;
	}

	private static void readIds(String list, Set<String> ids) {
		for (String id : list.split(",")) {
			if (!id.isBlank()) {
				ids.add(id.trim());
			}
		}
	}

	public boolean isNameTagged(EntityType<?> type) {
		return this.nameTagEntities().contains(type);
	}

	public void setNameTagged(EntityType<?> type, boolean tagged) {
		String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();

		if (tagged) {
			this.nameTagEntityIds.add(id);
		} else {
			this.nameTagEntityIds.remove(id);
		}

		this.nameTagEntitySet = null;
	}

	public void clearNameTagEntities() {
		this.nameTagEntityIds.clear();
		this.nameTagEntitySet = null;
	}

	public void setNameTagBlock(Block block, boolean tagged) {
		String id = BuiltInRegistries.BLOCK.getKey(block).toString();

		if (tagged) {
			this.nameTagBlockIds.add(id);
		} else {
			this.nameTagBlockIds.remove(id);
		}

		this.nameTagBlockSet = null;
	}

	public void clearNameTagBlocks() {
		this.nameTagBlockIds.clear();
		this.nameTagBlockSet = null;
	}

	// Resolved on first use because other mods may not have registered their entities during our init.
	public Set<EntityType<?>> nameTagEntities() {
		if (this.nameTagEntitySet == null) {
			Set<EntityType<?>> types = new ReferenceOpenHashSet<>();

			for (String name : this.nameTagEntityIds) {
				Identifier id = Identifier.tryParse(name);

				if (id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
					types.add(BuiltInRegistries.ENTITY_TYPE.getValue(id));
				}
			}

			this.nameTagEntitySet = types;
		}

		return this.nameTagEntitySet;
	}

	public Set<Block> nameTagBlocks() {
		if (this.nameTagBlockSet == null) {
			Set<Block> blocks = new ReferenceOpenHashSet<>();

			for (String name : this.nameTagBlockIds) {
				Identifier id = Identifier.tryParse(name);

				if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
					blocks.add(BuiltInRegistries.BLOCK.getValue(id));
				}
			}

			this.nameTagBlockSet = blocks;
		}

		return this.nameTagBlockSet;
	}

	public boolean isMobHighlighted(EntityType<?> type) {
		return isHostile(type) != this.mobOverrideTypes().contains(type);
	}

	public void setMobHighlighted(EntityType<?> type, boolean highlighted) {
		String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();

		if (highlighted == isHostile(type)) {
			this.mobOverrides.remove(id);
		} else {
			this.mobOverrides.add(id);
		}

		this.mobOverrideTypes = null;
	}

	public void resetMobs() {
		this.mobOverrides.clear();
		this.mobOverrideTypes = null;
	}

	private static boolean isHostile(EntityType<?> type) {
		return type.getCategory() == MobCategory.MONSTER && !UNAGGRESSIVE_MONSTERS.contains(type);
	}

	// Resolved on first use because other mods may not have registered their mobs during our init.
	private Set<EntityType<?>> mobOverrideTypes() {
		if (this.mobOverrideTypes == null) {
			Set<EntityType<?>> types = new ReferenceOpenHashSet<>();

			for (String name : this.mobOverrides) {
				Identifier id = Identifier.tryParse(name);

				if (id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
					types.add(BuiltInRegistries.ENTITY_TYPE.getValue(id));
				}
			}

			this.mobOverrideTypes = types;
		}

		return this.mobOverrideTypes;
	}

	// Resolved on first use because other mods may not have registered their blocks during our init.
	private Set<Block> excluded() {
		if (this.excluded == null) {
			Set<Block> blocks = new ReferenceOpenHashSet<>();

			for (String name : this.excludedBlocks) {
				Identifier id = Identifier.tryParse(name);

				if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
					blocks.add(BuiltInRegistries.BLOCK.getValue(id));
				}
			}

			this.excluded = blocks;
		}

		return this.excluded;
	}

	public static boolean isValidColor(String hex) {
		return hex != null && hex.matches("[0-9a-fA-F]{8}");
	}

	private static int parseColor(String hex, int fallback) {
		if (isValidColor(hex)) {
			return Integer.parseUnsignedInt(hex, 16);
		}

		BaseTracerClient.LOGGER.warn("Ignoring invalid color: {}", hex);
		return fallback;
	}
}
