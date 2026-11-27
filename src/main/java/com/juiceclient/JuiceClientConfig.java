package com.juiceclient;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
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
 * Edited through the settings screen and stored in config/juiceclient.properties. Colors are ARGB hex strings.
 */
public final class JuiceClientConfig {
	private static final String FILE_NAME = "juiceclient.properties";
	public static final int MAX_BASES_LIMIT = 50;
	public static final int MIN_MOB_WARNING_DISTANCE = 3;
	public static final int MAX_SMART_TOOL_DELAY = 40;
	public static final int MAX_AUTO_EAT_HUNGER = 19;
	public static final int MAX_AUTO_EAT_HEALTH = 95;
	public static final int MAX_TAKE_ALL_DELAY = 10;
	public static final int MAX_AUTO_TOTEM_DELAY = 100;
	public static final int MAX_AUTO_TOTEM_HEALTH = 10;
	public static final int MIN_AUTO_CLUTCH_HEIGHT = 4;
	public static final int MAX_AUTO_CLUTCH_HEIGHT = 64;
	public static final int MAX_AUTO_WEAPON_RETURN = 200;
	public static final int MAX_SORT_DELAY = 10;
	public static final int MIN_NAME_TAG_RANGE = 8;
	public static final int MAX_NAME_TAG_RANGE = 64;
	public static final int MAX_MOB_WARNING_DISTANCE = 32;
	/** From when this mod was called Base Tracer. */
	private static final String BASE_TRACER_FILE_NAME = "basetracer.properties";
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
	/** Shows where a held throwable, a drawn bow or a loaded crossbow would land. */
	public boolean trajectories = true;
	/** A water bucket or cobweb in the hotbar is placed to break a fall longer than {@link #autoClutchHeight}. */
	public boolean autoClutch = true;
	public int autoClutchHeight = 10;
	public String trajectoryColor = "FFFFFFFF";
	/** Boss bars with the same name are shown as one, with a count. */
	public boolean stackBossBars = true;
	/** Worn armor shown beside the hotbar with how much durability is left, in place of the armor bar. */
	public boolean armorStatus = true;
	/** Ticks of mining before the tool is switched, so that a misclick doesn't switch it. */
	public int smartToolDelay = 10;
	public boolean autoEat = true;
	/** Whether food from the rest of the inventory is moved into the hotbar once the hotbar has none. */
	public boolean autoEatResupply = true;
	public QuickUseAction quickUseAction = QuickUseAction.ENDER_PEARL;
	public boolean autoTotem = true;
	/** A fall that could kill also puts a totem in straight away, as low health does. */
	public boolean autoTotemFalls = true;
	public boolean autoArmor = true;
	/** Items on the Auto Drop list are thrown out as soon as they're in the inventory. */
	public boolean autoDrop = true;
	/** Items on the Anti Drop list can't be thrown out. */
	public boolean antiDrop = true;
	public boolean autoWeapon = true;
	/** Ticks without hitting anything before the slot from before the weapon comes back. 0 keeps the weapon. */
	public int autoWeaponReturn = 60;
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
	/** Sort buttons on storage screens and the inventory. */
	public boolean sortButtons = true;
	public SortMode sortMode = SortMode.TYPE;
	/** Most ticks between the clicks of a sort; each wait is one up to this. 0 sorts at once. */
	public int sortDelay = 3;
	/** Eats once the hunger bar is at or below this, out of 20. */
	public int autoEatHunger = 14;
	/** Also eats once health is below this percent of the maximum. 0 turns it off. */
	public int autoEatHealth = 50;
	/** How many bases are shown, the highest priority and then the nearest. Above {@link #MAX_BASES_LIMIT} means all. */
	public int maxBases = 10;
	/** Percent. Each rule's color fills its highlight at this opacity, and more for busier chunks. */
	public int fillOpacity = 9;
	public String hostileMobColor = "FFFF3333";
	public String playerColor = "FF55AAFF";
	public float lineWidth = 2.0F;
	public static final int MIN_LABEL_SIZE = 50;
	public static final int MAX_LABEL_SIZE = 150;
	/** How big labels in the world are drawn, in percent of the size before this setting existed. */
	public int labelSize = 80;
	/** Blocks per second. */
	public float freeLookSpeed = 20.0F;
	// Kept as ids so that entries from a temporarily removed mod aren't lost.
	private TreeSet<String> excludedBlocks = new TreeSet<>(DEFAULT_EXCLUDED_BLOCKS);
	// Only what differs from the default, so that hostile mobs from a newly added mod are on the list without asking.
	private TreeSet<String> mobOverrides = new TreeSet<>();
	private TreeSet<String> xrayBlockIds = new TreeSet<>(DEFAULT_XRAY_BLOCKS);
	private TreeSet<String> putAllItemIds = new TreeSet<>();
	private TreeSet<String> autoDropItemIds = new TreeSet<>();
	private TreeSet<String> antiDropItemIds = new TreeSet<>();
	/** Items Sort puts at the front, and at the back, ahead of the sort order. */
	private TreeSet<String> sortFrontIds = new TreeSet<>();
	private TreeSet<String> sortBackIds = new TreeSet<>();
	/** The order Sort by Type puts its groups in, every group once. */
	private ArrayList<ItemGroup> sortGroupOrder = new ArrayList<>(List.of(ItemGroup.values()));
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
	private transient int trajectoryArgb;

