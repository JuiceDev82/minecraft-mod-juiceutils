package com.juiceclient;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

/**
 * One combination of blocks and entities that marks a chunk as a base. A chunk matches when it holds at least
 * {@link #minBlocks} of the rule's blocks and entities, of at least {@link #minTypes} different types. The light
 * sources rule has no block list: it matches every light-emitting block that isn't excluded.
 */
public final class BaseRule {
	public static final int MAX_MIN_BLOCKS = 64;
	public static final int MAX_MIN_TYPES = 16;
	public static final int MAX_PRIORITY = 10;
	public static final int DEFAULT_PRIORITY = 5;
	/** Stand-ins in the entity list for any mob with a name tag, and any tamed animal, whatever their type. */
	public static final String NAMED_MOB = "juiceclient:named_mob";
	public static final String TAMED_ANIMAL = "juiceclient:tamed_animal";
	private static final List<String> COLORS = List.of(
			"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
			"light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
	);

	public final boolean lightSources;
	public String name;
	public boolean enabled = true;
	public int minBlocks;
	public int minTypes;
	public boolean showLabel = true;
	public boolean showTracer = true;
	/** 1 to {@link #MAX_PRIORITY}. When there are more bases than are shown, those of higher priority win. */
	public int priority = DEFAULT_PRIORITY;
	private String color = "FFFFFFFF";
	// Kept as ids so that entries from a temporarily removed mod aren't lost.
	private final TreeSet<String> blockIds;
	private final TreeSet<String> entityIds = new TreeSet<>();
	private @Nullable Set<Block> blocks;
	private @Nullable Set<EntityType<?>> entities;

	public BaseRule(String name, Collection<String> blockIds, int minBlocks, int minTypes) {
		this(false, name, blockIds, minBlocks, minTypes);
	}

	private BaseRule(boolean lightSources, String name, Collection<String> blockIds, int minBlocks, int minTypes) {
		this.lightSources = lightSources;
		this.name = name;
		this.blockIds = new TreeSet<>(blockIds);
		this.minBlocks = minBlocks;
		this.minTypes = minTypes;
	}

	public static BaseRule lightSources() {
		BaseRule rule = new BaseRule(true, "Light Sources", List.of(), 1, 1);
		rule.color = "CC00FFFF";
		// The most common find, and the least telling.
		rule.priority = 1;
		return rule;
	}

	private BaseRule withPriority(int priority) {
		this.priority = priority;
		return this;
	}

	private BaseRule withColor(String color) {
		this.color = color;
		return this;
	}

	private BaseRule withEntities(String... ids) {
		this.entityIds.addAll(List.of(ids));
		return this;
	}

	public String color() {
		return this.color;
	}

	/** Ignores anything that isn't an AARRGGBB hex color. */
	public void setColor(String color) {
		if (JuiceClientConfig.isValidColor(color)) {
			this.color = color;
		}
	}

	public int colorArgb() {
		return Integer.parseUnsignedInt(this.color, 16);
	}

	/** Chosen to rarely match generated structures, so that a match says something about players. */
	public static List<BaseRule> defaults() {
		List<String> playerStorage = new ArrayList<>(List.of("minecraft:shulker_box", "minecraft:ender_chest"));

		for (String color : COLORS) {
			playerStorage.add("minecraft:" + color + "_shulker_box");
		}

		List<BaseRule> rules = new ArrayList<>();
		rules.add(lightSources());
		rules.add(new BaseRule("Player Storage", playerStorage, 1, 1).withColor("FFFF55FF").withPriority(9));
		rules.add(new BaseRule("Rare Builds", ids("beacon", "conduit", "lodestone", "respawn_anchor"), 1, 1).withColor("FFFFFF55").withPriority(7));
		rules.add(new BaseRule("Storage Room", ids(
				"barrel", "chest", "trapped_chest",
				"copper_chest", "exposed_copper_chest", "weathered_copper_chest", "oxidized_copper_chest",
				"waxed_copper_chest", "waxed_exposed_copper_chest", "waxed_weathered_copper_chest", "waxed_oxidized_copper_chest"
		), 8, 1).withColor("FF55FF55").withPriority(8));
		rules.add(new BaseRule("Workstations", ids(
				"anvil", "blast_furnace", "brewing_stand", "cartography_table", "chipped_anvil", "crafter", "crafting_table", "damaged_anvil",
				"enchanting_table", "fletching_table", "furnace", "grindstone", "loom", "smithing_table", "smoker", "stonecutter"
		), 4, 4).withColor("FFFF8800").withPriority(6));
		rules.add(new BaseRule("Redstone", ids(
				"comparator", "dispenser", "dropper", "hopper", "observer", "piston", "repeater", "sticky_piston"
		// Four types, because jungle temples generate with three.
		), 5, 4).withColor("FFFF5555"));
		rules.addAll(entityDefaults());
		rules.add(custom());
		return rules;
	}

