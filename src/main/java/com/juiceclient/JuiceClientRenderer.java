package com.juiceclient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

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
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;

import com.juiceclient.BaseTracker.ChunkHighlight;
import com.juiceclient.BaseTracker.LabelLine;
import com.juiceclient.BaseTracker.RuleMatch;

public final class JuiceClientRenderer {
	private static final RenderPipeline QUADS_THROUGH_WALLS = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(JuiceClient.MOD_ID, "pipeline/quads_through_walls"))
			.withCull(false)
			.withDepthStencilState(Optional.empty())
			.build()
	);
	private static final StagedVertexBuffer STAGED_BUFFER = new StagedVertexBuffer(() -> "Juice Client Buffer", RenderType.SMALL_BUFFER_SIZE);
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
	/** Half the width of the X where a throw lands, in blocks. */
	private static final double TRAJECTORY_X_SIZE = 0.25;
	/** The path is drawn from this far from the camera, in blocks. */
	private static final double TRAJECTORY_START_DISTANCE = 2.5;
	/** Block highlights are gone this close, in blocks from the camera to the block's nearest side. */
	public static final float BLOCK_HIDE_DISTANCE = 2F;
	/** And back at full strength from this far. */
	public static final float BLOCK_FULL_DISTANCE = 5F;
	private static final double SIGHT_FACE_OFFSET = 0.45;
	/** Keeps the geometry per frame bounded in places with thousands of matching blocks. */
	public static final int MAX_BLOCK_HIGHLIGHTS = 512;
	/** Keeps the geometry per frame bounded around mob farms. */
	public static final int MAX_MOB_HIGHLIGHTS = 512;
	/** Around mob farms there can be thousands of items; the nearest are the ones worth showing. */
	public static final int MAX_ITEM_HIGHLIGHTS = 128;
	private static final int NAME_TAG_COLOR = 0xFFFFFF88;
	/** Text at a label size of 100%. */
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
	private final JuiceClientConfig config;

	private CameraRenderState cameraState;
	private List<ChunkHighlight> highlights = List.of();
	private List<ChunkHighlight> labelled = List.of();
	private List<BlockMark> blockMarks = List.of();
	private List<AABB> players = List.of();
	private List<DroppedItems.ItemMark> items = List.of();
	/** The dropped items shown, outlined by the game along their shape as if glowing. */
	private IntOpenHashSet outlinedItems = new IntOpenHashSet();
	/** Where the entities matching a rule are, and that rule: they're outlined like items, not drawn as blocks. */
	private Long2ObjectOpenHashMap<BaseRule> entityMarks = new Long2ObjectOpenHashMap<>();
	private List<NameTags.Tag> nameTags = List.of();
	/** Where what the player is about to throw or shoot would go, or null. */
	private Trajectory.@Nullable Path trajectory;
	private int snapshotRevision = -1;
	private long snapshotCameraBlock;
	private TracerMode snapshotTracerMode;
	private int snapshotLimit;

	public JuiceClientRenderer(BaseTracker tracker, JuiceClientConfig config) {
		this.tracker = tracker;
		this.config = config;
	}

	public void extract(LevelExtractionContext context) {
		this.cameraState = context.levelState().cameraRenderState;

		if (!this.config.enabled || !this.tracker.isCurrentLevel(Minecraft.getInstance().level)) {
			this.highlights = List.of();
			this.labelled = List.of();
			this.blockMarks = List.of();
			this.players = List.of();
			this.items = List.of();
			this.outlinedItems = new IntOpenHashSet();
			this.entityMarks = new Long2ObjectOpenHashMap<>();
			this.nameTags = List.of();
			this.trajectory = null;
			this.snapshotRevision = -1;
			return;
		}

		// The nearest source only needs recomputing once something changed or the camera crossed a block.
		Vec3 camera = context.levelState().cameraRenderState.pos;
		long cameraBlock = BlockPos.containing(camera).asLong();
		// Mobs and players move every frame, so unlike the rest these can't wait for a change.
		Minecraft client = Minecraft.getInstance();
		float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		this.trajectory = this.config.trajectories && client.player != null && !client.player.isSpectator() ? Trajectory.predict(client.player, partialTick) : null;
		this.players = this.config.showPlayers && this.config.showPlayerTracers ? otherPlayers(client.level, client.player, partialTick) : List.of();
		this.items = this.config.showItems ? JuiceClient.DROPPED_ITEMS.marks(client.level, this.config.itemMode, camera, partialTick, MAX_ITEM_HIGHLIGHTS) : List.of();
		IntOpenHashSet outlinedItems = new IntOpenHashSet();

		for (DroppedItems.ItemMark item : this.items) {
			outlinedItems.add(item.entityId());
		}

		this.outlinedItems = outlinedItems;
		this.nameTags = this.config.nameTags ? JuiceClient.NAME_TAGS.tags(client.level, this.config, camera, partialTick) : List.of();

		TracerMode tracerMode = this.config.tracerMode;
		int limit = this.config.baseLimit();

		if (this.snapshotRevision != this.tracker.revision() || this.snapshotCameraBlock != cameraBlock || this.snapshotTracerMode != tracerMode || this.snapshotLimit != limit) {
			this.highlights = this.tracker.snapshot(camera, tracerMode, limit);
			this.snapshotLimit = limit;
			this.splitMarks(client.level, nearestBlocks(this.highlights, camera, MAX_BLOCK_HIGHLIGHTS));
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

		if (!showBases && this.players.isEmpty() && this.trajectory == null) {
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

		if (this.trajectory != null) {
			this.addTrajectory(lines, this.trajectory, cameraState.pos);
		}

		// Players themselves are outlined by the game; only the tracers to them are drawn here.
		for (AABB player : this.players) {
			Vec3 center = player.getCenter();
			lines.color = this.config.playerArgb() | 0xFF000000;
			lines.line(
					lines.forward.x * TRACER_START_DISTANCE, lines.forward.y * TRACER_START_DISTANCE, lines.forward.z * TRACER_START_DISTANCE,
					(float) (center.x - cameraState.pos.x), (float) (center.y - cameraState.pos.y), (float) (center.z - cameraState.pos.z)
			);
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

	/** Separates the marks of blocks, drawn here, from those of entities, which the game outlines. */
	private void splitMarks(@Nullable ClientLevel level, List<BlockMark> marks) {
		List<BlockMark> blocks = new ArrayList<>(marks.size());
		Long2ObjectOpenHashMap<BaseRule> entities = new Long2ObjectOpenHashMap<>();

		for (BlockMark mark : marks) {
			if (level != null && !this.config.matches(mark.rule(), level.getBlockState(BlockPos.of(mark.pos())))) {
				entities.put(mark.pos(), mark.rule());
			} else {
				blocks.add(mark);
			}
		}

		this.blockMarks = blocks;
		this.entityMarks = entities;
	}

	/**
	 * The color of the outline the game draws around an entity as if it were glowing, following its shape, or -1 for
	 * none: listed mobs, other players, dropped items being shown, and entities matching a base rule.
	 */
	public static int outlineColor(Entity entity) {
		JuiceClientConfig config = JuiceClient.CONFIG;

		if (!config.enabled || !entity.isAlive()) {
			return -1;
		}

		// What a throw or shot would hit stands out in the trajectory's color.
		Trajectory.Path trajectory = JuiceClient.RENDERER.trajectory;

		if (trajectory != null && trajectory.entity() == entity) {
			return config.trajectoryArgb() & 0xFFFFFF;
		}

		if (config.showHostileMobs && config.isMobHighlighted(entity.getType())) {
			return config.hostileMobArgb() & 0xFFFFFF;
		}

		if (entity instanceof Player) {
			return config.showPlayers && entity != Minecraft.getInstance().player ? config.playerArgb() & 0xFFFFFF : -1;
		}

		JuiceClientRenderer renderer = JuiceClient.RENDERER;

		if (entity instanceof ItemEntity) {
			return config.showItems && renderer.outlinedItems.contains(entity.getId()) ? config.itemArgb() & 0xFFFFFF : -1;
		}

		BaseRule rule = config.showBlockHighlights ? renderer.entityMarks.get(entity.blockPosition().asLong()) : null;
		return rule != null && config.matches(rule, BaseTracker.find(entity)) ? rule.colorArgb() & 0xFFFFFF : -1;
	}

	/** The path as a line, and an X where it lands, flat on the side of the block it hits. */
	private void addTrajectory(LineBuilder lines, Trajectory.Path path, Vec3 camera) {
		lines.color = this.config.trajectoryArgb();
		List<Vec3> points = path.points();

		for (int i = 1; i < points.size(); i++) {
			// It starts just under the eyes, so the first stretch would run up from the bottom of the screen.
			if (points.get(i - 1).distanceToSqr(camera) < TRAJECTORY_START_DISTANCE * TRAJECTORY_START_DISTANCE) {
				continue;
			}

			Vec3 from = points.get(i - 1).subtract(camera);
			Vec3 to = points.get(i).subtract(camera);
			lines.line((float) from.x, (float) from.y, (float) from.z, (float) to.x, (float) to.y, (float) to.z);
		}

		if (path.hit() == null) {
			return;
		}

		Direction face = path.face() != null ? path.face() : Direction.UP;
		// Two directions along the side, and the X just off it so the block doesn't cover it.
		Vec3 u = face.getAxis() == Direction.Axis.X ? new Vec3(0, TRAJECTORY_X_SIZE, 0) : new Vec3(TRAJECTORY_X_SIZE, 0, 0);
		Vec3 w = face.getAxis() == Direction.Axis.Z ? new Vec3(0, TRAJECTORY_X_SIZE, 0) : new Vec3(0, 0, TRAJECTORY_X_SIZE);
		Vec3 center = path.hit().add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(0.01)).subtract(camera);
		Vec3 a = center.subtract(u).subtract(w);
		Vec3 b = center.add(u).add(w);
		Vec3 c = center.subtract(u).add(w);
		Vec3 d = center.add(u).subtract(w);
		lines.line((float) a.x, (float) a.y, (float) a.z, (float) b.x, (float) b.y, (float) b.z);
		lines.line((float) c.x, (float) c.y, (float) c.z, (float) d.x, (float) d.y, (float) d.z);
	}

	/** Whether a throw or shot is being aimed, for tests. */
	public Trajectory.@Nullable Path trajectory() {
		return this.trajectory;
	}

	private void addBlockHighlight(LineBuilder lines, BlockMark mark, Vec3 camera) {
		float minX = (float) (BlockPos.getX(mark.pos()) - camera.x);
		float minY = (float) (BlockPos.getY(mark.pos()) - camera.y);
		float minZ = (float) (BlockPos.getZ(mark.pos()) - camera.z);
		// Fades out up close, so that a block being looked at or mined isn't covered. One behind a wall stays, since
		// then the highlight is the only way to see it.
		float strength = nearStrength(blockDistance(mark.pos(), camera));
		ClientLevel level = Minecraft.getInstance().level;

		if (strength < 1F && level != null && !isInSight(level, mark.pos(), camera)) {
			strength = 1F;
		}

		if (strength <= 0F) {
			return;
		}

		// Along the block's own shape, like the outline of the block being looked at: just the stick of a torch. A block
		// with no shape, such as light, gets the whole block.
		VoxelShape shape = level != null ? level.getBlockState(BlockPos.of(mark.pos())).getShape(level, BlockPos.of(mark.pos())) : Shapes.block();

		if (shape.isEmpty()) {
			shape = Shapes.block();
		}

		int fill = Math.round(BLOCK_FILL_ALPHA * strength) << 24 | mark.argb() & 0xFFFFFF;
		shape.forAllBoxes((x1, y1, z1, x2, y2, z2) -> this.addFill(lines.buffer, lines.pose, fill,
				minX + (float) x1, minY + (float) y1, minZ + (float) z1, minX + (float) x2, minY + (float) y2, minZ + (float) z2));
		lines.color = Math.round(0xFF * strength) << 24 | mark.argb() & 0xFFFFFF;
		shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> lines.line(minX + (float) x1, minY + (float) y1, minZ + (float) z1,
				minX + (float) x2, minY + (float) y2, minZ + (float) z2));
	}

	/** From the camera to the nearest point of a block, so 0 inside it. */
	public static double blockDistance(long pos, Vec3 camera) {
		double dx = Math.max(0, Math.max(BlockPos.getX(pos) - camera.x, camera.x - (BlockPos.getX(pos) + 1)));
		double dy = Math.max(0, Math.max(BlockPos.getY(pos) - camera.y, camera.y - (BlockPos.getY(pos) + 1)));
		double dz = Math.max(0, Math.max(BlockPos.getZ(pos) - camera.z, camera.z - (BlockPos.getZ(pos) + 1)));
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/**
	 * Whether any of a block can be seen from the camera: its middle, or the middle of a side facing the camera, with
	 * nothing in between.
	 */
	public static boolean isInSight(BlockGetter level, long pos, Vec3 camera) {
		BlockPos block = BlockPos.of(pos);
		Vec3 center = Vec3.atCenterOf(block);
		Vec3 toCamera = camera.subtract(center);
		List<Vec3> targets = new ArrayList<>(4);
		targets.add(center);

		// Just inside each side facing the camera, so that a block peeking round a corner counts.
		if (Math.abs(toCamera.x) > 0.5) {
			targets.add(center.add(Math.signum(toCamera.x) * SIGHT_FACE_OFFSET, 0, 0));
		}

		if (Math.abs(toCamera.y) > 0.5) {
			targets.add(center.add(0, Math.signum(toCamera.y) * SIGHT_FACE_OFFSET, 0));
		}

		if (Math.abs(toCamera.z) > 0.5) {
			targets.add(center.add(0, 0, Math.signum(toCamera.z) * SIGHT_FACE_OFFSET));
		}

		for (Vec3 target : targets) {
			BlockHitResult hit = level.clip(new ClipContext(camera, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()));

			if (hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(block)) {
				return true;
			}
		}

		return false;
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
					marks.add(new BlockMark(pos, match.rule().colorArgb(), match.rule()));
				}
			}
		}

		marks.sort(Comparator.comparingDouble(mark -> camera.distanceToSqr(BlockPos.getX(mark.pos()) + 0.5, BlockPos.getY(mark.pos()) + 0.5, BlockPos.getZ(mark.pos()) + 0.5)));
		return marks.size() > max ? List.copyOf(marks.subList(0, max)) : marks;
	}

	/**
	 * Whether a mob gets the highlight outline: alive, and on the mob list, with mob highlights on. Drawn by the game
	 * as if glowing, so it follows the mob's shape rather than a box around it.
	 */
	public static boolean isOutlined(Entity entity) {
		return outlineColor(entity) >= 0;
	}

	/** The boxes of living listed mobs where they appear this frame, keeping the nearest if there are too many. */
	public static List<AABB> nearestMobs(ClientLevel level, JuiceClientConfig config, Vec3 camera, float partialTick, int max) {
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
		boolean nameTagLabels = this.config.nameTags && !this.nameTags.isEmpty();

		if (!chunkLabels && !itemLabels && !nameTagLabels) {
			return;
		}

		Font font = Minecraft.getInstance().font;
		CameraRenderState camera = this.cameraState;
		Matrix4f worldToClip = new Matrix4f(camera.projectionMatrix).mul(camera.viewRotationMatrix);
		List<int[]> drawn = new ArrayList<>();
		float scale = LABEL_SCALE * this.config.labelSize / 100F;

		// Name tags first: they're the nearest, and each is a single short line.
		if (nameTagLabels) {
			for (NameTags.Tag tag : this.nameTags) {
				drawLabel(graphics, font, scale, worldToClip, camera, tag.pos(), List.of(new LabelLine(tag.label(), NAME_TAG_COLOR)), MAX_ITEM_LABEL_SHIFT, drawn);
			}
		}

		// Items first: they're nearby, and their labels are small enough to leave room for the chunks'.
		if (itemLabels) {
			for (DroppedItems.ItemMark item : this.items) {
				String label = this.config.showLabelDistance ? itemLabel(item.label(), item.box().getCenter().distanceTo(camera.pos)) : item.label();
				drawLabel(graphics, font, scale, worldToClip, camera, item.labelPos(), List.of(new LabelLine(label, this.config.itemArgb() | 0xFF000000)), MAX_ITEM_LABEL_SHIFT, drawn);
			}
		}

		if (!chunkLabels) {
			return;
		}

		// Nearest first, so that when labels would overlap the nearest one stays in place.
		for (ChunkHighlight highlight : this.labelled) {
			Vec3 anchor = new Vec3((highlight.chunkX() << 4) + 8, highlight.maxY() + 1, (highlight.chunkZ() << 4) + 8);
			drawLabel(graphics, font, scale, worldToClip, camera, anchor, this.withInfoLine(highlight), MAX_LABEL_SHIFT, drawn);
		}
	}

	/** A label whose bottom sits on a point in the world, moved up by up to {@code maxShift} to clear those drawn. */
	private static void drawLabel(GuiGraphicsExtractor graphics, Font font, float scale, Matrix4f worldToClip, CameraRenderState camera, Vec3 anchor,
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

		width = Mth.ceil(width * scale);
		int height = Mth.ceil(labelLines.size() * LABEL_LINE_HEIGHT * scale);
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
		graphics.pose().scale(scale);
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

	/** An item's name with how far away it is, on one line to keep the label small. */
	public static String itemLabel(String name, double distance) {
		return name + " · " + Math.round(distance) + "m";
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
				.createRenderPass(() -> "Juice Client", colorTexture, Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
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

	/** One block or entity to highlight, as a packed position, and the rule it belongs to with its color. */
	public record BlockMark(long pos, int argb, BaseRule rule) {
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
