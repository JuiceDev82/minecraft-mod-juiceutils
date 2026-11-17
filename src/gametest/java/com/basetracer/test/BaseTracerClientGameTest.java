package com.basetracer.test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.terraformersmc.modmenu.gui.ModsScreen;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import com.basetracer.BaseRule;
import com.basetracer.DroppedItems;
import com.basetracer.AutoEat;
import com.basetracer.FreeLook;
import com.basetracer.HeldUse;
import com.basetracer.PutAllFilter;
import com.basetracer.PutAllItemsScreen;
import com.basetracer.QuickUseAction;
import com.basetracer.TakeAll;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import com.basetracer.SmartTool;
import com.basetracer.ItemMode;
import com.basetracer.MobWarning;
import com.basetracer.ModuleSplash;
import com.basetracer.BaseTracerClient;
import com.basetracer.BaseTracerConfig;
import com.basetracer.BaseTracerConfigScreen;
import com.basetracer.BaseTracerExcludedBlocksScreen;
import com.basetracer.BaseTracerModMenu;
import com.basetracer.BaseTracerMobsScreen;
import com.basetracer.BaseTracerRenderer;
import com.basetracer.BaseTracerRuleBlocksScreen;
import com.basetracer.BaseTracerRuleEntitiesScreen;
import com.basetracer.BaseTracerRuleScreen;
import com.basetracer.BaseTracerRulesScreen;
import com.basetracer.BaseTracker;
import com.basetracer.TracerMode;
import com.basetracer.XRay;
import com.basetracer.BaseTracerXRayBlocksScreen;

public class BaseTracerClientGameTest implements FabricClientGameTest {
	private static final int TIMEOUT_TICKS = 200;
	private static final BlockPos ENCLOSED_TORCH = new BlockPos(56, 100, 8);
	private static final BlockPos FAR_TORCH = new BlockPos(72, 100, 24);
	private static final BlockPos BEHIND_TORCH = new BlockPos(-24, 100, -8);
	private static final BlockPos MARKER = new BlockPos(24, 100, 72);

	@Override
	public void runTest(ClientGameTestContext context) {
		logLightEmittingBlocks();
		testColorValidation();
		testBlockHighlightFade();
		testBusynessGradient();
		testConfigStorage();
		testModMenu(context);
		testPutAllItemsFromTitleScreen(context);

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerContext server = singleplayer.getServer();
			TestServerConnection connection = singleplayer.getConnection();

			server.runCommand("gamemode spectator @a");
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("tp @a 8.5 100 8.5 -90 0");
			connection.waitForChunksDownload();

			// Joining a world with the mod on lists what's on.
			context.waitFor(client -> BaseTracerClient.SPLASH.isShowing(), TIMEOUT_TICKS);
			context.takeScreenshot("basetracer_module_splash_join");

			testBlockUpdates(context, server);
			testChunkReload(context, server, connection);
			testExcludedBlocksScreen(context, server);
			testBaseRules(context, server);
			testRuleLabels(context, server, connection);
			testHostileMobs(context, server, connection);
			testEntityRules(context, server, connection);
			testModuleSplash(context);
			testXRay(context, server, connection);
			testMobWarning(context, server);
			testPiglinWarning(context, server);
			testDroppedItems(context, server);
			testSmartTool(context, server);
			testAutoEat(context, server);
			testQuickUse(context, server);
			testTakeAll(context, server);
			testCustomRule(context, server);
			testBasePriority(context, server);
			testRuleScreens(context);
			surveyStructures(context, server, connection);
			buildScene(context, server);
			testTracerModes(context, server);
			takeScreenshots(context, server, connection);
			takeBusynessScreenshot(context, server, connection);
			testToggleKey(context);
			testFreeLook(context, server, connection);
		}

		context.waitFor(client -> !FreeLook.isActive(), TIMEOUT_TICKS);

