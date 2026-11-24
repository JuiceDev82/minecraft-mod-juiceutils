package com.juiceclient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import it.unimi.dsi.fastutil.objects.Object2IntMap;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import com.juiceclient.mixin.AbstractContainerScreenAccessor;

/**
 * Sort buttons for storage screens and the player's inventory, leaving the hotbar alone. Sorting goes the way a person
 * would do it by hand: a stack is picked up, carried, and put down where it belongs, or swapped with what's there, a
 * click at a time with a short, uneven wait between clicks. First partial stacks of the same thing are put together,
 * then everything is put in order.
 */
public final class Sorter {
	private static final int SORT_WIDTH = 28;
	private static final int MODE_WIDTH = 12;
	private static final int BUTTON_HEIGHT = 12;
	private static final int GAP = 2;
	/** Take All's width, which the Sort buttons sit to the left of. */
	private static final int TAKE_ALL_WIDTH = 50;

	private static final Random random = new Random();

	/** The slots being sorted, by menu index, in the order they're filled. Empty while not sorting. */
	private static List<Integer> region = List.of();
	private static int containerId = -1;
	/** The move under way: the slot the stack was picked up from and the one it goes to. -1 between moves. */
	private static int from = -1;
	private static int to = -1;
	/** Which click of the move is next: picking up, putting down, then putting back whatever was swapped out. */
	private static int step;
	private static int wait;
	private static final List<Button> modeButtons = new ArrayList<>();

	private Sorter() {
	}

	/** Adds Sort, and the small button choosing the order, beside the storage's title and the inventory's. */
	public static void addButtons(Screen screen) {
		JuiceClientConfig config = JuiceClient.CONFIG;
		modeButtons.clear();

		if (!config.enabled || !config.sortButtons || !(screen instanceof AbstractContainerScreen<?> containerScreen)) {
			return;
		}

		boolean storage = TakeAll.isStorage(containerScreen.getMenu());

		if (!storage && !(screen instanceof InventoryScreen)) {
			return;
		}

		AbstractContainerScreenAccessor panel = (AbstractContainerScreenAccessor) containerScreen;
		List<Button[]> rows = new ArrayList<>();

		if (storage) {
			rows.add(addRow(containerScreen, false));
		}

		rows.add(addRow(containerScreen, true));
		boolean takeAll = storage && config.takeAll;
		Runnable place = () -> {
			int right = panel.juiceclient$leftPos() + panel.juiceclient$imageWidth() - 7 - (takeAll ? TAKE_ALL_WIDTH + GAP : 0);

			for (int i = 0; i < rows.size(); i++) {
				boolean inventory = i == rows.size() - 1;
				int y = panel.juiceclient$topPos() + (inventory ? panel.juiceclient$inventoryLabelY() - 2 : 4);
				rows.get(i)[1].setPosition(right - MODE_WIDTH, y);
				rows.get(i)[0].setPosition(right - MODE_WIDTH - GAP - SORT_WIDTH, y);
			}
		};
		place.run();
		// The inventory's recipe book moves the panel over when opened, and the buttons with it.
		ScreenEvents.afterTick(screen).register(ignored -> place.run());
	}

	private static Button[] addRow(AbstractContainerScreen<?> screen, boolean inventory) {
		Button sort = Button.builder(Component.translatable("options.juiceclient.sort.button"), button -> start(screen, inventory))
				.size(SORT_WIDTH, BUTTON_HEIGHT)
				.build();
		Button mode = Button.builder(JuiceClient.CONFIG.sortMode.symbol(), button -> cycleMode())
				.size(MODE_WIDTH, BUTTON_HEIGHT)
				.tooltip(modeTooltip())
				.build();
		Screens.getWidgets(screen).add(sort);
		Screens.getWidgets(screen).add(mode);
		modeButtons.add(mode);
		return new Button[] {sort, mode};
	}

	private static Tooltip modeTooltip() {
		return Tooltip.create(Component.translatable("options.juiceclient.sortMode.tooltip", JuiceClient.CONFIG.sortMode.label()));
	}

	private static void cycleMode() {
		JuiceClientConfig config = JuiceClient.CONFIG;
		config.sortMode = config.sortMode.next();
		config.save();

		for (Button button : modeButtons) {
			button.setMessage(config.sortMode.symbol());
			button.setTooltip(modeTooltip());
		}
	}

