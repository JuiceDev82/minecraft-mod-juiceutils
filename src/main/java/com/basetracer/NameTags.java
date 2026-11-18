package com.basetracer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;

/**
 * Puts a label with its name above every chosen kind of entity or block within range, such as "Acacia Boat" above an
 * acacia boat. Entities are found each frame, since they move; blocks every so often, since searching for them costs
 * more.
 */
public final class NameTags {
	/** How many labels at most, the nearest, so that a field of chosen blocks doesn't bury the screen. */
	public static final int MAX_TAGS = 48;
	private static final int BLOCK_SCAN_INTERVAL_TICKS = 10;
	private static final double ENTITY_LABEL_GAP = 0.5;
	private static final double BLOCK_LABEL_HEIGHT = 1.1;

	public record Tag(Vec3 pos, String label, double distance) {
	}

	private List<Tag> blockTags = List.of();
	private int ticks;

	public void tick(Minecraft client) {
		BaseTracerConfig config = BaseTracerClient.CONFIG;

		if (client.level == null || client.player == null || !config.enabled || !config.nameTags || config.nameTagBlocks().isEmpty()) {
			this.blockTags = List.of();
			return;
		}

		if (++this.ticks % BLOCK_SCAN_INTERVAL_TICKS == 0) {
			this.blockTags = scanBlocks(client.level, client.player.position(), config.nameTagRange, config.nameTagBlocks());
		}
	}

	/** Forces the next tick to look for blocks again, such as after the list changed. */
	public void rescanSoon() {
		this.ticks = BLOCK_SCAN_INTERVAL_TICKS - 1;
	}

	/** The nearest tags around the camera: chosen entities where they are this frame, and the chosen blocks last found. */
	public List<Tag> tags(ClientLevel level, BaseTracerConfig config, Vec3 camera, float partialTick) {
		Set<EntityType<?>> types = config.nameTagEntities();
		double range = config.nameTagRange;
		List<Tag> tags = new ArrayList<>();

		if (!types.isEmpty()) {
			Entity self = Minecraft.getInstance().getCameraEntity();

			for (Entity entity : level.entitiesForRendering()) {
				if (entity == self || !entity.isAlive() || !types.contains(entity.getType())) {
					continue;
				}

				Vec3 pos = entity.getPosition(partialTick);
				double distance = pos.distanceTo(camera);

				if (distance <= range) {
					tags.add(new Tag(pos.add(0, entity.getBbHeight() + ENTITY_LABEL_GAP, 0), entity.getName().getString(), distance));
				}
			}
		}

		for (Tag tag : this.blockTags) {
			double distance = tag.pos().distanceTo(camera);

			if (distance <= range) {
				tags.add(new Tag(tag.pos(), tag.label(), distance));
			}
		}

		tags.sort(Comparator.comparingDouble(Tag::distance));
		return tags.size() > MAX_TAGS ? List.copyOf(tags.subList(0, MAX_TAGS)) : tags;
	}

	/** The chosen blocks within range, read section by section, skipping those whose palette can't hold any. */
	public static List<Tag> scanBlocks(ClientLevel level, Vec3 center, int range, Set<Block> blocks) {
		List<Tag> found = new ArrayList<>();
		int minChunkX = (int) Math.floor(center.x - range) >> 4;
		int maxChunkX = (int) Math.floor(center.x + range) >> 4;
		int minChunkZ = (int) Math.floor(center.z - range) >> 4;
		int maxChunkZ = (int) Math.floor(center.z + range) >> 4;
		double rangeSqr = (double) range * range;

		for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
			for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
				LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);

				if (chunk == null) {
					continue;
				}

				LevelChunkSection[] sections = chunk.getSections();

				for (int i = 0; i < sections.length; i++) {
					LevelChunkSection section = sections[i];
					int minY = chunk.getSectionYFromSectionIndex(i) << 4;

					if (section.hasOnlyAir() || minY > center.y + range || minY + 16 < center.y - range
							|| !section.maybeHas(state -> blocks.contains(state.getBlock()))) {
						continue;
					}

					for (int y = 0; y < 16; y++) {
						for (int z = 0; z < 16; z++) {
							for (int x = 0; x < 16; x++) {
								BlockState state = section.getBlockState(x, y, z);

								if (!blocks.contains(state.getBlock())) {
									continue;
								}

								BlockPos pos = new BlockPos((chunkX << 4) + x, minY + y, (chunkZ << 4) + z);
								Vec3 labelPos = new Vec3(pos.getX() + 0.5, pos.getY() + BLOCK_LABEL_HEIGHT, pos.getZ() + 0.5);

								if (labelPos.distanceToSqr(center) <= rangeSqr) {
									found.add(new Tag(labelPos, state.getBlock().getName().getString(), 0));
								}
							}
						}
					}
				}
			}
		}

		return found;
	}
}
