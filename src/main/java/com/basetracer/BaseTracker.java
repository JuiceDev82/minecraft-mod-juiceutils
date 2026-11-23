package com.basetracer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jspecify.annotations.Nullable;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.TraderLlama;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Tracks the positions of the blocks and entities that base rules care about, per chunk. Chunks are scanned once
 * when received and updated from block changes afterwards, so idle frames do no scanning. Entities move, so they're
 * gone through again every so often instead. Only touched on the client thread.
 */
public final class BaseTracker {
	/**
	 * Keeps one rule containing a common block like stone from eating all memory. Past this a chunk has matched
	 * any sensible rule already. It can undercount after removing blocks from such a chunk, until it's next scanned.
	 */
	public static final int MAX_FOUND_PER_CHUNK = 1024;
	public static final int MAX_LABEL_ITEMS = 6;

	private final BaseTracerConfig config;
	private final Long2ObjectOpenHashMap<ChunkFinds> findsByChunk = new Long2ObjectOpenHashMap<>();
	private @Nullable ClientLevel level;
	private int revision;

	public BaseTracker(BaseTracerConfig config) {
		this.config = config;
	}

	public void chunkLoaded(ClientLevel level, LevelChunk chunk) {
		if (this.level != level) {
			this.clear();
			this.level = level;
		}

		ChunkFinds finds = this.scan(chunk);
		long key = chunk.getPos().pack();
		ChunkFinds old = this.findsByChunk.get(key);

		// A block scan knows nothing of entities, which stay until the next entity update.
		if (old != null && !old.entities.isEmpty()) {
			if (finds == null) {
				finds = new ChunkFinds();
			}

			finds.entities = old.entities;
		}

		// Chunks can be resent, so a clean scan must also drop what was there before.
		if (finds == null) {
			if (this.findsByChunk.remove(key) != null) {
				this.revision++;
			}
		} else {
			this.findsByChunk.put(key, finds);
			this.revision++;
		}
	}

	public void chunkUnloaded(ClientLevel level, LevelChunk chunk) {
		if (this.level == level && this.findsByChunk.remove(chunk.getPos().pack()) != null) {
			this.revision++;
		}
	}

	public void blockChanged(ClientLevel level, BlockPos pos, BlockState oldState, BlockState newState) {
		boolean isTracked = this.config.isTracked(newState);

		if (this.level != level || oldState == newState || !isTracked && !this.config.isTracked(oldState)) {
			return;
		}

		long key = ChunkPos.pack(pos);
		ChunkFinds finds = this.findsByChunk.get(key);

		if (isTracked) {
			if (finds == null) {
				finds = new ChunkFinds();
				this.findsByChunk.put(key, finds);
			}

			if (finds.blocks.size() >= MAX_FOUND_PER_CHUNK && !finds.blocks.containsKey(pos.asLong())) {
				return;
			}

			finds.blocks.put(pos.asLong(), newState);
		} else {
			if (finds == null) {
				return;
			}

			finds.blocks.remove(pos.asLong());

			if (finds.blocks.isEmpty() && finds.entities.isEmpty()) {
				this.findsByChunk.remove(key);
			}
		}

		finds.sources = null;
		this.revision++;
	}

