package com.basetracer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;

import com.basetracer.BaseTracker.ChunkHighlight;
import com.basetracer.BaseTracker.LabelLine;
import com.basetracer.BaseTracker.RuleMatch;

public final class BaseTracerRenderer {
	private static final RenderPipeline QUADS_THROUGH_WALLS = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(BaseTracerClient.MOD_ID, "pipeline/quads_through_walls"))
			.withCull(false)
			.withDepthStencilState(Optional.empty())
			.build()
	);
	private static final StagedVertexBuffer STAGED_BUFFER = new StagedVertexBuffer(() -> "Base Tracer Buffer", RenderType.SMALL_BUFFER_SIZE);
	private static final Vector4f COLOR_MODULATOR = new Vector4f(1F, 1F, 1F, 1F);
	private static final Vector3f MODEL_OFFSET = new Vector3f();
	private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
	/** Lines are clipped to this distance in front of the camera, since their width is proportional to depth. */
	private static final float NEAR = 0.1F;
	private static final float TRACER_START_DISTANCE = 1.0F;
	private static final int FULL_BRIGHTNESS_SOURCES = 32;
	private static final float MAX_ALPHA_SCALE = 5.0F;
	private static final float MAX_WHITEN = 0.6F;
	private static final float RULE_BOX_INSET = 0.4F;
	private static final int BLOCK_FILL_ALPHA = 0x50;
	/** Block highlights are gone this close, in blocks from the camera to the block's nearest side. */
	public static final float BLOCK_HIDE_DISTANCE = 2F;
	/** And back at full strength from this far. */
	public static final float BLOCK_FULL_DISTANCE = 5F;
	/** Keeps the geometry per frame bounded in places with thousands of matching blocks. */
	public static final int MAX_BLOCK_HIGHLIGHTS = 512;
	/** Keeps the geometry per frame bounded around mob farms. */
	public static final int MAX_MOB_HIGHLIGHTS = 512;
	/** Around mob farms there can be thousands of items; the nearest are the ones worth showing. */
	public static final int MAX_ITEM_HIGHLIGHTS = 128;
	private static final int ITEM_LABEL_COLOR = 0xFFFFFFFF;
	/** How opaque a mob's fill is compared to its outline, so that the mob stays visible inside its box. */
	private static final float MOB_FILL_SHARE = 0.3F;
	private static final float LABEL_SCALE = 0.5F;
	/** Before scaling. */
	private static final int LABEL_LINE_HEIGHT = 10;
	private static final int LABEL_PADDING = 1;
	private static final int LABEL_BACKGROUND = 0x90000000;
	private static final int LABEL_INFO_COLOR = 0xFFCCCCCC;
	/** In GUI pixels. */
	private static final int MAX_LABEL_SHIFT = 40;
	/** Items lie in piles, so their labels stack up a little, but not so far as to lose track of which is which. */
	private static final int MAX_ITEM_LABEL_SHIFT = 15;

	private final BaseTracker tracker;
	private final BaseTracerConfig config;

	private CameraRenderState cameraState;
	private List<ChunkHighlight> highlights = List.of();
	private List<ChunkHighlight> labelled = List.of();
	private List<BlockMark> blockMarks = List.of();
	private List<AABB> hostileMobs = List.of();
	private List<AABB> players = List.of();
	private List<DroppedItems.ItemMark> items = List.of();
	private int snapshotRevision = -1;
	private long snapshotCameraBlock;
	private TracerMode snapshotTracerMode;
	private int snapshotLimit;

	public BaseTracerRenderer(BaseTracker tracker, BaseTracerConfig config) {
		this.tracker = tracker;
		this.config = config;
	}

	public void extract(LevelExtractionContext context) {
		this.cameraState = context.levelState().cameraRenderState;

		if (!this.config.enabled || !this.tracker.isCurrentLevel(Minecraft.getInstance().level)) {
			this.highlights = List.of();
			this.labelled = List.of();
			this.blockMarks = List.of();
			this.hostileMobs = List.of();
			this.players = List.of();
			this.items = List.of();
			this.snapshotRevision = -1;
			return;
		}

		// The nearest source only needs recomputing once something changed or the camera crossed a block.
		Vec3 camera = context.levelState().cameraRenderState.pos;
		long cameraBlock = BlockPos.containing(camera).asLong();
		// Mobs and players move every frame, so unlike the rest these can't wait for a change.
		Minecraft client = Minecraft.getInstance();
		float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		this.hostileMobs = this.config.showHostileMobs ? nearestMobs(client.level, this.config, camera, partialTick, MAX_MOB_HIGHLIGHTS) : List.of();
		this.players = this.config.showPlayers ? otherPlayers(client.level, client.player, partialTick) : List.of();
		this.items = this.config.showItems ? BaseTracerClient.DROPPED_ITEMS.marks(client.level, this.config.itemMode, camera, partialTick, MAX_ITEM_HIGHLIGHTS) : List.of();

		TracerMode tracerMode = this.config.tracerMode;
		int limit = this.config.baseLimit();

		if (this.snapshotRevision != this.tracker.revision() || this.snapshotCameraBlock != cameraBlock || this.snapshotTracerMode != tracerMode || this.snapshotLimit != limit) {
			this.highlights = this.tracker.snapshot(camera, tracerMode, limit);
			this.snapshotLimit = limit;
			this.blockMarks = nearestBlocks(this.highlights, camera, MAX_BLOCK_HIGHLIGHTS);
			this.labelled = this.highlights.stream()
					.filter(highlight -> !highlight.labelLines().isEmpty())
					.sorted(Comparator.comparingDouble(ChunkHighlight::nearestDistance))
					.toList();
			this.snapshotRevision = this.tracker.revision();
			this.snapshotCameraBlock = cameraBlock;
			this.snapshotTracerMode = tracerMode;
		}
	}

	/** How many bases are shown, for tests. */
	public int highlightCount() {
		return this.highlights.size();
	}

	public void render() {
		boolean showBases = !this.highlights.isEmpty() && (this.config.showChunkHighlights || this.config.showTracers || this.config.showBlockHighlights);

		if (!showBases && this.hostileMobs.isEmpty() && this.players.isEmpty() && this.items.isEmpty()) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		CameraRenderState cameraState = this.cameraState;
		StagedVertexBuffer.Draw draw = STAGED_BUFFER.appendDraw(
				QUADS_THROUGH_WALLS.getVertexFormatBinding(0),
				QUADS_THROUGH_WALLS.getPrimitiveTopology(),
				RenderSystem.getProjectionType().vertexSorting()
		);
		// World-space half width per unit of depth that makes lines lineWidth pixels wide on screen.
		float widthPerDepth = this.config.lineWidth / (cameraState.projectionMatrix.m11() * client.getWindow().getHeight());
		LineBuilder lines = new LineBuilder(
				STAGED_BUFFER.getVertexBuilder(draw),
				new PoseStack().last(),
				cameraState.orientation.transform(new Vector3f(0F, 0F, -1F)),
				widthPerDepth
		);

		for (ChunkHighlight highlight : this.highlights) {
			if (this.config.showChunkHighlights) {
				this.addChunkHighlight(lines, highlight, cameraState.pos);
			}

			if (this.config.showTracers) {
				this.addTracers(lines, highlight, cameraState.pos);
			}
		}

		if (this.config.showBlockHighlights) {
			for (BlockMark mark : this.blockMarks) {
				this.addBlockHighlight(lines, mark, cameraState.pos);
			}
		}

		for (AABB mob : this.hostileMobs) {
			this.addEntityHighlight(lines, mob, cameraState.pos, this.config.hostileMobArgb());
		}

		for (DroppedItems.ItemMark item : this.items) {
			this.addEntityHighlight(lines, item.box(), cameraState.pos, this.config.itemArgb());
		}

		for (AABB player : this.players) {
			this.addEntityHighlight(lines, player, cameraState.pos, this.config.playerArgb());

			if (this.config.showPlayerTracers) {
				Vec3 center = player.getCenter();
				lines.color = this.config.playerArgb() | 0xFF000000;
				lines.line(
						lines.forward.x * TRACER_START_DISTANCE, lines.forward.y * TRACER_START_DISTANCE, lines.forward.z * TRACER_START_DISTANCE,
						(float) (center.x - cameraState.pos.x), (float) (center.y - cameraState.pos.y), (float) (center.z - cameraState.pos.z)
				);
			}
		}

		STAGED_BUFFER.upload();
		StagedVertexBuffer.ExecuteInfo info = STAGED_BUFFER.getExecuteInfo(draw);

		if (info != null) {
			draw(client, info, cameraState);
		}

		STAGED_BUFFER.endFrame();
	}

	private void addChunkHighlight(LineBuilder lines, ChunkHighlight highlight, Vec3 camera) {
		float inset = 0F;

		for (RuleMatch match : highlight.matches()) {
			this.addRuleBox(lines, highlight, match, camera, inset);
			inset += RULE_BOX_INSET;
		}
	}

	/** Each matching rule gets its own box. The inset keeps their outlines from drawing over each other. */
	private void addRuleBox(LineBuilder lines, ChunkHighlight highlight, RuleMatch match, Vec3 camera, float inset) {
		float minX = (float) ((highlight.chunkX() << 4) - camera.x) + inset;
		float minY = (float) (match.minY() - camera.y);
		float minZ = (float) ((highlight.chunkZ() << 4) - camera.z) + inset;
		float maxX = minX + 16F - inset * 2F;
		float maxY = (float) (match.maxY() + 1 - camera.y);
		float maxZ = minZ + 16F - inset * 2F;

		float busyness = busyness(match.blockCount());
		int color = match.rule().colorArgb();
		int fill = this.config.fillAlpha() << 24 | color & 0xFFFFFF;

		// From inside, the fill would tint everything in view. The outline still shows where the chunk ends.
		if (!isCameraInChunk(camera, highlight.chunkX(), highlight.chunkZ())) {
			this.addFill(lines.buffer, lines.pose, brighten(fill, busyness), minX, minY, minZ, maxX, maxY, maxZ);
		}

		lines.color = brighten(color, busyness);
		addBoxLines(lines, minX, minY, minZ, maxX, maxY, maxZ);
	}

	private void addBlockHighlight(LineBuilder lines, BlockMark mark, Vec3 camera) {
		float minX = (float) (BlockPos.getX(mark.pos()) - camera.x);
		float minY = (float) (BlockPos.getY(mark.pos()) - camera.y);
		float minZ = (float) (BlockPos.getZ(mark.pos()) - camera.z);
		float maxX = minX + 1F;
		float maxY = minY + 1F;
		float maxZ = minZ + 1F;
		// Fades out up close, so that a block being looked at or mined isn't covered.
		float strength = nearStrength(blockDistance(mark.pos(), camera));

		if (strength <= 0F) {
			return;
		}

		this.addFill(lines.buffer, lines.pose, Math.round(BLOCK_FILL_ALPHA * strength) << 24 | mark.argb() & 0xFFFFFF, minX, minY, minZ, maxX, maxY, maxZ);
		lines.color = Math.round(0xFF * strength) << 24 | mark.argb() & 0xFFFFFF;
		addBoxLines(lines, minX, minY, minZ, maxX, maxY, maxZ);
	}

	/** From the camera to the nearest point of a block, so 0 inside it. */
	public static double blockDistance(long pos, Vec3 camera) {
		double dx = Math.max(0, Math.max(BlockPos.getX(pos) - camera.x, camera.x - (BlockPos.getX(pos) + 1)));
		double dy = Math.max(0, Math.max(BlockPos.getY(pos) - camera.y, camera.y - (BlockPos.getY(pos) + 1)));
		double dz = Math.max(0, Math.max(BlockPos.getZ(pos) - camera.z, camera.z - (BlockPos.getZ(pos) + 1)));
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/** How strongly a block highlight shows at a distance: none up close, rising to full. */
	public static float nearStrength(double distance) {
		return (float) Mth.clamp((distance - BLOCK_HIDE_DISTANCE) / (BLOCK_FULL_DISTANCE - BLOCK_HIDE_DISTANCE), 0, 1);
	}

	/** The blocks behind the matches, nearest first. */
	public static List<BlockMark> nearestBlocks(List<ChunkHighlight> highlights, Vec3 camera, int max) {
		List<BlockMark> marks = new ArrayList<>();

		for (ChunkHighlight highlight : highlights) {
			for (RuleMatch match : highlight.matches()) {
				for (long pos : match.ownBlocks()) {
					marks.add(new BlockMark(pos, match.rule().colorArgb()));
				}
			}
		}

		marks.sort(Comparator.comparingDouble(mark -> camera.distanceToSqr(BlockPos.getX(mark.pos()) + 0.5, BlockPos.getY(mark.pos()) + 0.5, BlockPos.getZ(mark.pos()) + 0.5)));
		return marks.size() > max ? List.copyOf(marks.subList(0, max)) : marks;
	}

	private void addEntityHighlight(LineBuilder lines, AABB mob, Vec3 camera, int color) {
		float minX = (float) (mob.minX - camera.x);
		float minY = (float) (mob.minY - camera.y);
		float minZ = (float) (mob.minZ - camera.z);
		float maxX = (float) (mob.maxX - camera.x);
		float maxY = (float) (mob.maxY - camera.y);
		float maxZ = (float) (mob.maxZ - camera.z);
		int alpha = Math.round((color >>> 24) * this.config.mobOpacity / 100F);

		this.addFill(lines.buffer, lines.pose, Math.round(alpha * MOB_FILL_SHARE) << 24 | color & 0xFFFFFF, minX, minY, minZ, maxX, maxY, maxZ);
		lines.color = alpha << 24 | color & 0xFFFFFF;
		addBoxLines(lines, minX, minY, minZ, maxX, maxY, maxZ);
	}

	/** The boxes of living listed mobs where they appear this frame, keeping the nearest if there are too many. */
	public static List<AABB> nearestMobs(ClientLevel level, BaseTracerConfig config, Vec3 camera, float partialTick, int max) {
		List<AABB> mobs = new ArrayList<>();

		for (Entity entity : level.entitiesForRendering()) {
			if (config.isMobHighlighted(entity.getType()) && entity.isAlive()) {
				mobs.add(entity.getBoundingBox().move(entity.getPosition(partialTick).subtract(entity.position())));
			}
		}

		if (mobs.size() <= max) {
			return mobs;
		}

		mobs.sort(Comparator.comparingDouble(mob -> camera.distanceToSqr(mob.getCenter())));
		return List.copyOf(mobs.subList(0, max));
	}

	/** The boxes of every other player where they appear this frame. */
	public static List<AABB> otherPlayers(ClientLevel level, @Nullable Entity self, float partialTick) {
		List<AABB> players = new ArrayList<>();

		for (Player player : level.players()) {
			if (player != self && player.isAlive()) {
				players.add(player.getBoundingBox().move(player.getPosition(partialTick).subtract(player.position())));
			}
		}

		return players;
	}

	private static void addBoxLines(LineBuilder lines, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		lines.line(minX, minY, minZ, maxX, minY, minZ);
		lines.line(maxX, minY, minZ, maxX, minY, maxZ);
		lines.line(maxX, minY, maxZ, minX, minY, maxZ);
		lines.line(minX, minY, maxZ, minX, minY, minZ);
		lines.line(minX, maxY, minZ, maxX, maxY, minZ);
		lines.line(maxX, maxY, minZ, maxX, maxY, maxZ);
		lines.line(maxX, maxY, maxZ, minX, maxY, maxZ);
		lines.line(minX, maxY, maxZ, minX, maxY, minZ);
		lines.line(minX, minY, minZ, minX, maxY, minZ);
		lines.line(maxX, minY, minZ, maxX, maxY, minZ);
		lines.line(maxX, minY, maxZ, maxX, maxY, maxZ);
		lines.line(minX, minY, maxZ, minX, maxY, maxZ);
	}

	/**
	 * Draws each chunk's list of found items above its highlight. On the HUD rather than in the level, so that
	 * labels stay readable at any distance and shader packs can't draw over them.
	 */
	public void renderLabels(GuiGraphicsExtractor graphics) {
		if (!this.config.enabled || this.cameraState == null) {
			return;
		}

		boolean chunkLabels = this.config.showLabels && !this.labelled.isEmpty();
		boolean itemLabels = this.config.showItems && this.config.showItemLabels && !this.items.isEmpty();

		if (!chunkLabels && !itemLabels) {
			return;
		}

		Font font = Minecraft.getInstance().font;
		CameraRenderState camera = this.cameraState;
		Matrix4f worldToClip = new Matrix4f(camera.projectionMatrix).mul(camera.viewRotationMatrix);
		List<int[]> drawn = new ArrayList<>();

		// Items first: they're nearby, and their labels are small enough to leave room for the chunks'.
		if (itemLabels) {
			for (DroppedItems.ItemMark item : this.items) {
				drawLabel(graphics, font, worldToClip, camera, item.labelPos(), List.of(new LabelLine(item.label(), ITEM_LABEL_COLOR)), MAX_ITEM_LABEL_SHIFT, drawn);
			}
		}

		if (!chunkLabels) {
			return;
		}

		// Nearest first, so that when labels would overlap the nearest one stays in place.
		for (ChunkHighlight highlight : this.labelled) {
			Vec3 anchor = new Vec3((highlight.chunkX() << 4) + 8, highlight.maxY() + 1, (highlight.chunkZ() << 4) + 8);
			drawLabel(graphics, font, worldToClip, camera, anchor, this.withInfoLine(highlight), MAX_LABEL_SHIFT, drawn);
		}
	}

	/** A label whose bottom sits on a point in the world, moved up by up to {@code maxShift} to clear those drawn. */
	private static void drawLabel(GuiGraphicsExtractor graphics, Font font, Matrix4f worldToClip, CameraRenderState camera, Vec3 anchor,
			List<LabelLine> labelLines, int maxShift, List<int[]> drawn) {
		Vector4f clip = worldToClip.transform(new Vector4f(
				(float) (anchor.x - camera.pos.x),
				(float) (anchor.y - camera.pos.y),
				(float) (anchor.z - camera.pos.z),
				1F
		));

		if (clip.w < NEAR) {
			return;
		}

		int width = 0;

		for (LabelLine line : labelLines) {
			width = Math.max(width, font.width(line.text()));
		}

		width = Mth.ceil(width * LABEL_SCALE);
		int height = Mth.ceil(labelLines.size() * LABEL_LINE_HEIGHT * LABEL_SCALE);
		int left = Math.round((clip.x / clip.w + 1F) / 2F * graphics.guiWidth()) - width / 2;
		int top = Math.round((1F - clip.y / clip.w) / 2F * graphics.guiHeight()) - height;
		int[] box = {left - LABEL_PADDING, top - LABEL_PADDING, left + width + LABEL_PADDING, top + height + LABEL_PADDING};

		// A label in the way of a nearer one moves up until it's clear, unless that takes it too far from what it labels.
		int[] blocker;
		int anchorBottom = box[3];

		while ((blocker = firstOverlap(box, drawn)) != null && anchorBottom - box[3] <= maxShift) {
			int shift = box[3] - blocker[1];
			box[1] -= shift;
			box[3] -= shift;
			top -= shift;
		}

		if (blocker != null || box[2] < 0 || box[0] > graphics.guiWidth() || box[3] < 0 || box[1] > graphics.guiHeight()) {
			return;
		}

		drawn.add(box);
		graphics.fill(box[0], box[1], box[2], box[3], LABEL_BACKGROUND);
		graphics.pose().pushMatrix();
		graphics.pose().translate(left, top);
		graphics.pose().scale(LABEL_SCALE);
		int y = 0;

		for (LabelLine line : labelLines) {
			graphics.text(font, line.text(), 0, y, line.argb());
			y += LABEL_LINE_HEIGHT;
		}

		graphics.pose().popMatrix();
	}

	/** Puts how far away the nearest find is, and where, above the items. */
	private List<LabelLine> withInfoLine(ChunkHighlight highlight) {
		String info = labelInfo(highlight, this.config.showLabelDistance, this.config.showLabelCoordinates);

		if (info.isEmpty()) {
			return highlight.labelLines();
		}

		List<LabelLine> lines = new ArrayList<>(highlight.labelLines().size() + 1);
		lines.add(new LabelLine(info, LABEL_INFO_COLOR));
		lines.addAll(highlight.labelLines());
		return lines;
	}

	public static String labelInfo(ChunkHighlight highlight, boolean distance, boolean coordinates) {
		List<String> parts = new ArrayList<>(2);

		if (distance) {
			parts.add(Math.round(Math.sqrt(highlight.nearestDistance())) + "m");
		}

		if (coordinates) {
			parts.add(BlockPos.getX(highlight.nearest()) + ", " + BlockPos.getY(highlight.nearest()) + ", " + BlockPos.getZ(highlight.nearest()));
		}

		return String.join(" \u00B7 ", parts);
	}

	private static int @Nullable [] firstOverlap(int[] box, List<int[]> others) {
		for (int[] other : others) {
			if (box[0] < other[2] && other[0] < box[2] && box[1] < other[3] && other[1] < box[3]) {
				return other;
			}
		}

		return null;
	}

	private void addTracers(LineBuilder lines, ChunkHighlight highlight, Vec3 camera) {
		float startX = lines.forward.x * TRACER_START_DISTANCE;
		float startY = lines.forward.y * TRACER_START_DISTANCE;
		float startZ = lines.forward.z * TRACER_START_DISTANCE;

		for (int i = 0; i < highlight.targets().length; i++) {
			long target = highlight.targets()[i];
			lines.color = highlight.targetColors()[i];
			lines.line(
					startX, startY, startZ,
					(float) (BlockPos.getX(target) + 0.5 - camera.x),
					(float) (BlockPos.getY(target) + 0.5 - camera.y),
					(float) (BlockPos.getZ(target) + 0.5 - camera.z)
			);
		}
	}

	/** At any height, since a highlight above or below still fills the view when looking up or down. */
	public static boolean isCameraInChunk(Vec3 camera, int chunkX, int chunkZ) {
		return Mth.floor(camera.x) >> 4 == chunkX && Mth.floor(camera.z) >> 4 == chunkZ;
	}

	/** 0 for a single source, rising logarithmically to 1 at {@link #FULL_BRIGHTNESS_SOURCES}. */
	public static float busyness(int sourceCount) {
		return (float) Math.min(1.0, Math.log(sourceCount) / Math.log(FULL_BRIGHTNESS_SOURCES));
	}

	/** Makes a color more opaque and closer to white as busyness rises. At 0 it's returned unchanged. */
	public static int brighten(int argb, float busyness) {
		int alpha = Math.min(255, Math.round((argb >>> 24) * (1F + busyness * (MAX_ALPHA_SCALE - 1F))));
		float whiten = busyness * MAX_WHITEN;
		return alpha << 24 | whiten(argb >> 16 & 0xFF, whiten) << 16 | whiten(argb >> 8 & 0xFF, whiten) << 8 | whiten(argb & 0xFF, whiten);
	}

	private static int whiten(int channel, float amount) {
		return Math.round(channel + (255 - channel) * amount);
	}

	private void addFill(VertexConsumer buffer, PoseStack.Pose pose, int c, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		buffer.addVertex(pose, minX, minY, maxZ).setColor(c);
		buffer.addVertex(pose, maxX, minY, maxZ).setColor(c);
		buffer.addVertex(pose, maxX, maxY, maxZ).setColor(c);
		buffer.addVertex(pose, minX, maxY, maxZ).setColor(c);

		buffer.addVertex(pose, maxX, minY, minZ).setColor(c);
		buffer.addVertex(pose, minX, minY, minZ).setColor(c);
		buffer.addVertex(pose, minX, maxY, minZ).setColor(c);
		buffer.addVertex(pose, maxX, maxY, minZ).setColor(c);

		buffer.addVertex(pose, minX, minY, minZ).setColor(c);
		buffer.addVertex(pose, minX, minY, maxZ).setColor(c);
		buffer.addVertex(pose, minX, maxY, maxZ).setColor(c);
		buffer.addVertex(pose, minX, maxY, minZ).setColor(c);

		buffer.addVertex(pose, maxX, minY, maxZ).setColor(c);
		buffer.addVertex(pose, maxX, minY, minZ).setColor(c);
		buffer.addVertex(pose, maxX, maxY, minZ).setColor(c);
		buffer.addVertex(pose, maxX, maxY, maxZ).setColor(c);

		buffer.addVertex(pose, minX, maxY, maxZ).setColor(c);
		buffer.addVertex(pose, maxX, maxY, maxZ).setColor(c);
		buffer.addVertex(pose, maxX, maxY, minZ).setColor(c);
		buffer.addVertex(pose, minX, maxY, minZ).setColor(c);

		buffer.addVertex(pose, minX, minY, minZ).setColor(c);
		buffer.addVertex(pose, maxX, minY, minZ).setColor(c);
		buffer.addVertex(pose, maxX, minY, maxZ).setColor(c);
		buffer.addVertex(pose, minX, minY, maxZ).setColor(c);
	}

	private static void draw(Minecraft client, StagedVertexBuffer.ExecuteInfo info, CameraRenderState cameraState) {
		GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
				.writeTransform(cameraState.viewRotationMatrix, COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);
		RenderTarget target = client.gameRenderer.mainRenderTarget();
		GpuTextureView colorTexture = target.getColorTextureView();

		try (RenderPass pass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(() -> "Base Tracer", colorTexture, Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
			pass.setPipeline(QUADS_THROUGH_WALLS);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transforms);
			pass.setVertexBuffer(0, info.vertexBuffer().slice());
			pass.setIndexBuffer(info.indexBuffer(), info.indexType());
			pass.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0);
		}
	}

	public static void close() {
		STAGED_BUFFER.close();
	}

	/** One block to highlight, as a packed position and the color of the rule it belongs to. */
	public record BlockMark(long pos, int argb) {
	}

	/**
	 * Draws camera-relative lines as camera-facing quads. The vanilla line shader isn't suitable for tracers:
	 * it derives line width from an 8-bit direction, which collapses for lines pointing away from the camera.
	 */
	private static final class LineBuilder {
		final VertexConsumer buffer;
		final PoseStack.Pose pose;
		final Vector3f forward;
		final float widthPerDepth;
		int color;

		LineBuilder(VertexConsumer buffer, PoseStack.Pose pose, Vector3f forward, float widthPerDepth) {
			this.buffer = buffer;
			this.pose = pose;
			this.forward = forward;
			this.widthPerDepth = widthPerDepth;
		}

		void line(float x1, float y1, float z1, float x2, float y2, float z2) {
			float depth1 = this.forward.x * x1 + this.forward.y * y1 + this.forward.z * z1;
			float depth2 = this.forward.x * x2 + this.forward.y * y2 + this.forward.z * z2;

			if (depth1 < NEAR && depth2 < NEAR) {
				return;
			}

			if (depth1 < NEAR) {
				float t = (NEAR - depth1) / (depth2 - depth1);
				x1 += (x2 - x1) * t;
				y1 += (y2 - y1) * t;
				z1 += (z2 - z1) * t;
				depth1 = NEAR;
			} else if (depth2 < NEAR) {
				float t = (NEAR - depth1) / (depth2 - depth1);
				x2 = x1 + (x2 - x1) * t;
				y2 = y1 + (y2 - y1) * t;
				z2 = z1 + (z2 - z1) * t;
				depth2 = NEAR;
			}

			// With the camera at the origin, this is perpendicular to both the line and the view direction.
			float sideX = y1 * z2 - z1 * y2;
			float sideY = z1 * x2 - x1 * z2;
			float sideZ = x1 * y2 - y1 * x2;
			float length = (float) Math.sqrt(sideX * sideX + sideY * sideY + sideZ * sideZ);

			// A line through the camera covers a single point on screen.
			if (length < 1.0E-5F) {
				return;
			}

			sideX /= length;
			sideY /= length;
			sideZ /= length;
			float width1 = depth1 * this.widthPerDepth;
			float width2 = depth2 * this.widthPerDepth;

			this.buffer.addVertex(this.pose, x1 - sideX * width1, y1 - sideY * width1, z1 - sideZ * width1).setColor(this.color);
			this.buffer.addVertex(this.pose, x1 + sideX * width1, y1 + sideY * width1, z1 + sideZ * width1).setColor(this.color);
			this.buffer.addVertex(this.pose, x2 + sideX * width2, y2 + sideY * width2, z2 + sideZ * width2).setColor(this.color);
			this.buffer.addVertex(this.pose, x2 - sideX * width2, y2 - sideY * width2, z2 - sideZ * width2).setColor(this.color);
		}
	}
}