	public static JuiceClientConfig load(Path configDir) {
		Path file = configDir.resolve(FILE_NAME);
		Path baseTracerFile = configDir.resolve(BASE_TRACER_FILE_NAME);
		Path oldNameFile = configDir.resolve(OLD_NAME_FILE_NAME);
		Path legacyFile = configDir.resolve(LEGACY_FILE_NAME);
		JuiceClientConfig config;

		if (Files.exists(file)) {
			config = readProperties(file);
		} else if (Files.exists(baseTracerFile)) {
			config = readProperties(baseTracerFile);
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
				Files.deleteIfExists(baseTracerFile);
				Files.deleteIfExists(oldNameFile);
				Files.deleteIfExists(legacyFile);
			} catch (IOException e) {
				JuiceClient.LOGGER.warn("Failed to delete old settings in {}", configDir, e);
			}
		}

		return config;
	}

	private static JuiceClientConfig readProperties(Path file) {
		JuiceClientConfig config = new JuiceClientConfig();
		Properties properties = new Properties();

		try (Reader reader = Files.newBufferedReader(file)) {
			properties.load(reader);
		} catch (IOException | IllegalArgumentException e) {
			JuiceClient.LOGGER.error("Failed to read {}, using defaults", file, e);
			return config;
		}

		return fromProperties(properties);
	}

	/** Settings read from properties as {@link #toProperties} writes them, with defaults for anything missing. */
	public static JuiceClientConfig fromProperties(Properties properties) {
		JuiceClientConfig config = new JuiceClientConfig();
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
			JuiceClient.LOGGER.warn("Ignoring unknown item mode: {}", properties.getProperty("itemMode"));
		}
		config.mobWarningDistance = Mth.clamp(readInt(properties, "mobWarningDistance", config.mobWarningDistance), MIN_MOB_WARNING_DISTANCE, MAX_MOB_WARNING_DISTANCE);
		config.xrayEnabled = Boolean.parseBoolean(properties.getProperty("xrayEnabled", String.valueOf(config.xrayEnabled)));
		config.smartTool = Boolean.parseBoolean(properties.getProperty("smartTool", String.valueOf(config.smartTool)));
		config.trajectories = Boolean.parseBoolean(properties.getProperty("trajectories", String.valueOf(config.trajectories)));
		config.autoClutch = Boolean.parseBoolean(properties.getProperty("autoClutch", String.valueOf(config.autoClutch)));
		config.autoClutchHeight = Mth.clamp(readInt(properties, "autoClutchHeight", config.autoClutchHeight), MIN_AUTO_CLUTCH_HEIGHT, MAX_AUTO_CLUTCH_HEIGHT);
		config.trajectoryColor = properties.getProperty("trajectoryColor", config.trajectoryColor);
		config.stackBossBars = Boolean.parseBoolean(properties.getProperty("stackBossBars", String.valueOf(config.stackBossBars)));
		config.armorStatus = Boolean.parseBoolean(properties.getProperty("armorStatus", String.valueOf(config.armorStatus)));
		config.smartToolDelay = Mth.clamp(readInt(properties, "smartToolDelay", config.smartToolDelay), 0, MAX_SMART_TOOL_DELAY);
		config.autoEat = Boolean.parseBoolean(properties.getProperty("autoEat", String.valueOf(config.autoEat)));
		config.takeAll = Boolean.parseBoolean(properties.getProperty("takeAll", String.valueOf(config.takeAll)));
		config.autoTotem = Boolean.parseBoolean(properties.getProperty("autoTotem", String.valueOf(config.autoTotem)));
		config.autoTotemFalls = Boolean.parseBoolean(properties.getProperty("autoTotemFalls", String.valueOf(config.autoTotemFalls)));
		config.autoArmor = Boolean.parseBoolean(properties.getProperty("autoArmor", String.valueOf(config.autoArmor)));
		config.autoDrop = Boolean.parseBoolean(properties.getProperty("autoDrop", String.valueOf(config.autoDrop)));
		config.antiDrop = Boolean.parseBoolean(properties.getProperty("antiDrop", String.valueOf(config.antiDrop)));
		config.autoWeapon = Boolean.parseBoolean(properties.getProperty("autoWeapon", String.valueOf(config.autoWeapon)));
		config.autoWeaponReturn = Mth.clamp(readInt(properties, "autoWeaponReturn", config.autoWeaponReturn), 0, MAX_AUTO_WEAPON_RETURN);
		config.autoTotemDelay = Mth.clamp(readInt(properties, "autoTotemDelay", config.autoTotemDelay), 0, MAX_AUTO_TOTEM_DELAY);
		config.autoTotemHealth = Mth.clamp(readInt(properties, "autoTotemHealth", config.autoTotemHealth), 0, MAX_AUTO_TOTEM_HEALTH);
		config.takeAllDelay = Mth.clamp(readInt(properties, "takeAllDelay", config.takeAllDelay), 0, MAX_TAKE_ALL_DELAY);
		config.putAllHotbar = Boolean.parseBoolean(properties.getProperty("putAllHotbar", String.valueOf(config.putAllHotbar)));
		config.nameTags = Boolean.parseBoolean(properties.getProperty("nameTags", String.valueOf(config.nameTags)));
		config.nameTagRange = Mth.clamp(readInt(properties, "nameTagRange", config.nameTagRange), MIN_NAME_TAG_RANGE, MAX_NAME_TAG_RANGE);
		readIds(properties.getProperty("nameTagEntities", ""), config.nameTagEntityIds);
		readIds(properties.getProperty("nameTagBlocks", ""), config.nameTagBlockIds);

		config.sortButtons = Boolean.parseBoolean(properties.getProperty("sortButtons", String.valueOf(config.sortButtons)));
		config.sortDelay = Mth.clamp(readInt(properties, "sortDelay", config.sortDelay), 0, MAX_SORT_DELAY);

		try {
			config.sortMode = SortMode.valueOf(properties.getProperty("sortMode", config.sortMode.name()));
		} catch (IllegalArgumentException e) {
			JuiceClient.LOGGER.warn("Ignoring unknown sort mode: {}", properties.getProperty("sortMode"));
		}

		try {
			config.putAllFilter = PutAllFilter.valueOf(properties.getProperty("putAllFilter", config.putAllFilter.name()));
		} catch (IllegalArgumentException e) {
			JuiceClient.LOGGER.warn("Ignoring unknown put all filter: {}", properties.getProperty("putAllFilter"));
		}

		for (String item : properties.getProperty("putAllItems", "").split(",")) {
			if (!item.isBlank()) {
				config.putAllItemIds.add(item.trim());
			}
		}

		readIds(properties.getProperty("autoDropItems", ""), config.autoDropItemIds);
		readIds(properties.getProperty("antiDropItems", ""), config.antiDropItemIds);
		readIds(properties.getProperty("sortFront", ""), config.sortFrontIds);
		readIds(properties.getProperty("sortBack", ""), config.sortBackIds);
		config.sortGroupOrder = readGroupOrder(properties.getProperty("sortGroupOrder", ""));

		try {
			config.quickUseAction = QuickUseAction.valueOf(properties.getProperty("quickUseAction", config.quickUseAction.name()));
		} catch (IllegalArgumentException e) {
			JuiceClient.LOGGER.warn("Ignoring unknown quick use action: {}", properties.getProperty("quickUseAction"));
		}
		config.autoEatResupply = Boolean.parseBoolean(properties.getProperty("autoEatResupply", String.valueOf(config.autoEatResupply)));
		config.autoEatHunger = Mth.clamp(readInt(properties, "autoEatHunger", config.autoEatHunger), 1, MAX_AUTO_EAT_HUNGER);
		config.autoEatHealth = Mth.clamp(readInt(properties, "autoEatHealth", config.autoEatHealth), 0, MAX_AUTO_EAT_HEALTH);
		config.maxBases = Mth.clamp(readInt(properties, "maxBases", config.maxBases), 1, MAX_BASES_LIMIT + 1);
		config.fillOpacity = Mth.clamp(readInt(properties, "fillOpacity", config.fillOpacity), 0, 100);
		config.hostileMobColor = properties.getProperty("hostileMobColor", config.hostileMobColor);
		config.playerColor = properties.getProperty("playerColor", config.playerColor);

		try {
			config.lineWidth = Float.parseFloat(properties.getProperty("lineWidth", String.valueOf(config.lineWidth)));
		} catch (NumberFormatException e) {
			JuiceClient.LOGGER.warn("Ignoring invalid line width: {}", properties.getProperty("lineWidth"));
		}

		config.labelSize = Mth.clamp(readInt(properties, "labelSize", config.labelSize), MIN_LABEL_SIZE, MAX_LABEL_SIZE);

		try {
			config.freeLookSpeed = Float.parseFloat(properties.getProperty("freeLookSpeed", String.valueOf(config.freeLookSpeed)));
		} catch (NumberFormatException e) {
			JuiceClient.LOGGER.warn("Ignoring invalid free look speed: {}", properties.getProperty("freeLookSpeed"));
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

	private static void readRules(JuiceClientConfig config, Properties properties) {
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
			JuiceClient.LOGGER.warn("Ignoring invalid value for {}: {}", key, value);
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
			JuiceClient.LOGGER.warn("Ignoring invalid tracer mode: {}", mode);
			return TracerMode.NEAREST_PER_CHUNK;
		}
	}

	// Settings used to be stored as JSON.
	private static JuiceClientConfig readLegacyJson(Path file) {
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file)) {
				JuiceClientConfig config = new Gson().fromJson(reader, JuiceClientConfig.class);

				if (config != null) {
					if (Boolean.TRUE.equals(config.legacyTracerPerSource)) {
						config.tracerMode = TracerMode.EVERY_SOURCE;
					}

					return config;
				}
			} catch (IOException | JsonParseException e) {
				JuiceClient.LOGGER.error("Failed to import {}, using defaults", file, e);
			}
		}

		return new JuiceClientConfig();
	}

	/** Every setting, as saved to the settings file and to exports. */
	public Properties toProperties() {
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
		properties.setProperty("trajectories", String.valueOf(this.trajectories));
		properties.setProperty("autoClutch", String.valueOf(this.autoClutch));
		properties.setProperty("autoClutchHeight", String.valueOf(this.autoClutchHeight));
		properties.setProperty("trajectoryColor", this.trajectoryColor);
		properties.setProperty("stackBossBars", String.valueOf(this.stackBossBars));
		properties.setProperty("armorStatus", String.valueOf(this.armorStatus));
		properties.setProperty("smartToolDelay", String.valueOf(this.smartToolDelay));
		properties.setProperty("autoEat", String.valueOf(this.autoEat));
		properties.setProperty("quickUseAction", this.quickUseAction.name());
		properties.setProperty("takeAll", String.valueOf(this.takeAll));
		properties.setProperty("autoTotem", String.valueOf(this.autoTotem));
		properties.setProperty("autoTotemFalls", String.valueOf(this.autoTotemFalls));
		properties.setProperty("autoEatResupply", String.valueOf(this.autoEatResupply));
		properties.setProperty("autoArmor", String.valueOf(this.autoArmor));
		properties.setProperty("autoDrop", String.valueOf(this.autoDrop));
		properties.setProperty("autoDropItems", String.join(",", this.autoDropItemIds));
		properties.setProperty("antiDrop", String.valueOf(this.antiDrop));
		properties.setProperty("antiDropItems", String.join(",", this.antiDropItemIds));
		properties.setProperty("autoWeapon", String.valueOf(this.autoWeapon));
		properties.setProperty("autoWeaponReturn", String.valueOf(this.autoWeaponReturn));
		properties.setProperty("autoTotemDelay", String.valueOf(this.autoTotemDelay));
		properties.setProperty("autoTotemHealth", String.valueOf(this.autoTotemHealth));
		properties.setProperty("takeAllDelay", String.valueOf(this.takeAllDelay));
		properties.setProperty("putAllHotbar", String.valueOf(this.putAllHotbar));
		properties.setProperty("putAllFilter", this.putAllFilter.name());
		properties.setProperty("sortButtons", String.valueOf(this.sortButtons));
		properties.setProperty("sortMode", this.sortMode.name());
		properties.setProperty("sortDelay", String.valueOf(this.sortDelay));
		properties.setProperty("putAllItems", String.join(",", this.putAllItemIds));
		properties.setProperty("sortFront", String.join(",", this.sortFrontIds));
		properties.setProperty("sortBack", String.join(",", this.sortBackIds));
		properties.setProperty("sortGroupOrder", String.join(",", this.sortGroupOrder.stream().map(ItemGroup::name).toList()));
		properties.setProperty("nameTags", String.valueOf(this.nameTags));
		properties.setProperty("nameTagRange", String.valueOf(this.nameTagRange));
		properties.setProperty("nameTagEntities", String.join(",", this.nameTagEntityIds));
		properties.setProperty("nameTagBlocks", String.join(",", this.nameTagBlockIds));
		properties.setProperty("autoEatHunger", String.valueOf(this.autoEatHunger));
		properties.setProperty("autoEatHealth", String.valueOf(this.autoEatHealth));
		properties.setProperty("maxBases", String.valueOf(this.maxBases));
		properties.setProperty("xrayBlocks", String.join(",", this.xrayBlockIds));
		properties.setProperty("fillOpacity", String.valueOf(this.fillOpacity));
		properties.setProperty("hostileMobColor", this.hostileMobColor);
		properties.setProperty("playerColor", this.playerColor);
		properties.setProperty("lineWidth", String.valueOf(this.lineWidth));
		properties.setProperty("labelSize", String.valueOf(this.labelSize));
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

		return properties;
	}

	/**
	 * Takes on every setting of another config, such as one just imported, keeping this one's file. Everything that's
	 * worked out from the settings is worked out again.
	 */
	public void copyFrom(JuiceClientConfig other) {
		for (java.lang.reflect.Field field : JuiceClientConfig.class.getDeclaredFields()) {
			if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.getName().equals("file")) {
				continue;
			}

			try {
				field.setAccessible(true);
				field.set(this, field.get(other));
			} catch (IllegalAccessException e) {
				throw new IllegalStateException("Can't copy setting " + field.getName(), e);
			}
		}

		this.refreshColors();
		this.markRulesChanged();
	}

	public void save() {
		Properties properties = this.toProperties();

		try (Writer writer = Files.newBufferedWriter(this.file)) {
			properties.store(writer, "Juice Client settings. Change these in-game through the settings screen.");
		} catch (IOException e) {
			JuiceClient.LOGGER.error("Failed to write {}", this.file, e);
		}
	}

	public void refreshColors() {
		this.hostileMobArgb = parseColor(this.hostileMobColor, 0xFFFF3333);
		this.playerArgb = parseColor(this.playerColor, 0xFF55AAFF);
		this.itemArgb = parseColor(this.itemColor, 0xFFFFCC33);
		this.trajectoryArgb = parseColor(this.trajectoryColor, 0xFFFFFFFF);
	}

	public int trajectoryArgb() {
		return this.trajectoryArgb;
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

	/** Call after changing anything about a rule. {@link JuiceClient#applyRuleChanges} then applies it. */
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

	public boolean isAutoDropListed(Item item) {
		return this.autoDropItemIds.contains(BuiltInRegistries.ITEM.getKey(item).toString());
	}

	/** Listed for Auto Drop, unless Anti Drop protects it too. */
	public boolean isAutoDropped(net.minecraft.world.item.ItemStack stack) {
		return !stack.isEmpty() && this.isAutoDropListed(stack.getItem()) && !(this.antiDrop && this.isAntiDropped(stack));
	}

	public boolean hasAutoDropItems() {
		return !this.autoDropItemIds.isEmpty();
	}

	public void setAutoDropItem(Item item, boolean listed) {
		String id = BuiltInRegistries.ITEM.getKey(item).toString();

		if (listed) {
			this.autoDropItemIds.add(id);
		} else {
			this.autoDropItemIds.remove(id);
		}
	}

	public void clearAutoDropItems() {
		this.autoDropItemIds.clear();
	}

	public boolean isAntiDropListed(Item item) {
		return this.antiDropItemIds.contains(BuiltInRegistries.ITEM.getKey(item).toString());
	}

	public boolean isAntiDropped(net.minecraft.world.item.ItemStack stack) {
		return !stack.isEmpty() && this.isAntiDropListed(stack.getItem());
	}

	public boolean hasAntiDropItems() {
		return !this.antiDropItemIds.isEmpty();
	}

	public void setAntiDropItem(Item item, boolean listed) {
		String id = BuiltInRegistries.ITEM.getKey(item).toString();

		if (listed) {
			this.antiDropItemIds.add(id);
		} else {
			this.antiDropItemIds.remove(id);
		}
	}

	public void clearAntiDropItems() {
		this.antiDropItemIds.clear();
	}

	/** Whether Sort puts this item at the front, at the back, or by the sort order. */
	public SortPriority sortPriority(Item item) {
		String id = BuiltInRegistries.ITEM.getKey(item).toString();
		return this.sortFrontIds.contains(id) ? SortPriority.FRONT : this.sortBackIds.contains(id) ? SortPriority.BACK : SortPriority.NORMAL;
	}

	public void setSortPriority(Item item, SortPriority priority) {
		String id = BuiltInRegistries.ITEM.getKey(item).toString();
		this.sortFrontIds.remove(id);
		this.sortBackIds.remove(id);

		switch (priority) {
			case FRONT -> this.sortFrontIds.add(id);
			case BACK -> this.sortBackIds.add(id);
			case NORMAL -> {
			}
		}
	}

	public void clearSortPriorities() {
		this.sortFrontIds.clear();
		this.sortBackIds.clear();
	}

	public List<ItemGroup> sortGroupOrder() {
		return Collections.unmodifiableList(this.sortGroupOrder);
	}

	/** Where a group comes in Sort by Type, 0 first. */
	public int sortGroupRank(ItemGroup group) {
		return this.sortGroupOrder.indexOf(group);
	}

	/** Moves a group to this place in the order, shifting the ones between along. */
	public void moveSortGroup(ItemGroup group, int place) {
		this.sortGroupOrder.remove(group);
		this.sortGroupOrder.add(Math.clamp(place, 0, this.sortGroupOrder.size()), group);
	}

	public void resetSortGroupOrder() {
		this.sortGroupOrder = new ArrayList<>(List.of(ItemGroup.values()));
	}

	/** The saved group order, leaving out unknown names and adding any group that's missing, such as a new one, last. */
	private static ArrayList<ItemGroup> readGroupOrder(String list) {
		ArrayList<ItemGroup> order = new ArrayList<>();

		for (String name : list.split(",")) {
			try {
				ItemGroup group = ItemGroup.valueOf(name.trim());

				if (!order.contains(group)) {
					order.add(group);
				}
			} catch (IllegalArgumentException e) {
				if (!name.isBlank()) {
					JuiceClient.LOGGER.warn("Ignoring unknown sort group: {}", name);
				}
			}
		}

		for (ItemGroup group : ItemGroup.values()) {
			if (!order.contains(group)) {
				order.add(group);
			}
		}

		return order;
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

		JuiceClient.LOGGER.warn("Ignoring invalid color: {}", hex);
		return fallback;
	}
}