	/** Starts sorting the player's inventory without the hotbar, or the storage's slots. */
	public static void start(AbstractContainerScreen<?> screen, boolean inventory) {
		LocalPlayer player = Minecraft.getInstance().player;
		AbstractContainerMenu menu = screen.getMenu();

		if (player == null || !menu.getCarried().isEmpty()) {
			return;
		}

		List<Integer> slots = new ArrayList<>();

		for (Slot slot : menu.slots) {
			boolean playerSlot = slot.container == player.getInventory();

			if (inventory ? playerSlot && slot.getContainerSlot() >= Inventory.getSelectionSize() && slot.getContainerSlot() < Inventory.INVENTORY_SIZE
					: !playerSlot) {
				slots.add(slot.index);
			}
		}

		region = slots;
		containerId = menu.containerId;
		from = -1;
		wait = 0;
	}

	public static boolean isSorting() {
		return !region.isEmpty();
	}

	private static void stop() {
		region = List.of();
		from = -1;
	}

	public static void tick(Minecraft client) {
		if (region.isEmpty()) {
			return;
		}

		LocalPlayer player = client.player;

		// Closed, or another screen opened: stop. Whatever's on the cursor goes back to the inventory with the screen.
		if (player == null || client.gameMode == null || !(client.gui.screen() instanceof AbstractContainerScreen<?> screen)
				|| screen.getMenu().containerId != containerId) {
			stop();
			return;
		}

		if (wait > 0) {
			wait--;
			return;
		}

		AbstractContainerMenu menu = screen.getMenu();

		if (from < 0) {
			// Something on the cursor between moves was put there by hand: leave it to the player.
			if (!menu.getCarried().isEmpty()) {
				stop();
				return;
			}

			int[] move = nextMove(menu, region, JuiceClient.CONFIG.sortMode);

			if (move == null) {
				stop();
				player.sendOverlayMessage(Component.translatable("message.juiceclient.sort.done").withStyle(ChatFormatting.AQUA));
				return;
			}

			from = move[0];
			to = move[1];
			step = 0;
		}

		if (step == 0) {
			click(client, player, from);
			step = 1;
		} else if (step == 1) {
			click(client, player, to);
			step = 2;

			if (menu.getCarried().isEmpty()) {
				from = -1;
			}
		} else {
			// Whatever was in the way goes where the stack came from.
			click(client, player, from);
			from = -1;
		}

		wait = nextWait(random, JuiceClient.CONFIG.sortDelay);
	}

	private static void click(Minecraft client, LocalPlayer player, int slot) {
		client.gameMode.handleContainerInput(containerId, slot, 0, ContainerInput.PICKUP, player);
	}

	/** Ticks to wait before the next click: one up to the delay, never the same beat twice in a row on purpose. */
	public static int nextWait(Random random, int delay) {
		return delay <= 0 ? 0 : 1 + random.nextInt(delay);
	}

	/**
	 * The next move toward sorted, as the slot to pick up from and the slot to put down in, or null when sorted. Partial
	 * stacks of the same thing are joined first, the later one onto the earlier one; then each slot in turn gets the
	 * stack that belongs there.
	 */
	public static int[] nextMove(AbstractContainerMenu menu, List<Integer> slots, SortMode mode) {
		Map<String, Integer> partials = new HashMap<>();
		List<ItemStack> stacks = new ArrayList<>();

		for (int index : slots) {
			Slot slot = menu.getSlot(index);
			ItemStack stack = slot.getItem();

			if (stack.isEmpty()) {
				continue;
			}

			stacks.add(stack);

			if (stack.getCount() < slot.getMaxStackSize(stack)) {
				Integer earlier = partials.putIfAbsent(variant(stack), index);

				if (earlier != null) {
					return new int[] {index, earlier};
				}
			}
		}

		List<ItemStack> sorted = new ArrayList<>(stacks);
		sorted.sort(order(mode, stacks));

		for (int i = 0; i < slots.size(); i++) {
			ItemStack here = menu.getSlot(slots.get(i)).getItem();
			ItemStack wanted = i < sorted.size() ? sorted.get(i) : ItemStack.EMPTY;

			if (ItemStack.matches(here, wanted)) {
				continue;
			}

			for (int j = i + 1; j < slots.size(); j++) {
				if (ItemStack.matches(menu.getSlot(slots.get(j)).getItem(), wanted)) {
					return new int[] {slots.get(j), slots.get(i)};
				}
			}

			return null;
		}

		return null;
	}

