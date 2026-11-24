package com.juiceclient.test;

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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import com.juiceclient.Trajectory;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import com.juiceclient.AutoDrop;
import com.juiceclient.AutoTotem;
import com.juiceclient.BaseRule;
import com.juiceclient.DroppedItems;
import com.juiceclient.AutoEat;
import com.juiceclient.FreeLook;
import com.juiceclient.HeldUse;
import com.juiceclient.PlayerAlerts;
import com.juiceclient.SectionToggle;
import com.juiceclient.PutAllFilter;
import com.juiceclient.PutAllItemsScreen;
import com.juiceclient.QuickUseAction;
import com.juiceclient.ItemGroup;
import com.juiceclient.SortGroupsScreen;
import com.juiceclient.SortMode;
import com.juiceclient.SortPriority;
import com.juiceclient.SortPriorityScreen;
import com.juiceclient.Sorter;
import com.juiceclient.TakeAll;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import com.juiceclient.SmartTool;
import com.juiceclient.ItemMode;
import com.juiceclient.MobWarning;
import com.juiceclient.ModuleSplash;
import com.juiceclient.JuiceClient;
import com.juiceclient.JuiceClientConfig;
import com.juiceclient.SettingsTransfer;
import com.juiceclient.JuiceClientConfigScreen;
import com.juiceclient.JuiceClientExcludedBlocksScreen;
import com.juiceclient.JuiceClientModMenu;
import com.juiceclient.JuiceClientMobsScreen;
import com.juiceclient.JuiceClientRenderer;
import com.juiceclient.JuiceClientRuleBlocksScreen;
import com.juiceclient.JuiceClientRuleEntitiesScreen;
import com.juiceclient.JuiceClientRuleScreen;
import com.juiceclient.JuiceClientRulesScreen;
import com.juiceclient.BaseTracker;
import com.juiceclient.TracerMode;
import com.juiceclient.XRay;
import com.juiceclient.JuiceClientXRayBlocksScreen;

public class JuiceClientGameTest implements FabricClientGameTest {
	private static final int TIMEOUT_TICKS = 200;
	private static final BlockPos ENCLOSED_TORCH = new BlockPos(56, 100, 8);
	private static final BlockPos FAR_TORCH = new BlockPos(72, 100, 24);
	private static final BlockPos BEHIND_TORCH = new BlockPos(-24, 100, -8);
	private static final BlockPos MARKER = new BlockPos(24, 100, 72);

	@Override
	public void runTest(ClientGameTestContext context) {
		logLightEmittingBlocks();
		testColorValidation();
		testPlayerAlerts();
		testSectionToggles(context);
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
			context.waitFor(client -> JuiceClient.SPLASH.isShowing(), TIMEOUT_TICKS);
			context.takeScreenshot("juiceclient_module_splash_join");

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
			testAutoTotem(context, server);
			testAutoArmor(context, server);
			testAutoDrop(context, server);
			testAntiDrop(context, server);
			testAutoWeapon(context, server);
			testSettingsTransfer(context);
			testFoodResupply(context, server);
			testNameTags(context, server);
			testTakeAll(context, server);
			testSort(context, server);
			testTrajectory(context, server);
			testBossBarStack(context, server);
			testArmorStatus(context, server);
			testCustomRule(context, server);
			testBasePriority(context, server);
			testRuleScreens(context);
			surveyStructures(context, server, connection);
			buildScene(context, server);
			testTracerModes(context, server);
			testItemLabelDistance();
			takeScreenshots(context, server, connection);
			takeBusynessScreenshot(context, server, connection);
			testToggleKey(context);
			testFreeLook(context, server, connection);
		}

		context.waitFor(client -> !FreeLook.isActive(), TIMEOUT_TICKS);