	/** Brings the entities of every chunk up to date. They're placed by the block they're in. */
	public void updateEntities(ClientLevel level, Iterable<Entity> entities) {
		if (this.level != level) {
			return;
		}

		Long2ObjectOpenHashMap<List<EntityFind>> found = new Long2ObjectOpenHashMap<>();

		if (this.config.tracksEntities()) {
			for (Entity entity : entities) {
				if (entity instanceof Player || !entity.isAlive()) {
					continue;
				}

				EntityFind find = find(entity);

				if (this.config.isTracked(find)) {
					found.computeIfAbsent(ChunkPos.pack(entity.blockPosition()), key -> new ArrayList<>()).add(find);
				}
			}
		}

		boolean changed = false;

		for (var it = this.findsByChunk.long2ObjectEntrySet().iterator(); it.hasNext(); ) {
			Long2ObjectMap.Entry<ChunkFinds> entry = it.next();
			ChunkFinds finds = entry.getValue();

			if (!finds.entities.isEmpty() && !found.containsKey(entry.getLongKey())) {
				finds.entities = List.of();
				finds.sources = null;
				changed = true;

				if (finds.blocks.isEmpty()) {
					it.remove();
				}
			}
		}

		for (Long2ObjectMap.Entry<List<EntityFind>> entry : found.long2ObjectEntrySet()) {
			ChunkFinds finds = this.findsByChunk.get(entry.getLongKey());

			if (finds == null) {
				finds = new ChunkFinds();
				this.findsByChunk.put(entry.getLongKey(), finds);
			}

			if (!finds.entities.equals(entry.getValue())) {
				finds.entities = entry.getValue();
				finds.sources = null;
				changed = true;
			}
		}

		if (changed) {
			this.revision++;
		}
	}

	/**
	 * Camels count as tamed from birth, so village camels would match too. Only a saddle says a player has
	 * been at one. Wandering traders' llamas belong to the trader.
	 */
	/** What base rules go by for an entity. */
	public static EntityFind find(Entity entity) {
		return new EntityFind(entity.blockPosition().asLong(), entity.getType(), entity.getType().getDescription().getString(), entity.hasCustomName(),
				isTamed(entity));
	}

	private static boolean isTamed(Entity entity) {
		if (entity instanceof Camel camel) {
			return camel.isSaddled();
		}

		return entity instanceof TamableAnimal animal && animal.isTame()
				|| entity instanceof AbstractHorse horse && horse.isTamed() && !(entity instanceof TraderLlama);
	}

	/**
	 * Drops chunks the client no longer holds. The unload event can't be relied on alone: after a long
	 * teleport the view window moves first, and ClientChunkCache#drop ignores chunks outside of it.
	 */
	public void pruneUnloaded(ClientLevel level) {
		if (this.level == level && this.findsByChunk.keySet().removeIf(
				key -> !level.getChunkSource().hasChunk(ChunkPos.getX(key), ChunkPos.getZ(key)))) {
			this.revision++;
		}
	}

	/** Scans everything again after the rules changed. */
	public void rescan(ClientLevel level, ChunkPos center, int radius) {
		this.findsByChunk.clear();
		this.level = level;

		for (int x = center.x() - radius; x <= center.x() + radius; x++) {
			for (int z = center.z() - radius; z <= center.z() + radius; z++) {
				LevelChunk chunk = level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
				ChunkFinds finds = chunk == null ? null : this.scan(chunk);

				if (finds != null) {
					this.findsByChunk.put(ChunkPos.pack(x, z), finds);
				}
			}
		}

		this.revision++;
	}

	public void clear() {
		this.findsByChunk.clear();
		this.level = null;
		this.revision++;
	}

	public int revision() {
		return this.revision;
	}

	public int chunkCount() {
		return this.findsByChunk.size();
	}

	/** Whether this block is part of a match, as opposed to being tracked and waiting for a rule's thresholds. */
	public boolean isSource(BlockPos pos) {
		ChunkFinds finds = this.findsByChunk.get(ChunkPos.pack(pos));
		return finds != null && finds.sources().contains(pos.asLong());
	}

	public boolean isCurrentLevel(@Nullable ClientLevel level) {
		return level != null && this.level == level;
	}

	/** How many tracked chunks each enabled rule matches right now. */
	public Map<String, Integer> matchesPerRule() {
		Map<String, Integer> matches = new LinkedHashMap<>();

		for (BaseRule rule : this.config.rules) {
			if (rule.enabled) {
				int count = 0;

				for (ChunkFinds finds : this.findsByChunk.values()) {
					if (this.ruleMatches(rule, finds)) {
						count++;
					}
				}

				matches.put(rule.name, count);
			}
		}

		return matches;
	}

