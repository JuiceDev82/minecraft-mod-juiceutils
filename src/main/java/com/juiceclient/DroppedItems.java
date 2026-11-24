package com.juiceclient;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

import it.unimi.dsi.fastutil.ints.Int2BooleanMap;
import it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Finds items lying on the ground, and tells which of them a mob dropped. The game doesn't say who dropped an item,
 * so one counts as a mob's drop when it appears near a mob that died at about the same time.
 */
public final class DroppedItems {
	/** How far from a dying mob its drops can land, in blocks. */
	private static final double DROP_RADIUS = 3.0;
	/** How far apart in ticks a death and its drops can show up, since their packets may arrive in either order. */
	private static final int DROP_WINDOW_TICKS = 20;
	/** Items are small, so their boxes are grown to stay visible from a distance. */
	private static final double BOX_GROWTH = 0.12;
	/** How far above an item its label sits. */
	private static final double LABEL_HEIGHT = 0.35;

	private record Death(Vec3 pos, long tick) {
	}

	public record ItemMark(int entityId, AABB box, Vec3 labelPos, String label) {
	}

	private final Deque<Death> deaths = new ArrayDeque<>();
	private final IntSet dying = new IntOpenHashSet();
	/** When each item was first seen, until it's known whether a mob dropped it. */
	private final Int2LongMap pending = new Int2LongOpenHashMap();
	private final Int2BooleanMap mobDrops = new Int2BooleanOpenHashMap();
	private ClientLevel level;

	public void tick(ClientLevel level) {
		if (level != this.level) {
			this.clear();
			this.level = level;
		}

		if (level == null) {
			return;
		}

		long now = level.getGameTime();
		IntSet seen = new IntOpenHashSet();
		IntSet nowDying = new IntOpenHashSet();
		List<ItemEntity> items = new ArrayList<>();

		// Deaths first, so that drops arriving in the same tick are matched to them.
		for (Entity entity : level.entitiesForRendering()) {
			if (entity instanceof LivingEntity living && living.isDeadOrDying()) {
				nowDying.add(entity.getId());

				if (!this.dying.contains(entity.getId())) {
					this.deaths.addLast(new Death(entity.position(), now));
				}
			} else if (entity instanceof ItemEntity item) {
				items.add(item);
			}
		}

		this.dying.clear();
		this.dying.addAll(nowDying);

		while (!this.deaths.isEmpty() && now - this.deaths.peekFirst().tick() > DROP_WINDOW_TICKS * 2) {
			this.deaths.removeFirst();
		}

		for (ItemEntity item : items) {
			int id = item.getId();
			seen.add(id);

			if (this.mobDrops.containsKey(id)) {
				continue;
			}

			long firstSeen = this.pending.containsKey(id) ? this.pending.get(id) : now;

			if (this.diedNear(item.position(), firstSeen)) {
				this.pending.remove(id);
				this.mobDrops.put(id, true);
			} else if (now - firstSeen >= DROP_WINDOW_TICKS) {
				this.pending.remove(id);
				this.mobDrops.put(id, false);
			} else {
				this.pending.put(id, firstSeen);
			}
		}

		this.pending.keySet().removeIf(id -> !seen.contains(id));
		this.mobDrops.keySet().removeIf(id -> !seen.contains(id));
	}

	private boolean diedNear(Vec3 pos, long tick) {
		for (Death death : this.deaths) {
			if (Math.abs(death.tick() - tick) <= DROP_WINDOW_TICKS && death.pos().distanceToSqr(pos) <= DROP_RADIUS * DROP_RADIUS) {
				return true;
			}
		}

		return false;
	}

	/** Whether a mob dropped this item. Unknown for an item's first second, and false until then. */
	public boolean isMobDrop(ItemEntity item) {
		return this.mobDrops.get(item.getId());
	}

	/** The items to show, nearest first, where they appear this frame. */
	public List<ItemMark> marks(ClientLevel level, ItemMode mode, Vec3 camera, float partialTick, int max) {
		List<ItemEntity> items = new ArrayList<>();

		for (Entity entity : level.entitiesForRendering()) {
			if (entity instanceof ItemEntity item && item.isAlive() && !item.getItem().isEmpty() && (mode == ItemMode.ALL || this.isMobDrop(item))) {
				items.add(item);
			}
		}

		items.sort(Comparator.comparingDouble(item -> item.distanceToSqr(camera)));
		List<ItemMark> marks = new ArrayList<>(Math.min(items.size(), max));

		for (ItemEntity item : items.subList(0, Math.min(items.size(), max))) {
			Vec3 offset = item.getPosition(partialTick).subtract(item.position());
			AABB box = item.getBoundingBox().inflate(BOX_GROWTH).move(offset);
			marks.add(new ItemMark(item.getId(), box, new Vec3(box.getCenter().x, box.maxY + LABEL_HEIGHT, box.getCenter().z), label(item.getItem())));
		}

		return marks;
	}

	public static String label(ItemStack stack) {
		MutableComponent name = stack.getHoverName().copy();
		return stack.getCount() > 1 ? Component.translatable("options.juiceclient.item.count", name, stack.getCount()).getString() : name.getString();
	}

	public void clear() {
		this.deaths.clear();
		this.dying.clear();
		this.pending.clear();
		this.mobDrops.clear();
	}
}