		context.waitFor(client -> JuiceClient.TRACKER.chunkCount() == 0, TIMEOUT_TICKS);
	}

	private static void logLightEmittingBlocks() {
		List<String> tracked = new ArrayList<>();
		List<String> excluded = new ArrayList<>();

		for (Block block : BuiltInRegistries.BLOCK) {
			boolean emits = false;
			boolean isTracked = false;

			for (BlockState state : block.getStateDefinition().getPossibleStates()) {
				emits |= state.getLightEmission() > 0;
				isTracked |= JuiceClient.CONFIG.isTracked(state);
			}

			if (emits) {
				(isTracked ? tracked : excluded).add(BuiltInRegistries.BLOCK.getKey(block).getPath());
			}
		}

		JuiceClient.LOGGER.info("Tracked light blocks ({}): {}", tracked.size(), tracked);
		JuiceClient.LOGGER.info("Excluded light blocks ({}): {}", excluded.size(), excluded);
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

		// Up close a chest in plain view fades out, but one behind a wall stays highlighted.
		Vec3 nearChest = new Vec3(34.5, 100.5, 30.5);
		check(context.computeOnClient(client -> JuiceClientRenderer.isInSight(client.level, chest1.asLong(), nearChest)), "Chest in the open should be in sight");
		server.runCommand("fill 32 99 32 40 102 32 minecraft:stone");
		sync(context, server, marker);
		check(!context.computeOnClient(client -> JuiceClientRenderer.isInSight(client.level, chest1.asLong(), nearChest)), "Chest behind a wall should be out of sight");
		server.runCommand("fill 32 99 32 40 102 32 minecraft:air");
		server.runCommand("fill 32 99 32 47 99 32 minecraft:stone");
		sync(context, server, marker);
		check(context.computeOnClient(client -> JuiceClientRenderer.isInSight(client.level, chest1.asLong(), nearChest)), "Chest should be in sight again");

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
		context.runOnClient(client -> JuiceClient.CONFIG.rules.add(stone));
		applyRules(context);
		server.runCommand("fill 80 100 80 95 104 95 minecraft:stone");
		context.waitFor(client -> highlightedBlocksNow(5, 5) == BaseTracker.MAX_FOUND_PER_CHUNK, TIMEOUT_TICKS);
		context.waitTicks(10);
		check(highlightedBlocks(context, 5, 5) == BaseTracker.MAX_FOUND_PER_CHUNK, "Block updates passed the cap");
		context.runOnClient(client -> JuiceClient.rescanLoadedChunks());
		check(highlightedBlocks(context, 5, 5) == BaseTracker.MAX_FOUND_PER_CHUNK, "A scan passed the cap");
		server.runCommand("fill 80 100 80 95 104 95 minecraft:air");
		server.runCommand("fill 32 99 32 47 99 47 minecraft:air");
		server.runCommand("setblock 24 100 72 minecraft:air");

		context.runOnClient(client -> {
			JuiceClient.CONFIG.resetRules();
			JuiceClient.applyRuleChanges();
		});
	}

	private static void testHostileMobs(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
		check(context.computeOnClient(client -> !JuiceClient.CONFIG.showHostileMobs), "Hostile mob highlights should start off");
		server.runCommand("difficulty normal");

		// Behind a wall, with no base anywhere near, to show mobs don't depend on a highlight being drawn.
		server.runCommand("fill 13 97 2 13 104 15 minecraft:stone");
		server.runCommand("summon minecraft:zombie 16.5 99 6.5 {NoAI:1b}");
		server.runCommand("summon minecraft:slime 16.5 99 11.5 {NoAI:1b,Size:1}");
		server.runCommand("summon minecraft:cow 16.5 99 3.5 {NoAI:1b}");
		server.runCommand("summon minecraft:wolf 16.5 99 14.5 {NoAI:1b}");
		connection.waitForChunksRender();

		Vec3 camera = new Vec3(8.5, 100, 8.5);
		context.waitFor(client -> JuiceClientRenderer.nearestMobs(client.level, JuiceClient.CONFIG, camera, 0F, 1000).size() == 2, TIMEOUT_TICKS);
		List<AABB> mobs = context.computeOnClient(client -> JuiceClientRenderer.nearestMobs(client.level, JuiceClient.CONFIG, camera, 1F, 1000));
		check(mobs.stream().anyMatch(mob -> mob.contains(16.5, 100, 6.5)), "The zombie's box isn't where the zombie is: " + mobs);
		check(mobs.stream().anyMatch(mob -> mob.contains(16.5, 99.2, 11.5)), "The slime's box isn't where the slime is: " + mobs);
		List<AABB> capped = context.computeOnClient(client -> JuiceClientRenderer.nearestMobs(client.level, JuiceClient.CONFIG, new Vec3(16.5, 100, 12), 1F, 1));
		check(capped.size() == 1 && capped.getFirst().contains(16.5, 99.2, 11.5), "The cap should keep the nearest mob: " + capped);

		// The default list has to be every hostile mob and nothing else, and the screen every mob and nothing else.
		context.runOnClient(client -> {
			List<String> wrongDefault = new ArrayList<>();
			List<String> wrongChoice = new ArrayList<>();
			List<EntityType<?>> choices = JuiceClientMobsScreen.mobTypes();

			for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
				Entity entity = type.create(client.level, EntitySpawnReason.LOAD);

				if (entity == null) {
					continue;
				}

				if (entity instanceof Enemy != JuiceClient.CONFIG.isMobHighlighted(type)) {
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
		context.setScreen(() -> new JuiceClientMobsScreen(null));
		context.waitTicks(5);
		context.takeScreenshot("juiceclient_mobs");
		context.getInput().typeChars("Zombie");
		context.waitTicks(5);
		context.takeScreenshot("juiceclient_mobs_search");
		context.setScreen(() -> null);
		context.setScreen(() -> new JuiceClientMobsScreen(null));
		context.waitTicks(5);
		context.getInput().typeChars("Slime");
		context.waitTicks(5);
		context.getInput().setCursorPos(427, 100);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitFor(client -> !JuiceClient.CONFIG.isMobHighlighted(EntityTypes.SLIME), TIMEOUT_TICKS);
		context.setScreen(() -> null);
		context.setScreen(() -> new JuiceClientMobsScreen(null));
		context.waitTicks(5);
		context.getInput().typeChars("Cow");
		context.waitTicks(5);
		context.takeScreenshot("juiceclient_mobs_cow");
		context.setScreen(() -> null);
		context.runOnClient(client -> JuiceClient.CONFIG.setMobHighlighted(EntityTypes.COW, true));
		List<AABB> edited = context.computeOnClient(client -> JuiceClientRenderer.nearestMobs(client.level, JuiceClient.CONFIG, camera, 1F, 1000));
		check(edited.size() == 2 && edited.stream().anyMatch(mob -> mob.contains(16.5, 99.5, 3.5)) && edited.stream().noneMatch(mob -> mob.contains(16.5, 99.2, 11.5)), "List changes didn't apply: " + edited);
		context.runOnClient(client -> JuiceClient.CONFIG.showHostileMobs = true);
		context.waitTicks(5);
		context.takeScreenshot("juiceclient_mobs_edited");
		context.runOnClient(client -> {
			JuiceClient.CONFIG.showHostileMobs = false;
			JuiceClient.CONFIG.resetMobs();
		});
		check(context.computeOnClient(client -> JuiceClient.CONFIG.isMobHighlighted(EntityTypes.SLIME) && !JuiceClient.CONFIG.isMobHighlighted(EntityTypes.COW)), "Reset didn't restore the default list");

		context.waitTicks(5);
		context.takeScreenshot("juiceclient_hostile_mobs_off");
		context.runOnClient(client -> JuiceClient.CONFIG.showHostileMobs = true);
		context.waitTicks(5);
		context.takeScreenshot("juiceclient_hostile_mobs");
		// Outlined as glowing, following the mob's shape: the zombie is, the cow isn't, and turning highlights off ends it.
		check(context.computeOnClient(client -> {
			Entity zombie = null;
			Entity cow = null;

			for (Entity entity : client.level.entitiesForRendering()) {
				if (entity.getType() == EntityTypes.ZOMBIE) {
					zombie = entity;
				} else if (entity.getType() == EntityTypes.COW) {
					cow = entity;
				}
			}

			return zombie != null && cow != null && client.shouldEntityAppearGlowing(zombie) && !client.shouldEntityAppearGlowing(cow);
		}), "The zombie should be outlined and the cow not");
		context.runOnClient(client -> JuiceClient.CONFIG.showHostileMobs = false);
		check(context.computeOnClient(client -> java.util.stream.StreamSupport.stream(client.level.entitiesForRendering().spliterator(), false)
				.noneMatch(entity -> entity.getType() == EntityTypes.ZOMBIE && client.shouldEntityAppearGlowing(entity))), "With mob highlights off, no outline");
		context.runOnClient(client -> JuiceClient.CONFIG.showHostileMobs = false);

		server.runCommand("kill @e[type=!player]");
		server.runCommand("fill 13 97 2 13 104 15 minecraft:air");
		context.waitFor(client -> JuiceClientRenderer.nearestMobs(client.level, JuiceClient.CONFIG, camera, 0F, 1000).isEmpty(), TIMEOUT_TICKS);
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
		String info = context.computeOnClient(client -> JuiceClientRenderer.labelInfo(
				JuiceClient.TRACKER.snapshot(camera, TracerMode.NEAREST_PER_CHUNK).stream().filter(highlight -> highlight.chunkX() == 1 && highlight.chunkZ() == 0).findFirst().orElseThrow(),
				true, true));
		check(info.equals("10m \u00B7 20, 100, 4"), "Wrong label info: " + info);

		// Other players are outlined, and the player looking never is.
		check(context.computeOnClient(client -> !JuiceClientRenderer.isOutlined(client.player)), "The player looking shouldn't be outlined");
		check(context.computeOnClient(client -> JuiceClientRenderer.otherPlayers(client.level, client.player, 0F)).isEmpty(), "The player looking shouldn't be highlighted");
		check(context.computeOnClient(client -> JuiceClientRenderer.otherPlayers(client.level, null, 0F)).size() == 1, "Players should be found");

		server.runCommand("tp @a 36 112 -14 0 35");
		connection.waitForChunksRender();
		context.waitTicks(20);
		context.takeScreenshot("juiceclient_entity_rules");
		// Entities matching a rule are outlined along their shape in the rule's color, rather than boxed as blocks.
		context.runOnClient(client -> {
			for (Entity entity : client.level.entitiesForRendering()) {
				if (entity.getType() == EntityTypes.ARMOR_STAND) {
					check(JuiceClientRenderer.outlineColor(entity) == 0xFFAAAA, "An armor stand should be outlined in its rule's color");
				} else if (entity.getType() == EntityTypes.HORSE && !((net.minecraft.world.entity.animal.equine.AbstractHorse) entity).isTamed()) {
					check(!JuiceClientRenderer.isOutlined(entity), "An untamed horse shouldn't be outlined");
				}
			}
		});

		// Choosing an entity through the screen. A plain horse then counts too, under its own name.
		BaseRule pets = context.computeOnClient(client -> JuiceClient.CONFIG.rules.stream().filter(rule -> rule.name.equals("Pets & Named Mobs")).findFirst().orElseThrow());
		context.setScreen(() -> {
			Screen settings = new JuiceClientConfigScreen(null);
			return new JuiceClientRuleEntitiesScreen(new JuiceClientRuleScreen(settings, pets), settings, pets);
		});
		context.waitTicks(5);
		context.takeScreenshot("juiceclient_rule_entities");
		context.getInput().typeChars("Horse");
		context.waitTicks(5);
		context.takeScreenshot("juiceclient_rule_entities_search");
		context.getInput().setCursorPos(427, 100);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitFor(client -> pets.entities().contains(EntityTypes.HORSE), TIMEOUT_TICKS);
		context.clickScreenButton("gui.done");
		context.waitFor(client -> client.gui.screen() instanceof JuiceClientRuleScreen, TIMEOUT_TICKS);
		context.waitTicks(5);
		context.takeScreenshot("juiceclient_rule_edit_entities");
		context.setScreen(() -> null);
		context.waitFor(client -> labelText(3, 0).equals(List.of("Horse x2")), TIMEOUT_TICKS);

		// Gone once the entities are.
		server.runCommand("kill @e[type=!player]");
		context.waitFor(client -> highlight(1, 0) == null && highlight(2, 0) == null && highlight(3, 0) == null, TIMEOUT_TICKS);
		server.runCommand("tp @a 8.5 100 8.5 -90 0");
		connection.waitForChunksDownload();
		context.runOnClient(client -> {
			JuiceClient.CONFIG.resetRules();
			JuiceClient.applyRuleChanges();
		});
	}

	/** Each section of the splash as "Title: entry, entry". */
	private static List<String> splashText(List<ModuleSplash.Group> groups) {
		return groups.stream().map(group -> group.title().getString() + ": "
				+ String.join(", ", group.entries().stream().map(Component::getString).toList())).toList();
	}

	private static void testModuleSplash(ClientGameTestContext context) {
		// What's listed follows the settings, with empty rules left out.
		List<String> lines = context.computeOnClient(client -> splashText(ModuleSplash.groups(JuiceClient.CONFIG, false)));
		check(lines.getFirst().startsWith("Block and Entity ESP: Chunk Highlights, Block Highlights") && lines.get(1).startsWith("Finding: ") && lines.get(1).contains("Player Storage"), "Splash is missing modules or rules: " + lines);
		check(lines.stream().noneMatch(line -> line.contains("Mob Highlights") || line.contains("Custom") || line.contains("Free Look")), "Splash lists something that's off: " + lines);
		check(lines.stream().anyMatch(line -> line.startsWith("Tools: ") && line.contains("Auto Armor")), "Splash should group the tools: " + lines);
		JuiceClientConfig allOff = new JuiceClientConfig();
		allOff.showChunkHighlights = false;
		allOff.showBlockHighlights = false;
		allOff.showTracers = false;
		allOff.showLabels = false;
		allOff.mobWarning = false;
		allOff.playerAlerts = false;
		allOff.smartTool = false;
		allOff.autoEat = false;
		allOff.autoTotem = false;
		allOff.autoArmor = false;
		allOff.autoDrop = false;
		allOff.antiDrop = false;
		allOff.sortButtons = false;
		allOff.trajectories = false;
		allOff.stackBossBars = false;
		allOff.armorStatus = false;
		allOff.autoWeapon = false;
		List<String> none = context.computeOnClient(client -> splashText(ModuleSplash.groups(allOff, false)));
		check(none.isEmpty(), "Splash should have no sections with everything off: " + none);

		// Turning the mod on shows it. Turning it off doesn't, and nor does changing a module or Free Look.
		context.waitFor(client -> !JuiceClient.SPLASH.isShowing(), TIMEOUT_TICKS);
		context.runOnClient(client -> JuiceClient.CONFIG.showHostileMobs = true);
		context.waitTicks(5);
		check(!context.computeOnClient(client -> JuiceClient.SPLASH.isShowing()), "Splash shouldn't show for changing a module");
		context.runOnClient(FreeLook::toggle);
		context.waitTicks(5);
		check(context.computeOnClient(client -> FreeLook.isActive()), "Free Look didn't turn on");
		check(!context.computeOnClient(client -> JuiceClient.SPLASH.isShowing()), "Splash shouldn't show for turning Free Look on");
		context.runOnClient(FreeLook::toggle);
		context.waitTicks(5);
		check(!context.computeOnClient(client -> JuiceClient.SPLASH.isShowing()), "Splash shouldn't show for turning Free Look off");
		context.runOnClient(client -> JuiceClient.CONFIG.enabled = false);
		context.waitTicks(5);
		check(!context.computeOnClient(client -> JuiceClient.SPLASH.isShowing()), "Splash shouldn't show for turning the mod off");
		context.runOnClient(client -> {
			JuiceClient.CONFIG.showPlayers = true;
			JuiceClient.CONFIG.enabled = true;
		});
		context.waitFor(client -> JuiceClient.SPLASH.isShowing(), TIMEOUT_TICKS);
		context.waitTicks(5);
		context.runOnClient(client -> client.player.sendOverlayMessage(Component.translatable("message.juiceclient.enabled")));
		context.takeScreenshot("juiceclient_module_splash");
		context.waitFor(client -> !JuiceClient.SPLASH.isShowing(), TIMEOUT_TICKS);
		context.waitTicks(5);
		context.takeScreenshot("juiceclient_module_splash_gone");
		context.runOnClient(client -> {
			JuiceClient.CONFIG.showHostileMobs = false;
			JuiceClient.CONFIG.showPlayers = false;
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
		List<JuiceClientRenderer.BlockMark> marks = context.computeOnClient(client -> JuiceClientRenderer.nearestBlocks(
				JuiceClient.TRACKER.snapshot(besideChest, TracerMode.NEAREST_PER_CHUNK), besideChest, 1000));
		check(marks.getFirst().pos() == nearChest.asLong() && marks.getFirst().argb() == 0xFF55FF55, "Nearest block should be the chest, in the first rule's color");
		check(marks.stream().filter(mark -> mark.pos() == nearChest.asLong()).count() == 1, "A block in two rules should only be marked once");
		// 3 + 4 chests, furnace and table + 3 lights + sponge + 8 of the long list.
		check(marks.size() == 3 + 4 + 3 + 1 + 8, "Wrong number of marks: " + marks.size());
		check(marks.stream().anyMatch(mark -> mark.pos() == new BlockPos(40, 100, 18).asLong() && mark.argb() == 0xFFFF8800), "Furnace should use its rule's color");
		List<JuiceClientRenderer.BlockMark> capped = context.computeOnClient(client -> JuiceClientRenderer.nearestBlocks(
				JuiceClient.TRACKER.snapshot(besideChest, TracerMode.NEAREST_PER_CHUNK), besideChest, 3));
		check(capped.size() == 3 && capped.equals(marks.subList(0, 3)), "The cap should keep the nearest blocks");

		// Hidden under stone, the blocks should still show.
		server.runCommand("fill 16 101 16 63 101 31 minecraft:stone");
		server.runCommand("tp @a 40 118 -8 0 50");
		connection.waitForChunksRender();
		context.waitTicks(20);
		context.takeScreenshot("juiceclient_rule_labels");
		context.runOnClient(client -> JuiceClient.CONFIG.showBlockHighlights = false);
		context.waitTicks(5);
		context.takeScreenshot("juiceclient_block_highlights_off");
		context.runOnClient(client -> JuiceClient.CONFIG.showBlockHighlights = true);

		context.runOnClient(client -> JuiceClient.CONFIG.showLabels = false);
		context.waitTicks(5);
		context.takeScreenshot("juiceclient_rule_labels_off");
		context.runOnClient(client -> JuiceClient.CONFIG.showLabels = true);

		server.runCommand("fill 16 99 16 63 101 40 minecraft:air");
		server.runCommand("tp @a 8.5 100 8.5 -90 0");
		connection.waitForChunksDownload();
		context.waitFor(client -> highlight(1, 1) == null, TIMEOUT_TICKS);
		context.runOnClient(client -> {
			JuiceClient.CONFIG.resetRules();
			JuiceClient.applyRuleChanges();
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
		List<String> kept = context.computeOnClient(client -> JuiceClient.TRACKER.snapshot(camera, TracerMode.NEAREST_PER_CHUNK, 2).stream()
				.map(highlight -> highlight.chunkX() + "," + highlight.chunkZ()).toList());
		check(kept.equals(List.of("6,2", "1,2")), "The high priority base, then the nearest, should be kept: " + kept);

		// Equal priorities: the nearest win.
		context.runOnClient(client -> high.priority = 1);
		List<String> nearest = context.computeOnClient(client -> JuiceClient.TRACKER.snapshot(camera, TracerMode.NEAREST_PER_CHUNK, 2).stream()
				.map(highlight -> highlight.chunkX() + "," + highlight.chunkZ()).toList());
		check(nearest.equals(List.of("1,2", "2,2")), "With equal priorities the nearest should be kept: " + nearest);
		context.runOnClient(client -> high.priority = 9);

		// The one chunk traced comes from the kept ones.
		long[] traced = context.computeOnClient(client -> JuiceClient.TRACKER.snapshot(camera, TracerMode.MOST_SOURCES, 1).stream()
				.flatMapToLong(highlight -> java.util.Arrays.stream(highlight.targets())).toArray());
		check(traced.length == 1 && traced[0] == BlockPos.asLong(100, 100, 40), "The traced chunk should be one of those kept");

		// Highest Priority traces the one high priority find past the nearer low priority ones.
		List<Long> highest = targets(context, camera, TracerMode.HIGHEST_PRIORITY);
		check(highest.equals(List.of(BlockPos.asLong(100, 100, 40))), "Highest Priority should trace the high priority find: " + highest);

		// In a chunk with both, it's the high priority block, even though the low one is nearer.
		server.runCommand("setblock 30 100 40 minecraft:melon");
		context.waitFor(client -> targets(JuiceClient.TRACKER.snapshot(camera, TracerMode.HIGHEST_PRIORITY)).equals(List.of(BlockPos.asLong(30, 100, 40))), TIMEOUT_TICKS);
		server.runCommand("setblock 30 100 40 minecraft:air");
		context.waitFor(client -> targets(JuiceClient.TRACKER.snapshot(camera, TracerMode.HIGHEST_PRIORITY)).equals(List.of(BlockPos.asLong(100, 100, 40))), TIMEOUT_TICKS);

		// Equal priorities: the nearest.
		context.runOnClient(client -> high.priority = 1);
		List<Long> tiedHighest = targets(context, camera, TracerMode.HIGHEST_PRIORITY);
		check(tiedHighest.equals(List.of(BlockPos.asLong(20, 100, 40))), "Highest Priority should break ties by distance: " + tiedHighest);

		// A rule with tracers off is passed over.
		context.runOnClient(client -> {
			high.priority = 9;
			high.showTracer = false;
		});
		List<Long> noTracer = targets(context, camera, TracerMode.HIGHEST_PRIORITY);
		check(noTracer.equals(List.of(BlockPos.asLong(20, 100, 40))), "Highest Priority should skip rules with tracers off: " + noTracer);
		context.runOnClient(client -> high.showTracer = true);

		// The renderer follows the setting, and All shows every one.
		context.runOnClient(client -> JuiceClient.CONFIG.maxBases = 2);
		context.waitFor(client -> JuiceClient.RENDERER.highlightCount() == 2, TIMEOUT_TICKS);
		context.runOnClient(client -> JuiceClient.CONFIG.maxBases = JuiceClientConfig.MAX_BASES_LIMIT + 1);
		context.waitFor(client -> JuiceClient.RENDERER.highlightCount() == 4, TIMEOUT_TICKS);
		context.runOnClient(client -> JuiceClient.CONFIG.maxBases = 10);

		server.runCommand("setblock 20 100 40 minecraft:air");
		server.runCommand("setblock 36 100 40 minecraft:air");
		server.runCommand("setblock 52 100 40 minecraft:air");
		server.runCommand("setblock 100 100 40 minecraft:air");
		context.waitFor(client -> highlight(1, 2) == null && highlight(6, 2) == null, TIMEOUT_TICKS);
		context.runOnClient(client -> {
			JuiceClient.CONFIG.resetRules();
			JuiceClient.applyRuleChanges();
		});
	}

	private static void testTrajectory(ClientGameTestContext context, TestServerContext server) {
		check(context.computeOnClient(client -> JuiceClient.CONFIG.trajectories), "Trajectories should start on");
		server.runCommand("gamemode spectator @a");
		server.runCommand("clear @a");
		server.runCommand("tp @a 0.5 100 200.5 0 -20");
		context.waitTicks(20);
		server.runCommand("fill -8 99 196 8 99 260 minecraft:stone");
		server.runCommand("fill -8 100 196 8 110 260 minecraft:air");
		server.runCommand("gamemode survival @a");
		server.runCommand("tp @a 0.5 100 200.5 0 -20");
		context.waitFor(client -> client.player.onGround(), TIMEOUT_TICKS);
		context.waitTicks(10);
		check(context.computeOnClient(client -> Trajectory.predict(client.player, 1F) == null), "Nothing in hand should have no path");

		// An ender pearl: the player lands where the path said.
		server.runCommand("item replace entity @a weapon.mainhand with minecraft:ender_pearl");
		context.waitTicks(5);
		Trajectory.Path pearlPath = context.computeOnClient(client -> Trajectory.predict(client.player, 1F));
		check(pearlPath != null && pearlPath.hit() != null && pearlPath.face() == net.minecraft.core.Direction.UP && Math.abs(pearlPath.hit().y - 100) < 0.01,
				"A pearl thrown upwards should be shown landing on the floor: " + (pearlPath == null ? null : pearlPath.hit()));
		check(pearlPath.hit().z > 215, "The pearl should be shown landing well ahead: " + pearlPath.hit());
		context.takeScreenshot("juiceclient_trajectory_pearl");
		context.runOnClient(client -> client.gameMode.useItem(client.player, net.minecraft.world.InteractionHand.MAIN_HAND));
		context.waitFor(client -> client.player.position().z > 210, TIMEOUT_TICKS);
		context.waitTicks(10);
		Vec3 landed = context.computeOnClient(client -> client.player.position());
		// Pearls are thrown with a little random spread, which adds up over 40 blocks.
		check(Math.abs(landed.x - pearlPath.hit().x) < 1.0 && Math.abs(landed.z - pearlPath.hit().z) < 2.5,
				"The pearl landed at " + landed + ", but the path said " + pearlPath.hit());

		// A loaded crossbow: the arrow sticks where the path ends.
		server.runCommand("effect give @a minecraft:instant_health 1 5");
		// Aimed down, as the shot's random spread moves a flat shot's landing a long way.
		server.runCommand("tp @a 0.5 100 200.5 0 10");
		server.runCommand("item replace entity @a weapon.mainhand with minecraft:crossbow[charged_projectiles=[{id:\"minecraft:arrow\",count:1}]]");
		context.waitTicks(20);
		Trajectory.Path arrowPath = context.computeOnClient(client -> Trajectory.predict(client.player, 1F));
		check(arrowPath != null && arrowPath.hit() != null && arrowPath.face() == net.minecraft.core.Direction.UP, "A loaded crossbow should show where the arrow lands");
		context.takeScreenshot("juiceclient_trajectory_crossbow");
		context.runOnClient(client -> client.gameMode.useItem(client.player, net.minecraft.world.InteractionHand.MAIN_HAND));
		context.waitTicks(80);
		Vec3 stuck = server.computeOnServer(minecraftServer -> minecraftServer.overworld()
				.getEntities(net.minecraft.world.entity.EntityTypes.ARROW, new net.minecraft.world.phys.AABB(-8, 90, 196, 8, 120, 400), arrow -> true)
				.stream().findFirst().map(net.minecraft.world.entity.Entity::position).orElse(null));
		check(stuck != null && Math.abs(stuck.x - arrowPath.hit().x) < 1.0 && Math.abs(stuck.z - arrowPath.hit().z) < 1.0,
				"The arrow stuck at " + stuck + ", but the path said " + arrowPath.hit());

		// A bow only shows a path while it's drawn.
		server.runCommand("kill @e[type=minecraft:arrow]");
		server.runCommand("item replace entity @a weapon.mainhand with minecraft:bow");
		server.runCommand("item replace entity @a inventory.0 with minecraft:arrow");
		context.waitTicks(5);
		check(context.computeOnClient(client -> Trajectory.predict(client.player, 1F) == null), "A bow not being drawn should have no path");

		server.runCommand("clear @a");
		server.runCommand("fill -8 99 196 8 99 260 minecraft:air");
		server.runCommand("gamemode spectator @a");
		server.runCommand("tp @a 8.5 100 8.5 -90 0");
		context.waitTicks(20);
	}

	private static void testArmorStatus(ClientGameTestContext context, TestServerContext server) {
		check(context.computeOnClient(client -> JuiceClient.CONFIG.armorStatus && com.juiceclient.ArmorStatus.replacesArmorBar()), "Armor status should start on");
		check(com.juiceclient.ArmorStatus.percentLeft(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK)) == -1, "A stick has no durability");
		server.runCommand("gamemode survival @a");
		server.runCommand("item replace entity @a armor.head with minecraft:diamond_helmet");
		server.runCommand("item replace entity @a armor.chest with minecraft:iron_chestplate[damage=120]");
		server.runCommand("item replace entity @a armor.feet with minecraft:leather_boots[damage=64]");
		server.runCommand("item replace entity @a weapon.offhand with minecraft:shield");
		context.waitFor(client -> !client.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).isEmpty(), TIMEOUT_TICKS);
		List<Integer> percents = context.computeOnClient(client -> java.util.Arrays.stream(com.juiceclient.ArmorStatus.SLOTS)
				.map(slot -> com.juiceclient.ArmorStatus.percentLeft(client.player.getItemBySlot(slot))).toList());
		// Iron chestplate 240, leather boots 65.
		check(percents.equals(List.of(100, 50, -1, 2)), "Armor durability should read 100, 50, none, 2: " + percents);
		context.waitTicks(2);
		context.takeScreenshot("juiceclient_armor_status");
		server.runCommand("clear @a");
		server.runCommand("gamemode spectator @a");
		context.waitTicks(5);
	}

	private static void testBossBarStack(ClientGameTestContext context, TestServerContext server) {
		check(context.computeOnClient(client -> JuiceClient.CONFIG.stackBossBars), "Boss bars should start stacked");

		// Three bars named Wither and one named Raid show as two.
		for (String id : List.of("a", "b", "c")) {
			server.runCommand("bossbar add test:" + id + " \"Wither\"");
			server.runCommand("bossbar set test:" + id + " players @a");
		}

		server.runCommand("bossbar add test:raid \"Raid\"");
		server.runCommand("bossbar set test:raid players @a");
		context.waitFor(client -> bossBars(client).size() == 4, TIMEOUT_TICKS);
		List<String> stacked = context.computeOnClient(client -> com.juiceclient.BossBarStack.shownNames(bossBars(client)));
		check(stacked.equals(List.of("Wither x3", "Raid")), "Boss bars should stack by name: " + stacked);
		context.takeScreenshot("juiceclient_boss_bars_stacked");

		// Off, each shows on its own.
		context.runOnClient(client -> JuiceClient.CONFIG.stackBossBars = false);
		List<String> separate = context.computeOnClient(client -> com.juiceclient.BossBarStack.shownNames(bossBars(client)));
		check(separate.equals(List.of("Wither", "Wither", "Wither", "Raid")), "Unstacked boss bars should each show: " + separate);
		context.waitTicks(2);
		context.takeScreenshot("juiceclient_boss_bars_separate");
		context.runOnClient(client -> JuiceClient.CONFIG.stackBossBars = true);

		for (String id : List.of("a", "b", "c", "raid")) {
			server.runCommand("bossbar remove test:" + id);
		}

		context.waitFor(client -> bossBars(client).isEmpty(), TIMEOUT_TICKS);
	}

	private static java.util.Collection<net.minecraft.client.gui.components.LerpingBossEvent> bossBars(net.minecraft.client.Minecraft client) {
		return ((com.juiceclient.mixin.BossHealthOverlayAccessor) client.gui.hud.getBossOverlay()).juiceclient$events().values();
	}

	private static void testCustomRule(ClientGameTestContext context, TestServerContext server) {
		BaseRule custom = context.computeOnClient(client -> JuiceClient.CONFIG.rules.stream().filter(rule -> rule.name.equals("Custom")).findFirst().orElseThrow());

		// Empty, it finds nothing: TNT placed now isn't highlighted.
		server.runCommand("setblock 18 100 18 minecraft:tnt");
		syncWithTorch(context, server);
		check(highlightedBlocks(context, 1, 1) == 0, "Empty Custom rule matched something");

		// Picking TNT by searching for it, as a player would, with a number of 2.
		context.setScreen(() -> {
			Screen settings = new JuiceClientModMenu().getModConfigScreenFactory().create(null);
			return new JuiceClientRuleBlocksScreen(new JuiceClientRuleScreen(settings, custom), settings, custom);
		});
		context.waitTicks(5);
		context.getInput().typeChars("TNT");
		context.waitTicks(5);
		context.takeScreenshot("juiceclient_custom_blocks");
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
			JuiceClient.CONFIG.resetRules();
			JuiceClient.applyRuleChanges();
		});
	}

	private static void syncWithTorch(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("setblock 24 100 72 minecraft:torch");
		awaitSource(context, MARKER, true);
		server.runCommand("setblock 24 100 72 minecraft:air");
		awaitSource(context, MARKER, false);
	}

	private static BaseTracker.ChunkHighlight highlight(int chunkX, int chunkZ) {
		for (BaseTracker.ChunkHighlight highlight : JuiceClient.TRACKER.snapshot(Vec3.ZERO, TracerMode.NEAREST_PER_CHUNK)) {
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
			JuiceClient.CONFIG.rules.clear();
			JuiceClient.CONFIG.rules.addAll(List.of(rules));
		});
		applyRules(context);
	}

	private static void applyRules(ClientGameTestContext context) {
		context.runOnClient(client -> {
			JuiceClient.CONFIG.markRulesChanged();
			JuiceClient.applyRuleChanges();
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
		for (BaseTracker.ChunkHighlight highlight : JuiceClient.TRACKER.snapshot(Vec3.ZERO, TracerMode.NEAREST_PER_CHUNK)) {
			if (highlight.chunkX() == chunkX && highlight.chunkZ() == chunkZ) {
				return highlight.sourceCount();
			}
		}

		return 0;
	}

	private static void testRuleScreens(ClientGameTestContext context) {
		// Real navigation from the settings screen, with screenshots at each stop.
		context.setScreen(() -> new JuiceClientModMenu().getModConfigScreenFactory().create(null));
		context.waitTicks(5);
		context.setScreen(() -> new JuiceClientRulesScreen(new JuiceClientModMenu().getModConfigScreenFactory().create(null)));
		context.waitTicks(5);
		context.takeScreenshot("juiceclient_rules");
		check(context.computeOnClient(client -> {
			List<BaseRule> sorted = JuiceClientRulesScreen.byPriority(JuiceClient.CONFIG.rules);

			for (int i = 1; i < sorted.size(); i++) {
				if (sorted.get(i - 1).priority < sorted.get(i).priority) {
					return false;
				}
			}

			return sorted.size() == JuiceClient.CONFIG.rules.size() && sorted.getLast().lightSources;
		}), "The rules should be listed highest priority first, with Light Sources last");

		int rulesBefore = context.computeOnClient(client -> JuiceClient.CONFIG.rules.size());
		context.clickScreenButton("options.juiceclient.rules.add");
		context.waitFor(client -> client.gui.screen() instanceof JuiceClientRuleScreen, TIMEOUT_TICKS);
		context.waitTicks(5);
		context.takeScreenshot("juiceclient_rule_edit");
		check(context.computeOnClient(client -> JuiceClient.CONFIG.rules.size()) == rulesBefore + 1, "Add Rule didn't add a rule");
		BaseRule added = context.computeOnClient(client -> JuiceClient.CONFIG.rules.getLast());

		// Picking a block through the picker, then back out level by level.
		context.setScreen(() -> {
			Screen settings = new JuiceClientModMenu().getModConfigScreenFactory().create(null);
			return new JuiceClientRuleBlocksScreen(new JuiceClientRuleScreen(settings, added), settings, added);
		});
		context.waitTicks(5);
		context.getInput().typeChars("Sponge");
		context.waitTicks(5);
		context.takeScreenshot("juiceclient_rule_blocks");
		context.getInput().setCursorPos(427, 100);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitFor(client -> added.blocks().contains(Blocks.SPONGE), TIMEOUT_TICKS);
		context.clickScreenButton("gui.done");
		context.waitFor(client -> client.gui.screen() instanceof JuiceClientRuleScreen, TIMEOUT_TICKS);
		context.waitTicks(5);
		context.takeScreenshot("juiceclient_rule_edit_chosen");

		// Deleting asks first, and No keeps the rule.
		context.clickScreenButton("options.juiceclient.rule.delete");
		context.waitFor(client -> client.gui.screen() instanceof ConfirmScreen, TIMEOUT_TICKS);
		context.clickScreenButton("gui.no");
		context.waitFor(client -> client.gui.screen() instanceof JuiceClientRuleScreen, TIMEOUT_TICKS);
		check(context.computeOnClient(client -> JuiceClient.CONFIG.rules.contains(added)), "Answering No deleted the rule");
		context.clickScreenButton("options.juiceclient.rule.delete");
		context.waitFor(client -> client.gui.screen() instanceof ConfirmScreen, TIMEOUT_TICKS);
		context.clickScreenButton("gui.yes");
		context.waitFor(client -> client.gui.screen() instanceof JuiceClientRulesScreen, TIMEOUT_TICKS);
		check(context.computeOnClient(client -> !JuiceClient.CONFIG.rules.contains(added)), "Answering Yes kept the rule");
		context.waitTicks(5);

		// Done goes back to the settings screen, which must draw properly on being returned to.
		context.clickScreenButton("gui.done");
		context.waitFor(client -> client.gui.screen() instanceof JuiceClientConfigScreen, TIMEOUT_TICKS);
		context.waitTicks(5);
		context.takeScreenshot("juiceclient_settings_returned");
		context.setScreen(() -> null);

		// The light sources rule leads to the excluded blocks, and can't be deleted.
		BaseRule light = context.computeOnClient(client -> JuiceClient.CONFIG.rules.getFirst());
		check(light.lightSources, "Light sources should be the first default rule");
		context.setScreen(() -> new JuiceClientRuleScreen(null, light));
		context.waitTicks(5);
		context.takeScreenshot("juiceclient_rule_light");
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
			Map<String, Integer> matches = context.computeOnClient(client -> JuiceClient.TRACKER.matchesPerRule());
			JuiceClient.LOGGER.info("Structure survey: {} ({}) -> {}", structure, placed, matches);
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
		JuiceClient.LOGGER.info("Effective render distance: {}", renderDistance);
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

		int[] colors = context.computeOnClient(client -> JuiceClient.TRACKER.snapshot(camera, TracerMode.EVERY_SOURCE).stream()
				.flatMapToInt(highlight -> java.util.Arrays.stream(highlight.targetColors())).distinct().toArray());
		check(colors.length == 1 && colors[0] == (BaseRule.lightSources().colorArgb() | 0xFF000000), "Tracers should be in the rule's color: " + java.util.Arrays.toString(colors));
		context.runOnClient(client -> JuiceClient.CONFIG.rules.getFirst().showTracer = false);
		check(targets(context, camera, TracerMode.EVERY_SOURCE).isEmpty(), "A rule with tracers off still has tracers");
		check(targets(context, camera, TracerMode.NEAREST_CHUNK).isEmpty(), "A rule with tracers off can still be the nearest chunk traced");
		context.runOnClient(client -> JuiceClient.CONFIG.rules.getFirst().showTracer = true);

		// Highlights stay on every chunk, whatever the tracer target.
		int chunks = context.computeOnClient(client -> JuiceClient.TRACKER.snapshot(camera, TracerMode.NEAREST_CHUNK).size());
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
		JuiceClient.LOGGER.info("Simulated mouse control: body yaw {} -> {}, windowActive={}", bodyYaw, controlYaw, windowActive);
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
		context.takeScreenshot("juiceclient_freelook");

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
		check(JuiceClientRenderer.busyness(1) == 0F, "A single source should have no extra brightness");
		check(JuiceClientRenderer.brighten(base, 0F) == base, "A single source should keep the configured color");
		check(JuiceClientRenderer.busyness(32) == 1F && JuiceClientRenderer.busyness(5000) == 1F, "Brightness should cap at 32 sources");

		int previousAlpha = -1;
		int previousRed = -1;

		for (int count : new int[] {1, 2, 4, 8, 16, 32}) {
			int color = JuiceClientRenderer.brighten(base, JuiceClientRenderer.busyness(count));
			check(color >>> 24 > previousAlpha && (color >> 16 & 0xFF) > previousRed, "Should get brighter at " + count + " sources: " + Integer.toHexString(color));
			previousAlpha = color >>> 24;
			previousRed = color >> 16 & 0xFF;
		}

		// A color set fully transparent stays that way, and opaque white can't overflow.
		check(JuiceClientRenderer.brighten(0x00FF0000, 1F) >>> 24 == 0, "Transparent colors should stay transparent");
		check(JuiceClientRenderer.brighten(0xFFFFFFFF, 1F) == 0xFFFFFFFF, "White should stay white");

		check(JuiceClientRenderer.isCameraInChunk(new Vec3(16.0, 300.0, 31.9), 1, 1), "Camera should count as in its own chunk, at any height");
		check(!JuiceClientRenderer.isCameraInChunk(new Vec3(15.9, 100.0, 16.0), 1, 1), "Camera in the next chunk over shouldn't count");
		check(JuiceClientRenderer.isCameraInChunk(new Vec3(-0.5, 100.0, -16.5), -1, -2), "Negative coordinates should round down");
	}

	private static void takeBusynessScreenshot(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
		// Side by side: chunks with 1, 8 and 64 sources.
		server.runCommand("fill 16 99 48 63 99 63 minecraft:stone");
		server.runCommand("setblock 24 100 56 minecraft:torch");
		server.runCommand("fill 32 100 48 35 100 49 minecraft:torch");
		server.runCommand("fill 48 100 48 55 100 55 minecraft:torch");
		awaitSource(context, new BlockPos(55, 100, 55), true);

		context.runOnClient(client -> JuiceClient.CONFIG.showTracers = false);
		server.runCommand("tp @a 40 118 34 0 45");
		connection.waitForChunksRender();
		context.waitTicks(20);
		context.takeScreenshot("juiceclient_busyness");
		context.runOnClient(client -> JuiceClient.CONFIG.showTracers = true);
	}

	private static List<Long> targets(ClientGameTestContext context, Vec3 camera, TracerMode mode) {
		return context.computeOnClient(client -> targets(JuiceClient.TRACKER.snapshot(camera, mode)));
	}

	private static void testItemLabelDistance() {
		String label = com.juiceclient.JuiceClientRenderer.itemLabel("Diamond x3", 12.4);
		check(label.equals("Diamond x3 · 12m"), "Item labels should end with the distance: " + label);
	}

	private static List<Long> targets(List<BaseTracker.ChunkHighlight> highlights) {
		List<Long> targets = new ArrayList<>();

		for (BaseTracker.ChunkHighlight highlight : highlights) {
			for (long target : highlight.targets()) {
				targets.add(target);
			}
		}

		return targets;
	}

	private static void takeScreenshots(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
		connection.waitForChunksRender();
		context.waitTicks(20);
		context.takeScreenshot("juiceclient_on");

		context.runOnClient(client -> JuiceClient.CONFIG.showChunkHighlights = false);
		context.waitTicks(5);
		context.takeScreenshot("juiceclient_tracers_only");

		context.runOnClient(client -> {
			JuiceClient.CONFIG.showChunkHighlights = true;
			JuiceClient.CONFIG.showTracers = false;
		});
		context.waitTicks(5);
		context.takeScreenshot("juiceclient_highlights_only");

		context.runOnClient(client -> {
			JuiceClient.CONFIG.showTracers = true;
			JuiceClient.CONFIG.tracerMode = TracerMode.NEAREST_CHUNK;
		});
		context.waitTicks(5);
		context.takeScreenshot("juiceclient_nearest_chunk");

		context.runOnClient(client -> JuiceClient.CONFIG.tracerMode = TracerMode.MOST_SOURCES);
		context.setScreen(() -> new JuiceClientModMenu().getModConfigScreenFactory().create(null));
		context.waitTicks(5);
		context.takeScreenshot("juiceclient_settings_most_sources");
		context.setScreen(() -> null);
		context.runOnClient(client -> JuiceClient.CONFIG.tracerMode = TracerMode.NEAREST_PER_CHUNK);

		context.runOnClient(client -> JuiceClient.CONFIG.enabled = false);
		context.waitTicks(5);
		context.takeScreenshot("juiceclient_off");
		context.runOnClient(client -> JuiceClient.CONFIG.enabled = true);

		// Inside a highlighted chunk, where box edges pass behind the camera.
		server.runCommand("tp @a 50.5 106 2.5 -60 25");
		connection.waitForChunksRender();
		context.waitTicks(20);
		context.takeScreenshot("juiceclient_inside_chunk");
	}

	private static void testSmartTool(ClientGameTestContext context, TestServerContext server) {
		check(context.computeOnClient(client -> JuiceClient.CONFIG.smartTool), "Smart tool should start on");
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

		// Cobwebs take shears, or without them a sword.
		server.runCommand("item replace entity @a hotbar.1 with minecraft:iron_sword");
		server.runCommand("item replace entity @a hotbar.4 with minecraft:shears");
		server.runCommand("setblock 9 103 8 minecraft:cobweb");
		context.waitTicks(5);
		// From the torch: a sword already in hand cuts the web before the switch's delay is up.
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
		context.waitTicks(2);
		check(mineWith(context, server, new BlockPos(9, 103, 8)) == 4, "A cobweb should select the shears");
		server.runCommand("item replace entity @a hotbar.4 with minecraft:iron_shovel");
		server.runCommand("setblock 9 103 8 minecraft:cobweb");
		context.waitTicks(5);
		check(mineWith(context, server, new BlockPos(9, 103, 8)) == 1, "Without shears a cobweb should select the sword");

		// A worse tool of the right kind in hand is swapped for the best.
		server.runCommand("setblock 9 102 7 minecraft:deepslate");
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(3));
		context.waitTicks(5);
		check(mineWith(context, server, new BlockPos(9, 102, 7)) == 2, "A plain iron pickaxe in hand should be swapped for the faster gold one");
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(2));
		context.waitTicks(5);
		check(mineWith(context, server, new BlockPos(9, 102, 7)) == 2, "The best pickaxe should stay selected");

		// Off, the selection is left alone.
		context.runOnClient(client -> JuiceClient.CONFIG.smartTool = false);
		check(mineWith(context, server, new BlockPos(9, 100, 9)) == 2, "Smart tool off shouldn't change the slot");
		context.runOnClient(client -> JuiceClient.CONFIG.smartTool = true);

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
		check(context.computeOnClient(client -> JuiceClient.CONFIG.autoEat && JuiceClient.CONFIG.autoEatHunger == 14), "Auto eat should start on, at 14");
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
		check(context.computeOnClient(client -> JuiceClient.CONFIG.takeAll && JuiceClient.CONFIG.takeAllDelay == 1), "Take all should start on, a slot a tick");

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
		context.takeScreenshot("juiceclient_take_all");

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
			JuiceClient.CONFIG.putAllHotbar = true;
			JuiceClient.CONFIG.putAllFilter = PutAllFilter.ONLY_LISTED;
			JuiceClient.CONFIG.setPutAllItem(Items.DIAMOND, true);
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
			JuiceClient.CONFIG.putAllFilter = PutAllFilter.EXCEPT_LISTED;
			JuiceClient.CONFIG.clearPutAllItems();
			JuiceClient.CONFIG.setPutAllItem(Items.OAK_LOG, true);
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
			JuiceClient.CONFIG.putAllHotbar = false;
			JuiceClient.CONFIG.putAllFilter = PutAllFilter.OFF;
			JuiceClient.CONFIG.clearPutAllItems();
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
		context.runOnClient(client -> JuiceClient.CONFIG.putAllHotbar = true);
		context.runOnClient(client -> TakeAll.startPut((net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) client.gui.screen()));
		context.waitFor(client -> !TakeAll.isTaking(), TIMEOUT_TICKS);
		context.waitTicks(5);
		check(context.computeOnClient(client -> client.player.getInventory().isEmpty()), "Put All with the hotbar should empty the inventory");
		context.runOnClient(client -> JuiceClient.CONFIG.putAllHotbar = false);
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

	/** The chest's slot contents on the server, as "item count" with empty slots left out, in slot order. */
	private static List<String> chestContents(TestServerContext server) {
		return server.computeOnServer(minecraftServer -> {
			var chest = (net.minecraft.world.Container) minecraftServer.overworld().getBlockEntity(new BlockPos(10, 100, 8));
			List<String> contents = new java.util.ArrayList<>();

			for (int slot = 0; slot < chest.getContainerSize(); slot++) {
				net.minecraft.world.item.ItemStack stack = chest.getItem(slot);
				contents.add(stack.isEmpty() ? "-" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath() + " " + stack.getCount());
			}

			return contents;
		});
	}

	private static void sortChest(ClientGameTestContext context, SortMode mode, boolean inventory) {
		context.runOnClient(client -> {
			JuiceClient.CONFIG.sortMode = mode;
			Sorter.start((net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) client.gui.screen(), inventory);
		});
		context.waitFor(client -> !Sorter.isSorting(), TIMEOUT_TICKS * 4);
		context.waitTicks(5);
	}

	private static void testSort(ClientGameTestContext context, TestServerContext server) {
		// Each group gets its own things, going by the game's tags once in a world.
		java.util.Map<net.minecraft.world.item.Item, ItemGroup> groups = new java.util.LinkedHashMap<>();
		groups.put(Items.IRON_CHESTPLATE, ItemGroup.ARMOR);
		groups.put(Items.SHIELD, ItemGroup.ARMOR);
		groups.put(Items.DIAMOND_SWORD, ItemGroup.WEAPONS);
		groups.put(Items.ARROW, ItemGroup.WEAPONS);
		groups.put(Items.IRON_PICKAXE, ItemGroup.TOOLS);
		groups.put(Items.WATER_BUCKET, ItemGroup.TOOLS);
		groups.put(Items.OAK_LOG, ItemGroup.WOOD);
		groups.put(Items.SPRUCE_PLANKS, ItemGroup.WOOD);
		groups.put(Items.STICK, ItemGroup.WOOD);
		groups.put(Items.COBBLESTONE, ItemGroup.STONE);
		groups.put(Items.DEEPSLATE, ItemGroup.STONE);
		groups.put(Items.STONE_BRICKS, ItemGroup.STONE);
		groups.put(Items.NETHERRACK, ItemGroup.STONE);
		groups.put(Items.BREAD, ItemGroup.FOOD);
		groups.put(Items.COOKED_BEEF, ItemGroup.FOOD);
		groups.put(Items.WHEAT_SEEDS, ItemGroup.FARMING);
		groups.put(Items.OAK_SAPLING, ItemGroup.FARMING);
		groups.put(Items.BONE_MEAL, ItemGroup.FARMING);
		groups.put(Items.SUGAR_CANE, ItemGroup.FARMING);
		groups.put(Items.DIAMOND, ItemGroup.MINERALS);
		groups.put(Items.IRON_INGOT, ItemGroup.MINERALS);
		groups.put(Items.RAW_GOLD, ItemGroup.MINERALS);
		groups.put(Items.DEEPSLATE_IRON_ORE, ItemGroup.MINERALS);
		groups.put(Items.DIRT, ItemGroup.NATURE);
		groups.put(Items.SAND, ItemGroup.NATURE);
		groups.put(Items.OAK_LEAVES, ItemGroup.NATURE);
		groups.put(Items.POPPY, ItemGroup.NATURE);
		groups.put(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse("white_wool")), ItemGroup.COLORED);
		groups.put(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse("red_concrete")), ItemGroup.COLORED);
		groups.put(Items.GLASS, ItemGroup.COLORED);
		groups.put(Items.REDSTONE, ItemGroup.REDSTONE);
		groups.put(Items.REPEATER, ItemGroup.REDSTONE);
		groups.put(Items.RAIL, ItemGroup.REDSTONE);
		groups.put(Items.CHEST, ItemGroup.UTILITY);
		groups.put(Items.CRAFTING_TABLE, ItemGroup.UTILITY);
		groups.put(Items.TORCH, ItemGroup.UTILITY);
		groups.put(Items.SHULKER_BOX, ItemGroup.UTILITY);
		groups.put(Items.POTION, ItemGroup.POTIONS);
		groups.put(Items.BONE, ItemGroup.MOB_DROPS);
		groups.put(Items.ROTTEN_FLESH, ItemGroup.MOB_DROPS);
		groups.put(Items.GUNPOWDER, ItemGroup.MOB_DROPS);
		List<String> wrong = context.computeOnClient(client -> groups.entrySet().stream()
				.filter(entry -> ItemGroup.of(new net.minecraft.world.item.ItemStack(entry.getKey())) != entry.getValue())
				.map(entry -> entry.getKey() + " is " + ItemGroup.of(new net.minecraft.world.item.ItemStack(entry.getKey())))
				.toList());
		check(wrong.isEmpty(), "Items in the wrong sort group: " + wrong);

		check(context.computeOnClient(client -> JuiceClient.CONFIG.sortButtons && JuiceClient.CONFIG.sortMode == SortMode.TYPE
				&& JuiceClient.CONFIG.sortDelay == 3), "Sort should start on, by type");
		server.runCommand("setblock 8 99 8 minecraft:barrier");
		server.runCommand("tp @a 8.5 100 8.5 -90 0");
		server.runCommand("gamemode survival @a");
		server.runCommand("clear @a");
		server.runCommand("setblock 10 100 8 minecraft:chest");
		server.runCommand("item replace block 10 100 8 container.0 with minecraft:cobblestone 20");
		server.runCommand("item replace block 10 100 8 container.3 with minecraft:diamond 5");
		server.runCommand("item replace block 10 100 8 container.4 with minecraft:bread 3");
		server.runCommand("item replace block 10 100 8 container.7 with minecraft:cobblestone 50");
		server.runCommand("item replace block 10 100 8 container.9 with minecraft:iron_sword");
		server.runCommand("item replace block 10 100 8 container.12 with minecraft:cobblestone 30");
		server.runCommand("item replace block 10 100 8 container.20 with minecraft:diamond 2");
		server.runCommand("item replace block 10 100 8 container.26 with minecraft:netherite_ingot 1");
		context.waitTicks(5);
		openChest(context, server);
		check(context.computeOnClient(client -> Screens.getWidgets(client.gui.screen()).stream()
				.filter(widget -> widget.getMessage().getString().equals("Sort")).count() == 2), "The chest should have two Sort buttons");

		// Watching it go: a stack is carried on the cursor between clicks.
		context.runOnClient(client -> {
			JuiceClient.CONFIG.sortMode = SortMode.TYPE;
			Sorter.start((net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) client.gui.screen(), false);
		});
		context.waitFor(client -> !client.player.containerMenu.getCarried().isEmpty(), TIMEOUT_TICKS);
		context.takeScreenshot("juiceclient_sort_carrying");
		context.waitFor(client -> !Sorter.isSorting(), TIMEOUT_TICKS * 4);
		context.waitTicks(5);
		check(context.computeOnClient(client -> client.player.containerMenu.getCarried().isEmpty()), "Nothing should be left on the cursor");
		// By type: the sword, the stone, the bread, then the minerals; the cobblestone joined into full stacks first.
		List<String> byType = chestContents(server);
		check(byType.subList(0, 7).equals(List.of("iron_sword 1", "cobblestone 64", "cobblestone 36", "bread 3", "diamond 7", "netherite_ingot 1", "-"))
				&& byType.stream().skip(7).allMatch("-"::equals), "Sorting by type gave " + byType);
		context.takeScreenshot("juiceclient_sort_type");

		sortChest(context, SortMode.VALUE, false);
		List<String> byValue = chestContents(server);
		check(byValue.subList(0, 6).equals(List.of("netherite_ingot 1", "diamond 7", "iron_sword 1", "cobblestone 64", "cobblestone 36", "bread 3")),
				"Sorting by value gave " + byValue);

		sortChest(context, SortMode.COUNT, false);
		List<String> byCount = chestContents(server);
		check(byCount.subList(0, 6).equals(List.of("cobblestone 64", "cobblestone 36", "diamond 7", "bread 3", "iron_sword 1", "netherite_ingot 1")),
				"Sorting by count gave " + byCount);

		// With food moved to the front of the groups, the bread goes first.
		context.runOnClient(client -> client.player.closeContainer());
		context.waitTicks(5);
		context.runOnClient(client -> client.gui.setScreen(new SortGroupsScreen(null)));
		context.waitTicks(5);
		pressListButton(context, "6. Food");
		context.takeScreenshot("juiceclient_sort_groups_picked");
		pressListButton(context, "1. Armor");
		context.waitTicks(2);
		context.takeScreenshot("juiceclient_sort_groups");
		check(context.computeOnClient(client -> JuiceClient.CONFIG.sortGroupOrder().getFirst() == ItemGroup.FOOD
				&& JuiceClient.CONFIG.sortGroupOrder().get(1) == ItemGroup.ARMOR
				&& JuiceClient.CONFIG.sortGroupOrder().size() == ItemGroup.values().length), "Food should have moved to the front");
		context.runOnClient(client -> client.gui.setScreen(null));
		openChest(context, server);
		sortChest(context, SortMode.TYPE, false);
		List<String> foodFirst = chestContents(server);
		check(foodFirst.subList(0, 6).equals(List.of("bread 3", "iron_sword 1", "cobblestone 64", "cobblestone 36", "diamond 7", "netherite_ingot 1")),
				"Sorting with food first gave " + foodFirst);
		context.runOnClient(client -> JuiceClient.CONFIG.resetSortGroupOrder());

		// Chosen items go first and last, whatever the order says about them.
		context.runOnClient(client -> {
			JuiceClient.CONFIG.setSortPriority(Items.NETHERITE_INGOT, SortPriority.FRONT);
			JuiceClient.CONFIG.setSortPriority(Items.COBBLESTONE, SortPriority.BACK);
		});
		sortChest(context, SortMode.COUNT, false);
		List<String> byPriority = chestContents(server);
		check(byPriority.subList(0, 7).equals(List.of("netherite_ingot 1", "diamond 7", "bread 3", "iron_sword 1", "cobblestone 64", "cobblestone 36", "-")),
				"Sorting with priorities gave " + byPriority);
		context.runOnClient(client -> client.player.closeContainer());
		context.waitTicks(5);
		context.runOnClient(client -> client.gui.setScreen(new SortPriorityScreen(null)));
		context.waitTicks(5);
		context.takeScreenshot("juiceclient_sort_priorities");
		context.runOnClient(client -> {
			client.gui.setScreen(null);
			JuiceClient.CONFIG.clearSortPriorities();
		});
		check(context.computeOnClient(client -> JuiceClient.CONFIG.sortPriority(Items.NETHERITE_INGOT) == SortPriority.NORMAL), "Clearing should put every item back to normal");

		// The inventory's sort leaves the hotbar alone.
		server.runCommand("clear @a");
		server.runCommand("item replace entity @a hotbar.0 with minecraft:torch 5");
		server.runCommand("item replace entity @a hotbar.4 with minecraft:dirt 5");
		server.runCommand("item replace entity @a inventory.5 with minecraft:dirt 10");
		server.runCommand("item replace entity @a inventory.20 with minecraft:diamond_sword");
		server.runCommand("item replace entity @a inventory.26 with minecraft:dirt 7");
		context.waitTicks(5);
		context.runOnClient(client -> client.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(client.player)));
		context.waitTicks(5);
		check(context.computeOnClient(client -> Screens.getWidgets(client.gui.screen()).stream()
				.filter(widget -> widget.getMessage().getString().equals("Sort")).count() == 1), "The inventory should have a Sort button");
		context.takeScreenshot("juiceclient_sort_inventory");
		sortChest(context, SortMode.TYPE, true);
		check(context.computeOnClient(client -> {
			net.minecraft.world.entity.player.Inventory inventory = client.player.getInventory();
			return inventory.getItem(0).is(Items.TORCH) && inventory.getItem(4).is(Items.DIRT) && inventory.getItem(4).getCount() == 5
					&& inventory.getItem(9).is(Items.DIAMOND_SWORD) && inventory.getItem(10).is(Items.DIRT) && inventory.getItem(10).getCount() == 17
					&& inventory.getItem(11).isEmpty() && inventory.getItem(35).isEmpty();
		}), "The inventory should be sorted with the hotbar untouched");
		check(server.computeOnServer(minecraftServer -> {
			net.minecraft.world.entity.player.Inventory inventory = minecraftServer.getPlayerList().getPlayers().getFirst().getInventory();
			return inventory.getItem(9).is(Items.DIAMOND_SWORD) && inventory.getItem(10).getCount() == 17 && inventory.getItem(4).getCount() == 5;
		}), "The inventory should be sorted on the server");

		context.runOnClient(client -> {
			JuiceClient.CONFIG.sortMode = SortMode.TYPE;
			client.player.closeContainer();
		});
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
		context.setScreen(() -> new com.juiceclient.NameTagEntitiesScreen(null));
		context.setScreen(() -> new com.juiceclient.NameTagBlocksScreen(null));
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

	private static void testNameTags(ClientGameTestContext context, TestServerContext server) {
		check(context.computeOnClient(client -> JuiceClient.CONFIG.nameTags && JuiceClient.CONFIG.nameTagEntities().isEmpty()
				&& JuiceClient.CONFIG.nameTagBlocks().isEmpty()), "Name tags should start on, with nothing chosen");
		server.runCommand("tp @a 8.5 100 8.5 -90 10");
		server.runCommand("setblock 12 99 8 minecraft:barrier");
		server.runCommand("setblock 12 99 11 minecraft:barrier");
		server.runCommand("summon minecraft:acacia_boat 12.5 100 8.5");
		server.runCommand("setblock 12 100 11 minecraft:chest");
		context.waitTicks(10);

		// Nothing chosen, nothing tagged.
		check(context.computeOnClient(client -> JuiceClient.NAME_TAGS.tags(client.level, JuiceClient.CONFIG, client.player.getEyePosition(), 1F).isEmpty()),
				"Nothing should be tagged before anything is chosen");

		// The acacia boat and the chest get tags with their names.
		context.runOnClient(client -> {
			JuiceClient.CONFIG.setNameTagged(net.minecraft.world.entity.EntityTypes.ACACIA_BOAT, true);
			JuiceClient.CONFIG.setNameTagBlock(Blocks.CHEST, true);
			JuiceClient.NAME_TAGS.rescanSoon();
		});
		context.waitTicks(5);
		List<String> labels = context.computeOnClient(client -> JuiceClient.NAME_TAGS.tags(client.level, JuiceClient.CONFIG, client.player.getEyePosition(), 1F)
				.stream().map(tag -> tag.label()).toList());
		check(labels.contains("Acacia Boat") && labels.contains("Chest") && labels.size() == 2, "The boat and the chest should be tagged, not: " + labels);
		context.takeScreenshot("juiceclient_name_tags");

		// Out of range, no tags.
		context.runOnClient(client -> JuiceClient.CONFIG.nameTagRange = JuiceClientConfig.MIN_NAME_TAG_RANGE);
		server.runCommand("tp @a 30.5 100 8.5 -90 10");
		context.waitTicks(15);
		check(context.computeOnClient(client -> JuiceClient.NAME_TAGS.tags(client.level, JuiceClient.CONFIG, client.player.getEyePosition(), 1F).isEmpty()),
				"Nothing should be tagged out of range");

		context.runOnClient(client -> {
			JuiceClient.CONFIG.nameTagRange = 32;
			JuiceClient.CONFIG.clearNameTagEntities();
			JuiceClient.CONFIG.clearNameTagBlocks();
		});
		server.runCommand("kill @e[type=minecraft:acacia_boat]");
		server.runCommand("setblock 12 100 11 minecraft:air");
		server.runCommand("setblock 12 99 8 minecraft:air");
		server.runCommand("setblock 12 99 11 minecraft:air");
		server.runCommand("kill @e[type=minecraft:item]");
		server.runCommand("tp @a 8.5 100 8.5 -90 0");
		context.waitTicks(5);
	}

	private static void testAutoTotem(ClientGameTestContext context, TestServerContext server) {
		check(context.computeOnClient(client -> JuiceClient.CONFIG.autoTotem && JuiceClient.CONFIG.autoTotemDelay == 5), "Auto totem should start on, after a quarter second");
		server.runCommand("setblock 8 99 8 minecraft:barrier");
		server.runCommand("tp @a 8.5 100 8.5 -90 0");
		server.runCommand("gamemode survival @a");
		server.runCommand("clear @a");
		server.runCommand("effect clear @a");
		context.runOnClient(client -> JuiceClient.CONFIG.autoTotemDelay = 20);

		// A shield in the offhand stays there, with two totems waiting in the main inventory.
		server.runCommand("item replace entity @a weapon.offhand with minecraft:shield");
		server.runCommand("item replace entity @a hotbar.0 with minecraft:iron_sword");
		server.runCommand("item replace entity @a inventory.5 with minecraft:totem_of_undying");
		server.runCommand("item replace entity @a inventory.20 with minecraft:totem_of_undying");
		context.waitTicks(40);
		check(context.computeOnClient(client -> client.player.getOffhandItem().is(Items.SHIELD)), "A shield in the offhand shouldn't be swapped for a totem");

		// Taking the shield out: after the wait, a totem goes in, and the inventory is closed after.
		int totemCloses = context.computeOnClient(client -> com.juiceclient.InventoryClicks.closesSent());
		server.runCommand("item replace entity @a weapon.offhand with minecraft:air");
		context.waitTicks(10);
		check(context.computeOnClient(client -> client.player.getOffhandItem().isEmpty()), "The totem shouldn't go in before the wait");
		context.waitFor(client -> client.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING), TIMEOUT_TICKS);
		context.waitTicks(5);
		check(server.computeOnServer(minecraftServer -> {
			var player = minecraftServer.getPlayerList().getPlayers().getFirst();
			var inventory = player.getInventory();
			return player.getOffhandItem().is(Items.TOTEM_OF_UNDYING) && inventory.getItem(14).isEmpty()
					&& inventory.getItem(29).is(Items.TOTEM_OF_UNDYING) && inventory.getItem(0).is(Items.IRON_SWORD);
		}), "On the server, the totem should be in the offhand and the sword kept");
		check(context.computeOnClient(client -> com.juiceclient.InventoryClicks.closesSent()) == totemCloses + 1, "The inventory should be closed after a totem goes in");

		// It pops, and after the wait the second one goes in.
		server.runCommand("damage @p 100 minecraft:generic");
		context.waitFor(client -> !client.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING), TIMEOUT_TICKS);
		check(context.computeOnClient(client -> client.player.isAlive()), "The totem should have saved the player");
		context.waitTicks(10);
		check(context.computeOnClient(client -> !client.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)), "The next totem shouldn't go in before the wait");
		context.waitFor(client -> client.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING), TIMEOUT_TICKS);
		context.waitTicks(5);
		check(server.computeOnServer(minecraftServer -> minecraftServer.getPlayerList().getPlayers().getFirst().getOffhandItem().is(Items.TOTEM_OF_UNDYING)),
				"The server should have the second totem in the offhand");

		// The last one pops, and with none left it says so.
		server.runCommand("effect clear @a");
		server.runCommand("effect give @a minecraft:instant_health 1 5");
		context.waitTicks(5);
		server.runCommand("damage @p 100 minecraft:generic");
		context.waitFor(client -> !client.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING), TIMEOUT_TICKS);
		context.waitTicks(30);
		check(context.computeOnClient(client -> client.player.getOffhandItem().isEmpty() && client.player.isAlive()), "With no totems left, the offhand should stay empty");

		// Low on health, a totem replaces even a shield, without the wait.
		server.runCommand("effect clear @a");
		server.runCommand("effect give @a minecraft:instant_health 1 5");
		context.waitTicks(5);
		check(context.computeOnClient(client -> JuiceClient.CONFIG.autoTotemHealth == 4), "Low health swap should start at 4 hearts");
		server.runCommand("item replace entity @a weapon.offhand with minecraft:shield");
		server.runCommand("item replace entity @a inventory.5 with minecraft:totem_of_undying");
		context.waitTicks(30);
		check(context.computeOnClient(client -> client.player.getOffhandItem().is(Items.SHIELD)), "At full health the shield should stay");
		server.runCommand("damage @p 14 minecraft:generic");
		context.waitTicks(10);
		check(context.computeOnClient(client -> client.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)), "Low on health, a totem should replace the shield at once");
		context.waitTicks(5);
		check(server.computeOnServer(minecraftServer -> {
			var player = minecraftServer.getPlayerList().getPlayers().getFirst();
			return player.getOffhandItem().is(Items.TOTEM_OF_UNDYING) && player.getInventory().getItem(14).is(Items.SHIELD);
		}), "On the server, the totem should be in the offhand and the shield where the totem was");
		check(context.computeOnClient(client -> AutoTotem.isShieldOut()), "The shield should be waiting to go back");

		// The server undoing the swap, as one turned down mid-meal shows once the meal ends: the totem goes back in.
		// Healed above the low mark first, so only the undoing brings the totem back.
		server.runCommand("effect give @a minecraft:instant_health 1 0");
		context.waitFor(client -> !AutoTotem.isLowHealth(client.player, JuiceClient.CONFIG), TIMEOUT_TICKS);
		server.runCommand("item replace entity @a weapon.offhand with minecraft:shield");
		server.runCommand("item replace entity @a inventory.5 with minecraft:totem_of_undying");
		context.waitFor(client -> client.player.getOffhandItem().is(Items.SHIELD), TIMEOUT_TICKS);
		context.waitFor(client -> client.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING), TIMEOUT_TICKS);
		context.waitTicks(5);
		check(context.computeOnClient(client -> AutoTotem.isShieldOut() && !AutoTotem.isLowHealth(client.player, JuiceClient.CONFIG)),
				"After the server undid the swap, the totem should be back in with the shield still waiting");
		check(server.computeOnServer(minecraftServer -> {
			var player = minecraftServer.getPlayerList().getPlayers().getFirst();
			return player.getOffhandItem().is(Items.TOTEM_OF_UNDYING) && player.getInventory().getItem(14).is(Items.SHIELD);
		}), "On the server, the totem should be back in the offhand");

		// Healed, but not yet full on food: the totem stays, even past the 15 seconds.
		server.runOnServer(minecraftServer -> {
			var player = minecraftServer.getPlayerList().getPlayers().getFirst();
			player.getFoodData().setFoodLevel(15);
			player.getFoodData().setSaturation(0F);
		});
		server.runCommand("effect clear @a");
		server.runCommand("effect give @a minecraft:instant_health 1 5");
		context.waitTicks(AutoTotem.SHIELD_BACK_TICKS + 20);
		check(context.computeOnClient(client -> client.player.getHealth() >= client.player.getMaxHealth()
				&& client.player.getFoodData().getFoodLevel() < 20 && client.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)),
				"Without full hunger the totem should stay");

		// Full on food too: the shield goes back, and the totem where the shield was.
		server.runCommand("effect give @a minecraft:saturation 1 20");
		context.waitFor(client -> client.player.getOffhandItem().is(Items.SHIELD), TIMEOUT_TICKS);
		context.waitTicks(5);
		check(context.computeOnClient(client -> !AutoTotem.isShieldOut()), "Nothing should be waiting once the shield is back");
		check(server.computeOnServer(minecraftServer -> {
			var player = minecraftServer.getPlayerList().getPlayers().getFirst();
			return player.getOffhandItem().is(Items.SHIELD) && player.getInventory().getItem(14).is(Items.TOTEM_OF_UNDYING);
		}), "On the server, the shield should be back in the offhand");

		// The server undoing putting the shield back: it's put back again.
		server.runCommand("item replace entity @a weapon.offhand with minecraft:totem_of_undying");
		server.runCommand("item replace entity @a inventory.5 with minecraft:shield");
		context.waitFor(client -> client.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING), TIMEOUT_TICKS);
		context.waitFor(client -> client.player.getOffhandItem().is(Items.SHIELD), TIMEOUT_TICKS);
		context.waitTicks(5);
		check(server.computeOnServer(minecraftServer -> {
			var player = minecraftServer.getPlayerList().getPlayers().getFirst();
			return player.getOffhandItem().is(Items.SHIELD) && player.getInventory().getItem(14).is(Items.TOTEM_OF_UNDYING);
		}), "On the server, the shield should be back in the offhand after the server undid it");
		context.waitTicks(AutoTotem.BACK_CHECK_TICKS + 5);

		// A totem put in by hand, with a shield in the inventory, isn't waiting to be swapped back.
		server.runCommand("item replace entity @a weapon.offhand with minecraft:totem_of_undying");
		server.runCommand("item replace entity @a inventory.5 with minecraft:shield");
		context.waitTicks(10);
		check(context.computeOnClient(client -> !AutoTotem.isShieldOut() && client.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)),
				"A totem put in by hand should stay");

		server.runCommand("effect clear @a");
		server.runCommand("effect give @a minecraft:instant_health 1 5");
		// Back to full food too, so that later tests can count on natural healing.
		server.runCommand("effect give @a minecraft:saturation 1 20");
		context.runOnClient(client -> JuiceClient.CONFIG.autoTotemDelay = 5);
		server.runCommand("clear @a");
		server.runCommand("gamemode spectator @a");
		server.runCommand("setblock 8 99 8 minecraft:air");
		server.runCommand("tp @a 8.5 100 8.5 -90 0");
		context.waitTicks(5);
	}

	/** Presses a button inside the open screen's list, which the test context's own clicking doesn't reach. */
	private static void pressListButton(ClientGameTestContext context, String label) {
		boolean pressed = context.computeOnClient(client -> {
			java.util.ArrayDeque<net.minecraft.client.gui.components.events.GuiEventListener> queue = new java.util.ArrayDeque<>(client.gui.screen().children());

			while (!queue.isEmpty()) {
				var child = queue.poll();

				if (child instanceof net.minecraft.client.gui.components.Button button && button.getMessage().getString().equals(label)) {
					button.onPress(new net.minecraft.client.input.KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
					return true;
				}

				if (child instanceof net.minecraft.client.gui.components.events.ContainerEventHandler container) {
					queue.addAll(container.children());
				}
			}

			return false;
		});
		check(pressed, "No button labelled " + label);
	}

	private static int hotbarSlotOf(net.minecraft.world.entity.player.Inventory inventory, net.minecraft.world.item.Item item) {
		for (int slot = 0; slot < 9; slot++) {
			if (inventory.getItem(slot).is(item)) {
				return slot;
			}
		}

		return -1;
	}

	private static void testFoodResupply(ClientGameTestContext context, TestServerContext server) {
		check(context.computeOnClient(client -> JuiceClient.CONFIG.autoEatResupply), "Food resupply should start on");
		server.runCommand("setblock 8 99 8 minecraft:barrier");
		server.runCommand("tp @a 8.5 100 8.5 -90 0");
		server.runCommand("gamemode survival @a");
		server.runCommand("clear @a");

		// Food in the hotbar: nothing moves.
		server.runCommand("item replace entity @a hotbar.3 with minecraft:bread");
		server.runCommand("item replace entity @a hotbar.0 with minecraft:stone");
		server.runCommand("item replace entity @a inventory.10 with minecraft:cooked_beef 5");
		context.waitTicks(20);
		check(context.computeOnClient(client -> client.player.getInventory().getItem(19).is(Items.COOKED_BEEF)), "Nothing should move while the hotbar has food");

		int closes = context.computeOnClient(client -> com.juiceclient.InventoryClicks.closesSent());
		// Out of food in the hotbar: the best food, ahead of the golden apple, goes into an empty hotbar slot (the one last
		// eaten from if that's empty, which an earlier test may have set).
		server.runCommand("item replace entity @a hotbar.3 with minecraft:air");
		server.runCommand("item replace entity @a inventory.2 with minecraft:golden_apple");
		server.runCommand("item replace entity @a inventory.20 with minecraft:bread 3");
		context.waitFor(client -> hotbarSlotOf(client.player.getInventory(), Items.COOKED_BEEF) >= 0, TIMEOUT_TICKS);
		int beefSlot = context.computeOnClient(client -> hotbarSlotOf(client.player.getInventory(), Items.COOKED_BEEF));
		context.waitTicks(5);
		check(server.computeOnServer(minecraftServer -> {
			var inventory = minecraftServer.getPlayerList().getPlayers().getFirst().getInventory();
			return inventory.getItem(beefSlot).is(Items.COOKED_BEEF) && inventory.getItem(beefSlot).getCount() == 5 && inventory.getItem(19).isEmpty()
					&& inventory.getItem(0).is(Items.STONE) && inventory.getItem(11).is(Items.GOLDEN_APPLE) && inventory.getItem(29).is(Items.BREAD);
		}), "On the server, the steak should be in the hotbar and the rest left alone");
		// The move is followed by closing the inventory, as anticheats expect.
		check(context.computeOnClient(client -> com.juiceclient.InventoryClicks.closesSent()) == closes + 1, "The inventory should be closed after moving food");

		// Turned off, nothing moves.
		context.runOnClient(client -> JuiceClient.CONFIG.autoEatResupply = false);
		server.runCommand("item replace entity @a hotbar." + beefSlot + " with minecraft:air");
		context.waitTicks(20);
		check(context.computeOnClient(client -> client.player.getInventory().getItem(29).is(Items.BREAD)), "Food shouldn't move while resupply is off");
		context.runOnClient(client -> JuiceClient.CONFIG.autoEatResupply = true);

		// With only golden apples left, the plain one comes down first, and the enchanted one stays put.
		server.runCommand("clear @a");
		server.runCommand("item replace entity @a inventory.4 with minecraft:enchanted_golden_apple");
		server.runCommand("item replace entity @a inventory.8 with minecraft:golden_apple 2");
		context.waitFor(client -> hotbarSlotOf(client.player.getInventory(), Items.GOLDEN_APPLE) >= 0, TIMEOUT_TICKS);
		context.waitTicks(20);
		check(context.computeOnClient(client -> client.player.getInventory().getItem(13).is(Items.ENCHANTED_GOLDEN_APPLE)
				&& hotbarSlotOf(client.player.getInventory(), Items.ENCHANTED_GOLDEN_APPLE) < 0), "The enchanted golden apple should wait while there's other food");

		// Once nothing else is left, the enchanted one comes down too, and only once.
		int appleSlot = context.computeOnClient(client -> hotbarSlotOf(client.player.getInventory(), Items.GOLDEN_APPLE));
		server.runCommand("item replace entity @a hotbar." + appleSlot + " with minecraft:air");
		server.runCommand("item replace entity @a inventory.20 with minecraft:enchanted_golden_apple");
		context.waitFor(client -> hotbarSlotOf(client.player.getInventory(), Items.ENCHANTED_GOLDEN_APPLE) >= 0, TIMEOUT_TICKS);
		context.waitTicks(20);
		check(context.computeOnClient(client -> client.player.getInventory().countItem(Items.ENCHANTED_GOLDEN_APPLE) == 2
				&& (client.player.getInventory().getItem(13).is(Items.ENCHANTED_GOLDEN_APPLE) || client.player.getInventory().getItem(29).is(Items.ENCHANTED_GOLDEN_APPLE))),
				"Only one enchanted golden apple should come down");

		server.runCommand("clear @a");
		server.runCommand("gamemode spectator @a");
		server.runCommand("setblock 8 99 8 minecraft:air");
		server.runCommand("tp @a 8.5 100 8.5 -90 0");
		context.waitTicks(5);
	}

	private static void testSettingsTransfer(ClientGameTestContext context) {
		// Exported as they are now, then changed, then imported back.
		int oldPriority = context.computeOnClient(client -> JuiceClient.CONFIG.rules.getFirst().priority);
		java.nio.file.Path file = context.computeOnClient(client -> {
			JuiceClient.CONFIG.autoTotemHealth = 6;
			JuiceClient.CONFIG.nameTagRange = 40;
			JuiceClient.CONFIG.rules.getFirst().priority = 9;
			JuiceClient.CONFIG.markRulesChanged();
			com.juiceclient.JuiceClientConfigScreen.sectionKey(client.options, "options.juiceclient.section.tools").setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_K));
			KeyMapping.resetMapping();

			try {
				return SettingsTransfer.export();
			} catch (java.io.IOException e) {
				throw new RuntimeException(e);
			}
		});
		check(context.computeOnClient(client -> SettingsTransfer.exports().contains(file)), "The export should be listed");
		String firstRule = context.computeOnClient(client -> JuiceClient.CONFIG.rules.getFirst().name);
		int ruleCount = context.computeOnClient(client -> JuiceClient.CONFIG.rules.size());

		context.runOnClient(client -> {
			JuiceClient.CONFIG.autoTotemHealth = 2;
			JuiceClient.CONFIG.nameTagRange = 16;
			JuiceClient.CONFIG.rules.clear();
			JuiceClient.CONFIG.markRulesChanged();
			com.juiceclient.JuiceClientConfigScreen.sectionKey(client.options, "options.juiceclient.section.tools").setKey(InputConstants.UNKNOWN);
			KeyMapping.resetMapping();
		});

		// Through the screen, as a player would.
		context.setScreen(() -> new JuiceClientModMenu().getModConfigScreenFactory().create(null));
		context.waitTicks(2);
		pressListButton(context, "Import Settings...");
		context.waitTicks(2);
		context.takeScreenshot("juiceclient_import");
		pressListButton(context, file.getFileName().toString().replace(".properties", ""));
		context.waitFor(client -> client.gui.screen() instanceof com.juiceclient.JuiceClientConfigScreen, TIMEOUT_TICKS);
		context.setScreen(() -> null);

		check(context.computeOnClient(client -> JuiceClient.CONFIG.autoTotemHealth == 6 && JuiceClient.CONFIG.nameTagRange == 40),
				"Imported settings should be back");
		check(context.computeOnClient(client -> JuiceClient.CONFIG.rules.size() == ruleCount
				&& JuiceClient.CONFIG.rules.getFirst().name.equals(firstRule) && JuiceClient.CONFIG.rules.getFirst().priority == 9),
				"Imported rules should be back");
		check(context.computeOnClient(client -> com.juiceclient.JuiceClientConfigScreen.sectionKey(client.options, "options.juiceclient.section.tools").saveString().equals("key.keyboard.k")),
				"Imported keys should be back");
		// The settings before the import were kept too, and the saved file has the imported ones.
		check(context.computeOnClient(client -> SettingsTransfer.exports().stream().anyMatch(path -> path.getFileName().toString().startsWith("before-import_"))),
				"Settings before an import should be saved");
		check(context.computeOnClient(client -> JuiceClientConfig.load(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()).autoTotemHealth == 6),
				"The imported settings should be saved");

		context.runOnClient(client -> {
			JuiceClient.CONFIG.autoTotemHealth = 4;
			JuiceClient.CONFIG.nameTagRange = 32;
			JuiceClient.CONFIG.rules.getFirst().priority = oldPriority;
			JuiceClient.CONFIG.markRulesChanged();
			JuiceClient.applyRuleChanges();
			com.juiceclient.JuiceClientConfigScreen.sectionKey(client.options, "options.juiceclient.section.tools").setKey(InputConstants.UNKNOWN);
			KeyMapping.resetMapping();
		});
	}

	/** Hits an entity of this type as the player would, and returns the slot selected for the hit. */
	private static int hitTagged(ClientGameTestContext context, net.minecraft.world.entity.EntityType<?> type) {
		return context.computeOnClient(client -> {
			Entity target = java.util.stream.StreamSupport.stream(client.level.entitiesForRendering().spliterator(), false)
					.filter(entity -> entity.getType() == type)
					.findFirst()
					.orElseThrow();
			client.gameMode.attack(client.player, target);
			client.player.resetAttackStrengthTicker();
			return client.player.getInventory().getSelectedSlot();
		});
	}

	private static void testAutoWeapon(ClientGameTestContext context, TestServerContext server) {
		check(context.computeOnClient(client -> JuiceClient.CONFIG.autoWeapon && JuiceClient.CONFIG.autoWeaponReturn == 60), "Auto weapon should start on, switching back after 3 seconds");
		server.runCommand("setblock 8 99 8 minecraft:barrier");
		server.runCommand("tp @a 8.5 100 8.5 -90 0");
		server.runCommand("gamemode survival @a");
		server.runCommand("clear @a");
		server.runCommand("item replace entity @a hotbar.0 with minecraft:torch");
		server.runCommand("item replace entity @a hotbar.1 with minecraft:netherite_axe");
		server.runCommand("item replace entity @a hotbar.2 with minecraft:diamond_sword");
		server.runCommand("item replace entity @a hotbar.3 with minecraft:iron_sword[enchantments={\"minecraft:sharpness\":5}]");
		server.runCommand("item replace entity @a hotbar.4 with minecraft:golden_sword[enchantments={\"minecraft:smite\":5}]");
		server.runCommand("item replace entity @a hotbar.5 with minecraft:mace");
		server.runCommand("summon minecraft:cow 10.5 100 8.5 {NoAI:1b,NoGravity:1b,Invulnerable:1b,Tags:[\"weapon_cow\"]}");
		server.runCommand("summon minecraft:zombie 10.5 100 9.5 {NoAI:1b,NoGravity:1b,Invulnerable:1b,Tags:[\"weapon_zombie\"]}");
		server.runCommand("summon minecraft:armor_stand 10.5 100 7.5 {NoGravity:1b,Tags:[\"weapon_stand\"]}");
		context.runOnClient(client -> {
			client.player.getInventory().setSelectedSlot(0);
			JuiceClient.CONFIG.autoWeaponReturn = 20;
		});
		context.waitTicks(10);

		// The best sword: Sharpness V iron beats plain diamond, and the netherite axe loses to any sword.
		check(hitTagged(context, net.minecraft.world.entity.EntityTypes.COW) == 3, "A cow should be hit with the Sharpness sword");
		// Against the undead, Smite counts.
		check(hitTagged(context, net.minecraft.world.entity.EntityTypes.ZOMBIE) == 4, "A zombie should be hit with the Smite sword");
		context.waitTicks(5);
		check(server.computeOnServer(minecraftServer -> minecraftServer.getPlayerList().getPlayers().getFirst().getInventory().getSelectedSlot() == 4),
				"The server should have the sword selected");

		// After a second without hitting, the torch is back.
		context.waitFor(client -> client.player.getInventory().getSelectedSlot() == 0, TIMEOUT_TICKS);

		// An armor stand isn't a fight, and a mace in hand is kept.
		check(hitTagged(context, net.minecraft.world.entity.EntityTypes.ARMOR_STAND) == 0, "Hitting an armor stand shouldn't switch");
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(5));
		check(hitTagged(context, net.minecraft.world.entity.EntityTypes.COW) == 5, "A mace in hand should be kept");

		// With no sword, the axe.
		server.runCommand("item replace entity @a hotbar.2 with minecraft:air");
		server.runCommand("item replace entity @a hotbar.3 with minecraft:air");
		server.runCommand("item replace entity @a hotbar.4 with minecraft:air");
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
		context.waitTicks(5);
		check(hitTagged(context, net.minecraft.world.entity.EntityTypes.COW) == 1, "With no sword the axe should be used");

		// Turned off, nothing switches.
		context.runOnClient(client -> {
			JuiceClient.CONFIG.autoWeapon = false;
			client.player.getInventory().setSelectedSlot(0);
		});
		check(hitTagged(context, net.minecraft.world.entity.EntityTypes.COW) == 0, "Auto weapon shouldn't switch while off");

		context.runOnClient(client -> {
			JuiceClient.CONFIG.autoWeapon = true;
			JuiceClient.CONFIG.autoWeaponReturn = 60;
		});
		server.runCommand("kill @e[tag=weapon_cow]");
		server.runCommand("kill @e[tag=weapon_zombie]");
		server.runCommand("kill @e[tag=weapon_stand]");
		// Their drops would be picked up later, and raw beef counts as food in the hotbar.
		server.runCommand("kill @e[type=minecraft:item]");
		server.runCommand("clear @a");
		server.runCommand("gamemode spectator @a");
		server.runCommand("setblock 8 99 8 minecraft:air");
		server.runCommand("tp @a 8.5 100 8.5 -90 0");
		context.waitTicks(5);
	}

	private static void testAutoArmor(ClientGameTestContext context, TestServerContext server) {
		check(context.computeOnClient(client -> JuiceClient.CONFIG.autoArmor), "Auto armor should start on");
		server.runCommand("setblock 8 99 8 minecraft:barrier");
		server.runCommand("tp @a 8.5 100 8.5 -90 0");
		server.runCommand("gamemode survival @a");
		server.runCommand("clear @a");

		// Head: Protection IV diamond beats bare netherite, which beats the leather being worn and the iron.
		server.runCommand("item replace entity @a armor.head with minecraft:leather_helmet");
		server.runCommand("item replace entity @a inventory.0 with minecraft:iron_helmet");
		server.runCommand("item replace entity @a inventory.1 with minecraft:netherite_helmet");
		server.runCommand("item replace entity @a inventory.2 with minecraft:diamond_helmet[enchantments={\"minecraft:protection\":4}]");
		// Chest: while an elytra is worn, no chestplate goes on.
		server.runCommand("item replace entity @a armor.chest with minecraft:elytra");
		server.runCommand("item replace entity @a inventory.3 with minecraft:diamond_chestplate");
		// Legs: from the hotbar into an empty slot.
		server.runCommand("item replace entity @a hotbar.4 with minecraft:iron_leggings");
		// Feet: Curse of Binding is never put on, so the worse leather boots are.
		server.runCommand("item replace entity @a inventory.5 with minecraft:diamond_boots[enchantments={\"minecraft:binding_curse\":1}]");
		server.runCommand("item replace entity @a inventory.6 with minecraft:leather_boots");

		int armorCloses = context.computeOnClient(client -> com.juiceclient.InventoryClicks.closesSent());
		// Not instant: the first piece waits half a second, and each after it another half second.
		context.waitTicks(5);
		check(context.computeOnClient(client -> client.player.getItemBySlot(EquipmentSlot.HEAD).is(Items.LEATHER_HELMET)), "The first piece shouldn't go on straight away");
		context.waitFor(client -> client.player.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET), TIMEOUT_TICKS);
		context.waitTicks(5);
		check(context.computeOnClient(client -> client.player.getItemBySlot(EquipmentSlot.LEGS).isEmpty()), "The next piece shouldn't follow straight away");
		context.waitFor(client -> client.player.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET)
				&& client.player.getItemBySlot(EquipmentSlot.LEGS).is(Items.IRON_LEGGINGS)
				&& client.player.getItemBySlot(EquipmentSlot.FEET).is(Items.LEATHER_BOOTS), TIMEOUT_TICKS);
		context.waitTicks(20);
		check(server.computeOnServer(minecraftServer -> {
			var player = minecraftServer.getPlayerList().getPlayers().getFirst();
			var inventory = player.getInventory();
			return player.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET)
					&& player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)
					&& player.getItemBySlot(EquipmentSlot.LEGS).is(Items.IRON_LEGGINGS)
					&& player.getItemBySlot(EquipmentSlot.FEET).is(Items.LEATHER_BOOTS)
					&& inventory.countItem(Items.LEATHER_HELMET) == 1 && inventory.countItem(Items.NETHERITE_HELMET) == 1
					&& inventory.countItem(Items.IRON_HELMET) == 1 && inventory.countItem(Items.DIAMOND_CHESTPLATE) == 1
					&& inventory.countItem(Items.DIAMOND_BOOTS) == 1 && inventory.getItem(4).isEmpty();
		}), "On the server, the best armor should be worn, the elytra kept and everything else still in the inventory");
		check(context.computeOnClient(client -> com.juiceclient.InventoryClicks.closesSent()) == armorCloses + 3, "The inventory should be closed after each piece");

		// Taking the elytra off lets the chestplate go on.
		server.runCommand("item replace entity @a armor.chest with minecraft:air");
		context.waitFor(client -> client.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE), TIMEOUT_TICKS);

		// Turned off, nothing changes.
		context.runOnClient(client -> JuiceClient.CONFIG.autoArmor = false);
		server.runCommand("item replace entity @a armor.legs with minecraft:air");
		server.runCommand("item replace entity @a hotbar.4 with minecraft:iron_leggings");
		context.waitTicks(20);
		check(context.computeOnClient(client -> client.player.getItemBySlot(EquipmentSlot.LEGS).isEmpty()), "Auto armor shouldn't act while off");
		context.runOnClient(client -> JuiceClient.CONFIG.autoArmor = true);

		server.runCommand("clear @a");
		server.runCommand("gamemode spectator @a");
		server.runCommand("setblock 8 99 8 minecraft:air");
		server.runCommand("tp @a 8.5 100 8.5 -90 0");
		context.waitTicks(5);
	}

	private static void testAutoDrop(ClientGameTestContext context, TestServerContext server) {
		check(context.computeOnClient(client -> JuiceClient.CONFIG.autoDrop && !JuiceClient.CONFIG.hasAutoDropItems()), "Auto drop should start on with nothing listed");
		server.runCommand("setblock 8 99 8 minecraft:barrier");
		server.runCommand("tp @a 8.5 100 8.5 -90 0");
		server.runCommand("gamemode survival @a");
		server.runCommand("clear @a");
		server.runCommand("item replace entity @a hotbar.0 with minecraft:iron_sword");
		server.runCommand("item replace entity @a armor.head with minecraft:leather_helmet");
		context.waitTicks(5);

		// Nothing listed, nothing dropped.
		server.runCommand("give @a minecraft:cobblestone 20");
		context.waitTicks(10);
		check(context.computeOnClient(client -> client.player.getInventory().countItem(Items.COBBLESTONE) == 20), "Nothing should be dropped with an empty list");

		// Listed items go, in the hotbar, the inventory and the offhand, and as soon as more are picked up.
		context.runOnClient(client -> {
			JuiceClient.CONFIG.setAutoDropItem(Items.COBBLESTONE, true);
			JuiceClient.CONFIG.setAutoDropItem(Items.LEATHER_HELMET, true);
		});
		server.runCommand("give @a minecraft:dirt 5");
		server.runCommand("item replace entity @a inventory.10 with minecraft:cobblestone 64");
		server.runCommand("item replace entity @a weapon.offhand with minecraft:cobblestone 3");
		// Half a second before each stack goes.
		context.waitTicks(5);
		check(context.computeOnClient(client -> client.player.getInventory().countItem(Items.COBBLESTONE) == 20 + 64 + 3), "Nothing should be dropped straight away");
		context.waitFor(client -> client.player.getInventory().countItem(Items.COBBLESTONE) == 0 && client.player.getOffhandItem().isEmpty(), TIMEOUT_TICKS);
		context.waitTicks(5);
		check(server.computeOnServer(minecraftServer -> minecraftServer.getPlayerList().getPlayers().getFirst().getInventory().countItem(Items.COBBLESTONE)) == 0,
				"The server should have the cobblestone dropped");
		int dropped = server.computeOnServer(minecraftServer -> minecraftServer.overworld()
				.getEntities(net.minecraft.world.entity.EntityTypes.ITEM, new net.minecraft.world.phys.AABB(-8, 90, -8, 30, 110, 30), item -> item.getItem().is(Items.COBBLESTONE))
				.stream().mapToInt(item -> item.getItem().getCount()).sum());
		check(dropped == 20 + 64 + 3, "All the cobblestone should be on the ground: " + dropped);
		check(context.computeOnClient(client -> client.player.getInventory().countItem(Items.DIRT) == 5 && client.player.getInventory().getItem(0).is(Items.IRON_SWORD)),
				"Items not listed should stay");
		check(context.computeOnClient(client -> client.player.getItemBySlot(EquipmentSlot.HEAD).is(Items.LEATHER_HELMET)), "Worn armor should stay, even when listed");

		// Not while a screen is open.
		context.runOnClient(client -> client.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(client.player)));
		server.runCommand("give @a minecraft:cobblestone 4");
		context.waitTicks(10);
		check(context.computeOnClient(client -> client.player.getInventory().countItem(Items.COBBLESTONE) == 4), "Nothing should be dropped while a screen is open");
		context.runOnClient(client -> client.gui.setScreen(null));
		context.waitFor(client -> client.player.getInventory().countItem(Items.COBBLESTONE) == 0, TIMEOUT_TICKS);
		// The server too, so that more given next doesn't join a stack it's still about to drop.
		context.waitTicks(5);
		check(server.computeOnServer(minecraftServer -> minecraftServer.getPlayerList().getPlayers().getFirst().getInventory().countItem(Items.COBBLESTONE)) == 0,
				"The server should have dropped the cobblestone after the screen closed");

		// Not while moving: only once standing still for half a second.
		server.runCommand("tp @a 8.5 100 8.5 -90 0");
		server.runCommand("give @a minecraft:cobblestone 4");
		context.waitFor(client -> client.player.getInventory().countItem(Items.COBBLESTONE) == 4, TIMEOUT_TICKS);

		for (int i = 0; i < 15; i++) {
			context.runOnClient(client -> client.player.setDeltaMovement(0.03, 0, 0));
			context.waitTicks(1);
		}

		check(context.computeOnClient(client -> client.player.getInventory().countItem(Items.COBBLESTONE) == 4), "Nothing should be dropped while moving");
		context.waitFor(client -> client.player.getInventory().countItem(Items.COBBLESTONE) == 0, TIMEOUT_TICKS);
		context.waitTicks(5);
		server.runCommand("tp @a 8.5 100 8.5 -90 0");

		// Turned off, listed items stay.
		context.runOnClient(client -> JuiceClient.CONFIG.autoDrop = false);
		server.runCommand("give @a minecraft:cobblestone 4");
		context.waitTicks(10);
		check(context.computeOnClient(client -> client.player.getInventory().countItem(Items.COBBLESTONE) == 4), "Auto drop shouldn't act while off");

		context.runOnClient(client -> {
			JuiceClient.CONFIG.autoDrop = true;
			JuiceClient.CONFIG.clearAutoDropItems();
		});
		server.runCommand("kill @e[type=minecraft:item]");
		server.runCommand("clear @a");
		server.runCommand("gamemode spectator @a");
		server.runCommand("setblock 8 99 8 minecraft:air");
		context.waitTicks(5);
	}

	private static int serverCount(TestServerContext server, net.minecraft.world.item.Item item) {
		return server.computeOnServer(minecraftServer -> minecraftServer.getPlayerList().getPlayers().getFirst().getInventory().countItem(item));
	}

	private static void testAntiDrop(ClientGameTestContext context, TestServerContext server) {
		check(context.computeOnClient(client -> JuiceClient.CONFIG.antiDrop && !JuiceClient.CONFIG.hasAntiDropItems()), "Anti drop should start on with nothing listed");
		server.runCommand("setblock 8 99 8 minecraft:barrier");
		server.runCommand("tp @a 8.5 100 8.5 -90 0");
		server.runCommand("gamemode survival @a");
		server.runCommand("clear @a");
		server.runCommand("kill @e[type=minecraft:item]");
		server.runCommand("item replace entity @a hotbar.0 with minecraft:diamond_sword");
		server.runCommand("item replace entity @a hotbar.1 with minecraft:dirt 5");
		context.runOnClient(client -> {
			client.player.getInventory().setSelectedSlot(0);
			JuiceClient.CONFIG.setAntiDropItem(Items.DIAMOND_SWORD, true);
		});
		context.waitTicks(5);

		// The drop key, outside a screen.
		check(!context.computeOnClient(client -> client.player.drop(true)), "The drop key shouldn't throw a protected item");
		// The drop key over the slot in the inventory.
		context.runOnClient(client -> client.gameMode.handleContainerInput(client.player.inventoryMenu.containerId, 36, 1, net.minecraft.world.inventory.ContainerInput.THROW, client.player));
		// Clicking outside the inventory with it on the cursor: it stays on the cursor and goes back.
		check(context.computeOnClient(client -> {
			int id = client.player.inventoryMenu.containerId;
			client.gameMode.handleContainerInput(id, 36, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, client.player);
			client.gameMode.handleContainerInput(id, net.minecraft.world.inventory.AbstractContainerMenu.SLOT_CLICKED_OUTSIDE, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, client.player);
			boolean kept = client.player.inventoryMenu.getCarried().is(Items.DIAMOND_SWORD);
			client.gameMode.handleContainerInput(id, 36, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, client.player);
			return kept;
		}), "A protected item should stay on the cursor when clicked outside");
		context.waitTicks(5);
		check(context.computeOnClient(client -> client.player.getInventory().getItem(0).is(Items.DIAMOND_SWORD)), "The protected sword should still be in the hotbar");
		check(serverCount(server, Items.DIAMOND_SWORD) == 1, "The server should still have the sword");

		// Moving it around the inventory is fine.
		context.runOnClient(client -> client.gameMode.handleContainerInput(client.player.inventoryMenu.containerId, 36, 0, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, client.player));
		context.waitTicks(5);
		check(context.computeOnClient(client -> client.player.getInventory().getItem(0).isEmpty() && client.player.getInventory().countItem(Items.DIAMOND_SWORD) == 1),
				"A protected item should still move around the inventory");

		// Items not listed still drop.
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(1));
		context.waitTicks(2);
		context.runOnClient(client -> client.player.drop(true));
		context.waitTicks(5);
		check(serverCount(server, Items.DIRT) == 0, "Dirt isn't protected and should drop");

		// Anti drop wins over auto drop.
		context.runOnClient(client -> {
			JuiceClient.CONFIG.setAntiDropItem(Items.COBBLESTONE, true);
			JuiceClient.CONFIG.setAutoDropItem(Items.COBBLESTONE, true);
		});
		server.runCommand("give @a minecraft:cobblestone 8");
		context.waitTicks(AutoDrop.DROP_DELAY_TICKS * 2 + 5);
		check(serverCount(server, Items.COBBLESTONE) == 8, "Auto drop shouldn't throw protected items");

		// Turned off, it drops.
		context.runOnClient(client -> {
			JuiceClient.CONFIG.antiDrop = false;
			JuiceClient.CONFIG.clearAutoDropItems();
		});
		server.runCommand("item replace entity @a hotbar.1 with minecraft:diamond_sword");
		context.waitTicks(5);
		context.runOnClient(client -> client.player.drop(true));
		context.waitTicks(5);
		check(serverCount(server, Items.DIAMOND_SWORD) == 1, "Anti drop shouldn't act while off");

		context.runOnClient(client -> {
			JuiceClient.CONFIG.antiDrop = true;
			JuiceClient.CONFIG.clearAntiDropItems();
		});
		server.runCommand("kill @e[type=minecraft:item]");
		server.runCommand("clear @a");
		server.runCommand("gamemode spectator @a");
		server.runCommand("setblock 8 99 8 minecraft:air");
		context.waitTicks(5);
	}

	private static void testQuickUse(ClientGameTestContext context, TestServerContext server) {
		check(context.computeOnClient(client -> JuiceClient.CONFIG.quickUseAction == QuickUseAction.ENDER_PEARL), "Quick use should start on ender pearls");
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

		// Some servers put the pearl back in hand right after it's thrown: the sword comes back again.
		context.runOnClient(client -> {
			HeldUse.use(client, 2);
			client.player.getInventory().setSelectedSlot(2);
		});
		context.waitTicks(3);
		check(context.computeOnClient(client -> client.player.getInventory().getSelectedSlot() == 0), "The sword should come back when the server puts the pearl back");
		check(server.computeOnServer(minecraftServer -> minecraftServer.getPlayerList().getPlayers().getFirst().getInventory().getSelectedSlot()) == 0, "The server should end up on the sword");
		context.waitTicks(25);

		// A potion is drunk through, then the sword is back.
		context.runOnClient(client -> JuiceClient.CONFIG.quickUseAction = QuickUseAction.POTION);
		context.getInput().pressKey(InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_4));
		context.waitFor(client -> client.player.getInventory().getItem(3).is(Items.GLASS_BOTTLE), TIMEOUT_TICKS);
		context.waitTicks(5);
		check(context.computeOnClient(client -> client.player.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION)), "The potion should have been drunk");
		check(context.computeOnClient(client -> client.player.getInventory().getSelectedSlot() == 0), "The sword should be back after the potion");

		// An enchanted golden apple is eaten, though auto eat never would.
		context.runOnClient(client -> JuiceClient.CONFIG.quickUseAction = QuickUseAction.ENCHANTED_APPLE);
		context.getInput().pressKey(InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_4));
		context.waitFor(client -> client.player.getInventory().getItem(4).getCount() == 3, TIMEOUT_TICKS);
		context.waitTicks(5);
		check(context.computeOnClient(client -> client.player.getAbsorptionAmount() > 0F), "The apple should have been eaten");
		check(context.computeOnClient(client -> client.player.getInventory().getSelectedSlot() == 0), "The sword should be back after the apple");

		// With none in the hotbar, nothing happens.
		context.runOnClient(client -> JuiceClient.CONFIG.quickUseAction = QuickUseAction.POTION);
		context.getInput().pressKey(InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_4));
		context.waitTicks(5);
		check(context.computeOnClient(client -> !HeldUse.isActive() && client.player.getInventory().getSelectedSlot() == 0), "Nothing should be used without a potion");

		context.runOnClient(client -> JuiceClient.CONFIG.quickUseAction = QuickUseAction.ENDER_PEARL);
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
		int delay = context.computeOnClient(client -> JuiceClient.CONFIG.smartToolDelay);

		for (int tick = 0; tick < delay; tick++) {
			context.waitTicks(1);
			context.runOnClient(client -> client.gameMode.continueDestroyBlock(pos, net.minecraft.core.Direction.WEST));
		}

		context.waitTicks(1);
	}

	private static void testDroppedItems(ClientGameTestContext context, TestServerContext server) {
		check(context.computeOnClient(client -> !JuiceClient.CONFIG.showItems), "Item highlights should start off");
		context.runOnClient(client -> JuiceClient.CONFIG.mobWarning = false);

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
			check(JuiceClient.DROPPED_ITEMS.isMobDrop(diamonds), "Diamonds by a dead zombie should count as its drop");
			check(!JuiceClient.DROPPED_ITEMS.isMobDrop(stick), "A stick far from any death shouldn't");

			Vec3 camera = new Vec3(8.5, 101.6, 8.5);
			List<String> all = JuiceClient.DROPPED_ITEMS.marks(client.level, ItemMode.ALL, camera, 0F, 100).stream().map(DroppedItems.ItemMark::label).toList();
			List<String> drops = JuiceClient.DROPPED_ITEMS.marks(client.level, ItemMode.MOB_DROPS, camera, 0F, 100).stream().map(DroppedItems.ItemMark::label).toList();
			check(all.contains("Diamond x3") && all.contains("Stick"), "All items should be shown, with counts: " + all);
			check(drops.contains("Diamond x3") && !drops.contains("Stick"), "Only mob drops should be shown: " + drops);
			JuiceClient.CONFIG.showItems = true;
		});
		context.waitTicks(5);
		context.takeScreenshot("juiceclient_items_all");
		// Shown items are outlined along their shape, like glowing, rather than boxed.
		context.runOnClient(client -> check(JuiceClientRenderer.outlineColor(findItem(client, Items.STICK)) == (JuiceClient.CONFIG.itemArgb() & 0xFFFFFF),
				"A shown item should be outlined in the item color"));
		context.runOnClient(client -> JuiceClient.CONFIG.itemMode = ItemMode.MOB_DROPS);
		context.waitTicks(5);
		context.takeScreenshot("juiceclient_items_mob_drops");
		context.runOnClient(client -> check(!JuiceClientRenderer.isOutlined(findItem(client, Items.STICK))
				&& JuiceClientRenderer.isOutlined(findItem(client, Items.DIAMOND)), "Only mob drops should be outlined"));

		check(context.computeOnClient(client -> splashText(ModuleSplash.groups(JuiceClient.CONFIG, false)).stream().anyMatch(line -> line.startsWith("Items & Tags: Item Highlights (Mob Drops)"))), "Splash should list item highlights");
		context.runOnClient(client -> {
			JuiceClient.CONFIG.showItems = false;
			JuiceClient.CONFIG.itemMode = ItemMode.ALL;
			JuiceClient.CONFIG.mobWarning = true;
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
		int dings = context.computeOnClient(client -> JuiceClient.MOB_WARNING.dings());
		server.runCommand("summon minecraft:zombified_piglin 18.5 100 8.5 {NoAI:1b,NoGravity:1b,Tags:[\"warn\"]}");
		server.runCommand("summon minecraft:piglin 8.5 100 18.5 {NoAI:1b,NoGravity:1b,IsImmuneToZombification:1b,Tags:[\"warn\"]}");
		context.waitTicks(40);

		// Passive, they come closer without a warning.
		server.runCommand("tp @e[type=minecraft:zombified_piglin,tag=warn] 15.5 100 8.5");
		server.runCommand("tp @e[type=minecraft:piglin,tag=warn] 8.5 100 15.5");
		context.waitTicks(20);
		check(context.computeOnClient(client -> JuiceClient.MOB_WARNING.dings()) == dings, "Passive piglins shouldn't warn");

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
		context.waitFor(client -> JuiceClient.MOB_WARNING.dings() == dings + 1, TIMEOUT_TICKS);
		String message = context.computeOnClient(client -> JuiceClient.MOB_WARNING.lastMessage().getString());
		check(message.startsWith("\u26a0 Zombified Piglin approaching"), "Wrong warning: " + message);

		server.runCommand("kill @e[tag=warn]");
		context.waitTicks(5);
	}

	private static void testMobWarning(ClientGameTestContext context, TestServerContext server) {
		check(context.computeOnClient(client -> JuiceClient.CONFIG.mobWarning && JuiceClient.CONFIG.mobWarningDistance == 10), "Mob warning should start on, at 10 blocks");
		check(MobWarning.isApproaching(8, 7.5) && !MobWarning.isApproaching(8, 8) && !MobWarning.isApproaching(Double.NaN, 5), "Wrong idea of approaching");
		server.runCommand("difficulty normal");
		int dings = context.computeOnClient(client -> JuiceClient.MOB_WARNING.dings());

		// A zombie and a cow appear nearby while the player stands still. Appearing isn't approaching.
		server.runCommand("summon minecraft:zombie 16.5 100 8.5 {NoAI:1b,NoGravity:1b,Tags:[\"warn\"]}");
		server.runCommand("summon minecraft:cow 8.5 100 16.5 {NoAI:1b,NoGravity:1b,Tags:[\"warn\"]}");
		context.waitTicks(40);
		check(context.computeOnClient(client -> JuiceClient.MOB_WARNING.dings()) == dings, "A mob just appearing shouldn't warn");

		// The cow coming closer is fine; the zombie isn't.
		server.runCommand("tp @e[type=minecraft:cow,tag=warn] 8.5 100 13.5");
		context.waitTicks(20);
		check(context.computeOnClient(client -> JuiceClient.MOB_WARNING.dings()) == dings, "A cow shouldn't warn");
		server.runCommand("tp @e[type=minecraft:zombie,tag=warn] 14.5 100 8.5");
		context.waitFor(client -> JuiceClient.MOB_WARNING.dings() == dings + 1, TIMEOUT_TICKS);
		String message = context.computeOnClient(client -> JuiceClient.MOB_WARNING.lastMessage().getString());
		// The zombie slides over to where it was teleported, so the warning can come as it passes 7m.
		check(message.equals("\u26a0 Zombie approaching (6m)") || message.equals("\u26a0 Zombie approaching (7m)"), "Wrong warning: " + message);
		context.takeScreenshot("juiceclient_mob_warning");

		// Coming closer still doesn't ding again for the same zombie.
		server.runCommand("tp @e[type=minecraft:zombie,tag=warn] 12.5 100 8.5");
		context.waitTicks(20);
		check(context.computeOnClient(client -> JuiceClient.MOB_WARNING.dings()) == dings + 1, "The same zombie shouldn't ding twice");

		// Gone out of range and back, it warns again once the ding has cooled down.
		server.runCommand("tp @e[type=minecraft:zombie,tag=warn] 30.5 100 8.5");
		context.waitTicks(80);
		server.runCommand("tp @e[type=minecraft:zombie,tag=warn] 17.5 100 8.5");
		context.waitTicks(10);
		server.runCommand("tp @e[type=minecraft:zombie,tag=warn] 15.5 100 8.5");
		context.waitFor(client -> JuiceClient.MOB_WARNING.dings() == dings + 2, TIMEOUT_TICKS);

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
		check(context.computeOnClient(client -> JuiceClient.MOB_WARNING.dings()) == dings + 2, "Shouldn't warn while moving");

		server.runCommand("kill @e[tag=warn]");
		server.runCommand("tp @a 8.5 100 8.5 -90 0");
		context.waitTicks(5);
	}

	private static void testXRay(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
		check(context.computeOnClient(client -> !JuiceClient.CONFIG.xrayEnabled), "X-Ray should start off");

		// Every default id has to exist, since unknown ids are ignored quietly.
		check(context.computeOnClient(client -> JuiceClient.CONFIG.xrayBlocks().size() == JuiceClient.CONFIG.xrayBlockIds().size()), "Unknown block in X-Ray's defaults");

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
		context.takeScreenshot("juiceclient_xray_off");

		BlockPos diamond = new BlockPos(0, 100, -38);
		BlockPos stone = new BlockPos(0, 100, -37);
		BlockPos chest = new BlockPos(4, 96, -38);
		check(context.computeOnClient(client -> regionState(client, stone).is(Blocks.STONE)), "Meshes should see the stone with X-Ray off");

		// X turns it on even though vanilla's Load Hotbar Activator is on X too, and chunk meshes then only see the
		// chosen blocks.
		check(context.computeOnClient(client -> client.options.keyLoadHotbarActivator.saveString().equals("key.keyboard.x")), "Vanilla should still have X bound");
		context.getInput().holdKeyFor(GLFW.GLFW_KEY_X, 3);
		context.waitFor(client -> JuiceClient.CONFIG.xrayEnabled && XRay.isActive(), TIMEOUT_TICKS);
		context.runOnClient(client -> {
			check(regionState(client, stone).isAir(), "Stone should be hidden from meshes");
			check(regionState(client, chest).isAir(), "A chest that isn't chosen should be hidden");
			check(regionState(client, diamond).is(Blocks.DIAMOND_ORE), "Diamond ore should stay");
			// Only meshes: the world itself is untouched.
			check(client.level.getBlockState(stone).is(Blocks.STONE), "X-Ray shouldn't change the world");
		});
		connection.waitForChunksRender();
		context.waitTicks(20);
		context.takeScreenshot("juiceclient_xray_on");

		// The splash lists it, and turning the mod off turns it off too.
		check(context.computeOnClient(client -> splashText(ModuleSplash.groups(JuiceClient.CONFIG, false)).stream().anyMatch(line -> line.startsWith("View: X-Ray"))), "Splash should list X-Ray");
		context.runOnClient(client -> JuiceClient.CONFIG.enabled = false);
		context.waitFor(client -> !XRay.isActive(), TIMEOUT_TICKS);
		check(context.computeOnClient(client -> regionState(client, stone).is(Blocks.STONE)), "X-Ray should be off with the mod");
		context.runOnClient(client -> JuiceClient.CONFIG.enabled = true);
		context.waitFor(client -> XRay.isActive(), TIMEOUT_TICKS);

		// Choosing a block through the screen shows it straight away.
		context.setScreen(() -> new JuiceClientXRayBlocksScreen(null));
		context.waitTicks(5);
		context.takeScreenshot("juiceclient_xray_blocks");
		context.getInput().typeChars("Chest");
		context.waitTicks(5);
		context.getInput().setCursorPos(427, 100);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitFor(client -> JuiceClient.CONFIG.xrayBlocks().contains(Blocks.CHEST), TIMEOUT_TICKS);
		context.setScreen(() -> null);
		context.waitFor(client -> regionState(client, chest).is(Blocks.CHEST), TIMEOUT_TICKS);
		connection.waitForChunksRender();
		context.waitTicks(20);
		context.takeScreenshot("juiceclient_xray_chest");

		// Kept on reload.
		context.runOnClient(client -> {
			JuiceClientConfig reloaded = JuiceClientConfig.load(FabricLoader.getInstance().getConfigDir());
			check(reloaded.xrayEnabled && reloaded.xrayBlocks().contains(Blocks.CHEST), "X-Ray settings lost on reload");
			JuiceClient.CONFIG.resetXrayBlocks();
			check(!JuiceClient.CONFIG.xrayBlocks().contains(Blocks.CHEST), "Reset should drop the chest");
		});

		// Held down, it only toggles once.
		context.getInput().holdKeyFor(GLFW.GLFW_KEY_X, 20);
		context.waitTicks(5);
		check(context.computeOnClient(client -> !JuiceClient.CONFIG.xrayEnabled && !XRay.isActive()), "Holding X should turn X-Ray off once");
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
		boolean toggledWithConflict = context.computeOnClient(client -> !JuiceClient.CONFIG.enabled);
		String screenWithConflict = context.computeOnClient(client -> String.valueOf(client.gui.screen()));
		JuiceClient.LOGGER.info("L with default Advancements binding: toggled={}, screen={}", toggledWithConflict, screenWithConflict);
		context.setScreen(() -> null);
		context.runOnClient(client -> {
			JuiceClient.CONFIG.enabled = true;
			// Advancements unbound, so L belongs to this mod alone.
			client.options.keyAdvancements.setKey(InputConstants.UNKNOWN);
			KeyMapping.resetMapping();
		});
		context.waitTicks(5);

		context.getInput().pressKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_L));
		context.waitFor(client -> !JuiceClient.CONFIG.enabled, TIMEOUT_TICKS);
		context.getInput().pressKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_L));
		context.waitFor(client -> JuiceClient.CONFIG.enabled, TIMEOUT_TICKS);
	}

	private static void testBlockHighlightFade() {
		// Gone right next to a block, and full strength a little way off.
		long pos = BlockPos.asLong(10, 100, 10);
		check(JuiceClientRenderer.blockDistance(pos, new Vec3(10.5, 100.5, 10.5)) == 0, "Inside a block should be no distance");
		check(JuiceClientRenderer.blockDistance(pos, new Vec3(12.5, 100.5, 10.5)) == 1.5, "Distance should be to the block's side");
		check(JuiceClientRenderer.nearStrength(JuiceClientRenderer.blockDistance(pos, new Vec3(12.5, 101.6, 10.5))) == 0F, "A block right next to the camera should be hidden");
		check(JuiceClientRenderer.nearStrength(3.5) == 0.5F, "Halfway should be half strength");
		check(JuiceClientRenderer.nearStrength(JuiceClientRenderer.blockDistance(pos, new Vec3(20.5, 100.5, 10.5))) == 1F, "A block further off should show fully");
	}

	private static void testModMenu(ClientGameTestContext context) {
		context.setScreen(() -> new ModsScreen(null));
		context.waitTicks(5);
		context.getInput().typeChars("Juice Client");
		context.waitTicks(5);
		context.getInput().setCursorPos(60, 66);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(5);
		context.takeScreenshot("juiceclient_modmenu");

		// Opened through the factory Mod Menu uses, so this also proves the entrypoint works.
		context.setScreen(() -> new JuiceClientModMenu().getModConfigScreenFactory().create(null));
		context.waitTicks(5);
		context.takeScreenshot("juiceclient_settings");

		// clickScreenButton can't reach widgets nested in an options list, so click the Enabled button directly.
		context.getInput().setCursorPos(265, 93);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitFor(client -> !JuiceClient.CONFIG.enabled, TIMEOUT_TICKS);

		context.setScreen(() -> null);
		context.waitTicks(1);
		assertConfigSaved();

		// The rest of the screen, with an invalid color so the swatch's error state shows.
		String playerColor = JuiceClient.CONFIG.playerColor;
		JuiceClient.CONFIG.playerColor = "nope";
		context.setScreen(() -> new JuiceClientModMenu().getModConfigScreenFactory().create(null));
		context.waitTicks(5);
		JuiceClient.CONFIG.playerColor = playerColor;

		// The whole screen, a page at a time, to show its sections.
		for (int page = 1; page <= 4; page++) {
			context.getInput().setCursorPos(427, 240);
			context.getInput().scroll(-3.5);
			context.waitTicks(5);
			context.takeScreenshot("juiceclient_settings_page_" + page);
		}

		context.getInput().scroll(-20);
		context.waitTicks(5);
		context.takeScreenshot("juiceclient_settings_bottom");
		context.setScreen(() -> null);

		context.runOnClient(client -> {
			JuiceClient.CONFIG.enabled = true;
			JuiceClient.CONFIG.save();
		});
	}

	private static void assertConfigSaved() {
		try {
			String saved = Files.readString(FabricLoader.getInstance().getConfigDir().resolve("juiceclient.properties"));

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

		context.setScreen(() -> new JuiceClientExcludedBlocksScreen(null));
		context.waitTicks(5);
		context.takeScreenshot("juiceclient_blocks");

		// Only one row matches, and the search box starts focused. Rescanning happens when the screen closes.
		context.getInput().typeChars("Soul Lantern");
		context.waitTicks(5);
		context.takeScreenshot("juiceclient_blocks_search");
		context.getInput().setCursorPos(427, 100);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(5);
		context.takeScreenshot("juiceclient_blocks_excluded");
		context.setScreen(() -> null);
		awaitSource(context, pos, false);

		context.setScreen(() -> new JuiceClientExcludedBlocksScreen(null));
		context.waitTicks(5);
		context.clickScreenButton("options.juiceclient.resetDefaults");
		context.setScreen(() -> null);
		awaitSource(context, pos, true);

		// One click on a merged row must cover the wall variant too.
		context.computeOnClient(client -> {
			JuiceClientExcludedBlocksScreen.lightEmittingBlocksByName().forEach((name, blocks) -> {
				if (blocks.size() > 1) {
					JuiceClient.LOGGER.info("Merged row '{}': {}", name, blocks);
				}
			});
			check(JuiceClientExcludedBlocksScreen.lightEmittingBlocksByName().get("Torch").contains(Blocks.WALL_TORCH), "Wall torch not merged into Torch");
			return null;
		});
		context.setScreen(() -> new JuiceClientExcludedBlocksScreen(null));
		context.waitTicks(5);
		context.getInput().typeChars("Soul Torch");
		context.waitTicks(5);
		context.takeScreenshot("juiceclient_blocks_merged");
		context.getInput().setCursorPos(427, 100);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitFor(client -> JuiceClient.CONFIG.isExcluded(Blocks.SOUL_TORCH), TIMEOUT_TICKS);
		check(JuiceClient.CONFIG.isExcluded(Blocks.SOUL_WALL_TORCH), "Merged row didn't exclude the wall variant");
		context.clickScreenButton("options.juiceclient.resetDefaults");
		context.setScreen(() -> null);
		context.waitTicks(1);

		server.runCommand("setblock 24 100 8 minecraft:air");
		awaitSource(context, pos, false);
	}

	private static void testConfigStorage() {
		try {
			Path dir = Files.createTempDirectory("juiceclient-config");
			Path json = dir.resolve("lighttracer.json");
			Files.writeString(json, """
					{"enabled": false, "tracerPerSource": true, "chunkFillColor": "11223344", "lineWidth": 5.0, "excludedBlocks": ["minecraft:torch"]}
					""");

			// The old JSON file is imported and removed.
			JuiceClientConfig imported = JuiceClientConfig.load(dir);
			check(!imported.enabled && imported.tracerMode == TracerMode.EVERY_SOURCE, "JSON settings not imported");
			check(imported.lineWidth == 5.0F, "JSON line width not imported");
			check(imported.isExcluded(Blocks.TORCH) && !imported.isExcluded(Blocks.LAVA), "JSON excluded blocks not imported");
			check(!Files.exists(json), "JSON file not removed");
			check(Files.exists(dir.resolve("juiceclient.properties")), "Properties file not written");

			// Changes survive a reload, including an empty block list.
			imported.setExcluded(Blocks.TORCH, false);
			imported.playerColor = "AABBCCDD";
			imported.tracerMode = TracerMode.NEAREST_CHUNK;
			imported.freeLookSpeed = 42F;
			imported.labelSize = 120;
			imported.showTracers = false;
			imported.showChunkHighlights = false;
			imported.save();
			JuiceClientConfig reloaded = JuiceClientConfig.load(dir);
			check(!reloaded.enabled && reloaded.tracerMode == TracerMode.NEAREST_CHUNK && reloaded.lineWidth == 5.0F, "Settings lost on reload");
			check(!reloaded.showTracers && !reloaded.showChunkHighlights, "Visibility toggles lost on reload");
			check(reloaded.freeLookSpeed == 42F, "Free look speed lost on reload");
			check(reloaded.labelSize == 120, "Label size lost on reload: " + reloaded.labelSize);
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
			reloaded.autoTotem = false;
			reloaded.nameTags = false;
			reloaded.nameTagRange = 48;
			reloaded.setNameTagged(net.minecraft.world.entity.EntityTypes.ACACIA_BOAT, true);
			reloaded.setNameTagBlock(Blocks.CHEST, true);
			reloaded.autoTotemDelay = 40;
			reloaded.autoTotemHealth = 7;
			reloaded.autoArmor = false;
			reloaded.autoDrop = false;
			reloaded.setAutoDropItem(Items.COBBLESTONE, true);
			reloaded.antiDrop = false;
			reloaded.setAntiDropItem(Items.DIAMOND_SWORD, true);
			reloaded.autoWeapon = false;
			reloaded.autoWeaponReturn = 0;
			reloaded.autoEatResupply = false;
			reloaded.takeAllDelay = 4;
			reloaded.sortButtons = false;
			reloaded.trajectories = false;
			reloaded.trajectoryColor = "FF00FF00";
			reloaded.stackBossBars = false;
			reloaded.armorStatus = false;
			reloaded.sortMode = SortMode.VALUE;
			reloaded.sortDelay = 7;
			reloaded.setSortPriority(Items.DIAMOND, SortPriority.FRONT);
			reloaded.setSortPriority(Items.DIRT, SortPriority.BACK);
			reloaded.setSortPriority(Items.STONE, SortPriority.FRONT);
			reloaded.setSortPriority(Items.STONE, SortPriority.NORMAL);
			reloaded.moveSortGroup(ItemGroup.MOB_DROPS, 0);
			reloaded.putAllHotbar = true;
			reloaded.playerAlerts = false;
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
			JuiceClientConfig withRules = JuiceClientConfig.load(dir);
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
			check(!withRules.autoTotem && withRules.autoTotemDelay == 40 && withRules.autoTotemHealth == 7 && !withRules.autoArmor && !withRules.autoDrop && withRules.isAutoDropListed(Items.COBBLESTONE) && !withRules.isAutoDropListed(Items.DIRT)
					&& !withRules.antiDrop && withRules.isAntiDropListed(Items.DIAMOND_SWORD) && !withRules.isAntiDropListed(Items.DIRT) && !withRules.autoWeapon && withRules.autoWeaponReturn == 0 && !withRules.autoEatResupply, "Auto totem settings lost on reload");
			check(!withRules.nameTags && withRules.nameTagRange == 48 && withRules.isNameTagged(net.minecraft.world.entity.EntityTypes.ACACIA_BOAT)
					&& withRules.nameTagBlocks().contains(Blocks.CHEST) && !withRules.nameTagBlocks().contains(Blocks.STONE), "Name tag settings lost on reload");
			check(!withRules.sortButtons && withRules.sortMode == SortMode.VALUE && withRules.sortDelay == 7
					&& withRules.sortPriority(Items.DIAMOND) == SortPriority.FRONT && withRules.sortPriority(Items.DIRT) == SortPriority.BACK
					&& withRules.sortPriority(Items.STONE) == SortPriority.NORMAL && withRules.sortGroupOrder().getFirst() == ItemGroup.MOB_DROPS
					&& withRules.sortGroupOrder().size() == ItemGroup.values().length, "Sort settings lost on reload");
			check(!withRules.trajectories && withRules.trajectoryArgb() == 0xFF00FF00 && !withRules.stackBossBars && !withRules.armorStatus, "Trajectory, boss bar or armor settings lost on reload");
			check(!withRules.takeAll && withRules.takeAllDelay == 4 && withRules.putAllHotbar
					&& !withRules.playerAlerts
					&& withRules.putAllFilter == PutAllFilter.EXCEPT_LISTED && withRules.isPutAllListed(Items.COBBLESTONE)
					&& !withRules.isPutAllListed(Items.DIRT), "Take all settings lost on reload");
			check(withRules.showPlayers && !withRules.showPlayerTracers && !withRules.showLabelDistance && !withRules.showLabelCoordinates, "Player or label settings lost on reload");
			check(!withRules.showLabels && !withRules.showBlockHighlights && withRules.fillOpacity == 40, "Label, block highlight or opacity setting lost on reload");
			check(withRules.showHostileMobs && withRules.hostileMobArgb() == 0x12345678, "Hostile mob settings lost on reload");
			check(!withRules.isMobHighlighted(EntityTypes.ZOMBIE) && withRules.isMobHighlighted(EntityTypes.COW), "Mob list changes lost on reload");
			check(withRules.isMobHighlighted(EntityTypes.SKELETON) && !withRules.isMobHighlighted(EntityTypes.PIG), "Mobs left alone should keep their default");

			// The Custom rule starts empty, reaches files saved before it existed, and stays gone once deleted.
			BaseRule defaultCustom = BaseRule.defaults().getLast();
			check(defaultCustom.name.equals("Custom") && defaultCustom.blockIds().isEmpty() && defaultCustom.enabled, "Custom rule should start empty and on");
			Path noCustomDir = Files.createTempDirectory("juiceclient-config");
			Files.writeString(noCustomDir.resolve("juiceclient.properties"), "ruleCount=1\nrule.0.name=Redstone\n");
			JuiceClientConfig gainedCustom = JuiceClientConfig.load(noCustomDir);
			check(gainedCustom.rules.stream().anyMatch(rule -> rule.name.equals("Custom")), "Older settings file didn't get the Custom rule");
			gainedCustom.rules.removeIf(rule -> rule.name.equals("Custom"));
			gainedCustom.save();
			check(JuiceClientConfig.load(noCustomDir).rules.stream().noneMatch(rule -> rule.name.equals("Custom")), "Deleted Custom rule came back");

			// Every default rule has a color of its own.
			check(BaseRule.defaults().stream().map(BaseRule::color).distinct().count() == BaseRule.defaults().size(), "Default rules share a color");

			// A rule saved before rules had colors gets its default one, and an unknown rule gets white.
			Path noColorDir = Files.createTempDirectory("juiceclient-config");
			Files.writeString(noColorDir.resolve("juiceclient.properties"), "ruleCount=2\nrule.0.name=Redstone\nrule.1.name=Mine\n");
			JuiceClientConfig noColors = JuiceClientConfig.load(noColorDir);
			check(noColors.rules.stream().filter(rule -> rule.name.equals("Redstone")).findFirst().orElseThrow().colorArgb() == 0xFFFF5555, "Old default rule lost its color");
			check(noColors.rules.stream().filter(rule -> rule.name.equals("Mine")).findFirst().orElseThrow().colorArgb() == 0xFFFFFFFF, "Old custom rule should be white");

			// Settings from when the mod was called Base Tracer carry over, and the old file goes.
			Path baseTracerDir = Files.createTempDirectory("juiceclient-config");
			Files.writeString(baseTracerDir.resolve("basetracer.properties"), "ruleCount=1\nrule.0.name=Redstone\nautoTotemHealth=7\n");
			check(JuiceClientConfig.load(baseTracerDir).autoTotemHealth == 7 && Files.exists(baseTracerDir.resolve("juiceclient.properties"))
					&& !Files.exists(baseTracerDir.resolve("basetracer.properties")), "Base Tracer settings didn't carry over");

			// The light sources rule can't go missing, whatever is in the file.
			Path noLightDir = Files.createTempDirectory("juiceclient-config");
			Files.writeString(noLightDir.resolve("juiceclient.properties"), "ruleCount=0\n");
			check(JuiceClientConfig.load(noLightDir).rules.getFirst().lightSources, "Light sources rule missing");

			// Every id in the default rules has to exist, since unknown ids are ignored quietly.
			for (BaseRule rule : BaseRule.defaults()) {
				check(rule.blocks().size() == rule.blockIds().size(), "Unknown block in default rule " + rule.name + ": " + rule.blockIds());
				check(rule.entityChoiceCount() == rule.entityIds().size(), "Unknown entity in default rule " + rule.name + ": " + rule.entityIds());
			}

			// Files from before entity rules get them, ahead of Custom, once.
			Path noEntityDir = Files.createTempDirectory("juiceclient-config");
			Files.writeString(noEntityDir.resolve("juiceclient.properties"), "ruleCount=2\nrule.0.name=Redstone\nrule.1.name=Custom\ncustomRuleOffered=true\n");
			JuiceClientConfig gainedEntities = JuiceClientConfig.load(noEntityDir);
			List<String> names = gainedEntities.rules.stream().map(rule -> rule.name).toList();
			check(names.equals(List.of("Light Sources", "Redstone", "Player Entities", "Pets & Named Mobs", "Custom")), "Entity rules not added in place: " + names);
			check(gainedEntities.rules.get(0).priority == 1 && gainedEntities.rules.get(1).priority == BaseRule.DEFAULT_PRIORITY, "Older rules should get their default priority");
			gainedEntities.rules.removeIf(rule -> rule.name.equals("Player Entities"));
			gainedEntities.save();
			check(JuiceClientConfig.load(noEntityDir).rules.stream().noneMatch(rule -> rule.name.equals("Player Entities")), "Deleted entity rule came back");

			// With no files at all, the defaults apply.
			Path emptyDir = Files.createTempDirectory("juiceclient-config");
			JuiceClientConfig defaults = JuiceClientConfig.load(emptyDir);
			check(defaults.isExcluded(Blocks.LAVA), "Default exclusions missing");
			check(defaults.showTracers && defaults.showChunkHighlights && defaults.tracerMode == TracerMode.NEAREST_PER_CHUNK, "Wrong default tracer settings");

			// A properties file from before tracerMode keeps its old on/off choice.
			Path oldPropertiesDir = Files.createTempDirectory("juiceclient-config");
			Files.writeString(oldPropertiesDir.resolve("juiceclient.properties"), "tracerPerSource=true\n");
			check(JuiceClientConfig.load(oldPropertiesDir).tracerMode == TracerMode.EVERY_SOURCE, "Old tracerPerSource not migrated");

			// Settings from when the mod was called Light Tracer move to the new file, rules included.
			Path oldNameDir = Files.createTempDirectory("juiceclient-config");
			Files.writeString(oldNameDir.resolve("lighttracer.properties"), "lineWidth=7.0\nruleCount=1\nrule.0.name=Mine\nrule.0.blocks=minecraft:sponge\n");
			JuiceClientConfig renamed = JuiceClientConfig.load(oldNameDir);
			check(renamed.lineWidth == 7.0F && renamed.rules.stream().anyMatch(rule -> rule.name.equals("Mine")), "Settings under the old name not migrated");
			check(!Files.exists(oldNameDir.resolve("lighttracer.properties")), "Old settings file not removed");
			check(JuiceClientConfig.load(oldNameDir).lineWidth == 7.0F, "Migrated settings lost on reload");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void testSectionToggles(ClientGameTestContext context) {
		// Every section has a key, and none is bound until chosen.
		for (SectionToggle section : SectionToggle.values()) {
			check(context.computeOnClient(client -> java.util.Arrays.stream(client.options.keyMappings)
					.anyMatch(key -> key.getName().equals(section.keyName()) && key.isUnbound())), "There should be an unbound key for " + section);
		}

		// Searching the controls for "Juice Client" finds every one of this mod's keys, as Controlling matches names.
		check(context.computeOnClient(client -> java.util.Arrays.stream(client.options.keyMappings)
				.filter(key -> key.getName().startsWith("key.juiceclient."))
				.allMatch(key -> net.minecraft.network.chat.Component.translatable(key.getName()).getString().toLowerCase(java.util.Locale.ROOT).contains("juice client"))),
				"Every key's name should contain \"Juice Client\"");

		// Each section's title shows its key: unbound at first, then whatever it's set to.
		for (SectionToggle section : SectionToggle.values()) {
			check(context.computeOnClient(client -> com.juiceclient.JuiceClientConfigScreen.sectionKey(client.options, "options.juiceclient.section." + section.keyName().substring("key.juiceclient.section.".length())) != null),
					"The " + section + " section should find its key");
		}

		check(context.computeOnClient(client -> com.juiceclient.JuiceClientConfigScreen.sectionKey(client.options, "options.juiceclient.section.xray").getName().equals("key.juiceclient.xray")
				&& com.juiceclient.JuiceClientConfigScreen.sectionKey(client.options, "options.juiceclient.section.appearance") == null), "X-Ray should show its key, and Appearance none");
		check(context.computeOnClient(client -> com.juiceclient.JuiceClientConfigScreen.sectionKeyLabel(
				com.juiceclient.JuiceClientConfigScreen.sectionKey(client.options, "options.juiceclient.section.bases")).getString().equals("[No Key]")), "An unbound section should say so");
		context.runOnClient(client -> {
			KeyMapping bases = com.juiceclient.JuiceClientConfigScreen.sectionKey(client.options, "options.juiceclient.section.bases");
			bases.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_B));
			KeyMapping.resetMapping();
		});
		check(context.computeOnClient(client -> com.juiceclient.JuiceClientConfigScreen.sectionKeyLabel(
				com.juiceclient.JuiceClientConfigScreen.sectionKey(client.options, "options.juiceclient.section.bases")).getString().equals("[B]")), "A bound section should show its key");
		context.setScreen(() -> new JuiceClientModMenu().getModConfigScreenFactory().create(null));
		context.waitTicks(5);
		context.takeScreenshot("juiceclient_section_keys");
		// The Tools section, grouped under its sub-headings.
		for (int i = 1; i <= 3; i++) {
			int step = i;
			context.runOnClient(client -> client.gui.screen().children().stream()
					.filter(child -> child instanceof net.minecraft.client.gui.components.OptionsList)
					.map(child -> (net.minecraft.client.gui.components.OptionsList) child)
					.forEach(list -> list.setScrollAmount(list.maxScrollAmount() - (3 - step) * 140)));
			context.waitTicks(2);
			context.takeScreenshot("juiceclient_tools_" + step);
		}
		context.setScreen(() -> null);
		context.runOnClient(client -> {
			com.juiceclient.JuiceClientConfigScreen.sectionKey(client.options, "options.juiceclient.section.bases").setKey(InputConstants.UNKNOWN);
			KeyMapping.resetMapping();
		});

		// Off remembers what was on, and on brings back just that.
		JuiceClientConfig config = new JuiceClientConfig();
		config.showHostileMobs = false;
		config.mobWarning = true;
		check(!SectionToggle.MOBS.toggle(config) && !config.showHostileMobs && !config.mobWarning, "Mobs should turn off");
		check(SectionToggle.MOBS.toggle(config) && !config.showHostileMobs && config.mobWarning, "Mobs should come back as they were");

		// With nothing remembered, on turns the whole section on.
		config.smartTool = false;
		config.autoEat = false;
		config.takeAll = false;
		config.autoTotem = false;
		config.autoArmor = false;
		config.autoDrop = false;
		config.antiDrop = false;
		config.autoWeapon = false;
		config.sortButtons = false;
		config.trajectories = false;
		check(SectionToggle.TOOLS.toggle(config) && config.smartTool && config.autoEat && config.takeAll && config.autoArmor && config.autoWeapon && config.sortButtons
				&& config.trajectories && config.autoDrop && config.antiDrop, "Tools should all turn on");
		SectionToggle.forgetAll();
	}

	private static void testPlayerAlerts() {
		PlayerAlerts alerts = new PlayerAlerts();
		java.util.UUID steve = java.util.UUID.randomUUID();
		java.util.UUID alex = java.util.UUID.randomUUID();
		check(alerts.update(java.util.Map.of()) == null, "No players, no alert");
		check(alerts.update(java.util.Map.of(steve, "Steve")).getString().equals("Steve came into range"), "Steve arriving should be announced");
		check(alerts.update(java.util.Map.of(steve, "Steve")) == null, "Steve staying shouldn't be announced again");

		java.util.Map<java.util.UUID, String> both = new java.util.LinkedHashMap<>();
		both.put(steve, "Steve");
		both.put(alex, "Alex");
		check(alerts.update(both).getString().equals("Alex came into range"), "Only the new player should be announced");
		check(alerts.update(java.util.Map.of(alex, "Alex")).getString().equals("Steve went out of range"), "Steve leaving should be announced");

		String swap = alerts.update(java.util.Map.of(steve, "Steve")).getString();
		check(swap.equals("Steve came into range  ·  Alex went out of range"), "Arriving and leaving together should share one alert, not: " + swap);
	}

	private static void testColorValidation() {
		assertTrue(JuiceClientConfig.isValidColor("FFFFD000"));
		assertTrue(!JuiceClientConfig.isValidColor("FFD000"));
		assertTrue(!JuiceClientConfig.isValidColor("GGGGGGGG"));
		assertTrue(!JuiceClientConfig.isValidColor(null));
	}

	private static void assertTrue(boolean condition) {
		if (!condition) {
			throw new AssertionError("Color validation failed");
		}
	}

	private static void awaitSource(ClientGameTestContext context, BlockPos pos, boolean expected) {
		try {
			context.waitFor(client -> JuiceClient.TRACKER.isSource(pos) == expected, TIMEOUT_TICKS);
		} catch (AssertionError e) {
			throw new AssertionError(context.computeOnClient(client -> "Expected isSource=%s at %s, client sees %s, hasChunk=%s, trackedChunks=%d, playerAt=%s".formatted(
					expected,
					pos,
					client.level.getBlockState(pos),
					client.level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4),
					JuiceClient.TRACKER.chunkCount(),
					client.player.blockPosition()
			)), e);
		}
	}

	private static void assertNotSource(ClientGameTestContext context, BlockPos pos) {
		if (context.computeOnClient(client -> JuiceClient.TRACKER.isSource(pos))) {
			throw new AssertionError("Should not be tracked: " + pos);
		}
	}
}