	public List<ChunkHighlight> snapshot(Vec3 camera, TracerMode mode) {
		return this.snapshot(camera, mode, Integer.MAX_VALUE);
	}

	/**
	 * At most {@code limit} chunks: those whose best rule has the highest priority, and the nearest among equals.
	 * The tracer modes that pick one chunk pick it from these.
	 */
	public List<ChunkHighlight> snapshot(Vec3 camera, TracerMode mode, int limit) {
		List<ChunkHighlight> highlights = new ArrayList<>(this.findsByChunk.size());
		ChunkHighlight nearest = null;
		ChunkHighlight mostSources = null;

		for (Long2ObjectMap.Entry<ChunkFinds> entry : this.findsByChunk.long2ObjectEntrySet()) {
			LongOpenHashSet sources = entry.getValue().sources();

			if (!sources.isEmpty()) {
				highlights.add(toHighlight(entry.getLongKey(), entry.getValue(), camera, mode == TracerMode.EVERY_SOURCE));
			}
		}

		if (highlights.size() > limit) {
			highlights.sort(Comparator.comparingInt(ChunkHighlight::priority).reversed().thenComparingDouble(ChunkHighlight::nearestDistance));
			highlights = new ArrayList<>(highlights.subList(0, limit));
		}

		for (ChunkHighlight highlight : highlights) {
			// Chunks whose rules all have their tracers off can't be the one chunk traced.
			if (highlight.targets().length == 0) {
				continue;
			}

			if (nearest == null || highlight.nearestDistance() < nearest.nearestDistance()) {
				nearest = highlight;
			}

			if (mostSources == null
					|| highlight.sourceCount() > mostSources.sourceCount()
					|| highlight.sourceCount() == mostSources.sourceCount() && highlight.nearestDistance() < mostSources.nearestDistance()) {
				mostSources = highlight;
			}
		}

		if (mode == TracerMode.NEAREST_CHUNK || mode == TracerMode.MOST_SOURCES) {
			ChunkHighlight onlyTarget = mode == TracerMode.NEAREST_CHUNK ? nearest : mostSources;
			highlights.replaceAll(highlight -> highlight == onlyTarget ? highlight : highlight.withoutTargets());
		}

		return highlights;
	}

	private static ChunkHighlight toHighlight(long key, ChunkFinds finds, Vec3 camera, boolean allTargets) {
		LongOpenHashSet sources = finds.sources();
		int minY = Integer.MAX_VALUE;
		int maxY = Integer.MIN_VALUE;
		long nearest = 0L;
		double nearestDistance = Double.MAX_VALUE;

		for (LongIterator it = sources.iterator(); it.hasNext(); ) {
			long pos = it.nextLong();
			int y = BlockPos.getY(pos);
			minY = Math.min(minY, y);
			maxY = Math.max(maxY, y);
			double distance = distanceSqr(pos, camera);

			if (distance < nearestDistance) {
				nearestDistance = distance;
				nearest = pos;
			}
		}

		// Tracers go to the blocks of rules that have them on, in each rule's color.
		LongArrayList targets = new LongArrayList();
		IntArrayList targetColors = new IntArrayList();
		long nearestTarget = 0L;
		int nearestTargetColor = 0;
		double nearestTargetDistance = Double.MAX_VALUE;

		for (RuleMatch match : finds.matches) {
			if (!match.rule().showTracer) {
				continue;
			}

			int color = match.rule().colorArgb() | 0xFF000000;

			for (long pos : match.ownBlocks()) {
				if (allTargets) {
					targets.add(pos);
					targetColors.add(color);
				}

				double distance = distanceSqr(pos, camera);

				if (distance < nearestTargetDistance) {
					nearestTargetDistance = distance;
					nearestTarget = pos;
					nearestTargetColor = color;
				}
			}
		}

		if (!allTargets && nearestTargetDistance < Double.MAX_VALUE) {
			targets.add(nearestTarget);
			targetColors.add(nearestTargetColor);
		}

		return new ChunkHighlight(
				ChunkPos.getX(key), ChunkPos.getZ(key), minY, maxY, sources.size(), nearestDistance, nearest,
				targets.toLongArray(), targetColors.toIntArray(), finds.matches, finds.labelLines
		);
	}

