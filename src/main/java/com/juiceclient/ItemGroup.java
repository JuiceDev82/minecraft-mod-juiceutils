package com.juiceclient;

import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

/**
 * The groups Sort by Type puts things in, in the order they come. Items are matched by the game's own tags where there
 * are some, so modded logs, ores and the like fall into the right group too, and by name or a short list otherwise.
 */
public enum ItemGroup {
	ARMOR,
	WEAPONS,
	TOOLS,
	WOOD,
	STONE,
	FOOD,
	FARMING,
	MINERALS,
	NATURE,
	COLORED,
	REDSTONE,
	UTILITY,
	POTIONS,
	MOB_DROPS,
	OTHER_BLOCKS,
	OTHER_ITEMS;

	private final Component label = Component.translatable("options.juiceclient.itemGroup." + this.name().toLowerCase(java.util.Locale.ROOT));

	public Component label() {
		return this.label;
	}

	private static final Set<Item> WEAPON_ITEMS = Set.of(Items.BOW, Items.CROSSBOW, Items.TRIDENT, Items.MACE, Items.WIND_CHARGE);
	private static final Set<Item> ARMOR_ITEMS = Set.of(Items.SHIELD, Items.TOTEM_OF_UNDYING, Items.ELYTRA);
	private static final Set<Item> TOOL_ITEMS = Set.of(Items.SHEARS, Items.FLINT_AND_STEEL, Items.FISHING_ROD, Items.BRUSH, Items.CLOCK,
			Items.SPYGLASS, Items.LEAD, Items.NAME_TAG, Items.SADDLE, Items.FIREWORK_ROCKET, Items.CARROT_ON_A_STICK,
			Items.WARPED_FUNGUS_ON_A_STICK, Items.MAP, Items.FILLED_MAP);
	/** Mob drops that are also food, but only eaten when there's nothing else. */
	private static final Set<Item> BAD_FOOD = Set.of(Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.POISONOUS_POTATO);
	private static final Set<Item> MOB_DROP_ITEMS = Set.of(Items.STRING, Items.BONE, Items.GUNPOWDER, Items.SLIME_BALL, Items.ENDER_PEARL,
			Items.ENDER_EYE, Items.BLAZE_ROD, Items.BLAZE_POWDER, Items.BREEZE_ROD, Items.LEATHER, Items.FEATHER, Items.INK_SAC,
			Items.GLOW_INK_SAC, Items.PHANTOM_MEMBRANE, Items.GHAST_TEAR, Items.SHULKER_SHELL, Items.RABBIT_HIDE, Items.RABBIT_FOOT,
			Items.MAGMA_CREAM, Items.ARMADILLO_SCUTE, Items.TURTLE_SCUTE, Items.NAUTILUS_SHELL, Items.PRISMARINE_SHARD,
			Items.PRISMARINE_CRYSTALS, Items.HONEYCOMB, Items.NETHER_STAR, Items.DRAGON_BREATH, Items.EGG);
	private static final Set<Item> FARMING_ITEMS = Set.of(Items.WHEAT, Items.SUGAR_CANE, Items.BONE_MEAL, Items.BAMBOO, Items.CACTUS,
			Items.PUMPKIN, Items.CARVED_PUMPKIN, Items.MELON, Items.NETHER_WART, Items.COCOA_BEANS, Items.HAY_BLOCK, Items.KELP,
			Items.COMPOSTER, Items.PITCHER_POD, Items.FARMLAND, Items.SWEET_BERRIES, Items.GLOW_BERRIES);
	private static final Set<String> MINERAL_IDS = Set.of("diamond", "emerald", "lapis_lazuli", "quartz", "amethyst_shard",
			"netherite_scrap", "ancient_debris", "coal", "charcoal", "diamond_block", "emerald_block", "iron_block", "gold_block",
			"netherite_block", "lapis_block", "coal_block", "amethyst_block", "copper_block", "resin_clump", "flint", "clay_ball",
			"brick", "nether_brick", "glowstone_dust");
	private static final Set<Item> REDSTONE_ITEMS = Set.of(Items.REDSTONE, Items.REDSTONE_TORCH, Items.REDSTONE_BLOCK, Items.REPEATER,
			Items.COMPARATOR, Items.PISTON, Items.STICKY_PISTON, Items.OBSERVER, Items.HOPPER, Items.DROPPER, Items.DISPENSER,
			Items.LEVER, Items.TRIPWIRE_HOOK, Items.DAYLIGHT_DETECTOR, Items.TARGET, Items.REDSTONE_LAMP, Items.NOTE_BLOCK, Items.TNT,
			Items.SLIME_BLOCK, Items.HONEY_BLOCK, Items.SCULK_SENSOR, Items.CALIBRATED_SCULK_SENSOR, Items.CRAFTER,
			Items.STONE_BUTTON, Items.STONE_PRESSURE_PLATE, Items.LIGHT_WEIGHTED_PRESSURE_PLATE, Items.HEAVY_WEIGHTED_PRESSURE_PLATE);
	private static final Set<Item> UTILITY_ITEMS = Set.of(Items.CHEST, Items.TRAPPED_CHEST, Items.ENDER_CHEST, Items.BARREL,
			Items.CRAFTING_TABLE, Items.FURNACE, Items.BLAST_FURNACE, Items.SMOKER, Items.ENCHANTING_TABLE, Items.SMITHING_TABLE,
			Items.CARTOGRAPHY_TABLE, Items.FLETCHING_TABLE, Items.LOOM, Items.STONECUTTER, Items.GRINDSTONE, Items.BREWING_STAND,
			Items.CAULDRON, Items.LECTERN, Items.BEACON, Items.RESPAWN_ANCHOR, Items.LODESTONE, Items.TORCH, Items.SOUL_TORCH,
			Items.LANTERN, Items.SOUL_LANTERN, Items.LADDER, Items.SCAFFOLDING, Items.CAMPFIRE, Items.SOUL_CAMPFIRE, Items.BELL,
			Items.CONDUIT, Items.JUKEBOX, Items.END_CRYSTAL, Items.ITEM_FRAME, Items.GLOW_ITEM_FRAME, Items.ARMOR_STAND,
			Items.FLOWER_POT, Items.DECORATED_POT, Items.BOOK, Items.WRITABLE_BOOK, Items.ENCHANTED_BOOK);
	private static final Set<Item> WOOD_ITEMS = Set.of(Items.STICK, Items.BOWL, Items.BOOKSHELF, Items.CHISELED_BOOKSHELF);
	private static final Set<Item> NATURE_ITEMS = Set.of(Items.GRAVEL, Items.CLAY, Items.SOUL_SAND, Items.SOUL_SOIL, Items.VINE,
			Items.LILY_PAD, Items.SHORT_GRASS, Items.TALL_GRASS, Items.FERN, Items.LARGE_FERN, Items.DEAD_BUSH, Items.SEAGRASS,
			Items.SPONGE, Items.WET_SPONGE, Items.COBWEB, Items.GLOW_LICHEN, Items.SNOWBALL, Items.HANGING_ROOTS);
	private static final Set<Item> POTION_ITEMS = Set.of(Items.EXPERIENCE_BOTTLE, Items.GLASS_BOTTLE, Items.OMINOUS_BOTTLE);

