package com.basetracer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

import net.fabricmc.fabric.api.client.screen.v1.Screens;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import com.basetracer.mixin.AbstractContainerScreenAccessor;

/**
 * Take All and Put All buttons on storage screens: chests, barrels, ender chests, shulker boxes, hoppers,
 * dispensers and droppers. They shift-click each slot in turn, sending the server just what shift-clicking them by
 * hand would. Each wait between slots is picked anew, from none up to the chosen delay, so there's no steady beat.
 */
public final class TakeAll {
	private static final int BUTTON_WIDTH = 50;
	private static final int BUTTON_HEIGHT = 12;

	private static final Deque<Integer> queue = new ArrayDeque<>();
	private static int containerId = -1;
	private static int wait;
	private static boolean putting;
	private static final Random random = new Random();

	private TakeAll() {
	}

	public static boolean isStorage(AbstractContainerMenu menu) {
		return menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu || menu instanceof HopperMenu || menu instanceof DispenserMenu;
	}

	/** Adds Take All beside the storage's title, and Put All beside the inventory's, each at the right. */
	public static void addButton(Screen screen) {
		BaseTracerConfig config = BaseTracerClient.CONFIG;

		if (!config.enabled || !config.takeAll || !(screen instanceof AbstractContainerScreen<?> containerScreen) || !isStorage(containerScreen.getMenu())) {
			return;
		}

		AbstractContainerScreenAccessor panel = (AbstractContainerScreenAccessor) containerScreen;
		int x = panel.basetracer$leftPos() + panel.basetracer$imageWidth() - BUTTON_WIDTH - 7;
		Screens.getWidgets(screen).add(Button.builder(Component.translatable("options.basetracer.takeAll.button"), button -> start(containerScreen))
				.bounds(x, panel.basetracer$topPos() + 4, BUTTON_WIDTH, BUTTON_HEIGHT)
				.build());
		Screens.getWidgets(screen).add(Button.builder(Component.translatable("options.basetracer.putAll.button"), button -> startPut(containerScreen))
				.bounds(x, panel.basetracer$topPos() + panel.basetracer$inventoryLabelY() - 2, BUTTON_WIDTH, BUTTON_HEIGHT)
				.build());
	}

	/** Queues the storage's slots that hold something, to move into the inventory. */
	public static void start(AbstractContainerScreen<?> screen) {
		queue(screen, false);
	}

	/**
	 * Queues the inventory's slots that hold something, to move into the storage. The hotbar only if chosen, and only
	 * the items the Put All filter lets through.
	 */
	public static void startPut(AbstractContainerScreen<?> screen) {
		queue(screen, true);
	}

	private static void queue(AbstractContainerScreen<?> screen, boolean put) {
		LocalPlayer player = Minecraft.getInstance().player;

		if (player == null) {
			return;
		}

		AbstractContainerMenu menu = screen.getMenu();
		queue.clear();
		containerId = menu.containerId;
		wait = 0;
		putting = put;
		boolean hotbar = BaseTracerClient.CONFIG.putAllHotbar;

		for (Slot slot : menu.slots) {
			boolean inventory = slot.container == player.getInventory();

			if (!slot.hasItem() || inventory != put) {
				continue;
			}

			if (put && !hotbar && slot.getContainerSlot() < Inventory.getSelectionSize()) {
				continue;
			}

			if (put && !BaseTracerClient.CONFIG.putAllMoves(slot.getItem().getItem())) {
				continue;
			}

			queue.addLast(slot.index);
		}
	}

	public static boolean isTaking() {
		return !queue.isEmpty();
	}

	public static void tick(Minecraft client) {
		if (queue.isEmpty()) {
			return;
		}

		LocalPlayer player = client.player;

		// Closed, or another screen opened: stop.
		if (player == null || client.gameMode == null || !(client.gui.screen() instanceof AbstractContainerScreen<?> screen)
				|| screen.getMenu().containerId != containerId) {
			queue.clear();
			return;
		}

		if (wait > 0) {
			wait--;
			return;
		}

		AbstractContainerMenu menu = screen.getMenu();
		int delay = BaseTracerClient.CONFIG.takeAllDelay;
		int next = 0;

		// A wait of none takes the next slot this same tick; with no delay at all, that's everything.
		do {
			int index = queue.removeFirst();
			Slot slot = menu.getSlot(index);

			if (!slot.hasItem()) {
				continue;
			}

			ItemStack before = slot.getItem().copy();
			client.gameMode.handleContainerInput(containerId, index, 0, ContainerInput.QUICK_MOVE, player);

			// Nothing moved: where it was going is full.
			if (ItemStack.matches(before, slot.getItem())) {
				queue.clear();
				String key = putting ? "message.basetracer.putAll.full" : "message.basetracer.takeAll.full";
				player.sendOverlayMessage(Component.translatable(key).withStyle(ChatFormatting.RED));
				return;
			}

			next = nextWait(random, delay);
		} while (next == 0 && !queue.isEmpty());

		wait = next - 1;
	}

	/** Ticks to wait before the next slot: any from none up to the delay. */
	public static int nextWait(Random random, int delay) {
		return delay <= 0 ? 0 : random.nextInt(delay + 1);
	}
}