	/** Entities that don't generate on their own, apart from the odd item frame in end cities. */
	static List<BaseRule> entityDefaults() {
		return List.of(
				new BaseRule("Player Entities", List.of(), 2, 1)
						.withEntities("minecraft:armor_stand", "minecraft:glow_item_frame", "minecraft:item_frame", "minecraft:painting")
						.withColor("FFFFAAAA").withPriority(6),
				new BaseRule("Pets & Named Mobs", List.of(), 1, 1).withEntities(NAMED_MOB, TAMED_ANIMAL).withColor("FFAA66FF").withPriority(4)
		);
	}

	/** Starts empty, so it matches nothing until blocks are chosen for it. */
	static BaseRule custom() {
		return new BaseRule("Custom", List.of(), 1, 1);
	}

	private static List<String> ids(String... paths) {
		List<String> ids = new ArrayList<>(paths.length);

		for (String path : paths) {
			ids.add("minecraft:" + path);
		}

		return ids;
	}

	static BaseRule restored(boolean lightSources, String name, Collection<String> blockIds, int minBlocks, int minTypes) {
		return new BaseRule(lightSources, name, blockIds, minBlocks, minTypes);
	}

	public Set<String> blockIds() {
		return this.blockIds;
	}

	public void setBlock(Block block, boolean included) {
		String id = BuiltInRegistries.BLOCK.getKey(block).toString();

		if (included) {
			this.blockIds.add(id);
		} else {
			this.blockIds.remove(id);
		}

		this.blocks = null;
	}

	public Set<String> entityIds() {
		return this.entityIds;
	}

	public void setEntity(String id, boolean included) {
		if (included) {
			this.entityIds.add(id);
		} else {
			this.entityIds.remove(id);
		}

		this.entities = null;
	}

	public boolean matchesNamedMobs() {
		return this.entityIds.contains(NAMED_MOB);
	}

	public boolean matchesTamedAnimals() {
		return this.entityIds.contains(TAMED_ANIMAL);
	}

	public boolean hasEntities() {
		return !this.entities().isEmpty() || this.matchesNamedMobs() || this.matchesTamedAnimals();
	}

	/** Variants such as wall torches share their block's name, and count as one type. */
	public int blockTypeCount() {
		return (int) this.blocks().stream().map(block -> block.getName().getString()).distinct().count();
	}

	public int entityChoiceCount() {
		return this.entities().size() + (this.matchesNamedMobs() ? 1 : 0) + (this.matchesTamedAnimals() ? 1 : 0);
	}

	public int typeCount() {
		return this.blockTypeCount() + this.entityChoiceCount();
	}

	// Resolved on first use because other mods may not have registered their entities during our init.
	public Set<EntityType<?>> entities() {
		if (this.entities == null) {
			Set<EntityType<?>> resolved = new ReferenceOpenHashSet<>();

			for (String name : this.entityIds) {
				Identifier id = Identifier.tryParse(name);

				if (id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
					resolved.add(BuiltInRegistries.ENTITY_TYPE.getValue(id));
				}
			}

			this.entities = resolved;
		}

		return this.entities;
	}

	// Resolved on first use because other mods may not have registered their blocks during our init.
	public Set<Block> blocks() {
		if (this.blocks == null) {
			Set<Block> resolved = new ReferenceOpenHashSet<>();

			for (String name : this.blockIds) {
				Identifier id = Identifier.tryParse(name);

				if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
					resolved.add(BuiltInRegistries.BLOCK.getValue(id));
				}
			}

			this.blocks = resolved;
		}

		return this.blocks;
	}
}