	/** The same item with the same enchantments, name and the like, which stack together. */
	private static String variant(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()) + stack.getComponentsPatch().toString();
	}

	/**
	 * The order a mode puts stacks in, after the items chosen for the front and before those chosen for the back. Ties
	 * fall to type, then the item, so the same things always end up together.
	 */
	public static Comparator<ItemStack> order(SortMode mode, List<ItemStack> stacks) {
		Comparator<ItemStack> byPriority = Comparator.comparingInt(stack -> JuiceClient.CONFIG.sortPriority(stack.getItem()).ordinal());
		return byPriority.thenComparing(modeOrder(mode, stacks));
	}

	private static Comparator<ItemStack> modeOrder(SortMode mode, List<ItemStack> stacks) {
		Comparator<ItemStack> byType = Comparator.<ItemStack>comparingInt(stack -> JuiceClient.CONFIG.sortGroupRank(ItemGroup.of(stack)))
				.thenComparingInt(stack -> BuiltInRegistries.ITEM.getId(stack.getItem()))
				.thenComparing(Sorter::variant)
				.thenComparing(Comparator.comparingInt(ItemStack::getCount).reversed());

		return switch (mode) {
			case TYPE -> byType;
			case COUNT -> {
				Map<Item, Integer> totals = new IdentityHashMap<>();

				for (ItemStack stack : stacks) {
					totals.merge(stack.getItem(), stack.getCount(), Integer::sum);
				}

				yield Comparator.<ItemStack>comparingInt(stack -> -totals.get(stack.getItem())).thenComparing(byType);
			}
			case VALUE -> Comparator.<ItemStack>comparingInt(stack -> -value(stack)).thenComparing(byType);
		};
	}

	/** Roughly what one of an item is worth, in iron ingots. */
	private static final Map<Item, Integer> VALUES = new IdentityHashMap<>();

	static {
		VALUES.put(Items.DRAGON_EGG, 20000);
		VALUES.put(Items.NETHERITE_BLOCK, 9000);
		VALUES.put(Items.BEACON, 5000);
		VALUES.put(Items.NETHER_STAR, 5000);
		VALUES.put(Items.ELYTRA, 4000);
		VALUES.put(Items.ENCHANTED_GOLDEN_APPLE, 3000);
		VALUES.put(Items.HEAVY_CORE, 3000);
		VALUES.put(Items.MACE, 3000);
		VALUES.put(Items.TOTEM_OF_UNDYING, 2500);
		VALUES.put(Items.CONDUIT, 2000);
		VALUES.put(Items.TRIDENT, 1500);
		VALUES.put(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1200);
		VALUES.put(Items.NETHERITE_INGOT, 1000);
		VALUES.put(Items.DIAMOND_BLOCK, 900);
		VALUES.put(Items.EMERALD_BLOCK, 450);
		VALUES.put(Items.ANCIENT_DEBRIS, 400);
		VALUES.put(Items.ENCHANTING_TABLE, 300);
		VALUES.put(Items.NETHERITE_SCRAP, 250);
		VALUES.put(Items.SHULKER_SHELL, 200);
		VALUES.put(Items.GOLDEN_APPLE, 150);
		VALUES.put(Items.END_CRYSTAL, 120);
		VALUES.put(Items.DIAMOND, 100);
		VALUES.put(Items.GOLD_BLOCK, 90);
		VALUES.put(Items.ENDER_CHEST, 80);
		VALUES.put(Items.EMERALD, 50);
		VALUES.put(Items.IRON_BLOCK, 45);
		VALUES.put(Items.EXPERIENCE_BOTTLE, 20);
		VALUES.put(Items.ENDER_PEARL, 20);
		VALUES.put(Items.OBSIDIAN, 15);
		VALUES.put(Items.GOLD_INGOT, 10);
		VALUES.put(Items.LAPIS_BLOCK, 9);
		VALUES.put(Items.REDSTONE_BLOCK, 9);
		VALUES.put(Items.IRON_INGOT, 5);
		VALUES.put(Items.BLAZE_ROD, 5);
	}

	/**
	 * What one of a stack is worth: listed treasures, then gear by its material, then by rarity, plus its enchantments.
	 * Shulker boxes count high, since they usually hold something.
	 */
	public static int value(ItemStack stack) {
		int value = VALUES.getOrDefault(stack.getItem(), 0);

		if (stack.is(ItemTags.SHULKER_BOXES)) {
			value = Math.max(value, 800);
		}

		if (stack.isDamageableItem()) {
			String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
			value = Math.max(value, id.startsWith("netherite_") ? 2000 : id.startsWith("diamond_") ? 400 : id.startsWith("golden_") ? 40
					: id.startsWith("iron_") || id.startsWith("chainmail_") ? 30 : id.startsWith("copper_") ? 10 : 2);
		}

		value = Math.max(value, switch (stack.getRarity()) {
			case EPIC -> 1000;
			case RARE -> 300;
			case UNCOMMON -> 50;
			default -> 0;
		});

		for (ItemEnchantments enchantments : List.of(
				stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY),
				stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY))) {
			for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
				value += entry.getIntValue() * 50;
			}
		}

		return value;
	}
}