	public static ItemGroup of(ItemStack stack) {
		Item item = stack.getItem();
		String id = BuiltInRegistries.ITEM.getKey(item).getPath();
		BlockState block = item instanceof BlockItem blockItem ? blockItem.getBlock().defaultBlockState() : null;

		if (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.SPEARS) || stack.is(ItemTags.ARROWS) || WEAPON_ITEMS.contains(item)) {
			return WEAPONS;
		}

		if (block == null && stack.has(DataComponents.EQUIPPABLE) || ARMOR_ITEMS.contains(item)) {
			return ARMOR;
		}

		if (stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES)
				|| stack.is(ItemTags.COMPASSES) || stack.is(ItemTags.BUNDLES) || id.endsWith("bucket") || TOOL_ITEMS.contains(item)) {
			return TOOLS;
		}

		if (BAD_FOOD.contains(item) || MOB_DROP_ITEMS.contains(item) || stack.is(ItemTags.SKULLS)) {
			return MOB_DROPS;
		}

		if (stack.has(DataComponents.POTION_CONTENTS) || POTION_ITEMS.contains(item)) {
			return POTIONS;
		}

		if (FARMING_ITEMS.contains(item) || stack.is(ItemTags.SAPLINGS) || stack.is(ItemTags.VILLAGER_PLANTABLE_SEEDS)
				|| id.endsWith("_seeds") || block != null && block.is(BlockTags.CROPS)) {
			return FARMING;
		}

		if (stack.has(DataComponents.FOOD)) {
			return FOOD;
		}

		if (UTILITY_ITEMS.contains(item) || stack.is(ItemTags.SHULKER_BOXES) || stack.is(ItemTags.ANVIL) || id.endsWith("_chest")
				|| id.endsWith("_lantern") || id.endsWith("_torch") && !id.startsWith("redstone")) {
			return UTILITY;
		}

		if (REDSTONE_ITEMS.contains(item) || stack.is(ItemTags.RAILS) || id.contains("redstone") || id.contains("minecart") || id.contains("lightning_rod")) {
			return REDSTONE;
		}

		if (MINERAL_IDS.contains(id) || id.endsWith("_ore") || id.endsWith("_ingot") || id.startsWith("raw_") || stack.is(ItemTags.COALS)
				|| stack.is(ItemTags.METAL_NUGGETS) || stack.is(ItemTags.GOLD_ORES) || stack.is(ItemTags.IRON_ORES)
				|| stack.is(ItemTags.COPPER_ORES) || stack.is(ItemTags.DIAMOND_ORES) || stack.is(ItemTags.EMERALD_ORES)
				|| stack.is(ItemTags.LAPIS_ORES) || stack.is(ItemTags.REDSTONE_ORES) || stack.is(ItemTags.COAL_ORES)) {
			return MINERALS;
		}

		if (WOOD_ITEMS.contains(item) || stack.is(ItemTags.LOGS) || stack.is(ItemTags.PLANKS) || stack.is(ItemTags.WOODEN_STAIRS)
				|| stack.is(ItemTags.WOODEN_SLABS) || stack.is(ItemTags.WOODEN_FENCES) || stack.is(ItemTags.FENCE_GATES)
				|| stack.is(ItemTags.WOODEN_DOORS) || stack.is(ItemTags.WOODEN_TRAPDOORS) || stack.is(ItemTags.WOODEN_BUTTONS)
				|| stack.is(ItemTags.WOODEN_PRESSURE_PLATES) || stack.is(ItemTags.WOODEN_SHELVES) || stack.is(ItemTags.SIGNS)
				|| stack.is(ItemTags.HANGING_SIGNS) || stack.is(ItemTags.BOATS) || stack.is(ItemTags.CHEST_BOATS)
				|| stack.is(ItemTags.BAMBOO_BLOCKS)
				|| block != null && block.is(BlockTags.MINEABLE_WITH_AXE) && block.instrument() == NoteBlockInstrument.BASS) {
			return WOOD;
		}

		if (stack.is(ItemTags.WOOL) || stack.is(ItemTags.WOOL_CARPETS) || stack.is(ItemTags.BEDS) || stack.is(ItemTags.BANNERS)
				|| stack.is(ItemTags.CANDLES) || stack.is(ItemTags.TERRACOTTA) || stack.is(ItemTags.GLAZED_TERRACOTTA)
				|| stack.is(ItemTags.CONCRETE) || stack.is(ItemTags.CONCRETE_POWDERS) || stack.is(ItemTags.DYES)
				|| id.contains("glass") || id.endsWith("_terracotta") || id.equals("terracotta")) {
			return COLORED;
		}

		if (NATURE_ITEMS.contains(item) || stack.is(ItemTags.DIRT) || stack.is(ItemTags.SAND) || stack.is(ItemTags.MUD)
				|| stack.is(ItemTags.MOSS_BLOCKS) || stack.is(ItemTags.LEAVES) || stack.is(ItemTags.WART_BLOCKS)
				|| block != null && (block.is(BlockTags.FLOWERS) || block.is(BlockTags.ICE) || block.is(BlockTags.SNOW)
						|| block.is(BlockTags.NYLIUM) || block.is(BlockTags.CORALS) || block.is(BlockTags.CORAL_BLOCKS))) {
			return NATURE;
		}

		if (stack.is(ItemTags.STONE_BRICKS) || stack.is(ItemTags.WALLS) || stack.is(ItemTags.STONE_CRAFTING_MATERIALS)
				|| stack.is(ItemTags.STONE_TOOL_MATERIALS)
				|| block != null && (block.is(BlockTags.BASE_STONE_OVERWORLD) || block.is(BlockTags.BASE_STONE_NETHER)
						|| block.is(BlockTags.MINEABLE_WITH_PICKAXE) && block.instrument() == NoteBlockInstrument.BASEDRUM)) {
			return STONE;
		}

		return block != null ? OTHER_BLOCKS : OTHER_ITEMS;
	}
}