	private static double distanceSqr(long pos, Vec3 camera) {
		double dx = BlockPos.getX(pos) + 0.5 - camera.x;
		double dy = BlockPos.getY(pos) + 0.5 - camera.y;
		double dz = BlockPos.getZ(pos) + 0.5 - camera.z;
		return dx * dx + dy * dy + dz * dz;
	}

	private boolean ruleMatches(BaseRule rule, ChunkFinds finds) {
		Set<String> types = new HashSet<>();
		Set<Block> blockTypes = new ReferenceOpenHashSet<>();
		int count = 0;

		for (BlockState state : finds.blocks.values()) {
			if (this.config.matches(rule, state)) {
				count++;
				blockTypes.add(state.getBlock());
			}
		}

		for (EntityFind entity : finds.entities) {
			if (this.config.matches(rule, entity)) {
				count++;
				types.add(itemName(rule, entity));
			}
		}

		if (count < rule.minBlocks) {
			return false;
		}

		// Variants such as wall torches share their block's name: one row in the block list, and one type here.
		if (rule.minTypes <= 1) {
			return true;
		}

		for (Block block : blockTypes) {
			types.add(block.getName().getString());
		}

		return types.size() >= rule.minTypes;
	}

	/** What an entity is listed as on labels: its type if the rule names it, or else why it matched. */
	private static String itemName(BaseRule rule, EntityFind entity) {
		if (rule.entities().contains(entity.type())) {
			return entity.typeName();
		}

		return Component.translatable(entity.named() && rule.matchesNamedMobs() ? "options.basetracer.entity.namedMob" : "options.basetracer.entity.tamedAnimal").getString();
	}