		context.waitFor(client -> BaseTracerClient.TRACKER.chunkCount() == 0, TIMEOUT_TICKS);
	}

	private static void logLightEmittingBlocks() {
		List<String> tracked = new ArrayList<>();
		List<String> excluded = new ArrayList<>();

		for (Block block : BuiltInRegistries.BLOCK) {
			boolean emits = false;
			boolean isTracked = false;

			for (BlockState state : block.getStateDefinition().getPossibleStates()) {
				emits |= state.getLightEmission() > 0;
				isTracked |= BaseTracerClient.CONFIG.isTracked(state);
			}

			if (emits) {
				(isTracked ? tracked : excluded).add(BuiltInRegistries.BLOCK.getKey(block).getPath());
			}
		}

		BaseTracerClient.LOGGER.info("Tracked light blocks ({}): {}", tracked.size(), tracked);
		BaseTracerClient.LOGGER.info("Excluded light blocks ({}): {}", excluded.size(), excluded);
	}

	private static void testBlockUpdates(ClientGameTestContext context, TestServerContext server) {
		BlockPos pos = new BlockPos(24, 100, 8);
		server.runCommand("setblock 24 99 8 minecraft:stone");

		server.runCommand("setblock 24 100 8 minecraft:torch");
		awaitSource(context, pos, true);
		server.runCommand("setblock 24 100 8 minecraft:air");
		awaitSource(context, pos, false);

		// Light level depends on the state: a lit lamp counts, an unlit one doesn't.
		server.runCommand("setblock 24 100 8 minecraft:redstone_lamp[lit=true]");
		awaitSource(context, pos, true);
		server.runCommand("setblock 24 100 8 minecraft:redstone_lamp[lit=false]");
		awaitSource(context, pos, false);

		// Naturally generating light sources are never tracked. The marker proves their updates have arrived.
		server.runCommand("setblock 24 101 24 minecraft:glowstone");
		server.runCommand("setblock 24 101 25 minecraft:lava");
		server.runCommand("setblock 24 101 26 minecraft:magma_block");
		server.runCommand("setblock 24 99 72 minecraft:stone");
		server.runCommand("setblock 24 100 72 minecraft:torch");
		awaitSource(context, MARKER, true);
		assertNotSource(context, new BlockPos(24, 101, 24));
		assertNotSource(context, new BlockPos(24, 101, 25));
		assertNotSource(context, new BlockPos(24, 101, 26));
		server.runCommand("setblock 24 100 72 minecraft:air");
		awaitSource(context, MARKER, false);

		server.runCommand("setblock 24 101 24 minecraft:air");
		server.runCommand("setblock 24 101 25 minecraft:air");
		server.runCommand("setblock 24 101 26 minecraft:air");
	}

	private static void testChunkReload(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
		BlockPos pos = new BlockPos(24, 100, 8);
		server.runCommand("setblock 24 100 8 minecraft:torch");
		awaitSource(context, pos, true);

		server.runCommand("tp @a 5000 100 5000");
		awaitSource(context, pos, false);
		connection.waitForChunksDownload();

		// Coming back, the torch can only be found by the scan of the received chunk.
		server.runCommand("tp @a 8.5 100 8.5 -90 0");
		awaitSource(context, pos, true);
		connection.waitForChunksDownload();

		server.runCommand("setblock 24 100 8 minecraft:air");
		awaitSource(context, pos, false);
	}

	private static void testBaseRules(ClientGameTestContext context, TestServerContext server) {
		// All in one chunk, with a sponge elsewhere as proof that earlier block updates have arrived.
		BaseRule marker = new BaseRule("Marker", List.of("minecraft:sponge"), 1, 1);
		BaseRule chests = new BaseRule("Chests", List.of("minecraft:chest"), 2, 1);
		BaseRule combo = new BaseRule("Combo", List.of("minecraft:furnace", "minecraft:crafting_table", "minecraft:torch", "minecraft:wall_torch"), 1, 2);
		BaseRule light = BaseRule.lightSources();
		setRules(context, marker, chests, combo, light);
		light.enabled = false;
		applyRules(context);
		server.runCommand("fill 32 99 32 47 99 47 minecraft:stone");

		// A minimum count: one chest isn't enough, and the second lights up both.
		BlockPos chest1 = new BlockPos(34, 100, 34);
		BlockPos chest2 = new BlockPos(38, 100, 34);
		server.runCommand("setblock 34 100 34 minecraft:chest");
		sync(context, server, marker);
		assertNotSource(context, chest1);
		server.runCommand("setblock 38 100 34 minecraft:chest");
		awaitSource(context, chest1, true);
		awaitSource(context, chest2, true);
		server.runCommand("setblock 38 100 34 minecraft:air");
		awaitSource(context, chest1, false);

		// A combination: two of one type aren't enough, and a second type completes it.
		BlockPos furnace1 = new BlockPos(34, 100, 38);
		BlockPos furnace2 = new BlockPos(36, 100, 38);
		BlockPos table = new BlockPos(38, 100, 38);
		server.runCommand("setblock 34 100 38 minecraft:furnace");
		server.runCommand("setblock 36 100 38 minecraft:furnace");
		sync(context, server, marker);
		assertNotSource(context, furnace1);
		server.runCommand("setblock 38 100 38 minecraft:crafting_table");
		awaitSource(context, furnace1, true);
		awaitSource(context, furnace2, true);
		awaitSource(context, table, true);
		// Only the matching rule's blocks count, so the lone chest still isn't a target.
		assertNotSource(context, chest1);
		check(highlightedBlocks(context, 2, 2) == 3, "Combo should light up 3 blocks: " + highlightedBlocks(context, 2, 2));

		// Two rules matching together add up.
		server.runCommand("setblock 38 100 34 minecraft:chest");
		awaitSource(context, chest1, true);
		check(highlightedBlocks(context, 2, 2) == 5, "Both rules should count: " + highlightedBlocks(context, 2, 2));

		// Switching a rule off applies through a rescan.
		context.runOnClient(client -> combo.enabled = false);
		applyRules(context);
		awaitSource(context, furnace1, false);
		check(highlightedBlocks(context, 2, 2) == 2, "Disabled rule still counted: " + highlightedBlocks(context, 2, 2));
		context.runOnClient(client -> {
			combo.enabled = true;
			chests.enabled = false;
		});
		applyRules(context);
		awaitSource(context, furnace1, true);
		awaitSource(context, chest1, false);
		context.runOnClient(client -> chests.enabled = true);
		applyRules(context);
		server.runCommand("fill 32 100 32 47 101 47 minecraft:air");
		sync(context, server, marker);
		check(highlightedBlocks(context, 2, 2) == 0, "Cleared chunk still highlighted");

		// A torch and a wall torch share a name, so together they're one type and the combo doesn't match.
		BlockPos torch = new BlockPos(34, 100, 34);
		server.runCommand("setblock 34 100 34 minecraft:torch");
		server.runCommand("setblock 36 100 36 minecraft:stone");
		server.runCommand("setblock 37 100 36 minecraft:wall_torch[facing=east]");
		sync(context, server, marker);
		assertNotSource(context, torch);
		server.runCommand("fill 32 100 32 47 100 47 minecraft:air");

		// Light sources are a rule like any other, and still depend on the state.
		BlockPos lamp = new BlockPos(40, 100, 40);
		context.runOnClient(client -> light.enabled = true);
		applyRules(context);
		server.runCommand("setblock 40 100 40 minecraft:redstone_lamp[lit=false]");
		sync(context, server, marker);
		assertNotSource(context, lamp);
		server.runCommand("setblock 40 100 40 minecraft:redstone_lamp[lit=true]");
		awaitSource(context, lamp, true);
		context.runOnClient(client -> light.enabled = false);
		applyRules(context);
		awaitSource(context, lamp, false);
		server.runCommand("setblock 40 100 40 minecraft:air");

		// A rule on a common block stays within the cap, through both block updates and scans.
		BaseRule stone = new BaseRule("Stone", List.of("minecraft:stone"), 1, 1);
		context.runOnClient(client -> BaseTracerClient.CONFIG.rules.add(stone));
		applyRules(context);
		server.runCommand("fill 80 100 80 95 104 95 minecraft:stone");
		context.waitFor(client -> highlightedBlocksNow(5, 5) == BaseTracker.MAX_FOUND_PER_CHUNK, TIMEOUT_TICKS);
		context.waitTicks(10);
		check(highlightedBlocks(context, 5, 5) == BaseTracker.MAX_FOUND_PER_CHUNK, "Block updates passed the cap");
		context.runOnClient(client -> BaseTracerClient.rescanLoadedChunks());
		check(highlightedBlocks(context, 5, 5) == BaseTracker.MAX_FOUND_PER_CHUNK, "A scan passed the cap");
		server.runCommand("fill 80 100 80 95 104 95 minecraft:air");
		server.runCommand("fill 32 99 32 47 99 47 minecraft:air");
		server.runCommand("setblock 24 100 72 minecraft:air");

		context.runOnClient(client -> {
			BaseTracerClient.CONFIG.resetRules();
			BaseTracerClient.applyRuleChanges();
		});
	}

	private static void testHostileMobs(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
		check(context.computeOnClient(client -> !BaseTracerClient.CONFIG.showHostileMobs), "Hostile mob highlights should start off");
		server.runCommand("difficulty normal");

		// Behind a wall, with no base anywhere near, to show mobs don't depend on a highlight being drawn.
		server.runCommand("fill 13 97 2 13 104 15 minecraft:stone");
		server.runCommand("summon minecraft:zombie 16.5 99 6.5 {NoAI:1b}");
		server.runCommand("summon minecraft:slime 16.5 99 11.5 {NoAI:1b,Size:1}");
		server.runCommand("summon minecraft:cow 16.5 99 3.5 {NoAI:1b}");
		server.runCommand("summon minecraft:wolf 16.5 99 14.5 {NoAI:1b}");
		connection.waitForChunksRender();

		Vec3 camera = new Vec3(8.5, 100, 8.5);
		context.waitFor(client -> BaseTracerRenderer.nearestMobs(client.level, BaseTracerClient.CONFIG, camera, 0F, 1000).size() == 2, TIMEOUT_TICKS);
		List<AABB> mobs = context.computeOnClient(client -> BaseTracerRenderer.nearestMobs(client.level, BaseTracerClient.CONFIG, camera, 1F, 1000));
		check(mobs.stream().anyMatch(mob -> mob.contains(16.5, 100, 6.5)), "The zombie's box isn't where the zombie is: " + mobs);
		check(mobs.stream().anyMatch(mob -> mob.contains(16.5, 99.2, 11.5)), "The slime's box isn't where the slime is: " + mobs);
		List<AABB> capped = context.computeOnClient(client -> BaseTracerRenderer.nearestMobs(client.level, BaseTracerClient.CONFIG, new Vec3(16.5, 100, 12), 1F, 1));
		check(capped.size() == 1 && capped.getFirst().contains(16.5, 99.2, 11.5), "The cap should keep the nearest mob: " + capped);

		// The default list has to be every hostile mob and nothing else, and the screen every mob and nothing else.
		context.runOnClient(client -> {
			List<String> wrongDefault = new ArrayList<>();
			List<String> wrongChoice = new ArrayList<>();
			List<EntityType<?>> choices = BaseTracerMobsScreen.mobTypes();

			for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
				Entity entity = type.create(client.level, EntitySpawnReason.LOAD);

				if (entity == null) {
					continue;
				}

				if (entity instanceof Enemy != BaseTracerClient.CONFIG.isMobHighlighted(type)) {
					wrongDefault.add(type.toShortString());
				}

				if (entity instanceof Mob != choices.contains(type)) {
					wrongChoice.add(type.toShortString());
				}
			}

			check(wrongDefault.isEmpty(), "Wrong default for: " + wrongDefault);
			check(wrongChoice.isEmpty(), "Wrongly offered or left out: " + wrongChoice);
		});

		// Taking a mob off and putting another on, through the screen.
		context.setScreen(() -> new BaseTracerMobsScreen(null));
		context.waitTicks(5);
		context.takeScreenshot("basetracer_mobs");
		context.getInput().typeChars("Zombie");
		context.waitTicks(5);
		context.takeScreenshot("basetracer_mobs_search");
		context.setScreen(() -> null);
		context.setScreen(() -> new BaseTracerMobsScreen(null));
		context.waitTicks(5);
		context.getInput().typeChars("Slime");
		context.waitTicks(5);
		context.getInput().setCursorPos(427, 100);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitFor(client -> !BaseTracerClient.CONFIG.isMobHighlighted(EntityTypes.SLIME), TIMEOUT_TICKS);
		context.setScreen(() -> null);
		context.setScreen(() -> new BaseTracerMobsScreen(null));
		context.waitTicks(5);
		context.getInput().typeChars("Cow");
		context.waitTicks(5);
		context.takeScreenshot("basetracer_mobs_cow");
		context.setScreen(() -> null);
		context.runOnClient(client -> BaseTracerClient.CONFIG.setMobHighlighted(EntityTypes.COW, true));
		List<AABB> edited = context.computeOnClient(client -> BaseTracerRenderer.nearestMobs(client.level, BaseTracerClient.CONFIG, camera, 1F, 1000));
		check(edited.size() == 2 && edited.stream().anyMatch(mob -> mob.contains(16.5, 99.5, 3.5)) && edited.stream().noneMatch(mob -> mob.contains(16.5, 99.2, 11.5)), "List changes didn't apply: " + edited);
		context.runOnClient(client -> BaseTracerClient.CONFIG.showHostileMobs = true);
		context.waitTicks(5);
		context.takeScreenshot("basetracer_mobs_edited");
		context.runOnClient(client -> {
			BaseTracerClient.CONFIG.showHostileMobs = false;
			BaseTracerClient.CONFIG.resetMobs();
		});
		check(context.computeOnClient(client -> BaseTracerClient.CONFIG.isMobHighlighted(EntityTypes.SLIME) && !BaseTracerClient.CONFIG.isMobHighlighted(EntityTypes.COW)), "Reset didn't restore the default list");

		context.waitTicks(5);
		context.takeScreenshot("basetracer_hostile_mobs_off");
		context.runOnClient(client -> BaseTracerClient.CONFIG.showHostileMobs = true);
		context.waitTicks(5);
		context.takeScreenshot("basetracer_hostile_mobs");
		context.runOnClient(client -> BaseTracerClient.CONFIG.mobOpacity = 100);
		context.waitTicks(5);
		context.takeScreenshot("basetracer_hostile_mobs_full_opacity");
		context.runOnClient(client -> BaseTracerClient.CONFIG.mobOpacity = new BaseTracerConfig().mobOpacity);
		context.runOnClient(client -> BaseTracerClient.CONFIG.showHostileMobs = false);

		server.runCommand("kill @e[type=!player]");
		server.runCommand("fill 13 97 2 13 104 15 minecraft:air");
		context.waitFor(client -> BaseTracerRenderer.nearestMobs(client.level, BaseTracerClient.CONFIG, camera, 0F, 1000).isEmpty(), TIMEOUT_TICKS);
	}

	private static void testEntityRules(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
		// The default rules: two armor stands, a named pig and a tamed horse, each in a chunk of its own.
		server.runCommand("summon minecraft:armor_stand 20.5 100 4.5 {NoGravity:1b}");
		context.waitTicks(25);
		check(context.computeOnClient(client -> highlight(1, 0)) == null, "One armor stand shouldn't be enough");
		server.runCommand("summon minecraft:armor_stand 22.5 100 4.5 {NoGravity:1b}");
		server.runCommand("summon minecraft:pig 36.5 100 4.5 {NoAI:1b,NoGravity:1b,CustomName:\"Bob\"}");
		server.runCommand("summon minecraft:horse 52.5 100 4.5 {NoAI:1b,NoGravity:1b,Tame:1b}");
		server.runCommand("summon minecraft:horse 54.5 100 12.5 {NoAI:1b,NoGravity:1b}");
		// Camels are tamed from birth, so only a saddled one counts.
		server.runCommand("summon minecraft:camel 52.5 100 -11.5 {NoAI:1b,NoGravity:1b}");
		server.runCommand("summon minecraft:camel 36.5 100 -11.5 {NoAI:1b,NoGravity:1b,equipment:{saddle:{id:\"minecraft:saddle\",count:1}}}");
		context.waitFor(client -> labelText(1, 0).equals(List.of("Armor Stand x2")), TIMEOUT_TICKS);
		context.waitFor(client -> labelText(2, 0).equals(List.of("Named Mob x1")), TIMEOUT_TICKS);
		context.waitFor(client -> labelText(3, 0).equals(List.of("Tamed Animal x1")), TIMEOUT_TICKS);
		context.waitFor(client -> labelText(2, -1).equals(List.of("Tamed Animal x1")), TIMEOUT_TICKS);
		check(context.computeOnClient(client -> highlight(3, -1)) == null, "A camel without a saddle shouldn't count as tamed");
		BaseTracker.ChunkHighlight stands = context.computeOnClient(client -> highlight(1, 0));
		check(stands.matches().getFirst().rule().name.equals("Player Entities") && stands.sourceCount() == 2, "Armor stands should match Player Entities");
		check(stands.targetColors()[0] == 0xFFFFAAAA, "Entity rule's tracer should be in its color");

		// The label's first line says how far the nearest find is, and where.
		Vec3 camera = new Vec3(20.5, 100.5, 14.5);
		String info = context.computeOnClient(client -> BaseTracerRenderer.labelInfo(
				BaseTracerClient.TRACKER.snapshot(camera, TracerMode.NEAREST_PER_CHUNK).stream().filter(highlight -> highlight.chunkX() == 1 && highlight.chunkZ() == 0).findFirst().orElseThrow(),
				true, true));
		check(info.equals("10m \u00B7 20, 100, 4"), "Wrong label info: " + info);

		// Other players get boxes, and the player looking never does.
		check(context.computeOnClient(client -> BaseTracerRenderer.otherPlayers(client.level, client.player, 0F)).isEmpty(), "The player looking shouldn't be highlighted");
		check(context.computeOnClient(client -> BaseTracerRenderer.otherPlayers(client.level, null, 0F)).size() == 1, "Players should be found");

		server.runCommand("tp @a 36 112 -14 0 35");
		connection.waitForChunksRender();
		context.waitTicks(20);
		context.takeScreenshot("basetracer_entity_rules");

		// Choosing an entity through the screen. A plain horse then counts too, under its own name.
		BaseRule pets = context.computeOnClient(client -> BaseTracerClient.CONFIG.rules.stream().filter(rule -> rule.name.equals("Pets & Named Mobs")).findFirst().orElseThrow());
		context.setScreen(() -> {
			Screen settings = new BaseTracerConfigScreen(null);
			return new BaseTracerRuleEntitiesScreen(new BaseTracerRuleScreen(settings, pets), settings, pets);
		});
		context.waitTicks(5);
		context.takeScreenshot("basetracer_rule_entities");
		context.getInput().typeChars("Horse");
		context.waitTicks(5);
		context.takeScreenshot("basetracer_rule_entities_search");
		context.getInput().setCursorPos(427, 100);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitFor(client -> pets.entities().contains(EntityTypes.HORSE), TIMEOUT_TICKS);
		context.clickScreenButton("gui.done");
		context.waitFor(client -> client.gui.screen() instanceof BaseTracerRuleScreen, TIMEOUT_TICKS);
		context.waitTicks(5);
		context.takeScreenshot("basetracer_rule_edit_entities");
		context.setScreen(() -> null);
		context.waitFor(client -> labelText(3, 0).equals(List.of("Horse x2")), TIMEOUT_TICKS);

		// Gone once the entities are.
		server.runCommand("kill @e[type=!player]");
		context.waitFor(client -> highlight(1, 0) == null && highlight(2, 0) == null && highlight(3, 0) == null, TIMEOUT_TICKS);
		server.runCommand("tp @a 8.5 100 8.5 -90 0");
		connection.waitForChunksDownload();
		context.runOnClient(client -> {
			BaseTracerClient.CONFIG.resetRules();
			BaseTracerClient.applyRuleChanges();
		});
	}

	private static void testModuleSplash(ClientGameTestContext context) {
		// What's listed follows the settings, with empty rules left out.
		List<String> lines = context.computeOnClient(client -> ModuleSplash.lines(BaseTracerClient.CONFIG, false).stream().map(Component::getString).toList());
		check(lines.size() == 2 && lines.getFirst().startsWith("Base Tracer: Chunk Highlights · Block Highlights") && lines.get(1).contains("Player Storage"), "Splash is missing modules or rules: " + lines);
		check(!lines.getFirst().contains("Mob Highlights") && !lines.get(1).contains("Custom") && !lines.getFirst().contains("Free Look"), "Splash lists something that's off: " + lines);
		BaseTracerConfig allOff = new BaseTracerConfig();
		allOff.showChunkHighlights = false;
		allOff.showBlockHighlights = false;
		allOff.showTracers = false;
		allOff.showLabels = false;
		allOff.mobWarning = false;
		allOff.smartTool = false;
		allOff.autoEat = false;
		List<String> none = context.computeOnClient(client -> ModuleSplash.lines(allOff, false).stream().map(Component::getString).toList());
		check(none.equals(List.of("Base Tracer: No modules on")), "Wrong splash with everything off: " + none);

		// Turning the mod on shows it. Turning it off doesn't, and nor does changing a module or Free Look.
		context.waitFor(client -> !BaseTracerClient.SPLASH.isShowing(), TIMEOUT_TICKS);
		context.runOnClient(client -> BaseTracerClient.CONFIG.showHostileMobs = true);
		context.waitTicks(5);
		check(!context.computeOnClient(client -> BaseTracerClient.SPLASH.isShowing()), "Splash shouldn't show for changing a module");
		context.runOnClient(FreeLook::toggle);
		context.waitTicks(5);
		check(context.computeOnClient(client -> FreeLook.isActive()), "Free Look didn't turn on");
		check(!context.computeOnClient(client -> BaseTracerClient.SPLASH.isShowing()), "Splash shouldn't show for turning Free Look on");
		context.runOnClient(FreeLook::toggle);
		context.waitTicks(5);
		check(!context.computeOnClient(client -> BaseTracerClient.SPLASH.isShowing()), "Splash shouldn't show for turning Free Look off");
		context.runOnClient(client -> BaseTracerClient.CONFIG.enabled = false);
		context.waitTicks(5);
		check(!context.computeOnClient(client -> BaseTracerClient.SPLASH.isShowing()), "Splash shouldn't show for turning the mod off");
		context.runOnClient(client -> {
			BaseTracerClient.CONFIG.showPlayers = true;
			BaseTracerClient.CONFIG.enabled = true;
		});
		context.waitFor(client -> BaseTracerClient.SPLASH.isShowing(), TIMEOUT_TICKS);
		context.waitTicks(5);
		context.runOnClient(client -> client.player.sendOverlayMessage(Component.translatable("message.basetracer.enabled")));
		context.takeScreenshot("basetracer_module_splash");
		context.waitFor(client -> !BaseTracerClient.SPLASH.isShowing(), TIMEOUT_TICKS);
		context.waitTicks(5);
		context.takeScreenshot("basetracer_module_splash_gone");
		context.runOnClient(client -> {
			BaseTracerClient.CONFIG.showHostileMobs = false;
			BaseTracerClient.CONFIG.showPlayers = false;
		});
	}

	private static void testRuleLabels(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
		BaseRule chests = new BaseRule("Chests", List.of("minecraft:chest"), 1, 1);
		chests.setColor("FF55FF55");
		BaseRule workshop = new BaseRule("Workshop", List.of("minecraft:chest", "minecraft:furnace", "minecraft:crafting_table"), 1, 2);
		workshop.setColor("FFFF8800");
		BaseRule quiet = new BaseRule("Quiet", List.of("minecraft:sponge"), 1, 1);
		quiet.setColor("FFFF55FF");
		quiet.showLabel = false;
		BaseRule many = new BaseRule("Many", List.of(
				"minecraft:bricks", "minecraft:bookshelf", "minecraft:cobblestone", "minecraft:gold_block",
				"minecraft:iron_block", "minecraft:melon", "minecraft:oak_planks", "minecraft:pumpkin"
		), 1, 1);
		many.setColor("FF55FFFF");
		setRules(context, chests, workshop, BaseRule.lightSources(), quiet, many);

		// One rule, counted. The label lists what was found and how many, in the rule's color.
		server.runCommand("fill 18 100 18 20 100 18 minecraft:chest");
		context.waitFor(client -> labelText(1, 1).equals(List.of("Chest x3")), TIMEOUT_TICKS);
		check(context.computeOnClient(client -> highlight(1, 1).labelLines().getFirst().argb()) == 0xFF55FF55, "Label should be in the rule's color");

		// Two rules in one chunk: a box each, and a chest is only listed once, under the first rule.
		server.runCommand("setblock 34 100 18 minecraft:chest");
		server.runCommand("setblock 37 100 18 minecraft:chest");
		server.runCommand("setblock 40 100 18 minecraft:furnace");
		server.runCommand("setblock 42 100 18 minecraft:crafting_table");
		context.waitFor(client -> labelText(2, 1).size() == 3, TIMEOUT_TICKS);
		check(context.computeOnClient(client -> labelText(2, 1)).equals(List.of("Chest x2", "Crafting Table x1", "Furnace x1")), "Wrong label: " + context.computeOnClient(client -> labelText(2, 1)));
		check(context.computeOnClient(client -> highlight(2, 1).matches().size()) == 2, "Both rules should have a box");
		check(context.computeOnClient(client -> highlight(2, 1).labelLines().get(1).argb()) == 0xFFFF8800, "Second rule's items should use its color");

		// Light sources are labeled too, with wall variants merged into one count.
		server.runCommand("fill 48 99 16 63 99 31 minecraft:stone");
		server.runCommand("setblock 50 100 18 minecraft:torch");
		server.runCommand("setblock 52 100 18 minecraft:stone");
		server.runCommand("setblock 53 100 18 minecraft:wall_torch[facing=east]");
		server.runCommand("setblock 56 100 18 minecraft:lantern");
		context.waitFor(client -> labelText(3, 1).equals(List.of("Torch x2", "Lantern x1")) || labelText(3, 1).equals(List.of("Lantern x1", "Torch x2")), TIMEOUT_TICKS);

		// A rule can stay highlighted without a label.
		server.runCommand("setblock 18 100 34 minecraft:sponge");
		context.waitFor(client -> highlight(1, 2) != null, TIMEOUT_TICKS);
		check(context.computeOnClient(client -> highlight(1, 2).labelLines().isEmpty()), "Rule with its label off still has one");

		// Long lists are cut short and say by how much.
		server.runCommand("setblock 34 100 34 minecraft:bricks");
		server.runCommand("setblock 35 100 34 minecraft:bookshelf");
		server.runCommand("setblock 36 100 34 minecraft:cobblestone");
		server.runCommand("setblock 37 100 34 minecraft:gold_block");
		server.runCommand("setblock 38 100 34 minecraft:iron_block");
		server.runCommand("setblock 39 100 34 minecraft:melon");
		server.runCommand("setblock 40 100 34 minecraft:oak_planks");
		server.runCommand("setblock 41 100 34 minecraft:pumpkin");
		context.waitFor(client -> labelText(2, 2).contains("+2 more"), TIMEOUT_TICKS);
		check(context.computeOnClient(client -> labelText(2, 2).size()) == BaseTracker.MAX_LABEL_ITEMS + 1, "Label wasn't cut short");

		// Only blocks behind a match get highlighted, nearest first, with a chest in two rules taking the first's color.
		BlockPos nearChest = new BlockPos(34, 100, 18);
		Vec3 besideChest = Vec3.atCenterOf(nearChest);
		List<BaseTracerRenderer.BlockMark> marks = context.computeOnClient(client -> BaseTracerRenderer.nearestBlocks(
				BaseTracerClient.TRACKER.snapshot(besideChest, TracerMode.NEAREST_PER_CHUNK), besideChest, 1000));
		check(marks.getFirst().pos() == nearChest.asLong() && marks.getFirst().argb() == 0xFF55FF55, "Nearest block should be the chest, in the first rule's color");
		check(marks.stream().filter(mark -> mark.pos() == nearChest.asLong()).count() == 1, "A block in two rules should only be marked once");
		// 3 + 4 chests, furnace and table + 3 lights + sponge + 8 of the long list.
		check(marks.size() == 3 + 4 + 3 + 1 + 8, "Wrong number of marks: " + marks.size());
		check(marks.stream().anyMatch(mark -> mark.pos() == new BlockPos(40, 100, 18).asLong() && mark.argb() == 0xFFFF8800), "Furnace should use its rule's color");
		List<BaseTracerRenderer.BlockMark> capped = context.computeOnClient(client -> BaseTracerRenderer.nearestBlocks(
				BaseTracerClient.TRACKER.snapshot(besideChest, TracerMode.NEAREST_PER_CHUNK), besideChest, 3));
		check(capped.size() == 3 && capped.equals(marks.subList(0, 3)), "The cap should keep the nearest blocks");

		// Hidden under stone, the blocks should still show.
		server.runCommand("fill 16 101 16 63 101 31 minecraft:stone");
		server.runCommand("tp @a 40 118 -8 0 50");
		connection.waitForChunksRender();
		context.waitTicks(20);
		context.takeScreenshot("basetracer_rule_labels");
		context.runOnClient(client -> BaseTracerClient.CONFIG.showBlockHighlights = false);
		context.waitTicks(5);
		context.takeScreenshot("basetracer_block_highlights_off");
		context.runOnClient(client -> BaseTracerClient.CONFIG.showBlockHighlights = true);

		context.runOnClient(client -> BaseTracerClient.CONFIG.showLabels = false);
		context.waitTicks(5);
		context.takeScreenshot("basetracer_rule_labels_off");
		context.runOnClient(client -> BaseTracerClient.CONFIG.showLabels = true);

		server.runCommand("fill 16 99 16 63 101 40 minecraft:air");
		server.runCommand("tp @a 8.5 100 8.5 -90 0");
		connection.waitForChunksDownload();
		context.waitFor(client -> highlight(1, 1) == null, TIMEOUT_TICKS);
		context.runOnClient(client -> {
			BaseTracerClient.CONFIG.resetRules();
			BaseTracerClient.applyRuleChanges();
		});
	}

	private static void testBasePriority(ClientGameTestContext context, TestServerContext server) {
		// Three near chunks of a low priority rule, and a far one of a high priority rule.
		BaseRule low = new BaseRule("Low", List.of("minecraft:sponge"), 1, 1);
		low.priority = 1;
		BaseRule high = new BaseRule("High", List.of("minecraft:melon"), 1, 1);
		high.priority = 9;
		setRules(context, low, high);
		server.runCommand("setblock 20 100 40 minecraft:sponge");
		server.runCommand("setblock 36 100 40 minecraft:sponge");
		server.runCommand("setblock 52 100 40 minecraft:sponge");
		server.runCommand("setblock 100 100 40 minecraft:melon");
		context.waitFor(client -> highlight(1, 2) != null && highlight(2, 2) != null && highlight(3, 2) != null && highlight(6, 2) != null, TIMEOUT_TICKS);

		Vec3 camera = new Vec3(8.5, 100.5, 40.5);
		List<String> kept = context.computeOnClient(client -> BaseTracerClient.TRACKER.snapshot(camera, TracerMode.NEAREST_PER_CHUNK, 2).stream()
				.map(highlight -> highlight.chunkX() + "," + highlight.chunkZ()).toList());
		check(kept.equals(List.of("6,2", "1,2")), "The high priority base, then the nearest, should be kept: " + kept);

		// Equal priorities: the nearest win.
		context.runOnClient(client -> high.priority = 1);
		List<String> nearest = context.computeOnClient(client -> BaseTracerClient.TRACKER.snapshot(camera, TracerMode.NEAREST_PER_CHUNK, 2).stream()
				.map(highlight -> highlight.chunkX() + "," + highlight.chunkZ()).toList());
		check(nearest.equals(List.of("1,2", "2,2")), "With equal priorities the nearest should be kept: " + nearest);
		context.runOnClient(client -> high.priority = 9);

		// The one chunk traced comes from the kept ones.
		long[] traced = context.computeOnClient(client -> BaseTracerClient.TRACKER.snapshot(camera, TracerMode.MOST_SOURCES, 1).stream()
				.flatMapToLong(highlight -> java.util.Arrays.stream(highlight.targets())).toArray());
		check(traced.length == 1 && traced[0] == BlockPos.asLong(100, 100, 40), "The traced chunk should be one of those kept");

		// The renderer follows the setting, and All shows every one.
		context.runOnClient(client -> BaseTracerClient.CONFIG.maxBases = 2);
		context.waitFor(client -> BaseTracerClient.RENDERER.highlightCount() == 2, TIMEOUT_TICKS);
		context.runOnClient(client -> BaseTracerClient.CONFIG.maxBases = BaseTracerConfig.MAX_BASES_LIMIT + 1);
		context.waitFor(client -> BaseTracerClient.RENDERER.highlightCount() == 4, TIMEOUT_TICKS);
		context.runOnClient(client -> BaseTracerClient.CONFIG.maxBases = 10);

		server.runCommand("setblock 20 100 40 minecraft:air");
		server.runCommand("setblock 36 100 40 minecraft:air");
		server.runCommand("setblock 52 100 40 minecraft:air");
		server.runCommand("setblock 100 100 40 minecraft:air");
		context.waitFor(client -> highlight(1, 2) == null && highlight(6, 2) == null, TIMEOUT_TICKS);
		context.runOnClient(client -> {
			BaseTracerClient.CONFIG.resetRules();
			BaseTracerClient.applyRuleChanges();
		});
	}

	private static void testCustomRule(ClientGameTestContext context, TestServerContext server) {
		BaseRule custom = context.computeOnClient(client -> BaseTracerClient.CONFIG.rules.stream().filter(rule -> rule.name.equals("Custom")).findFirst().orElseThrow());

		// Empty, it finds nothing: TNT placed now isn't highlighted.
		server.runCommand("setblock 18 100 18 minecraft:tnt");
		syncWithTorch(context, server);
		check(highlightedBlocks(context, 1, 1) == 0, "Empty Custom rule matched something");

		// Picking TNT by searching for it, as a player would, with a number of 2.
		context.setScreen(() -> {
			Screen settings = new BaseTracerModMenu().getModConfigScreenFactory().create(null);
			return new BaseTracerRuleBlocksScreen(new BaseTracerRuleScreen(settings, custom), settings, custom);
		});
		context.waitTicks(5);
		context.getInput().typeChars("TNT");
		context.waitTicks(5);
		context.takeScreenshot("basetracer_custom_blocks");
		context.getInput().setCursorPos(427, 100);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitFor(client -> custom.blocks().contains(Blocks.TNT), TIMEOUT_TICKS);
		context.runOnClient(client -> custom.minBlocks = 2);
		context.setScreen(() -> null);
		context.waitTicks(1);

		// One TNT is below the number, and two reach it.
		syncWithTorch(context, server);
		check(highlightedBlocks(context, 1, 1) == 0, "One TNT shouldn't reach a number of 2");
		server.runCommand("setblock 20 100 18 minecraft:tnt");
		context.waitFor(client -> labelText(1, 1).equals(List.of("TNT x2")), TIMEOUT_TICKS);

		server.runCommand("fill 18 100 18 20 100 18 minecraft:air");
		syncWithTorch(context, server);
		context.runOnClient(client -> {
			BaseTracerClient.CONFIG.resetRules();
			BaseTracerClient.applyRuleChanges();
		});
	}

	private static void syncWithTorch(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("setblock 24 100 72 minecraft:torch");
		awaitSource(context, MARKER, true);
		server.runCommand("setblock 24 100 72 minecraft:air");
		awaitSource(context, MARKER, false);
	}

	private static BaseTracker.ChunkHighlight highlight(int chunkX, int chunkZ) {
		for (BaseTracker.ChunkHighlight highlight : BaseTracerClient.TRACKER.snapshot(Vec3.ZERO, TracerMode.NEAREST_PER_CHUNK)) {
			if (highlight.chunkX() == chunkX && highlight.chunkZ() == chunkZ) {
				return highlight;
			}
		}

		return null;
	}

	private static List<String> labelText(int chunkX, int chunkZ) {
		BaseTracker.ChunkHighlight highlight = highlight(chunkX, chunkZ);
		return highlight == null ? List.of() : highlight.labelLines().stream().map(BaseTracker.LabelLine::text).toList();
	}

	private static void setRules(ClientGameTestContext context, BaseRule... rules) {
		context.runOnClient(client -> {
			BaseTracerClient.CONFIG.rules.clear();
			BaseTracerClient.CONFIG.rules.addAll(List.of(rules));
		});
		applyRules(context);
	}

	private static void applyRules(ClientGameTestContext context) {
		context.runOnClient(client -> {
			BaseTracerClient.CONFIG.markRulesChanged();
			BaseTracerClient.applyRuleChanges();
		});
	}

	private static void sync(ClientGameTestContext context, TestServerContext server, BaseRule marker) {
		server.runCommand("setblock 24 100 72 minecraft:sponge");
		awaitSource(context, MARKER, true);
		server.runCommand("setblock 24 100 72 minecraft:air");
		awaitSource(context, MARKER, false);
	}

	private static int highlightedBlocks(ClientGameTestContext context, int chunkX, int chunkZ) {
		return context.computeOnClient(client -> highlightedBlocksNow(chunkX, chunkZ));
	}

	private static int highlightedBlocksNow(int chunkX, int chunkZ) {
		for (BaseTracker.ChunkHighlight highlight : BaseTracerClient.TRACKER.snapshot(Vec3.ZERO, TracerMode.NEAREST_PER_CHUNK)) {
			if (highlight.chunkX() == chunkX && highlight.chunkZ() == chunkZ) {
				return highlight.sourceCount();
			}
		}

		return 0;
	}

	private static void testRuleScreens(ClientGameTestContext context) {
		// Real navigation from the settings screen, with screenshots at each stop.
		context.setScreen(() -> new BaseTracerModMenu().getModConfigScreenFactory().create(null));
		context.waitTicks(5);
		context.setScreen(() -> new BaseTracerRulesScreen(new BaseTracerModMenu().getModConfigScreenFactory().create(null)));
		context.waitTicks(5);
		context.takeScreenshot("basetracer_rules");

		int rulesBefore = context.computeOnClient(client -> BaseTracerClient.CONFIG.rules.size());
		context.clickScreenButton("options.basetracer.rules.add");
		context.waitFor(client -> client.gui.screen() instanceof BaseTracerRuleScreen, TIMEOUT_TICKS);
		context.waitTicks(5);
		context.takeScreenshot("basetracer_rule_edit");
		check(context.computeOnClient(client -> BaseTracerClient.CONFIG.rules.size()) == rulesBefore + 1, "Add Rule didn't add a rule");
		BaseRule added = context.computeOnClient(client -> BaseTracerClient.CONFIG.rules.getLast());

		// Picking a block through the picker, then back out level by level.
		context.setScreen(() -> {
			Screen settings = new BaseTracerModMenu().getModConfigScreenFactory().create(null);
			return new BaseTracerRuleBlocksScreen(new BaseTracerRuleScreen(settings, added), settings, added);
		});
		context.waitTicks(5);
		context.getInput().typeChars("Sponge");
		context.waitTicks(5);
		context.takeScreenshot("basetracer_rule_blocks");
		context.getInput().setCursorPos(427, 100);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitFor(client -> added.blocks().contains(Blocks.SPONGE), TIMEOUT_TICKS);
		context.clickScreenButton("gui.done");
		context.waitFor(client -> client.gui.screen() instanceof BaseTracerRuleScreen, TIMEOUT_TICKS);
		context.waitTicks(5);
		context.takeScreenshot("basetracer_rule_edit_chosen");

		// Deleting asks first, and No keeps the rule.
		context.clickScreenButton("options.basetracer.rule.delete");
		context.waitFor(client -> client.gui.screen() instanceof ConfirmScreen, TIMEOUT_TICKS);
		context.clickScreenButton("gui.no");
		context.waitFor(client -> client.gui.screen() instanceof BaseTracerRuleScreen, TIMEOUT_TICKS);
		check(context.computeOnClient(client -> BaseTracerClient.CONFIG.rules.contains(added)), "Answering No deleted the rule");
		context.clickScreenButton("options.basetracer.rule.delete");
		context.waitFor(client -> client.gui.screen() instanceof ConfirmScreen, TIMEOUT_TICKS);
		context.clickScreenButton("gui.yes");
		context.waitFor(client -> client.gui.screen() instanceof BaseTracerRulesScreen, TIMEOUT_TICKS);
		check(context.computeOnClient(client -> !BaseTracerClient.CONFIG.rules.contains(added)), "Answering Yes kept the rule");
		context.waitTicks(5);

		// Done goes back to the settings screen, which must draw properly on being returned to.
		context.clickScreenButton("gui.done");
		context.waitFor(client -> client.gui.screen() instanceof BaseTracerConfigScreen, TIMEOUT_TICKS);
		context.waitTicks(5);
		context.takeScreenshot("basetracer_settings_returned");
		context.setScreen(() -> null);

		// The light sources rule leads to the excluded blocks, and can't be deleted.
		BaseRule light = context.computeOnClient(client -> BaseTracerClient.CONFIG.rules.getFirst());
		check(light.lightSources, "Light sources should be the first default rule");
		context.setScreen(() -> new BaseTracerRuleScreen(null, light));
		context.waitTicks(5);
		context.takeScreenshot("basetracer_rule_light");
		context.setScreen(() -> null);
	}

	/** Logs which rules structures set off by themselves, since that's what would make a rule a false alarm. */
	private static void surveyStructures(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
		String[] structures = {
				"village_plains", "village_desert", "village_savanna", "village_snowy", "village_taiga",
				"igloo", "jungle_pyramid", "desert_pyramid", "swamp_hut", "pillager_outpost", "mansion", "monument", "shipwreck",
				"mineshaft", "stronghold", "ancient_city", "trial_chambers", "trail_ruins", "ruined_portal",
				"fortress", "bastion_remnant", "end_city"
		};
		int x = 4000;

		for (String structure : structures) {
			teleport(context, server, connection, x, 4000);
			// Client chunks can arrive before the server considers the player's own area fully loaded.
			context.waitTicks(40);
			int placeX = x;
			String placed = server.computeOnServer(minecraftServer -> {
				try {
					return "placed " + minecraftServer.getCommands().getDispatcher().execute(
							"place structure minecraft:" + structure + " " + placeX + " 64 4000",
							minecraftServer.createCommandSourceStack()
					);
				} catch (CommandSyntaxException e) {
					return e.getMessage();
				}
			});
			context.waitTicks(60);
			Map<String, Integer> matches = context.computeOnClient(client -> BaseTracerClient.TRACKER.matchesPerRule());
			BaseTracerClient.LOGGER.info("Structure survey: {} ({}) -> {}", structure, placed, matches);
			x += 2000;
		}

		teleport(context, server, connection, 8, 8);
		server.runCommand("tp @a 8.5 100 8.5 -90 0");
		connection.waitForChunksDownload();
	}

	/** Waits for the player to arrive first. Before that, waiting for chunks only sees the old position's chunks. */
	private static void teleport(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int z) {
		server.runCommand("tp @a " + x + " 100 " + z);
		context.waitFor(client -> client.player.blockPosition().getX() == x && client.player.blockPosition().getZ() == z, TIMEOUT_TICKS);
		connection.waitForChunksDownload();
	}

	private static void buildScene(ClientGameTestContext context, TestServerContext server) {
		// A stone shell, so the torch inside can only be visible through walls.
		server.runCommand("fill 54 98 6 58 102 10 minecraft:stone hollow");
		server.runCommand("setblock 56 99 8 minecraft:stone");
		server.runCommand("setblock 56 100 8 minecraft:torch");
		server.runCommand("setblock 72 99 24 minecraft:stone");
		server.runCommand("setblock 72 100 24 minecraft:lantern");
		int renderDistance = context.computeOnClient(client -> client.options.getEffectiveRenderDistance());
		BaseTracerClient.LOGGER.info("Effective render distance: {}", renderDistance);
		server.runCommand("setblock -24 99 -8 minecraft:stone");
		server.runCommand("setblock -24 100 -8 minecraft:torch");

		awaitSource(context, ENCLOSED_TORCH, true);
		awaitSource(context, FAR_TORCH, true);
		awaitSource(context, BEHIND_TORCH, true);
	}

	private static void testTracerModes(ClientGameTestContext context, TestServerContext server) {
		BlockPos secondTorch = new BlockPos(60, 100, 8);
		server.runCommand("setblock 60 99 8 minecraft:stone");
		server.runCommand("setblock 60 100 8 minecraft:torch");
		awaitSource(context, secondTorch, true);

		// Standing right next to the enclosed torch, it's the nearest source in the nearest chunk.
		Vec3 camera = Vec3.atCenterOf(ENCLOSED_TORCH);
		List<Long> every = targets(context, camera, TracerMode.EVERY_SOURCE);
		List<Long> perChunk = targets(context, camera, TracerMode.NEAREST_PER_CHUNK);
		List<Long> nearestChunk = targets(context, camera, TracerMode.NEAREST_CHUNK);

		check(every.size() == 4 && every.contains(secondTorch.asLong()), "Every Source should target all 4 sources: " + every);
		check(perChunk.size() == 3 && !perChunk.contains(secondTorch.asLong()), "Nearest per Chunk should target one source per chunk: " + perChunk);
		check(nearestChunk.equals(List.of(ENCLOSED_TORCH.asLong())), "Nearest Chunk Only should target only the nearest source: " + nearestChunk);

		int[] colors = context.computeOnClient(client -> BaseTracerClient.TRACKER.snapshot(camera, TracerMode.EVERY_SOURCE).stream()
				.flatMapToInt(highlight -> java.util.Arrays.stream(highlight.targetColors())).distinct().toArray());
		check(colors.length == 1 && colors[0] == (BaseRule.lightSources().colorArgb() | 0xFF000000), "Tracers should be in the rule's color: " + java.util.Arrays.toString(colors));
		context.runOnClient(client -> BaseTracerClient.CONFIG.rules.getFirst().showTracer = false);
		check(targets(context, camera, TracerMode.EVERY_SOURCE).isEmpty(), "A rule with tracers off still has tracers");
		check(targets(context, camera, TracerMode.NEAREST_CHUNK).isEmpty(), "A rule with tracers off can still be the nearest chunk traced");
		context.runOnClient(client -> BaseTracerClient.CONFIG.rules.getFirst().showTracer = true);

		// Highlights stay on every chunk, whatever the tracer target.
		int chunks = context.computeOnClient(client -> BaseTracerClient.TRACKER.snapshot(camera, TracerMode.NEAREST_CHUNK).size());
		check(chunks == 3, "Nearest Chunk Only should keep all chunk highlights: " + chunks);

		// The busiest chunk wins even when another chunk is closer. Its nearest source is the target.
		Vec3 besideBehindTorch = Vec3.atCenterOf(BEHIND_TORCH);
		List<Long> mostSources = targets(context, besideBehindTorch, TracerMode.MOST_SOURCES);
		check(mostSources.equals(List.of(ENCLOSED_TORCH.asLong())), "Most Sources should target the 2-source chunk: " + mostSources);

		server.runCommand("setblock 60 100 8 minecraft:air");
		awaitSource(context, secondTorch, false);

		// With every chunk now tied at one source, the closest one wins.
		List<Long> tied = targets(context, besideBehindTorch, TracerMode.MOST_SOURCES);
		check(tied.equals(List.of(BEHIND_TORCH.asLong())), "Most Sources should break ties by distance: " + tied);
	}

	private static void testFreeLook(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
		// Survival-style body on a floor, looking at the stone in front of its feet, with a light source nearby.
		server.runCommand("gamemode creative @a");
		server.runCommand("fill -72 99 -72 -56 99 -56 minecraft:stone");
		server.runCommand("setblock -60 100 -62 minecraft:torch");
		server.runCommand("tp @a -63.5 100 -63.5 0 60");
		connection.waitForChunksDownload();
		context.waitTicks(20);

		Vec3 bodyPosition = context.computeOnClient(client -> client.player.position());
		float bodyYaw = context.computeOnClient(client -> client.player.getYRot());
		BlockPos lookedAt = context.computeOnClient(client -> ((BlockHitResult) client.hitResult).getBlockPos());

		// Control: whether simulated mouse movement reaches the game at all depends on the window having focus.
		context.getInput().moveCursor(200, 0);
		context.waitTicks(5);
		float controlYaw = context.computeOnClient(client -> client.player.getYRot());
		boolean windowActive = context.computeOnClient(Minecraft::isWindowActive);
		BaseTracerClient.LOGGER.info("Simulated mouse control: body yaw {} -> {}, windowActive={}", bodyYaw, controlYaw, windowActive);
		context.runOnClient(client -> {
			client.player.setYRot(bodyYaw);
			client.player.yRotO = bodyYaw;
		});
		context.waitTicks(1);

		InputConstants.Key freeLookKey = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_J);
		context.getInput().pressKey(freeLookKey);
		context.waitFor(client -> FreeLook.isActive(), TIMEOUT_TICKS);

		// Clicks stay away from the body: the block survives here, and breaks to the same click below.
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(5);
		check(context.computeOnClient(client -> !client.level.getBlockState(lookedAt).isAir()), "Clicking during free look broke a block");

		// The movement keys and mouse steer the camera and leave the body where it was.
		context.getInput().holdKeyFor(options -> options.keyDown, 20);
		// The same call MouseHandler#turnPlayer makes for real mouse movement.
		context.runOnClient(client -> client.player.turn(200, 0));
		context.waitTicks(5);
		Vec3 cameraPosition = context.computeOnClient(client -> client.gameRenderer.mainCamera().position());
		float cameraYaw = context.computeOnClient(client -> client.gameRenderer.mainCamera().yRot());
		check(cameraPosition.distanceTo(bodyPosition) > 3.0, "Camera didn't move: " + cameraPosition);
		check(Math.abs(cameraYaw - bodyYaw) > 1F, "Camera didn't turn: " + cameraYaw);
		check(context.computeOnClient(client -> client.player.position().distanceTo(bodyPosition) < 0.01), "Body moved during free look");
		check(context.computeOnClient(client -> Math.abs(client.player.getYRot() - bodyYaw) < 0.01F), "Body turned during free look");
		check(context.computeOnClient(client -> client.player.input.getClass() == ClientInput.class), "Body still has keyboard input");

		context.runOnClient(client -> client.player.turn(-200, 0));
		context.waitTicks(5);
		context.takeScreenshot("basetracer_freelook");

		// Coming back restores control of the body.
		context.getInput().pressKey(freeLookKey);
		context.waitFor(client -> !FreeLook.isActive(), TIMEOUT_TICKS);
		context.waitTicks(5);
		check(context.computeOnClient(client -> client.gameRenderer.mainCamera().position().distanceTo(client.player.getEyePosition()) < 1.0), "Camera didn't return to the body");
		check(context.computeOnClient(client -> client.player.input.getClass() != ClientInput.class), "Body didn't get its input back");

		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitFor(client -> client.level.getBlockState(lookedAt).isAir(), TIMEOUT_TICKS);

		testFreeLookDamage(context, server, freeLookKey);

		// Left on when leaving the world, so the caller can check that it switches itself off.
		server.runCommand("gamemode spectator @a");
		context.getInput().pressKey(freeLookKey);
		context.waitFor(client -> FreeLook.isActive(), TIMEOUT_TICKS);
	}

	private static void testFreeLookDamage(ClientGameTestContext context, TestServerContext server, InputConstants.Key freeLookKey) {
		server.runCommand("gamemode survival @a");
		server.runCommand("effect give @a minecraft:instant_damage 1 0");
		context.waitFor(client -> client.player.getHealth() < client.player.getMaxHealth(), TIMEOUT_TICKS);

		// Started while still flashing from that hit, it stays on. So does healing.
		context.getInput().pressKey(freeLookKey);
		context.waitFor(client -> FreeLook.isActive(), TIMEOUT_TICKS);
		context.waitTicks(20);
		check(context.computeOnClient(client -> FreeLook.isActive()), "An earlier hit switched free look off");
		server.runCommand("effect give @a minecraft:instant_health 1 0");
		context.waitFor(client -> client.player.getHealth() == client.player.getMaxHealth(), TIMEOUT_TICKS);
		context.waitTicks(5);
		check(context.computeOnClient(client -> FreeLook.isActive()), "Healing switched free look off");

		// A new hit does, and the body gets its controls back.
		hurtPlayer(server, 2F);
		context.waitFor(client -> !FreeLook.isActive(), TIMEOUT_TICKS);
		check(context.computeOnClient(client -> client.player.input.getClass() != ClientInput.class), "Body didn't get its input back after damage");
		context.waitTicks(20);

		// A hit absorbed by golden hearts leaves health untouched, and still counts.
		server.runCommand("effect give @a minecraft:absorption 30 1");
		context.waitFor(client -> client.player.getAbsorptionAmount() > 0F, TIMEOUT_TICKS);
		float healthBeforeAbsorbedHit = context.computeOnClient(client -> client.player.getHealth());
		context.getInput().pressKey(freeLookKey);
		context.waitFor(client -> FreeLook.isActive(), TIMEOUT_TICKS);
		context.waitTicks(5);
		hurtPlayer(server, 1F);
		context.waitFor(client -> !FreeLook.isActive(), TIMEOUT_TICKS);
		check(context.computeOnClient(client -> client.player.getHealth() >= healthBeforeAbsorbedHit), "Absorbed hit test lost health");
	}

	private static void hurtPlayer(TestServerContext server, float amount) {
		server.runOnServer(minecraftServer -> {
			ServerPlayer player = minecraftServer.getPlayerList().getPlayers().getFirst();
			player.invulnerableTime = 0;
			player.hurtServer(player.level(), player.damageSources().generic(), amount);
		});
	}

	private static void testBusynessGradient() {
		int base = 0x1800FFFF;
		check(BaseTracerRenderer.busyness(1) == 0F, "A single source should have no extra brightness");
		check(BaseTracerRenderer.brighten(base, 0F) == base, "A single source should keep the configured color");
		check(BaseTracerRenderer.busyness(32) == 1F && BaseTracerRenderer.busyness(5000) == 1F, "Brightness should cap at 32 sources");

		int previousAlpha = -1;
		int previousRed = -1;

		for (int count : new int[] {1, 2, 4, 8, 16, 32}) {
			int color = BaseTracerRenderer.brighten(base, BaseTracerRenderer.busyness(count));
			check(color >>> 24 > previousAlpha && (color >> 16 & 0xFF) > previousRed, "Should get brighter at " + count + " sources: " + Integer.toHexString(color));
			previousAlpha = color >>> 24;
			previousRed = color >> 16 & 0xFF;
		}

		// A color set fully transparent stays that way, and opaque white can't overflow.
		check(BaseTracerRenderer.brighten(0x00FF0000, 1F) >>> 24 == 0, "Transparent colors should stay transparent");
		check(BaseTracerRenderer.brighten(0xFFFFFFFF, 1F) == 0xFFFFFFFF, "White should stay white");

		check(BaseTracerRenderer.isCameraInChunk(new Vec3(16.0, 300.0, 31.9), 1, 1), "Camera should count as in its own chunk, at any height");
		check(!BaseTracerRenderer.isCameraInChunk(new Vec3(15.9, 100.0, 16.0), 1, 1), "Camera in the next chunk over shouldn't count");
		check(BaseTracerRenderer.isCameraInChunk(new Vec3(-0.5, 100.0, -16.5), -1, -2), "Negative coordinates should round down");
	}

	private static void takeBusynessScreenshot(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
		// Side by side: chunks with 1, 8 and 64 sources.
		server.runCommand("fill 16 99 48 63 99 63 minecraft:stone");
		server.runCommand("setblock 24 100 56 minecraft:torch");
		server.runCommand("fill 32 100 48 35 100 49 minecraft:torch");
		server.runCommand("fill 48 100 48 55 100 55 minecraft:torch");
		awaitSource(context, new BlockPos(55, 100, 55), true);

		context.runOnClient(client -> BaseTracerClient.CONFIG.showTracers = false);
		server.runCommand("tp @a 40 118 34 0 45");
		connection.waitForChunksRender();
		context.waitTicks(20);
		context.takeScreenshot("basetracer_busyness");
		context.runOnClient(client -> BaseTracerClient.CONFIG.showTracers = true);
	}

	private static List<Long> targets(ClientGameTestContext context, Vec3 camera, TracerMode mode) {
		return context.computeOnClient(client -> {
			List<Long> targets = new ArrayList<>();

			for (BaseTracker.ChunkHighlight highlight : BaseTracerClient.TRACKER.snapshot(camera, mode)) {
				for (long target : highlight.targets()) {
					targets.add(target);
				}
			}

			return targets;
		});
	}

	private static void takeScreenshots(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
		connection.waitForChunksRender();
		context.waitTicks(20);
		context.takeScreenshot("basetracer_on");

		context.runOnClient(client -> BaseTracerClient.CONFIG.showChunkHighlights = false);
		context.waitTicks(5);
		context.takeScreenshot("basetracer_tracers_only");

		context.runOnClient(client -> {
			BaseTracerClient.CONFIG.showChunkHighlights = true;
			BaseTracerClient.CONFIG.showTracers = false;
		});
		context.waitTicks(5);
		context.takeScreenshot("basetracer_highlights_only");

		context.runOnClient(client -> {
			BaseTracerClient.CONFIG.showTracers = true;
			BaseTracerClient.CONFIG.tracerMode = TracerMode.NEAREST_CHUNK;
		});
		context.waitTicks(5);
		context.takeScreenshot("basetracer_nearest_chunk");

		context.runOnClient(client -> BaseTracerClient.CONFIG.tracerMode = TracerMode.MOST_SOURCES);
		context.setScreen(() -> new BaseTracerModMenu().getModConfigScreenFactory().create(null));
		context.waitTicks(5);
		context.takeScreenshot("basetracer_settings_most_sources");
		context.setScreen(() -> null);
		context.runOnClient(client -> BaseTracerClient.CONFIG.tracerMode = TracerMode.NEAREST_PER_CHUNK);

		context.runOnClient(client -> BaseTracerClient.CONFIG.enabled = false);
		context.waitTicks(5);
		context.takeScreenshot("basetracer_off");
		context.runOnClient(client -> BaseTracerClient.CONFIG.enabled = true);

		// Inside a highlighted chunk, where box edges pass behind the camera.
		server.runCommand("tp @a 50.5 106 2.5 -60 25");
		connection.waitForChunksRender();
		context.waitTicks(20);
		context.takeScreenshot("basetracer_inside_chunk");
	}

	private static void testSmartTool(ClientGameTestContext context, TestServerContext server) {
		check(context.computeOnClient(client -> BaseTracerClient.CONFIG.smartTool), "Smart tool should start on");
		// Something to stand on, or a survival player falls out of the world during the test.
		server.runCommand("setblock 8 99 8 minecraft:barrier");
		server.runCommand("tp @a 8.5 100 8.5 -90 0");
		server.runCommand("gamemode survival @a");
		server.runCommand("clear @a");
		server.runCommand("item replace entity @a hotbar.0 with minecraft:torch");
		server.runCommand("item replace entity @a hotbar.1 with minecraft:stone_shovel");
		server.runCommand("item replace entity @a hotbar.2 with minecraft:golden_pickaxe[enchantments={efficiency:5}]");
		server.runCommand("item replace entity @a hotbar.3 with minecraft:iron_pickaxe");
		server.runCommand("item replace entity @a hotbar.4 with minecraft:iron_shovel");
		server.runCommand("item replace entity @a hotbar.5 with minecraft:iron_pickaxe[enchantments={efficiency:3}]");
		server.runCommand("item replace entity @a hotbar.6 with minecraft:stone_axe");
		server.runCommand("item replace entity @a hotbar.7 with minecraft:diamond_axe[damage=1560]");
		server.runCommand("item replace entity @a hotbar.8 with minecraft:iron_hoe");
		server.runCommand("setblock 9 100 8 minecraft:stone");
		server.runCommand("setblock 9 100 9 minecraft:dirt");
		server.runCommand("setblock 9 100 7 minecraft:oak_log");
		server.runCommand("setblock 9 101 8 minecraft:diamond_ore");
		server.runCommand("setblock 9 101 9 minecraft:glass");
		server.runCommand("setblock 9 101 7 minecraft:sculk");
		server.runCommand("setblock 9 102 8 minecraft:oak_leaves");
		// The torch stands in for a sword, gone back to after each block.
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
		context.waitTicks(10);

		// Stone takes the fastest pickaxe, the gold one with Efficiency V. Diamond ore takes the best that gets its
		// drops, the iron one with Efficiency III over the plain one. The nearly broken axe is skipped.
		check(mineWith(context, server, new BlockPos(9, 100, 8)) == 2, "Stone should select the fastest pickaxe");
		check(mineWith(context, server, new BlockPos(9, 101, 8)) == 5, "Diamond ore should select the best pickaxe that can mine it");
		check(context.computeOnClient(client -> client.level.getBlockState(new BlockPos(9, 101, 8)).is(net.minecraft.world.level.block.Blocks.DIAMOND_ORE)), "The gold pickaxe shouldn't break diamond ore before the switch");
		check(mineWith(context, server, new BlockPos(9, 100, 9)) == 4, "Dirt should select the best shovel");
		check(mineWith(context, server, new BlockPos(9, 100, 7)) == 6, "Wood should select an axe that won't break");
		check(mineWith(context, server, new BlockPos(9, 101, 9)) == 0, "Glass needs no tool, so the slot shouldn't change");
		check(mineWith(context, server, new BlockPos(9, 102, 8)) == 0, "Leaves shouldn't select the hoe");
		check(mineWith(context, server, new BlockPos(9, 101, 7)) == 8, "Sculk should select the hoe");

		// Without Silk Touch anywhere, glass keeps what's in hand. With it, ender chests and glass take the Silk Touch
		// pickaxe over faster ones, though stone still takes the fastest.
		check(mineWith(context, server, new BlockPos(9, 101, 9)) == 0, "Glass without a Silk Touch tool shouldn't change the slot");

		// Mining on from one block to another switches again, and goes back to the first slot at the end.
		context.runOnClient(client -> client.gameMode.startDestroyBlock(new BlockPos(9, 100, 8), net.minecraft.core.Direction.WEST));
		holdMining(context, new BlockPos(9, 100, 8));
		check(context.computeOnClient(client -> client.player.getInventory().getSelectedSlot() == 2), "Stone should select the pickaxe");
		context.runOnClient(client -> client.gameMode.startDestroyBlock(new BlockPos(9, 100, 9), net.minecraft.core.Direction.WEST));
		holdMining(context, new BlockPos(9, 100, 9));
		check(context.computeOnClient(client -> client.player.getInventory().getSelectedSlot() == 4), "Dirt next should select the shovel");
		context.runOnClient(client -> client.gameMode.stopDestroyBlock());
		context.waitTicks(5);
		check(context.computeOnClient(client -> client.player.getInventory().getSelectedSlot() == 0), "Letting go should go back to the first slot");

		// The stone shovel in hand from here.
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(1));
		server.runCommand("item replace entity @a hotbar.0 with minecraft:stone_pickaxe[enchantments={silk_touch:1}]");
		server.runCommand("setblock 9 102 9 minecraft:ender_chest");
		// Without Silk Touch the glass was just broken by hand, as it would be in play.
		server.runCommand("setblock 9 101 9 minecraft:glass");
		context.waitTicks(5);
		// A click shorter than the delay is a misclick, and switches nothing.
		int before = context.computeOnClient(client -> client.player.getInventory().getSelectedSlot());
		context.runOnClient(client -> client.gameMode.startDestroyBlock(new BlockPos(9, 101, 9), net.minecraft.core.Direction.WEST));
		for (int tick = 0; tick < 3; tick++) {
			context.waitTicks(1);
			context.runOnClient(client -> client.gameMode.continueDestroyBlock(new BlockPos(9, 101, 9), net.minecraft.core.Direction.WEST));
		}

		check(context.computeOnClient(client -> SmartTool.isPending()), "A switch should wait for the delay");
		context.runOnClient(client -> client.gameMode.stopDestroyBlock());
		context.waitTicks(20);
		check(context.computeOnClient(client -> !SmartTool.isPending() && client.player.getInventory().getSelectedSlot() == before), "A misclick shouldn't switch the tool");

		// Glass breaks by hand before the delay is up, so it waits for the switch instead.
		check(mineWith(context, server, new BlockPos(9, 101, 9)) == 0, "Glass should select the Silk Touch tool");
		check(context.computeOnClient(client -> client.level.getBlockState(new BlockPos(9, 101, 9)).is(net.minecraft.world.level.block.Blocks.GLASS)), "Glass shouldn't break before the switch");
		check(mineWith(context, server, new BlockPos(9, 100, 8)) == 2, "Stone should still select the fastest pickaxe");
		check(mineWith(context, server, new BlockPos(9, 102, 9)) == 0, "An ender chest should select the Silk Touch pickaxe");
		server.runCommand("item replace entity @a hotbar.0 with minecraft:torch");
		context.waitTicks(2);
		check(mineWith(context, server, new BlockPos(9, 100, 7)) == 6, "Wood should select the axe again");

		// A worse tool of the right kind in hand is swapped for the best.
		server.runCommand("setblock 9 102 7 minecraft:deepslate");
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(3));
		context.waitTicks(5);
		check(mineWith(context, server, new BlockPos(9, 102, 7)) == 2, "A plain iron pickaxe in hand should be swapped for the faster gold one");
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(2));
		context.waitTicks(5);
		check(mineWith(context, server, new BlockPos(9, 102, 7)) == 2, "The best pickaxe should stay selected");

		// Off, the selection is left alone.
		context.runOnClient(client -> BaseTracerClient.CONFIG.smartTool = false);
		check(mineWith(context, server, new BlockPos(9, 100, 9)) == 2, "Smart tool off shouldn't change the slot");
		context.runOnClient(client -> BaseTracerClient.CONFIG.smartTool = true);

		// A boat is hit with the axe, kept for a moment after for the next hit, then the pickaxe is back.
		server.runCommand("summon minecraft:oak_boat 10.5 100 8.5 {NoGravity:1b}");
		context.waitTicks(10);
		int axeSlot = context.computeOnClient(client -> {
			for (Entity entity : client.level.entitiesForRendering()) {
				if (entity instanceof net.minecraft.world.entity.vehicle.boat.AbstractBoat) {
					client.gameMode.attack(client.player, entity);
					return client.player.getInventory().getSelectedSlot();
				}
			}

			return -1;
		});
		check(axeSlot == 6, "Hitting a boat should select the axe, not " + axeSlot);
		context.waitTicks(10);
		check(context.computeOnClient(client -> client.player.getInventory().getSelectedSlot() == 6), "The axe should stay for the next hit");
		context.waitTicks(20);
		check(context.computeOnClient(client -> client.player.getInventory().getSelectedSlot() == 2), "The pickaxe should be back after hitting the boat");
		check(server.computeOnServer(minecraftServer -> minecraftServer.getPlayerList().getPlayers().getFirst().getInventory().getSelectedSlot()) == 2, "The server should be back on the pickaxe");
		server.runCommand("kill @e[type=minecraft:oak_boat]");
		server.runCommand("kill @e[type=minecraft:item]");

		server.runCommand("fill 9 100 7 9 102 9 minecraft:air");
		server.runCommand("clear @a");
		server.runCommand("gamemode spectator @a");
		server.runCommand("setblock 8 99 8 minecraft:air");
		server.runCommand("tp @a 8.5 100 8.5 -90 0");
		context.waitTicks(5);
	}

	private static void testAutoEat(ClientGameTestContext context, TestServerContext server) {
		check(context.computeOnClient(client -> BaseTracerClient.CONFIG.autoEat && BaseTracerClient.CONFIG.autoEatHunger == 14), "Auto eat should start on, at 14");
		server.runCommand("setblock 8 99 8 minecraft:barrier");
		server.runCommand("tp @a 8.5 100 8.5 -90 0");
		server.runCommand("gamemode survival @a");
		server.runCommand("clear @a");
		server.runCommand("item replace entity @a hotbar.0 with minecraft:iron_sword");
		server.runCommand("item replace entity @a hotbar.3 with minecraft:rotten_flesh 8");
		server.runCommand("item replace entity @a hotbar.4 with minecraft:bread 8");
		server.runCommand("item replace entity @a hotbar.5 with minecraft:cooked_beef 8");
		server.runCommand("item replace entity @a hotbar.6 with minecraft:golden_apple 8");
		server.runCommand("item replace entity @a hotbar.7 with minecraft:golden_carrot 8");
		server.runCommand("item replace entity @a hotbar.8 with minecraft:enchanted_golden_apple 8");
		// Something to right-click by mistake: eating mustn't open it.
		server.runCommand("setblock 10 100 8 minecraft:crafting_table");
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
		context.waitTicks(5);

		// Above the threshold, nothing is eaten.
		server.runOnServer(minecraftServer -> minecraftServer.getPlayerList().getPlayers().getFirst().getFoodData().setFoodLevel(15));
		context.waitTicks(20);
		check(context.computeOnClient(client -> !AutoEat.isEating() && client.player.getInventory().getItem(5).getCount() == 8), "Shouldn't eat above the threshold");

		// At it, the steak is eaten over everything else, then the sword is back in hand.
		server.runOnServer(minecraftServer -> minecraftServer.getPlayerList().getPlayers().getFirst().getFoodData().setFoodLevel(10));
		context.waitFor(client -> AutoEat.isEating(), TIMEOUT_TICKS);
		check(context.computeOnClient(client -> client.player.getInventory().getSelectedSlot() == 5 && client.player.isUsingItem()), "Should be eating the steak");
		context.waitFor(client -> !AutoEat.isEating(), TIMEOUT_TICKS);
		context.waitTicks(5);
		check(context.computeOnClient(client -> client.player.getInventory().getSelectedSlot() == 0), "The sword should be back in hand");
		check(context.computeOnClient(client -> client.gui.screen() == null), "Eating shouldn't use the crafting table");
		int food = server.computeOnServer(minecraftServer -> minecraftServer.getPlayerList().getPlayers().getFirst().getFoodData().getFoodLevel());
		check(food == 18, "One steak should have been eaten: food is " + food);
		check(context.computeOnClient(client -> client.player.getInventory().getItem(5).getCount() == 7
				&& client.player.getInventory().getItem(4).getCount() == 8
				&& client.player.getInventory().getItem(3).getCount() == 8
				&& client.player.getInventory().getItem(6).getCount() == 8
				&& client.player.getInventory().getItem(7).getCount() == 8), "Only the steak should have been eaten");

		// Without steak and bread, the golden carrot comes next, then the golden apple.
		server.runCommand("item replace entity @a hotbar.4 with minecraft:air");
		server.runCommand("item replace entity @a hotbar.5 with minecraft:air");
		check(eatsFrom(context, server) == 7, "The golden carrot should be eaten next");
		server.runCommand("item replace entity @a hotbar.7 with minecraft:air");
		check(eatsFrom(context, server) == 6, "The golden apple should be eaten next");

		// With only rotten flesh and an enchanted golden apple, nothing is eaten.
		server.runCommand("item replace entity @a hotbar.6 with minecraft:air");
		server.runOnServer(minecraftServer -> minecraftServer.getPlayerList().getPlayers().getFirst().getFoodData().setFoodLevel(10));
		context.waitTicks(40);
		check(context.computeOnClient(client -> !AutoEat.isEating() && client.player.getInventory().getItem(3).getCount() == 8
				&& client.player.getInventory().getItem(8).getCount() == 8), "Rotten flesh and enchanted golden apples shouldn't be eaten");

		// Hurt, with a full hunger bar, only a golden apple can be eaten, and is. Once it's healing, no more.
		server.runCommand("item replace entity @a hotbar.4 with minecraft:bread 8");
		server.runCommand("item replace entity @a hotbar.6 with minecraft:golden_apple 8");
		// Still healing from the golden apple eaten above.
		server.runCommand("effect clear @a");
		context.waitTicks(5);
		server.runOnServer(minecraftServer -> {
			var player = minecraftServer.getPlayerList().getPlayers().getFirst();
			player.getFoodData().setFoodLevel(20);
			player.getFoodData().setSaturation(0F);
			player.setHealth(8F);
		});
		context.waitFor(client -> AutoEat.isEating(), TIMEOUT_TICKS);
		check(context.computeOnClient(client -> client.player.getInventory().getSelectedSlot() == 6), "Hurt, the golden apple should be eaten");
		context.waitFor(client -> !AutoEat.isEating(), TIMEOUT_TICKS);
		context.waitTicks(30);
		check(context.computeOnClient(client -> client.player.getInventory().getItem(6).getCount() == 7
				&& client.player.getInventory().getItem(4).getCount() == 8), "Only one golden apple should be eaten while healing");

		// Hurt and a little hungry, without golden apples, the bread is eaten.
		server.runCommand("item replace entity @a hotbar.6 with minecraft:air");
		server.runCommand("effect clear @a");
		server.runOnServer(minecraftServer -> {
			var player = minecraftServer.getPlayerList().getPlayers().getFirst();
			player.getFoodData().setFoodLevel(19);
			player.setHealth(8F);
		});
		context.waitFor(client -> AutoEat.isEating(), TIMEOUT_TICKS);
		check(context.computeOnClient(client -> client.player.getInventory().getSelectedSlot() == 4), "Hurt, the bread should be eaten");
		context.waitFor(client -> !AutoEat.isEating(), TIMEOUT_TICKS);
		server.runOnServer(minecraftServer -> minecraftServer.getPlayerList().getPlayers().getFirst().setHealth(20F));
		context.waitTicks(10);

		server.runCommand("setblock 10 100 8 minecraft:air");
		// Later tests need a full hunger bar to heal.
		server.runOnServer(minecraftServer -> minecraftServer.getPlayerList().getPlayers().getFirst().getFoodData().setFoodLevel(20));
		server.runCommand("clear @a");
		server.runCommand("gamemode spectator @a");
		server.runCommand("setblock 8 99 8 minecraft:air");
		server.runCommand("tp @a 8.5 100 8.5 -90 0");
		context.waitTicks(5);
	}

	private static void testTakeAll(ClientGameTestContext context, TestServerContext server) {
		check(context.computeOnClient(client -> BaseTracerClient.CONFIG.takeAll && BaseTracerClient.CONFIG.takeAllDelay == 1), "Take all should start on, a slot a tick");

		// Waits vary, from none up to the delay, and are none with no delay.
		java.util.Random random = new java.util.Random(1);
		java.util.Set<Integer> waits = new java.util.HashSet<>();

		for (int i = 0; i < 200; i++) {
			waits.add(TakeAll.nextWait(random, 4));
		}

		check(waits.equals(java.util.Set.of(0, 1, 2, 3, 4)), "Waits should range from none to the delay: " + waits);
		check(TakeAll.nextWait(random, 0) == 0, "No delay should mean no wait");
		server.runCommand("setblock 8 99 8 minecraft:barrier");
		server.runCommand("tp @a 8.5 100 8.5 -90 0");
		server.runCommand("gamemode survival @a");
		server.runCommand("clear @a");
		server.runCommand("setblock 10 100 8 minecraft:chest");
		server.runCommand("item replace block 10 100 8 container.0 with minecraft:diamond 64");
		server.runCommand("item replace block 10 100 8 container.5 with minecraft:iron_ingot 32");
		server.runCommand("item replace block 10 100 8 container.13 with minecraft:oak_log 64");
		server.runCommand("item replace block 10 100 8 container.26 with minecraft:bread 10");
		context.waitTicks(5);

		// The button is on the chest's screen.
		openChest(context, server);
		check(context.computeOnClient(client -> Screens.getWidgets(client.gui.screen()).stream()
				.anyMatch(widget -> widget.getMessage().getString().equals("Take All"))), "The chest should have a Take All button");
		check(context.computeOnClient(client -> Screens.getWidgets(client.gui.screen()).stream()
				.anyMatch(widget -> widget.getMessage().getString().equals("Put All"))), "The chest should have a Put All button");
		context.takeScreenshot("basetracer_take_all");

		// Taking all, a slot a tick, empties the chest into the inventory, on the server too.
		context.runOnClient(client -> TakeAll.start((net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) client.gui.screen()));
		check(context.computeOnClient(client -> TakeAll.isTaking()), "Take all should have slots queued");
		context.waitFor(client -> !TakeAll.isTaking(), TIMEOUT_TICKS);
		context.waitTicks(5);
		check(context.computeOnClient(client -> client.player.getInventory().countItem(Items.DIAMOND) == 64
				&& client.player.getInventory().countItem(Items.IRON_INGOT) == 32
				&& client.player.getInventory().countItem(Items.OAK_LOG) == 64
				&& client.player.getInventory().countItem(Items.BREAD) == 10), "Everything should be in the inventory");
		check(server.computeOnServer(minecraftServer -> {
			var chest = (net.minecraft.world.Container) minecraftServer.overworld().getBlockEntity(new BlockPos(10, 100, 8));
			return chest.isEmpty();
		}), "The chest should be empty on the server");

		// With only diamonds listed, Put All moves just the diamonds.
		context.runOnClient(client -> {
			BaseTracerClient.CONFIG.putAllHotbar = true;
			BaseTracerClient.CONFIG.putAllFilter = PutAllFilter.ONLY_LISTED;
			BaseTracerClient.CONFIG.setPutAllItem(Items.DIAMOND, true);
			TakeAll.startPut((net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) client.gui.screen());
		});
		context.waitFor(client -> !TakeAll.isTaking(), TIMEOUT_TICKS);
		context.waitTicks(5);
		check(context.computeOnClient(client -> client.player.getInventory().countItem(Items.DIAMOND) == 0
				&& client.player.getInventory().countItem(Items.IRON_INGOT) == 32
				&& client.player.getInventory().countItem(Items.OAK_LOG) == 64
				&& client.player.getInventory().countItem(Items.BREAD) == 10), "Only listed should move only the diamonds");

		// With oak logs listed to leave, everything else goes.
		context.runOnClient(client -> {
			BaseTracerClient.CONFIG.putAllFilter = PutAllFilter.EXCEPT_LISTED;
			BaseTracerClient.CONFIG.clearPutAllItems();
			BaseTracerClient.CONFIG.setPutAllItem(Items.OAK_LOG, true);
			TakeAll.startPut((net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) client.gui.screen());
		});
		context.waitFor(client -> !TakeAll.isTaking(), TIMEOUT_TICKS);
		context.waitTicks(5);
		check(context.computeOnClient(client -> client.player.getInventory().countItem(Items.IRON_INGOT) == 0
				&& client.player.getInventory().countItem(Items.OAK_LOG) == 64
				&& client.player.getInventory().countItem(Items.BREAD) == 0), "All but listed should leave only the oak logs");
		check(server.computeOnServer(minecraftServer -> {
			var chest = (net.minecraft.world.Container) minecraftServer.overworld().getBlockEntity(new BlockPos(10, 100, 8));
			return chest.countItem(Items.DIAMOND) == 64 && chest.countItem(Items.IRON_INGOT) == 32 && chest.countItem(Items.BREAD) == 10
					&& chest.countItem(Items.OAK_LOG) == 0;
		}), "The filtered items should be in the chest, on the server");
		context.runOnClient(client -> {
			BaseTracerClient.CONFIG.putAllHotbar = false;
			BaseTracerClient.CONFIG.putAllFilter = PutAllFilter.OFF;
			BaseTracerClient.CONFIG.clearPutAllItems();
		});
		server.runCommand("setblock 10 100 8 minecraft:air");
		server.runCommand("setblock 10 100 8 minecraft:chest");

		// Put All puts the inventory's things in the chest, but the hotbar's bread stays unless chosen.
		context.runOnClient(client -> client.player.closeContainer());
		server.runCommand("clear @a");
		server.runCommand("item replace entity @a inventory.0 with minecraft:diamond 64");
		server.runCommand("item replace entity @a inventory.20 with minecraft:oak_log 64");
		server.runCommand("item replace entity @a hotbar.0 with minecraft:bread 10");
		context.waitTicks(5);
		openChest(context, server);
		context.runOnClient(client -> TakeAll.startPut((net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) client.gui.screen()));
		context.waitFor(client -> !TakeAll.isTaking(), TIMEOUT_TICKS);
		context.waitTicks(5);
		check(context.computeOnClient(client -> client.player.getInventory().countItem(Items.BREAD) == 10
				&& client.player.getInventory().countItem(Items.DIAMOND) == 0), "Put All should leave only the hotbar's bread");
		check(server.computeOnServer(minecraftServer -> {
			var chest = (net.minecraft.world.Container) minecraftServer.overworld().getBlockEntity(new BlockPos(10, 100, 8));
			return chest.countItem(Items.DIAMOND) == 64 && chest.countItem(Items.OAK_LOG) == 64 && chest.countItem(Items.BREAD) == 0;
		}), "The chest should have the items back, on the server");
		context.runOnClient(client -> BaseTracerClient.CONFIG.putAllHotbar = true);
		context.runOnClient(client -> TakeAll.startPut((net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) client.gui.screen()));
		context.waitFor(client -> !TakeAll.isTaking(), TIMEOUT_TICKS);
		context.waitTicks(5);
		check(context.computeOnClient(client -> client.player.getInventory().isEmpty()), "Put All with the hotbar should empty the inventory");
		context.runOnClient(client -> BaseTracerClient.CONFIG.putAllHotbar = false);
		context.runOnClient(client -> client.player.closeContainer());
		server.runCommand("setblock 10 100 8 minecraft:air");
		server.runCommand("setblock 10 100 8 minecraft:chest");

		// With a full inventory, nothing moves and it stops.
		for (int slot = 0; slot < 36; slot++) {
			server.runCommand("item replace entity @a " + (slot < 9 ? "hotbar." + slot : "inventory." + (slot - 9)) + " with minecraft:cobblestone 64");
		}

		server.runCommand("item replace block 10 100 8 container.0 with minecraft:diamond 64");
		context.waitTicks(5);
		openChest(context, server);
		context.runOnClient(client -> TakeAll.start((net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) client.gui.screen()));
		context.waitFor(client -> !TakeAll.isTaking(), TIMEOUT_TICKS);
		context.waitTicks(5);
		check(server.computeOnServer(minecraftServer -> {
			var chest = (net.minecraft.world.Container) minecraftServer.overworld().getBlockEntity(new BlockPos(10, 100, 8));
			return chest.getItem(0).getCount() == 64;
		}), "A full inventory should leave the diamonds in the chest");
		context.runOnClient(client -> client.player.closeContainer());

		server.runCommand("setblock 10 100 8 minecraft:air");
		server.runCommand("kill @e[type=minecraft:item]");
		server.runCommand("clear @a");
		server.runCommand("gamemode spectator @a");
		server.runCommand("setblock 8 99 8 minecraft:air");
		server.runCommand("tp @a 8.5 100 8.5 -90 0");
		context.waitTicks(5);
	}

	/** Settings can be opened before any world, when items have no data components to make stacks from yet. */
	private static void testPutAllItemsFromTitleScreen(ClientGameTestContext context) {
		context.setScreen(() -> new PutAllItemsScreen(null));
		check(context.computeOnClient(client -> client.gui.screen() instanceof PutAllItemsScreen), "The Put All item list should open from the title screen");
		context.setScreen(() -> null);
	}

	private static void openChest(ClientGameTestContext context, TestServerContext server) {
		server.runOnServer(minecraftServer -> {
			var player = minecraftServer.getPlayerList().getPlayers().getFirst();
			player.openMenu((net.minecraft.world.MenuProvider) minecraftServer.overworld().getBlockEntity(new BlockPos(10, 100, 8)));
		});
		context.waitFor(client -> client.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen, TIMEOUT_TICKS);
		context.waitTicks(2);
	}

	private static void testQuickUse(ClientGameTestContext context, TestServerContext server) {
		check(context.computeOnClient(client -> BaseTracerClient.CONFIG.quickUseAction == QuickUseAction.ENDER_PEARL), "Quick use should start on ender pearls");
		server.runCommand("setblock 8 99 8 minecraft:barrier");
		server.runCommand("tp @a 8.5 100 8.5 -90 0");
		server.runCommand("gamemode survival @a");
		server.runCommand("clear @a");
		server.runCommand("effect clear @a");
		server.runCommand("item replace entity @a hotbar.0 with minecraft:iron_sword");
		server.runCommand("item replace entity @a hotbar.2 with minecraft:ender_pearl 16");
		server.runCommand("item replace entity @a hotbar.3 with minecraft:potion[potion_contents={potion:\"minecraft:night_vision\"}]");
		server.runCommand("item replace entity @a hotbar.4 with minecraft:enchanted_golden_apple 4");
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
		context.waitTicks(5);

		// Mouse button 4 throws a pearl, and the sword is back in hand straight after. Thrown at the feet, so that it
		// can't land somewhere and take the player off their platform.
		server.runCommand("tp @a 8.5 100 8.5 -90 90");
		context.waitTicks(5);
		context.getInput().pressKey(InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_4));
		context.waitTicks(5);
		check(context.computeOnClient(client -> client.player.getInventory().getItem(2).getCount() == 15), "Mouse button 4 should throw an ender pearl");
		check(context.computeOnClient(client -> client.player.getInventory().getSelectedSlot() == 0), "The sword should be back after the pearl");
		check(server.computeOnServer(minecraftServer -> minecraftServer.getPlayerList().getPlayers().getFirst().getInventory().getSelectedSlot()) == 0, "The server should be back on the sword");

		// Pearls cool down, so a second right after isn't thrown.
		context.getInput().pressKey(InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_4));
		context.waitTicks(5);
		check(context.computeOnClient(client -> client.player.getInventory().getItem(2).getCount() == 15), "A pearl shouldn't be thrown while cooling down");

		// A potion is drunk through, then the sword is back.
		context.runOnClient(client -> BaseTracerClient.CONFIG.quickUseAction = QuickUseAction.POTION);
		context.getInput().pressKey(InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_4));
		context.waitFor(client -> client.player.getInventory().getItem(3).is(Items.GLASS_BOTTLE), TIMEOUT_TICKS);
		context.waitTicks(5);
		check(context.computeOnClient(client -> client.player.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION)), "The potion should have been drunk");
		check(context.computeOnClient(client -> client.player.getInventory().getSelectedSlot() == 0), "The sword should be back after the potion");

		// An enchanted golden apple is eaten, though auto eat never would.
		context.runOnClient(client -> BaseTracerClient.CONFIG.quickUseAction = QuickUseAction.ENCHANTED_APPLE);
		context.getInput().pressKey(InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_4));
		context.waitFor(client -> client.player.getInventory().getItem(4).getCount() == 3, TIMEOUT_TICKS);
		context.waitTicks(5);
		check(context.computeOnClient(client -> client.player.getAbsorptionAmount() > 0F), "The apple should have been eaten");
		check(context.computeOnClient(client -> client.player.getInventory().getSelectedSlot() == 0), "The sword should be back after the apple");

		// With none in the hotbar, nothing happens.
		context.runOnClient(client -> BaseTracerClient.CONFIG.quickUseAction = QuickUseAction.POTION);
		context.getInput().pressKey(InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_4));
		context.waitTicks(5);
		check(context.computeOnClient(client -> !HeldUse.isActive() && client.player.getInventory().getSelectedSlot() == 0), "Nothing should be used without a potion");

		context.runOnClient(client -> BaseTracerClient.CONFIG.quickUseAction = QuickUseAction.ENDER_PEARL);
		server.runCommand("kill @e[type=minecraft:ender_pearl]");
		server.runCommand("effect clear @a");
		server.runCommand("clear @a");
		server.runCommand("gamemode spectator @a");
		server.runCommand("setblock 8 99 8 minecraft:air");
		server.runCommand("tp @a 8.5 100 8.5 -90 0");
		context.waitTicks(5);
	}

	/** Makes the player hungry, and returns the slot of the food then eaten, after checking one was eaten. */
	private static int eatsFrom(ClientGameTestContext context, TestServerContext server) {
		context.waitTicks(5);
		server.runOnServer(minecraftServer -> minecraftServer.getPlayerList().getPlayers().getFirst().getFoodData().setFoodLevel(4));
		context.waitFor(client -> AutoEat.isEating(), TIMEOUT_TICKS);
		int slot = context.computeOnClient(client -> client.player.getInventory().getSelectedSlot());
		context.waitFor(client -> !AutoEat.isEating(), TIMEOUT_TICKS);
		context.waitTicks(5);
		check(context.computeOnClient(client -> client.player.getInventory().getItem(slot).getCount() == 7), "Food in slot " + slot + " wasn't eaten");
		return slot;
	}

	/**
	 * Mines a block for as long as the delay, and returns the slot then selected. Then lets go, and checks the slot
	 * from before is back, on the server too.
	 */
	private static int mineWith(ClientGameTestContext context, TestServerContext server, BlockPos pos) {
		int before = context.computeOnClient(client -> client.player.getInventory().getSelectedSlot());
		context.runOnClient(client -> client.gameMode.startDestroyBlock(pos, net.minecraft.core.Direction.WEST));
		holdMining(context, pos);
		int slot = context.computeOnClient(client -> {
			client.gameMode.stopDestroyBlock();
			return client.player.getInventory().getSelectedSlot();
		});
		context.waitTicks(5);
		int after = context.computeOnClient(client -> client.player.getInventory().getSelectedSlot());
		int serverSlot = server.computeOnServer(minecraftServer -> minecraftServer.getPlayerList().getPlayers().getFirst().getInventory().getSelectedSlot());
		check(after == before, "Letting go should go back to slot " + before + ", not " + after);
		check(serverSlot == before, "The server should be back on slot " + before + ", not " + serverSlot);
		return slot;
	}

	/** Goes on mining a block, as holding the attack key would, for as long as the delay. */
	private static void holdMining(ClientGameTestContext context, BlockPos pos) {
		int delay = context.computeOnClient(client -> BaseTracerClient.CONFIG.smartToolDelay);

		for (int tick = 0; tick < delay; tick++) {
			context.waitTicks(1);
			context.runOnClient(client -> client.gameMode.continueDestroyBlock(pos, net.minecraft.core.Direction.WEST));
		}

		context.waitTicks(1);
	}

	private static void testDroppedItems(ClientGameTestContext context, TestServerContext server) {
		check(context.computeOnClient(client -> !BaseTracerClient.CONFIG.showItems), "Item highlights should start off");
		context.runOnClient(client -> BaseTracerClient.CONFIG.mobWarning = false);

		// A zombie dies with diamonds next to it, and a stick lies further off on its own.
		server.runCommand("summon minecraft:zombie 13.5 100 7.5 {NoAI:1b,NoGravity:1b,Tags:[\"drop\"]}");
		context.waitTicks(10);
		server.runCommand("kill @e[tag=drop]");
		server.runCommand("summon minecraft:item 13.5 100 7.5 {NoGravity:1b,PickupDelay:32767,Item:{id:\"minecraft:diamond\",count:3}}");
		server.runCommand("summon minecraft:item 12.5 100 13.5 {NoGravity:1b,PickupDelay:32767,Item:{id:\"minecraft:stick\",count:1}}");
		context.waitTicks(40);

		context.runOnClient(client -> {
			ItemEntity diamonds = findItem(client, Items.DIAMOND);
			ItemEntity stick = findItem(client, Items.STICK);
			check(diamonds != null && stick != null, "Items should be on the ground");
			check(BaseTracerClient.DROPPED_ITEMS.isMobDrop(diamonds), "Diamonds by a dead zombie should count as its drop");
			check(!BaseTracerClient.DROPPED_ITEMS.isMobDrop(stick), "A stick far from any death shouldn't");

			Vec3 camera = new Vec3(8.5, 101.6, 8.5);
			List<String> all = BaseTracerClient.DROPPED_ITEMS.marks(client.level, ItemMode.ALL, camera, 0F, 100).stream().map(DroppedItems.ItemMark::label).toList();
			List<String> drops = BaseTracerClient.DROPPED_ITEMS.marks(client.level, ItemMode.MOB_DROPS, camera, 0F, 100).stream().map(DroppedItems.ItemMark::label).toList();
			check(all.contains("Diamond x3") && all.contains("Stick"), "All items should be shown, with counts: " + all);
			check(drops.contains("Diamond x3") && !drops.contains("Stick"), "Only mob drops should be shown: " + drops);
			BaseTracerClient.CONFIG.showItems = true;
		});
		context.waitTicks(5);
		context.takeScreenshot("basetracer_items_all");
		context.runOnClient(client -> BaseTracerClient.CONFIG.itemMode = ItemMode.MOB_DROPS);
		context.waitTicks(5);
		context.takeScreenshot("basetracer_items_mob_drops");

		check(context.computeOnClient(client -> ModuleSplash.lines(BaseTracerClient.CONFIG, false).getFirst().getString().contains("Item Highlights (Mob Drops)")), "Splash should list item highlights");
		context.runOnClient(client -> {
			BaseTracerClient.CONFIG.showItems = false;
			BaseTracerClient.CONFIG.itemMode = ItemMode.ALL;
			BaseTracerClient.CONFIG.mobWarning = true;
		});
		server.runCommand("kill @e[type=minecraft:item]");
		context.waitTicks(5);
	}

	private static ItemEntity findItem(Minecraft client, net.minecraft.world.item.Item type) {
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity instanceof ItemEntity item && item.getItem().is(type)) {
				return item;
			}
		}

		return null;
	}

	private static void testPiglinWarning(ClientGameTestContext context, TestServerContext server) {
		// Past the cooldown from the last ding.
		context.waitTicks(70);
		int dings = context.computeOnClient(client -> BaseTracerClient.MOB_WARNING.dings());
		server.runCommand("summon minecraft:zombified_piglin 18.5 100 8.5 {NoAI:1b,NoGravity:1b,Tags:[\"warn\"]}");
		server.runCommand("summon minecraft:piglin 8.5 100 18.5 {NoAI:1b,NoGravity:1b,IsImmuneToZombification:1b,Tags:[\"warn\"]}");
		context.waitTicks(40);

		// Passive, they come closer without a warning.
		server.runCommand("tp @e[type=minecraft:zombified_piglin,tag=warn] 15.5 100 8.5");
		server.runCommand("tp @e[type=minecraft:piglin,tag=warn] 8.5 100 15.5");
		context.waitTicks(20);
		check(context.computeOnClient(client -> BaseTracerClient.MOB_WARNING.dings()) == dings, "Passive piglins shouldn't warn");

		// Angry, as one attacking shows, the zombified piglin does.
		context.runOnClient(client -> {
			for (Entity entity : client.level.entitiesForRendering()) {
				if (entity instanceof net.minecraft.world.entity.monster.zombie.ZombifiedPiglin piglin) {
					check(MobWarning.isPassive(piglin), "A zombified piglin should start passive");
					piglin.setAggressive(true);
					check(!MobWarning.isPassive(piglin), "An attacking zombified piglin isn't passive");
				}
			}
		});
		server.runCommand("tp @e[type=minecraft:zombified_piglin,tag=warn] 13.5 100 8.5");
		context.waitFor(client -> BaseTracerClient.MOB_WARNING.dings() == dings + 1, TIMEOUT_TICKS);
		String message = context.computeOnClient(client -> BaseTracerClient.MOB_WARNING.lastMessage().getString());
		check(message.startsWith("\u26a0 Zombified Piglin approaching"), "Wrong warning: " + message);

		server.runCommand("kill @e[tag=warn]");
		context.waitTicks(5);
	}

	private static void testMobWarning(ClientGameTestContext context, TestServerContext server) {
		check(context.computeOnClient(client -> BaseTracerClient.CONFIG.mobWarning && BaseTracerClient.CONFIG.mobWarningDistance == 10), "Mob warning should start on, at 10 blocks");
		check(MobWarning.isApproaching(8, 7.5) && !MobWarning.isApproaching(8, 8) && !MobWarning.isApproaching(Double.NaN, 5), "Wrong idea of approaching");
		server.runCommand("difficulty normal");
		int dings = context.computeOnClient(client -> BaseTracerClient.MOB_WARNING.dings());

		// A zombie and a cow appear nearby while the player stands still. Appearing isn't approaching.
		server.runCommand("summon minecraft:zombie 16.5 100 8.5 {NoAI:1b,NoGravity:1b,Tags:[\"warn\"]}");
		server.runCommand("summon minecraft:cow 8.5 100 16.5 {NoAI:1b,NoGravity:1b,Tags:[\"warn\"]}");
		context.waitTicks(40);
		check(context.computeOnClient(client -> BaseTracerClient.MOB_WARNING.dings()) == dings, "A mob just appearing shouldn't warn");

		// The cow coming closer is fine; the zombie isn't.
		server.runCommand("tp @e[type=minecraft:cow,tag=warn] 8.5 100 13.5");
		context.waitTicks(20);
		check(context.computeOnClient(client -> BaseTracerClient.MOB_WARNING.dings()) == dings, "A cow shouldn't warn");
		server.runCommand("tp @e[type=minecraft:zombie,tag=warn] 14.5 100 8.5");
		context.waitFor(client -> BaseTracerClient.MOB_WARNING.dings() == dings + 1, TIMEOUT_TICKS);
		String message = context.computeOnClient(client -> BaseTracerClient.MOB_WARNING.lastMessage().getString());
		check(message.equals("\u26a0 Zombie approaching (6m)"), "Wrong warning: " + message);
		context.takeScreenshot("basetracer_mob_warning");

		// Coming closer still doesn't ding again for the same zombie.
		server.runCommand("tp @e[type=minecraft:zombie,tag=warn] 12.5 100 8.5");
		context.waitTicks(20);
		check(context.computeOnClient(client -> BaseTracerClient.MOB_WARNING.dings()) == dings + 1, "The same zombie shouldn't ding twice");

		// Gone out of range and back, it warns again once the ding has cooled down.
		server.runCommand("tp @e[type=minecraft:zombie,tag=warn] 30.5 100 8.5");
		context.waitTicks(80);
		server.runCommand("tp @e[type=minecraft:zombie,tag=warn] 17.5 100 8.5");
		context.waitTicks(10);
		server.runCommand("tp @e[type=minecraft:zombie,tag=warn] 15.5 100 8.5");
		context.waitFor(client -> BaseTracerClient.MOB_WARNING.dings() == dings + 2, TIMEOUT_TICKS);

		// Not while moving.
		server.runCommand("tp @e[type=minecraft:zombie,tag=warn] 30.5 100 8.5");
		context.waitTicks(80);
		server.runCommand("tp @e[type=minecraft:zombie,tag=warn] 17.5 100 8.5");
		context.waitTicks(10);
		for (int i = 0; i < 6; i++) {
			server.runCommand("tp @a " + (8.5 + (i % 2)) + " 100 8.5 -90 0");
			server.runCommand("tp @e[type=minecraft:zombie,tag=warn] " + (16.5 - i) + " 100 8.5");
			context.waitTicks(3);
		}
		check(context.computeOnClient(client -> BaseTracerClient.MOB_WARNING.dings()) == dings + 2, "Shouldn't warn while moving");

		server.runCommand("kill @e[tag=warn]");
		server.runCommand("tp @a 8.5 100 8.5 -90 0");
		context.waitTicks(5);
	}

	private static void testXRay(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
		check(context.computeOnClient(client -> !BaseTracerClient.CONFIG.xrayEnabled), "X-Ray should start off");

		// Every default id has to exist, since unknown ids are ignored quietly.
		check(context.computeOnClient(client -> BaseTracerClient.CONFIG.xrayBlocks().size() == BaseTracerClient.CONFIG.xrayBlockIds().size()), "Unknown block in X-Ray's defaults");

		// Ores and a chest buried in a solid block of stone, in front of the camera.
		server.runCommand("fill -8 92 -48 8 108 -36 minecraft:stone");
		server.runCommand("setblock 0 100 -38 minecraft:diamond_ore");
		server.runCommand("setblock -3 97 -40 minecraft:gold_ore");
		server.runCommand("setblock 3 103 -42 minecraft:iron_ore");
		server.runCommand("fill -4 104 -44 -2 104 -44 minecraft:deepslate_diamond_ore");
		server.runCommand("setblock 4 96 -38 minecraft:chest");
		server.runCommand("tp @a 0.5 100.5 -24.5 180 0");
		connection.waitForChunksRender();
		context.waitTicks(20);
		context.takeScreenshot("basetracer_xray_off");

		BlockPos diamond = new BlockPos(0, 100, -38);
		BlockPos stone = new BlockPos(0, 100, -37);
		BlockPos chest = new BlockPos(4, 96, -38);
		check(context.computeOnClient(client -> regionState(client, stone).is(Blocks.STONE)), "Meshes should see the stone with X-Ray off");

		// X turns it on even though vanilla's Load Hotbar Activator is on X too, and chunk meshes then only see the
		// chosen blocks.
		check(context.computeOnClient(client -> client.options.keyLoadHotbarActivator.saveString().equals("key.keyboard.x")), "Vanilla should still have X bound");
		context.getInput().holdKeyFor(GLFW.GLFW_KEY_X, 3);
		context.waitFor(client -> BaseTracerClient.CONFIG.xrayEnabled && XRay.isActive(), TIMEOUT_TICKS);
		context.runOnClient(client -> {
			check(regionState(client, stone).isAir(), "Stone should be hidden from meshes");
			check(regionState(client, chest).isAir(), "A chest that isn't chosen should be hidden");
			check(regionState(client, diamond).is(Blocks.DIAMOND_ORE), "Diamond ore should stay");
			// Only meshes: the world itself is untouched.
			check(client.level.getBlockState(stone).is(Blocks.STONE), "X-Ray shouldn't change the world");
		});
		connection.waitForChunksRender();
		context.waitTicks(20);
		context.takeScreenshot("basetracer_xray_on");

		// The splash lists it, and turning the mod off turns it off too.
		check(context.computeOnClient(client -> ModuleSplash.lines(BaseTracerClient.CONFIG, false).getFirst().getString().contains("X-Ray")), "Splash should list X-Ray");
		context.runOnClient(client -> BaseTracerClient.CONFIG.enabled = false);
		context.waitFor(client -> !XRay.isActive(), TIMEOUT_TICKS);
		check(context.computeOnClient(client -> regionState(client, stone).is(Blocks.STONE)), "X-Ray should be off with the mod");
		context.runOnClient(client -> BaseTracerClient.CONFIG.enabled = true);
		context.waitFor(client -> XRay.isActive(), TIMEOUT_TICKS);

		// Choosing a block through the screen shows it straight away.
		context.setScreen(() -> new BaseTracerXRayBlocksScreen(null));
		context.waitTicks(5);
		context.takeScreenshot("basetracer_xray_blocks");
		context.getInput().typeChars("Chest");
		context.waitTicks(5);
		context.getInput().setCursorPos(427, 100);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitFor(client -> BaseTracerClient.CONFIG.xrayBlocks().contains(Blocks.CHEST), TIMEOUT_TICKS);
		context.setScreen(() -> null);
		context.waitFor(client -> regionState(client, chest).is(Blocks.CHEST), TIMEOUT_TICKS);
		connection.waitForChunksRender();
		context.waitTicks(20);
		context.takeScreenshot("basetracer_xray_chest");

		// Kept on reload.
		context.runOnClient(client -> {
			BaseTracerConfig reloaded = BaseTracerConfig.load(FabricLoader.getInstance().getConfigDir());
			check(reloaded.xrayEnabled && reloaded.xrayBlocks().contains(Blocks.CHEST), "X-Ray settings lost on reload");
			BaseTracerClient.CONFIG.resetXrayBlocks();
			check(!BaseTracerClient.CONFIG.xrayBlocks().contains(Blocks.CHEST), "Reset should drop the chest");
		});

		// Held down, it only toggles once.
		context.getInput().holdKeyFor(GLFW.GLFW_KEY_X, 20);
		context.waitTicks(5);
		check(context.computeOnClient(client -> !BaseTracerClient.CONFIG.xrayEnabled && !XRay.isActive()), "Holding X should turn X-Ray off once");
		server.runCommand("fill -8 92 -48 8 108 -36 minecraft:air");
		server.runCommand("tp @a 8.5 100 8.5 -90 0");
		connection.waitForChunksDownload();
	}

	/** A block as chunk meshes see it. */
	private static BlockState regionState(Minecraft client, BlockPos pos) {
		return new RenderRegionCache().createRegion(client.level, net.minecraft.core.SectionPos.asLong(pos)).getBlockState(pos);
	}

	private static void testToggleKey(ClientGameTestContext context) {
		// L is also vanilla's Advancements key. Log what happens with that conflict, without failing on it.
		context.getInput().pressKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_L));
		context.waitTicks(10);
		boolean toggledWithConflict = context.computeOnClient(client -> !BaseTracerClient.CONFIG.enabled);
		String screenWithConflict = context.computeOnClient(client -> String.valueOf(client.gui.screen()));
		BaseTracerClient.LOGGER.info("L with default Advancements binding: toggled={}, screen={}", toggledWithConflict, screenWithConflict);
		context.setScreen(() -> null);
		context.runOnClient(client -> {
			BaseTracerClient.CONFIG.enabled = true;
			// Advancements unbound, so L belongs to this mod alone.
			client.options.keyAdvancements.setKey(InputConstants.UNKNOWN);
			KeyMapping.resetMapping();
		});
		context.waitTicks(5);

		context.getInput().pressKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_L));
		context.waitFor(client -> !BaseTracerClient.CONFIG.enabled, TIMEOUT_TICKS);
		context.getInput().pressKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_L));
		context.waitFor(client -> BaseTracerClient.CONFIG.enabled, TIMEOUT_TICKS);
	}

	private static void testBlockHighlightFade() {
		// Gone right next to a block, and full strength a little way off.
		long pos = BlockPos.asLong(10, 100, 10);
		check(BaseTracerRenderer.blockDistance(pos, new Vec3(10.5, 100.5, 10.5)) == 0, "Inside a block should be no distance");
		check(BaseTracerRenderer.blockDistance(pos, new Vec3(12.5, 100.5, 10.5)) == 1.5, "Distance should be to the block's side");
		check(BaseTracerRenderer.nearStrength(BaseTracerRenderer.blockDistance(pos, new Vec3(12.5, 101.6, 10.5))) == 0F, "A block right next to the camera should be hidden");
		check(BaseTracerRenderer.nearStrength(3.5) == 0.5F, "Halfway should be half strength");
		check(BaseTracerRenderer.nearStrength(BaseTracerRenderer.blockDistance(pos, new Vec3(20.5, 100.5, 10.5))) == 1F, "A block further off should show fully");
	}

	private static void testModMenu(ClientGameTestContext context) {
		context.setScreen(() -> new ModsScreen(null));
		context.waitTicks(5);
		context.getInput().typeChars("Base Tracer");
		context.waitTicks(5);
		context.getInput().setCursorPos(60, 66);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(5);
		context.takeScreenshot("basetracer_modmenu");

		// Opened through the factory Mod Menu uses, so this also proves the entrypoint works.
		context.setScreen(() -> new BaseTracerModMenu().getModConfigScreenFactory().create(null));
		context.waitTicks(5);
		context.takeScreenshot("basetracer_settings");

		// clickScreenButton can't reach widgets nested in an options list, so click the Enabled button directly.
		context.getInput().setCursorPos(265, 93);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitFor(client -> !BaseTracerClient.CONFIG.enabled, TIMEOUT_TICKS);

		context.setScreen(() -> null);
		context.waitTicks(1);
		assertConfigSaved();

		// The rest of the screen, with an invalid color so the swatch's error state shows.
		String playerColor = BaseTracerClient.CONFIG.playerColor;
		BaseTracerClient.CONFIG.playerColor = "nope";
		context.setScreen(() -> new BaseTracerModMenu().getModConfigScreenFactory().create(null));
		context.waitTicks(5);
		BaseTracerClient.CONFIG.playerColor = playerColor;

		// The whole screen, a page at a time, to show its sections.
		for (int page = 1; page <= 4; page++) {
			context.getInput().setCursorPos(427, 240);
			context.getInput().scroll(-3.5);
			context.waitTicks(5);
			context.takeScreenshot("basetracer_settings_page_" + page);
		}

		context.getInput().scroll(-20);
		context.waitTicks(5);
		context.takeScreenshot("basetracer_settings_bottom");
		context.setScreen(() -> null);

		context.runOnClient(client -> {
			BaseTracerClient.CONFIG.enabled = true;
			BaseTracerClient.CONFIG.save();
		});
	}

	private static void assertConfigSaved() {
		try {
			String saved = Files.readString(FabricLoader.getInstance().getConfigDir().resolve("basetracer.properties"));

			if (!saved.contains("enabled=false")) {
				throw new AssertionError("Closing the settings screen didn't save the config: " + saved);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void testExcludedBlocksScreen(ClientGameTestContext context, TestServerContext server) {
		BlockPos pos = new BlockPos(24, 100, 8);
		server.runCommand("setblock 24 100 8 minecraft:soul_lantern");
		awaitSource(context, pos, true);

		context.setScreen(() -> new BaseTracerExcludedBlocksScreen(null));
		context.waitTicks(5);
		context.takeScreenshot("basetracer_blocks");

		// Only one row matches, and the search box starts focused. Rescanning happens when the screen closes.
		context.getInput().typeChars("Soul Lantern");
		context.waitTicks(5);
		context.takeScreenshot("basetracer_blocks_search");
		context.getInput().setCursorPos(427, 100);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(5);
		context.takeScreenshot("basetracer_blocks_excluded");
		context.setScreen(() -> null);
		awaitSource(context, pos, false);

		context.setScreen(() -> new BaseTracerExcludedBlocksScreen(null));
		context.waitTicks(5);
		context.clickScreenButton("options.basetracer.resetDefaults");
		context.setScreen(() -> null);
		awaitSource(context, pos, true);

		// One click on a merged row must cover the wall variant too.
		context.computeOnClient(client -> {
			BaseTracerExcludedBlocksScreen.lightEmittingBlocksByName().forEach((name, blocks) -> {
				if (blocks.size() > 1) {
					BaseTracerClient.LOGGER.info("Merged row '{}': {}", name, blocks);
				}
			});
			check(BaseTracerExcludedBlocksScreen.lightEmittingBlocksByName().get("Torch").contains(Blocks.WALL_TORCH), "Wall torch not merged into Torch");
			return null;
		});
		context.setScreen(() -> new BaseTracerExcludedBlocksScreen(null));
		context.waitTicks(5);
		context.getInput().typeChars("Soul Torch");
		context.waitTicks(5);
		context.takeScreenshot("basetracer_blocks_merged");
		context.getInput().setCursorPos(427, 100);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitFor(client -> BaseTracerClient.CONFIG.isExcluded(Blocks.SOUL_TORCH), TIMEOUT_TICKS);
		check(BaseTracerClient.CONFIG.isExcluded(Blocks.SOUL_WALL_TORCH), "Merged row didn't exclude the wall variant");
		context.clickScreenButton("options.basetracer.resetDefaults");
		context.setScreen(() -> null);
		context.waitTicks(1);

		server.runCommand("setblock 24 100 8 minecraft:air");
		awaitSource(context, pos, false);
	}

	private static void testConfigStorage() {
		try {
			Path dir = Files.createTempDirectory("basetracer-config");
			Path json = dir.resolve("lighttracer.json");
			Files.writeString(json, """
					{"enabled": false, "tracerPerSource": true, "chunkFillColor": "11223344", "lineWidth": 5.0, "excludedBlocks": ["minecraft:torch"]}
					""");

			// The old JSON file is imported and removed.
			BaseTracerConfig imported = BaseTracerConfig.load(dir);
			check(!imported.enabled && imported.tracerMode == TracerMode.EVERY_SOURCE, "JSON settings not imported");
			check(imported.lineWidth == 5.0F, "JSON line width not imported");
			check(imported.isExcluded(Blocks.TORCH) && !imported.isExcluded(Blocks.LAVA), "JSON excluded blocks not imported");
			check(!Files.exists(json), "JSON file not removed");
			check(Files.exists(dir.resolve("basetracer.properties")), "Properties file not written");

			// Changes survive a reload, including an empty block list.
			imported.setExcluded(Blocks.TORCH, false);
			imported.playerColor = "AABBCCDD";
			imported.tracerMode = TracerMode.NEAREST_CHUNK;
			imported.freeLookSpeed = 42F;
			imported.showTracers = false;
			imported.showChunkHighlights = false;
			imported.save();
			BaseTracerConfig reloaded = BaseTracerConfig.load(dir);
			check(!reloaded.enabled && reloaded.tracerMode == TracerMode.NEAREST_CHUNK && reloaded.lineWidth == 5.0F, "Settings lost on reload");
			check(!reloaded.showTracers && !reloaded.showChunkHighlights, "Visibility toggles lost on reload");
			check(reloaded.freeLookSpeed == 42F, "Free look speed lost on reload");
			check(reloaded.playerArgb() == 0xAABBCCDD, "Color lost on reload");
			check(!reloaded.isExcluded(Blocks.TORCH) && !reloaded.isExcluded(Blocks.LAVA), "Emptied block list lost on reload");

			// Rules survive a reload, including a deleted default staying deleted.
			check(reloaded.rules.size() == BaseRule.defaults().size(), "A config from before rules should get the defaults: " + reloaded.rules.size());
			reloaded.rules.removeIf(rule -> rule.name.equals("Redstone"));
			BaseRule custom = new BaseRule("Mine", List.of("minecraft:sponge", "othermod:removed_block"), 7, 2);
			custom.enabled = false;
			custom.showLabel = false;
			custom.setColor("11223344");
			custom.setColor("not a color");
			reloaded.showLabels = false;
			reloaded.showBlockHighlights = false;
			reloaded.fillOpacity = 40;
			reloaded.showHostileMobs = true;
			reloaded.hostileMobColor = "12345678";
			reloaded.mobOpacity = 35;
			reloaded.showPlayers = true;
			reloaded.showPlayerTracers = false;
			reloaded.showLabelDistance = false;
			reloaded.showLabelCoordinates = false;
			custom.showTracer = false;
			custom.priority = 3;
			reloaded.maxBases = 25;
			reloaded.smartTool = false;
			reloaded.smartToolDelay = 20;
			reloaded.autoEat = false;
			reloaded.autoEatHunger = 6;
			reloaded.autoEatHealth = 30;
			reloaded.quickUseAction = QuickUseAction.POTION;
			reloaded.takeAll = false;
			reloaded.takeAllDelay = 4;
			reloaded.putAllHotbar = true;
			reloaded.putAllFilter = PutAllFilter.EXCEPT_LISTED;
			reloaded.setPutAllItem(Items.COBBLESTONE, true);
			reloaded.showItems = true;
			reloaded.itemMode = ItemMode.MOB_DROPS;
			reloaded.showItemLabels = false;
			custom.setEntity("minecraft:armor_stand", true);
			custom.setEntity(BaseRule.TAMED_ANIMAL, true);
			reloaded.setMobHighlighted(EntityTypes.ZOMBIE, false);
			reloaded.setMobHighlighted(EntityTypes.COW, true);
			reloaded.setMobHighlighted(EntityTypes.PIG, true);
			reloaded.setMobHighlighted(EntityTypes.PIG, false);
			reloaded.rules.add(custom);
			reloaded.rules.getFirst().enabled = false;
			reloaded.save();
			BaseTracerConfig withRules = BaseTracerConfig.load(dir);
			check(withRules.rules.size() == BaseRule.defaults().size(), "Wrong rule count on reload: " + withRules.rules.size());
			check(withRules.rules.stream().noneMatch(rule -> rule.name.equals("Redstone")), "Deleted rule came back");
			check(withRules.rules.getFirst().lightSources && !withRules.rules.getFirst().enabled, "Light sources rule lost on reload");
			BaseRule restored = withRules.rules.getLast();
			check(restored.name.equals("Mine") && !restored.enabled && restored.minBlocks == 7 && restored.minTypes == 2, "Custom rule lost on reload");
			check(restored.blockIds().contains("othermod:removed_block") && restored.blocks().equals(Set.of(Blocks.SPONGE)), "Custom rule's blocks lost on reload");
			check(restored.colorArgb() == 0x11223344 && !restored.showLabel, "Custom rule's color or label setting lost on reload");
			check(!restored.showTracer && restored.entities().equals(Set.of(EntityTypes.ARMOR_STAND)) && restored.matchesTamedAnimals() && !restored.matchesNamedMobs(), "Custom rule's entities or tracer setting lost on reload");
			check(restored.priority == 3 && withRules.maxBases == 25, "Priority or base limit lost on reload");
			check(withRules.showItems && withRules.itemMode == ItemMode.MOB_DROPS && !withRules.showItemLabels, "Item settings lost on reload");
			check(!withRules.smartTool && withRules.smartToolDelay == 20, "Smart tool settings lost on reload");
			check(!withRules.autoEat && withRules.autoEatHunger == 6 && withRules.autoEatHealth == 30, "Auto eat settings lost on reload");
			check(withRules.quickUseAction == QuickUseAction.POTION, "Quick use action lost on reload");
			check(!withRules.takeAll && withRules.takeAllDelay == 4 && withRules.putAllHotbar
					&& withRules.putAllFilter == PutAllFilter.EXCEPT_LISTED && withRules.isPutAllListed(Items.COBBLESTONE)
					&& !withRules.isPutAllListed(Items.DIRT), "Take all settings lost on reload");
			check(withRules.showPlayers && !withRules.showPlayerTracers && !withRules.showLabelDistance && !withRules.showLabelCoordinates, "Player or label settings lost on reload");
			check(!withRules.showLabels && !withRules.showBlockHighlights && withRules.fillOpacity == 40, "Label, block highlight or opacity setting lost on reload");
			check(withRules.showHostileMobs && withRules.hostileMobArgb() == 0x12345678 && withRules.mobOpacity == 35, "Hostile mob settings lost on reload");
			check(!withRules.isMobHighlighted(EntityTypes.ZOMBIE) && withRules.isMobHighlighted(EntityTypes.COW), "Mob list changes lost on reload");
			check(withRules.isMobHighlighted(EntityTypes.SKELETON) && !withRules.isMobHighlighted(EntityTypes.PIG), "Mobs left alone should keep their default");

			// The Custom rule starts empty, reaches files saved before it existed, and stays gone once deleted.
			BaseRule defaultCustom = BaseRule.defaults().getLast();
			check(defaultCustom.name.equals("Custom") && defaultCustom.blockIds().isEmpty() && defaultCustom.enabled, "Custom rule should start empty and on");
			Path noCustomDir = Files.createTempDirectory("basetracer-config");
			Files.writeString(noCustomDir.resolve("basetracer.properties"), "ruleCount=1\nrule.0.name=Redstone\n");
			BaseTracerConfig gainedCustom = BaseTracerConfig.load(noCustomDir);
			check(gainedCustom.rules.stream().anyMatch(rule -> rule.name.equals("Custom")), "Older settings file didn't get the Custom rule");
			gainedCustom.rules.removeIf(rule -> rule.name.equals("Custom"));
			gainedCustom.save();
			check(BaseTracerConfig.load(noCustomDir).rules.stream().noneMatch(rule -> rule.name.equals("Custom")), "Deleted Custom rule came back");

			// Every default rule has a color of its own.
			check(BaseRule.defaults().stream().map(BaseRule::color).distinct().count() == BaseRule.defaults().size(), "Default rules share a color");

			// A rule saved before rules had colors gets its default one, and an unknown rule gets white.
			Path noColorDir = Files.createTempDirectory("basetracer-config");
			Files.writeString(noColorDir.resolve("basetracer.properties"), "ruleCount=2\nrule.0.name=Redstone\nrule.1.name=Mine\n");
			BaseTracerConfig noColors = BaseTracerConfig.load(noColorDir);
			check(noColors.rules.stream().filter(rule -> rule.name.equals("Redstone")).findFirst().orElseThrow().colorArgb() == 0xFFFF5555, "Old default rule lost its color");
			check(noColors.rules.stream().filter(rule -> rule.name.equals("Mine")).findFirst().orElseThrow().colorArgb() == 0xFFFFFFFF, "Old custom rule should be white");

			// The light sources rule can't go missing, whatever is in the file.
			Path noLightDir = Files.createTempDirectory("basetracer-config");
			Files.writeString(noLightDir.resolve("basetracer.properties"), "ruleCount=0\n");
			check(BaseTracerConfig.load(noLightDir).rules.getFirst().lightSources, "Light sources rule missing");

			// Every id in the default rules has to exist, since unknown ids are ignored quietly.
			for (BaseRule rule : BaseRule.defaults()) {
				check(rule.blocks().size() == rule.blockIds().size(), "Unknown block in default rule " + rule.name + ": " + rule.blockIds());
				check(rule.entityChoiceCount() == rule.entityIds().size(), "Unknown entity in default rule " + rule.name + ": " + rule.entityIds());
			}

			// Files from before entity rules get them, ahead of Custom, once.
			Path noEntityDir = Files.createTempDirectory("basetracer-config");
			Files.writeString(noEntityDir.resolve("basetracer.properties"), "ruleCount=2\nrule.0.name=Redstone\nrule.1.name=Custom\ncustomRuleOffered=true\n");
			BaseTracerConfig gainedEntities = BaseTracerConfig.load(noEntityDir);
			List<String> names = gainedEntities.rules.stream().map(rule -> rule.name).toList();
			check(names.equals(List.of("Light Sources", "Redstone", "Player Entities", "Pets & Named Mobs", "Custom")), "Entity rules not added in place: " + names);
			check(gainedEntities.rules.get(0).priority == 1 && gainedEntities.rules.get(1).priority == BaseRule.DEFAULT_PRIORITY, "Older rules should get their default priority");
			gainedEntities.rules.removeIf(rule -> rule.name.equals("Player Entities"));
			gainedEntities.save();
			check(BaseTracerConfig.load(noEntityDir).rules.stream().noneMatch(rule -> rule.name.equals("Player Entities")), "Deleted entity rule came back");

			// With no files at all, the defaults apply.
			Path emptyDir = Files.createTempDirectory("basetracer-config");
			BaseTracerConfig defaults = BaseTracerConfig.load(emptyDir);
			check(defaults.isExcluded(Blocks.LAVA), "Default exclusions missing");
			check(defaults.showTracers && defaults.showChunkHighlights && defaults.tracerMode == TracerMode.NEAREST_PER_CHUNK, "Wrong default tracer settings");

			// A properties file from before tracerMode keeps its old on/off choice.
			Path oldPropertiesDir = Files.createTempDirectory("basetracer-config");
			Files.writeString(oldPropertiesDir.resolve("basetracer.properties"), "tracerPerSource=true\n");
			check(BaseTracerConfig.load(oldPropertiesDir).tracerMode == TracerMode.EVERY_SOURCE, "Old tracerPerSource not migrated");

			// Settings from when the mod was called Light Tracer move to the new file, rules included.
			Path oldNameDir = Files.createTempDirectory("basetracer-config");
			Files.writeString(oldNameDir.resolve("lighttracer.properties"), "lineWidth=7.0\nruleCount=1\nrule.0.name=Mine\nrule.0.blocks=minecraft:sponge\n");
			BaseTracerConfig renamed = BaseTracerConfig.load(oldNameDir);
			check(renamed.lineWidth == 7.0F && renamed.rules.stream().anyMatch(rule -> rule.name.equals("Mine")), "Settings under the old name not migrated");
			check(!Files.exists(oldNameDir.resolve("lighttracer.properties")), "Old settings file not removed");
			check(BaseTracerConfig.load(oldNameDir).lineWidth == 7.0F, "Migrated settings lost on reload");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void testColorValidation() {
		assertTrue(BaseTracerConfig.isValidColor("FFFFD000"));
		assertTrue(!BaseTracerConfig.isValidColor("FFD000"));
		assertTrue(!BaseTracerConfig.isValidColor("GGGGGGGG"));
		assertTrue(!BaseTracerConfig.isValidColor(null));
	}

	private static void assertTrue(boolean condition) {
		if (!condition) {
			throw new AssertionError("Color validation failed");
		}
	}

	private static void awaitSource(ClientGameTestContext context, BlockPos pos, boolean expected) {
		try {
			context.waitFor(client -> BaseTracerClient.TRACKER.isSource(pos) == expected, TIMEOUT_TICKS);
		} catch (AssertionError e) {
			throw new AssertionError(context.computeOnClient(client -> "Expected isSource=%s at %s, client sees %s, hasChunk=%s, trackedChunks=%d, playerAt=%s".formatted(
					expected,
					pos,
					client.level.getBlockState(pos),
					client.level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4),
					BaseTracerClient.TRACKER.chunkCount(),
					client.player.blockPosition()
			)), e);
		}
	}

	private static void assertNotSource(ClientGameTestContext context, BlockPos pos) {
		if (context.computeOnClient(client -> BaseTracerClient.TRACKER.isSource(pos))) {
			throw new AssertionError("Should not be tracked: " + pos);
		}
	}
}
