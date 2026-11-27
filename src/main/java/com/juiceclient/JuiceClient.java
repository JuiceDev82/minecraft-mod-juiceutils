package com.juiceclient;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;

public class JuiceClient implements ClientModInitializer {
	public static final String MOD_ID = "juiceclient";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static final int PRUNE_INTERVAL_TICKS = 20;
	/** Often enough to follow pets around, while staying cheap with thousands of entities. */
	private static final int ENTITY_INTERVAL_TICKS = 10;
	/** How far past the render distance the client keeps chunks; see ClientChunkCache#calculateStorageRange. */
	private static final int CHUNK_STORAGE_MARGIN = 3;
	public static final JuiceClientConfig CONFIG = JuiceClientConfig.load(FabricLoader.getInstance().getConfigDir());
	public static final BaseTracker TRACKER = new BaseTracker(CONFIG);
	public static final JuiceClientRenderer RENDERER = new JuiceClientRenderer(TRACKER, CONFIG);
	public static final ModuleSplash SPLASH = new ModuleSplash();
	public static final MobWarning MOB_WARNING = new MobWarning();
	public static final PlayerAlerts PLAYER_ALERTS = new PlayerAlerts();
	public static final DroppedItems DROPPED_ITEMS = new DroppedItems();
	public static final NameTags NAME_TAGS = new NameTags();

	public static void rescanLoadedChunks() {
		Minecraft client = Minecraft.getInstance();

		if (client.level != null && client.player != null) {
			TRACKER.rescan(client.level, client.player.chunkPosition(), client.options.getEffectiveRenderDistance() + CHUNK_STORAGE_MARGIN);
		}
	}

	/** For screens to call on their way out, so a rule being edited doesn't rescan on every change. */
	public static void applyRuleChanges() {
		if (CONFIG.consumeRulesChanged()) {
			CONFIG.save();
			rescanLoadedChunks();
		}
	}

	/** Whether the X-Ray key was down at the last tick, for {@link #sharedKeyPressed}. */
	private static boolean xrayKeyWasDown;

	/**
	 * Whether a key was just pressed, even when a vanilla binding shares it. The game only passes a shared key to
	 * one of its bindings, and X, X-Ray's default, is also vanilla's Load Hotbar Activator. So a keyboard key is also
	 * read directly, while no screen is open so that typing doesn't count.
	 */
	private static boolean sharedKeyPressed(Minecraft client, KeyMapping mapping) {
		boolean clicked = false;

		while (mapping.consumeClick()) {
			clicked = true;
		}

		InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(mapping);
		boolean down = key.getType() == InputConstants.Type.KEYSYM && key.getValue() != InputConstants.UNKNOWN.getValue()
				&& client.gui.screen() == null && InputConstants.isKeyDown(client.getWindow(), key.getValue());
		boolean pressed = down && !xrayKeyWasDown;
		xrayKeyWasDown = down;
		return clicked || pressed;
	}

	@Override
	public void onInitializeClient() {
		SettingsTransfer.migrateOldName();
		ClientChunkEvents.CHUNK_LOAD.register(TRACKER::chunkLoaded);
		ClientChunkEvents.CHUNK_UNLOAD.register(TRACKER::chunkUnloaded);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> TRACKER.clear());

		LevelExtractionEvents.END_EXTRACTION.register(RENDERER::extract);
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "labels"), (graphics, deltaTracker) -> RENDERER.renderLabels(graphics));
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "splash"), (graphics, deltaTracker) -> SPLASH.render(graphics));
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "armor_status"), (graphics, deltaTracker) -> ArmorStatus.render(graphics));
		// The armor slots show what the armor bar did, and more.
		HudElementRegistry.replaceElement(net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.ARMOR_BAR, armorBar -> (graphics, deltaTracker) -> {
			if (!ArmorStatus.replacesArmorBar()) {
				armorBar.extractRenderState(graphics, deltaTracker);
			}
		});

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		KeyMapping toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.juiceclient.toggle",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_L,
				category
		));

		KeyMapping freeLookKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.juiceclient.freeLook",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_J,
				category
		));

		KeyMapping xrayKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.juiceclient.xray",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_X,
				category
		));

		KeyMapping quickUseKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.juiceclient.quickUse",
				InputConstants.Type.MOUSE,
				GLFW.GLFW_MOUSE_BUTTON_4,
				category
		));

		java.util.Map<SectionToggle, KeyMapping> sectionKeys = new java.util.EnumMap<>(SectionToggle.class);

		for (SectionToggle section : SectionToggle.values()) {
			sectionKeys.put(section, KeyMappingHelper.registerKeyMapping(new KeyMapping(
					section.keyName(),
					InputConstants.Type.KEYSYM,
					InputConstants.UNKNOWN.getValue(),
					category
			)));
		}

		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			TakeAll.addButton(screen);
			Sorter.addButtons(screen);
		});
		ClientTickEvents.START_CLIENT_TICK.register(FreeLook::tick);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (freeLookKey.consumeClick()) {
				FreeLook.toggle(client);

				if (client.player != null) {
					client.player.sendOverlayMessage(Component.translatable(FreeLook.isActive() ? "message.juiceclient.freeLook.enabled" : "message.juiceclient.freeLook.disabled"));
				}
			}

			if (client.level != null && client.level.getGameTime() % PRUNE_INTERVAL_TICKS == 0) {
				TRACKER.pruneUnloaded(client.level);
			}

			if (client.level != null && client.level.getGameTime() % ENTITY_INTERVAL_TICKS == 0) {
				TRACKER.updateEntities(client.level, client.level.entitiesForRendering());
			}

			if (sharedKeyPressed(client, xrayKey)) {
				CONFIG.xrayEnabled = !CONFIG.xrayEnabled;
				CONFIG.save();

				if (client.player != null) {
					String key = !CONFIG.xrayEnabled ? "message.juiceclient.xray.disabled" : CONFIG.enabled ? "message.juiceclient.xray.enabled" : "message.juiceclient.xray.modOff";
					client.player.sendOverlayMessage(Component.translatable(key));
				}
			}

			SPLASH.tick(client);
			MOB_WARNING.tick(client);
			PLAYER_ALERTS.tick(client);
			DROPPED_ITEMS.tick(client.level);
			SmartTool.tick(client);
			HeldUse.tick(client);
			TakeAll.tick(client);
			Sorter.tick(client);
			// The totem first: low on health, it goes in before a meal starts, which some servers won't take a swap during.
			AutoTotem.tick(client);
			AutoEat.tick(client);
			AutoArmor.tick(client);
			AutoDrop.tick(client);
			AutoWeapon.tick(client);
			AutoClutch.tick(client);
			NAME_TAGS.tick(client);

			while (quickUseKey.consumeClick()) {
				QuickUse.press(client);
			}

			while (toggleKey.consumeClick()) {
				CONFIG.enabled = !CONFIG.enabled;
				CONFIG.save();

				if (client.player != null) {
					client.player.sendOverlayMessage(Component.translatable(CONFIG.enabled ? "message.juiceclient.enabled" : "message.juiceclient.disabled"));
				}
			}

			sectionKeys.forEach((section, key) -> {
				while (key.consumeClick()) {
					boolean on = section.toggle(CONFIG);
					CONFIG.save();

					if (client.player != null) {
						String message = !on ? "message.juiceclient.section.off" : CONFIG.enabled ? "message.juiceclient.section.on" : "message.juiceclient.section.modOff";
						client.player.sendOverlayMessage(Component.translatable(message, section.label()));
					}
				}
			});

			XRay.update(client);
		});
	}
}