	private @Nullable ChunkFinds scan(LevelChunk chunk) {
		ChunkFinds found = null;
		LevelChunkSection[] sections = chunk.getSections();
		int minX = chunk.getPos().getMinBlockX();
		int minZ = chunk.getPos().getMinBlockZ();

		for (int i = 0; i < sections.length; i++) {
			LevelChunkSection section = sections[i];

			// The palette check rules out nearly every section without reading a single block.
			if (section.hasOnlyAir() || !section.maybeHas(this.config::isTracked)) {
				continue;
			}

			int minY = chunk.getSectionYFromSectionIndex(i) << 4;

			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						BlockState state = section.getBlockState(x, y, z);

						if (this.config.isTracked(state)) {
							if (found == null) {
								found = new ChunkFinds();
							}

							found.blocks.put(BlockPos.asLong(minX + x, minY + y, minZ + z), state);

							if (found.blocks.size() >= MAX_FOUND_PER_CHUNK) {
								return found;
							}
						}
					}
				}
			}
		}

		return found;
	}

	private final class ChunkFinds {
		final Long2ObjectOpenHashMap<BlockState> blocks = new Long2ObjectOpenHashMap<>();
		List<EntityFind> entities = List.of();
		/** The blocks of the rules this chunk matches. Worked out when next needed, since block changes come in bursts. */
		@Nullable LongOpenHashSet sources;
		List<RuleMatch> matches = List.of();
		List<LabelLine> labelLines = List.of();

		LongOpenHashSet sources() {
			if (this.sources == null) {
				this.sources = new LongOpenHashSet();
				this.matches = new ArrayList<>();

				for (BaseRule rule : BaseTracker.this.config.rules) {
					if (rule.enabled && BaseTracker.this.ruleMatches(rule, this)) {
						this.matches.add(this.addMatch(rule));
					}
				}

				this.labelLines = labelLines(this.matches);
			}

			return this.sources;
		}

		private RuleMatch addMatch(BaseRule rule) {
			Map<String, Integer> counts = new HashMap<>();
			int minY = Integer.MAX_VALUE;
			int maxY = Integer.MIN_VALUE;
			int blockCount = 0;
			LongArrayList ownBlocks = new LongArrayList();

			for (Long2ObjectMap.Entry<BlockState> entry : this.blocks.long2ObjectEntrySet()) {
				if (BaseTracker.this.config.matches(rule, entry.getValue())) {
					int y = BlockPos.getY(entry.getLongKey());
					minY = Math.min(minY, y);
					maxY = Math.max(maxY, y);
					blockCount++;
					counts.merge(entry.getValue().getBlock().getName().getString(), 1, Integer::sum);

					// A block in several rules takes the color of the first, as it's listed under that one too.
					if (this.sources.add(entry.getLongKey())) {
						ownBlocks.add(entry.getLongKey());
					}
				}
			}

			for (EntityFind entity : this.entities) {
				if (BaseTracker.this.config.matches(rule, entity)) {
					int y = BlockPos.getY(entity.pos());
					minY = Math.min(minY, y);
					maxY = Math.max(maxY, y);
					blockCount++;
					counts.merge(itemName(rule, entity), 1, Integer::sum);

					if (this.sources.add(entity.pos())) {
						ownBlocks.add(entity.pos());
					}
				}
			}

			List<Map.Entry<String, Integer>> items = new ArrayList<>(counts.entrySet());
			items.sort(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()));
			return new RuleMatch(rule, minY, maxY, blockCount, items, ownBlocks.toLongArray());
		}
	}

	/** One line per item, most common first per rule. A block in several rules is listed under the first. */
	private static List<LabelLine> labelLines(List<RuleMatch> matches) {
		List<LabelLine> lines = new ArrayList<>();
		Set<String> listed = new HashSet<>();
		int notShown = 0;

		for (RuleMatch match : matches) {
			if (match.rule().showLabel) {
				for (Map.Entry<String, Integer> item : match.items()) {
					if (listed.add(item.getKey())) {
						if (lines.size() < MAX_LABEL_ITEMS) {
							lines.add(new LabelLine(item.getKey() + " x" + item.getValue(), match.rule().colorArgb() | 0xFF000000));
						} else {
							notShown++;
						}
					}
				}
			}
		}

		if (notShown > 0) {
			lines.add(new LabelLine("+" + notShown + " more", 0xFFFFFFFF));
		}

		return lines;
	}

	/** The part of a chunk that matched one rule. {@code ownBlocks} leaves out blocks claimed by an earlier rule. */
	public record RuleMatch(BaseRule rule, int minY, int maxY, int blockCount, List<Map.Entry<String, Integer>> items, long[] ownBlocks) {
	}

	public record LabelLine(String text, int argb) {
	}

	/** An entity a rule cares about, at the block it's in. */
	public record EntityFind(long pos, EntityType<?> type, String typeName, boolean named, boolean tamed) {
	}

	/**
	 * Immutable render data for one chunk. {@code nearestDistance} is squared, to {@code nearest}. {@code targets}
	 * holds packed block positions for tracers, with the color for each in {@code targetColors}.
	 */
	public record ChunkHighlight(int chunkX, int chunkZ, int minY, int maxY, int sourceCount, double nearestDistance, long nearest,
			long[] targets, int[] targetColors, List<RuleMatch> matches, List<LabelLine> labelLines) {
		private static final long[] NO_TARGETS = new long[0];
		private static final int[] NO_COLORS = new int[0];

		/** That of its most important rule. */
		public int priority() {
			int priority = 0;

			for (RuleMatch match : this.matches) {
				priority = Math.max(priority, match.rule().priority);
			}

			return priority;
		}

		ChunkHighlight withoutTargets() {
			return new ChunkHighlight(this.chunkX, this.chunkZ, this.minY, this.maxY, this.sourceCount, this.nearestDistance, this.nearest, NO_TARGETS, NO_COLORS, this.matches, this.labelLines);
		}
	}
}
