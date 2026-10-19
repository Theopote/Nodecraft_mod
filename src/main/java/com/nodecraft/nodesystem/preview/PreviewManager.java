package com.nodecraft.nodesystem.preview;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.bake.PlacementMode;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.preview.protocol.PreviewBlock;
import com.nodecraft.nodesystem.preview.protocol.PreviewBlocksPayload;
import com.nodecraft.nodesystem.preview.protocol.PreviewCurvePayload;
import com.nodecraft.nodesystem.preview.protocol.PreviewFramePayload;
import com.nodecraft.nodesystem.preview.protocol.PreviewGeometryPayload;
import com.nodecraft.nodesystem.preview.protocol.PreviewKind;
import com.nodecraft.nodesystem.preview.protocol.PreviewLabelsPayload;
import com.nodecraft.nodesystem.preview.protocol.PreviewPlanePayload;
import com.nodecraft.nodesystem.preview.protocol.PreviewRegionPayload;
import com.nodecraft.nodesystem.preview.protocol.PreviewPayload;
import com.nodecraft.nodesystem.preview.protocol.PreviewPayloadAdapters;
import com.nodecraft.nodesystem.preview.protocol.PreviewPointsPayload;
import com.nodecraft.nodesystem.preview.protocol.PreviewRequest;
import com.nodecraft.nodesystem.preview.protocol.PreviewStyle;
import com.nodecraft.nodesystem.preview.protocol.PreviewVectorsPayload;
import com.nodecraft.nodesystem.util.BlockStateResolver;
import com.nodecraft.nodesystem.util.Coordinate;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-level helper API for preview rendering.
 * <p>
 * Unified entry: {@link #showPreview(PreviewRequest)}. Convenience helpers
 * ({@code showPaths}, …) construct protocol types and pass {@link PreviewBackend#GHOST}.
 * <p>
 * Sole product dispatch for {@link PreviewBackend#TRACKED_WORLD} block previews;
 * non-{@link PreviewKind#BLOCKS} kinds must stay GHOST.
 * See {@link TrackedWorldCapabilityInventory}.
 */
public final class PreviewManager {

    /**
     * Lazily resolved so bookkeeping helpers (empty-input grace) can run on dedicated servers
     * even when client preview element classes fail to link.
     */
    private static final class RendererAccess {
        private static final PreviewRenderer INSTANCE = PreviewRenderer.getInstance();
    }

    private static PreviewRenderer renderer() {
        return RendererAccess.INSTANCE;
    }

    private static final long EMPTY_INPUT_GRACE_MS = 750L;
    /** Coalesce rapid identical upserts so nodes can publish outputs every evaluation. */
    private static final long UPSERT_COALESCE_MS = 50L;
    private static final Map<String, Long> LAST_NON_EMPTY_BY_NODE_TYPE = new ConcurrentHashMap<>();
    private static final Map<String, PreviewBackend> LAST_BLOCKS_BACKEND_BY_NODE = new ConcurrentHashMap<>();
    private static final Map<String, TrackedWorldRequestState> LAST_TRACKED_WORLD_REQUEST_BY_NODE = new ConcurrentHashMap<>();
    private static final Map<String, CoalesceState> LAST_COALESCE_BY_NODE_TYPE = new ConcurrentHashMap<>();

    private PreviewManager() {
    }

    private static String nodeTypeKey(String nodeId, String previewType) {
        return nodeId + "|" + previewType;
    }

    private static void touchNonEmpty(String nodeId, String previewType) {
        LAST_NON_EMPTY_BY_NODE_TYPE.put(nodeTypeKey(nodeId, previewType), System.currentTimeMillis());
    }

    private static boolean withinEmptyGrace(String nodeId, String previewType) {
        Long last = LAST_NON_EMPTY_BY_NODE_TYPE.get(nodeTypeKey(nodeId, previewType));
        return last != null && (System.currentTimeMillis() - last) < EMPTY_INPUT_GRACE_MS;
    }

    /**
     * Drops empty-input retention so the next empty upsert can hide GHOST previews immediately
     * (e.g. after a graph structure / upstream change invalidates prior non-empty output).
     */
    public static void clearEmptyInputGrace() {
        LAST_NON_EMPTY_BY_NODE_TYPE.clear();
    }

    /** Clears empty-input grace for a single owner node without touching unrelated nodes. */
    public static void clearEmptyInputGraceForNode(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return;
        }
        String prefix = nodeId + "|";
        LAST_NON_EMPTY_BY_NODE_TYPE.keySet().removeIf(key -> key.startsWith(prefix));
    }

    /** Visible for unit tests of empty-input grace bookkeeping. */
    public static void touchNonEmptyForTests(String nodeId, String previewType) {
        touchNonEmpty(nodeId, previewType);
    }

    /** Visible for unit tests of empty-input grace bookkeeping. */
    public static boolean withinEmptyGraceForTests(String nodeId, String previewType) {
        return withinEmptyGrace(nodeId, previewType);
    }

    private static void clearNodeBookkeeping(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return;
        }
        String prefix = nodeId + "|";
        LAST_NON_EMPTY_BY_NODE_TYPE.keySet().removeIf(key -> key.startsWith(prefix));
        LAST_COALESCE_BY_NODE_TYPE.keySet().removeIf(key -> key.startsWith(prefix));
        LAST_BLOCKS_BACKEND_BY_NODE.remove(nodeId);
        clearTrackedWorldRequestState(nodeId);
    }

    private static void clearTypePreviewState(String nodeId, String previewType) {
        LAST_NON_EMPTY_BY_NODE_TYPE.remove(nodeTypeKey(nodeId, previewType));
        LAST_COALESCE_BY_NODE_TYPE.remove(nodeTypeKey(nodeId, previewType));
        renderer().hidePreviewsByNodeAndType(nodeId, previewType);
    }

    /**
     * Returns a previous preview id when an identical upsert landed within {@link #UPSERT_COALESCE_MS}.
     */
    @Nullable
    private static String coalesceIdenticalUpsert(String nodeId, String previewType, long contentFingerprint) {
        String key = nodeTypeKey(nodeId, previewType);
        long now = System.currentTimeMillis();
        CoalesceState previous = LAST_COALESCE_BY_NODE_TYPE.get(key);
        if (previous != null
            && previous.fingerprint() == contentFingerprint
            && previous.previewId() != null
            && (now - previous.atMillis()) < UPSERT_COALESCE_MS
            && renderer().hasActivePreview(previous.previewId())) {
            return previous.previewId();
        }
        return null;
    }

    private static void rememberCoalesce(String nodeId, String previewType, long contentFingerprint, @Nullable String previewId) {
        if (previewId == null) {
            return;
        }
        LAST_COALESCE_BY_NODE_TYPE.put(
            nodeTypeKey(nodeId, previewType),
            new CoalesceState(contentFingerprint, previewId, System.currentTimeMillis())
        );
    }

    private record CoalesceState(long fingerprint, String previewId, long atMillis) {
    }

    private static void clearTrackedWorldRequestState(String nodeId) {
        LAST_TRACKED_WORLD_REQUEST_BY_NODE.remove(nodeId);
    }

    public static String highlightBlock(String nodeId, Coordinate position, PreviewOptions options) {
        try {
            String previewId = renderer().showPreview(nodeId, "block_highlight", position, options);
            if (previewId == null) {
                NodeCraft.LOGGER.error("PreviewManager.highlightBlock failed: renderer.showPreview returned null");
            }
            return previewId;
        } catch (Exception e) {
            NodeCraft.LOGGER.error("PreviewManager.highlightBlock failed: nodeId={}, position={}", nodeId, position, e);
            return null;
        }
    }

    public static String highlightBlocks(String nodeId, List<Coordinate> positions, PreviewOptions options) {
        return renderer().showPreview(nodeId, "block_highlight", positions, options);
    }

    /**
     * v1 unified entry: dispatches on {@link PreviewKind} and {@link PreviewBackend}.
     * Implements {@link PreviewKind#BLOCKS} (GHOST + TRACKED_WORLD), {@link PreviewKind#POINTS},
     * {@link PreviewKind#VECTORS}, {@link PreviewKind#REGIONS}, {@link PreviewKind#CURVES},
     * {@link PreviewKind#GEOMETRY}, {@link PreviewKind#PLANE}, {@link PreviewKind#FRAME},
     * {@link PreviewKind#LABELS} (GHOST only).
     */
    @Nullable
    public static String showPreview(PreviewRequest request) {
        try {
            String nodeId = request.ownerNodeId();
            PreviewPayload payload = request.payload();
            PreviewKind kind = payload.getKind();
            PreviewOptions opts = request.style().toPreviewOptions(kind);

            if (request.backend() != PreviewBackend.GHOST && kind != PreviewKind.BLOCKS) {
                NodeCraft.LOGGER.warn(
                    "PreviewManager.showPreview: backend {} not supported for kind {}",
                    request.backend(),
                    kind
                );
                return null;
            }

            ExecutionContext ctx = request.executionContext();

            return switch (kind) {
                case BLOCKS -> showPreviewBlocks(request, nodeId, payload, opts, ctx);
                case POINTS -> showPreviewPoints(nodeId, payload, opts);
                case VECTORS -> showPreviewVectors(nodeId, payload, opts);
                case REGIONS -> showPreviewRegions(nodeId, payload, opts);
                case CURVES -> showPreviewCurves(nodeId, payload, opts);
                case GEOMETRY -> showPreviewGeometry(nodeId, payload, opts);
                case PLANE -> showPreviewPlaneGrid(nodeId, payload, opts);
                case FRAME -> showPreviewFrameAxes(nodeId, payload, opts);
                case LABELS -> showPreviewTextLabels(nodeId, payload, opts);
            };
        } catch (Exception e) {
            NodeCraft.LOGGER.error("PreviewManager.showPreview failed: nodeId={}", request.ownerNodeId(), e);
            return null;
        }
    }

    @Nullable
    private static String showPreviewBlocks(
        PreviewRequest request,
        String nodeId,
        PreviewPayload payload,
        PreviewOptions opts,
        @Nullable ExecutionContext ctx
    ) {
        if (!(payload instanceof PreviewBlocksPayload blocksPayload)) {
            NodeCraft.LOGGER.warn(
                "PreviewManager.showPreview BLOCKS: wrong concrete type {}",
                payload.getClass().getName()
            );
            return null;
        }

        PreviewBackend previousBackend = LAST_BLOCKS_BACKEND_BY_NODE.put(nodeId, request.backend());
        if (previousBackend != null && previousBackend != request.backend()) {
            // Force immediate backend switch visibility update even when transient empty input is present.
            renderer().hidePreviewsByNodeAndType(nodeId, "ghost_block");
            TrackedPreviewPlacementService.getInstance().clearTrackedPreviewAcrossWorlds(nodeId, ctx);
            clearTrackedWorldRequestState(nodeId);
        }

        if (request.backend() == PreviewBackend.GHOST) {
            if (blocksPayload.getBlocks().isEmpty()) {
                if (withinEmptyGrace(nodeId, "ghost_block")) {
                    return null;
                }
                clearTypePreviewState(nodeId, "ghost_block");
                clearTrackedWorldRequestState(nodeId);
                return null;
            }
            touchNonEmpty(nodeId, "ghost_block");
            // Backend switch safety: entering GHOST should always clear tracked-world remnants immediately.
            TrackedPreviewPlacementService.getInstance().clearTrackedPreviewAcrossWorlds(nodeId, ctx);
            clearTrackedWorldRequestState(nodeId);
            long fingerprint = PreviewFingerprint.ofPreviewBlocks(blocksPayload.getBlocks());
            String coalesced = coalesceIdenticalUpsert(nodeId, "ghost_block", fingerprint);
            if (coalesced != null) {
                return coalesced;
            }
            String previewId = renderer().upsertPreview(nodeId, "ghost_block", blocksPayload, opts);
            rememberCoalesce(nodeId, "ghost_block", fingerprint, previewId);
            return previewId;
        }

        if (request.backend() == PreviewBackend.TRACKED_WORLD) {
            // Backend switch safety: entering TRACKED_WORLD should clear ghost overlays immediately.
            renderer().hidePreviewsByNodeAndType(nodeId, "ghost_block");
            if (ctx == null || ctx.getWorld() == null) {
                NodeCraft.LOGGER.warn("PreviewManager.showPreview TRACKED_WORLD: missing execution context / world");
                return null;
            }
            List<PreviewBlock> cells = blocksPayload.getBlocks();
            if (cells.isEmpty()) {
                TrackedPreviewPlacementService.getInstance().clearTrackedPreviewOnWorldThread(ctx.getWorld(), nodeId, ctx);
                clearTrackedWorldRequestState(nodeId);
                return nodeId + ":tracked:cleared";
            }
            if (!isUniformTrackedWorldPayload(cells)) {
                NodeCraft.LOGGER.warn(
                    "PreviewManager.showPreview TRACKED_WORLD: heterogeneous BlockState payload — "
                        + "falling back to GHOST (Tracked World is uniform-state only)."
                );
                // Compatibility backend cannot express per-cell materials; demote to Ghost.
                TrackedPreviewPlacementService.getInstance().clearTrackedPreviewAcrossWorlds(nodeId, ctx);
                clearTrackedWorldRequestState(nodeId);
                LAST_BLOCKS_BACKEND_BY_NODE.put(nodeId, PreviewBackend.GHOST);
                touchNonEmpty(nodeId, "ghost_block");
                return renderer().upsertPreview(nodeId, "ghost_block", blocksPayload, opts);
            }
            BlockState state = BlockStateResolver.resolve(cells.getFirst().blockId(), cells.getFirst().stateData());
            if (state == null) {
                NodeCraft.LOGGER.warn("PreviewManager.showPreview TRACKED_WORLD: invalid block id {}", cells.getFirst().blockId());
                return null;
            }
            List<BlockPos> positions = PreviewPayloadAdapters.toBlockPosList(blocksPayload);
            String worldKey = ctx.getWorld().getRegistryKey().getValue().toString();
            int requestSignature = computeTrackedWorldRequestSignature(worldKey, cells.getFirst().blockId(), positions);
            TrackedWorldRequestState lastRequestState = LAST_TRACKED_WORLD_REQUEST_BY_NODE.get(nodeId);
            if (lastRequestState != null && lastRequestState.matches(worldKey, requestSignature)) {
                return lastRequestState.previewId();
            }

            int placed = TrackedPreviewPlacementService.getInstance().updateTrackedPreviewOnWorldThread(
                ctx.getWorld(),
                nodeId,
                new ArrayList<>(positions),
                state,
                PlacementMode.OVERWRITE,
                ctx
            );
            String previewId = nodeId + ":tracked:" + placed;
            LAST_TRACKED_WORLD_REQUEST_BY_NODE.put(nodeId, new TrackedWorldRequestState(worldKey, requestSignature, previewId));
            return previewId;
        }

        NodeCraft.LOGGER.warn("PreviewManager.showPreview BLOCKS: unsupported backend {}", request.backend());
        return null;
    }

    @Nullable
    private static String showPreviewPoints(String nodeId, PreviewPayload payload, PreviewOptions opts) {
        if (!(payload instanceof PreviewPointsPayload pointsPayload)) {
            return null;
        }
        if (pointsPayload.getPoints().isEmpty()) {
            if (!withinEmptyGrace(nodeId, "points")) {
                clearTypePreviewState(nodeId, "points");
            }
            return null;
        }
        touchNonEmpty(nodeId, "points");
        return renderer().upsertPreview(nodeId, "points", pointsPayload, opts);
    }

    @Nullable
    private static String showPreviewVectors(String nodeId, PreviewPayload payload, PreviewOptions opts) {
        if (!(payload instanceof PreviewVectorsPayload vectorsPayload)) {
            return null;
        }
        if (vectorsPayload.getVectors().isEmpty()) {
            if (!withinEmptyGrace(nodeId, "vectors")) {
                clearTypePreviewState(nodeId, "vectors");
            }
            return null;
        }
        touchNonEmpty(nodeId, "vectors");
        return renderer().upsertPreview(nodeId, "vectors", vectorsPayload, opts);
    }

    @Nullable
    private static String showPreviewRegions(String nodeId, PreviewPayload payload, PreviewOptions opts) {
        if (!(payload instanceof PreviewRegionPayload regionPayload)) {
            return null;
        }
        touchNonEmpty(nodeId, "region_box");
        return renderer().upsertPreview(nodeId, "region_box", regionPayload, opts);
    }

    @Nullable
    private static String showPreviewCurves(String nodeId, PreviewPayload payload, PreviewOptions opts) {
        if (!(payload instanceof PreviewCurvePayload curvePayload)) {
            return null;
        }
        if (curvePayload.getPoints().size() < 2) {
            if (!withinEmptyGrace(nodeId, "paths")) {
                clearTypePreviewState(nodeId, "paths");
            }
            return null;
        }
        touchNonEmpty(nodeId, "paths");
        return renderer().upsertPreview(nodeId, "paths", curvePayload, opts);
    }

    /**
     * Polyline-style path preview using v1 {@link PreviewCurvePayload}.
     */
    public static String showCurve(String nodeId, PreviewCurvePayload payload, PreviewOptions options) {
        if (payload == null || payload.getPoints().size() < 2) {
            hideNodePreviews(nodeId);
            return null;
        }
        PreviewStyle style = PreviewStyle.from(options, PreviewKind.CURVES);
        return showPreview(new PreviewRequest(nodeId, payload, style, PreviewBackend.GHOST, null));
    }

    /**
     * Multiple path/curve previews for one node: optionally {@link #hideNodePreviews} first, then each
     * {@link PreviewCurvePayload} as {@code paths}. Use {@code replaceNodePreviews=false} after an initial
     * manual clear to append another batch (e.g. section paths then rail segments with different styles).
     */
    public static List<String> showPathCurves(String nodeId, List<PreviewCurvePayload> curves, PreviewOptions options) {
        return showPathCurves(nodeId, curves, options, true);
    }

    public static List<String> showPathCurves(
        String nodeId,
        List<PreviewCurvePayload> curves,
        PreviewOptions options,
        boolean replaceNodePreviews
    ) {
        if (curves == null || curves.isEmpty()) {
            if (replaceNodePreviews) {
                if (!withinEmptyGrace(nodeId, "paths")) {
                    clearTypePreviewState(nodeId, "paths");
                }
            }
            return List.of();
        }
        PreviewStyle style = PreviewStyle.from(options, PreviewKind.CURVES);
        PreviewOptions opts = style.toPreviewOptions(PreviewKind.CURVES);
        List<PreviewCurvePayload> valid = new ArrayList<>();
        for (PreviewCurvePayload c : curves) {
            if (c != null && c.getPoints().size() >= 2) {
                valid.add(c);
            }
        }
        if (replaceNodePreviews) {
            touchNonEmpty(nodeId, "paths");
            return renderer().upsertPreviews(nodeId, "paths", valid, opts);
        }
        List<String> ids = new ArrayList<>();
        for (PreviewCurvePayload c : valid) {
            String id = renderer().showPreview(nodeId, "paths", c, opts);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    /**
     * Multiple geometry-surface previews for one node: clears node previews once, then registers each payload.
     */
    public static List<String> showGeometrySurfaces(String nodeId, List<GeometryData> geometries, PreviewOptions options) {
        if (geometries == null || geometries.isEmpty()) {
            if (!withinEmptyGrace(nodeId, "geometry_surface")) {
                clearTypePreviewState(nodeId, "geometry_surface");
            }
            return List.of();
        }
        touchNonEmpty(nodeId, "geometry_surface");
        PreviewStyle style = PreviewStyle.from(options, PreviewKind.GEOMETRY);
        PreviewOptions opts = style.toPreviewOptions(PreviewKind.GEOMETRY);
        List<PreviewGeometryPayload> payloads = new ArrayList<>();
        for (GeometryData g : geometries) {
            if (g == null) {
                continue;
            }
            payloads.add(new PreviewGeometryPayload(g));
        }
        return renderer().upsertPreviews(nodeId, "geometry_surface", payloads, withDefaultGeometryLod(opts));
    }

    @Nullable
    private static String showPreviewGeometry(String nodeId, PreviewPayload payload, PreviewOptions opts) {
        if (!(payload instanceof PreviewGeometryPayload geometryPayload)) {
            return null;
        }
        if (geometryPayload.getGeometry() == null) {
            if (!withinEmptyGrace(nodeId, "geometry_surface")) {
                clearTypePreviewState(nodeId, "geometry_surface");
            }
            return null;
        }
        touchNonEmpty(nodeId, "geometry_surface");
        return renderer().upsertPreview(nodeId, "geometry_surface", geometryPayload, withDefaultGeometryLod(opts));
    }

    private static PreviewOptions withDefaultGeometryLod(PreviewOptions opts) {
        PreviewOptions applied = opts != null ? opts : new PreviewOptions();
        if (applied.enableLOD == null) {
            applied.enableLOD = true;
        }
        return applied;
    }

    @Nullable
    private static String showPreviewPlaneGrid(String nodeId, PreviewPayload payload, PreviewOptions opts) {
        if (!(payload instanceof PreviewPlanePayload planePayload)) {
            return null;
        }
        if (planePayload.getPlaneGridData() == null || planePayload.getPlaneGridData().getPlane() == null) {
            if (!withinEmptyGrace(nodeId, "plane_grid")) {
                clearTypePreviewState(nodeId, "plane_grid");
            }
            return null;
        }
        touchNonEmpty(nodeId, "plane_grid");
        return renderer().upsertPreview(nodeId, "plane_grid", planePayload, opts);
    }

    @Nullable
    private static String showPreviewFrameAxes(String nodeId, PreviewPayload payload, PreviewOptions opts) {
        if (!(payload instanceof PreviewFramePayload framePayload)) {
            return null;
        }
        if (framePayload.getFrameData() == null) {
            if (!withinEmptyGrace(nodeId, "frame_axes")) {
                clearTypePreviewState(nodeId, "frame_axes");
            }
            return null;
        }
        touchNonEmpty(nodeId, "frame_axes");
        return renderer().upsertPreview(nodeId, "frame_axes", framePayload, opts);
    }

    @Nullable
    private static String showPreviewTextLabels(String nodeId, PreviewPayload payload, PreviewOptions opts) {
        if (!(payload instanceof PreviewLabelsPayload labelsPayload)) {
            return null;
        }
        if (labelsPayload.getLabelData() == null) {
            if (!withinEmptyGrace(nodeId, "text_labels")) {
                clearTypePreviewState(nodeId, "text_labels");
            }
            return null;
        }
        touchNonEmpty(nodeId, "text_labels");
        return renderer().upsertPreview(nodeId, "text_labels", labelsPayload, opts);
    }

    // Region box

    /**
     * Shows a region preview from min-inclusive / max-exclusive world-space corners.
     * <p>
     * Example: min=(10,64,10), maxExclusive=(13,67,13) previews blocks x:[10..12], y:[64..66], z:[10..12].
     */
    public static String showRegionBox(String nodeId, Vec3d min, Vec3d maxExclusive) {
        return showRegionBox(nodeId, min, maxExclusive, PreviewOptions.createRegionBox());
    }

    /**
     * Shows a region preview from min-inclusive / max-exclusive world-space corners.
     */
    public static String showRegionBox(String nodeId, Vec3d min, Vec3d maxExclusive, PreviewOptions options) {
        PreviewRegionPayload payload = PreviewPayloadAdapters.regionFromBoxCorners(min, maxExclusive);
        PreviewStyle style = PreviewStyle.from(options, PreviewKind.REGIONS);
        return showPreview(new PreviewRequest(nodeId, payload, style, PreviewBackend.GHOST, null));
    }


    public static String showPoints(String nodeId, List<Coordinate> points, PreviewOptions options) {
        if (points == null || points.isEmpty()) {
            hideNodePreviews(nodeId);
            return null;
        }
        PreviewPointsPayload payload = PreviewPayloadAdapters.previewPointsFromCoordinates(points);
        PreviewStyle style = PreviewStyle.from(options, PreviewKind.POINTS);
        return showPreview(new PreviewRequest(nodeId, payload, style, PreviewBackend.GHOST, null));
    }

    public static String showVectors(
            String nodeId,
            List<Vec3d> vectors,
            List<Vec3d> startPoints,
            PreviewOptions options
    ) {
        if (vectors == null || startPoints == null || vectors.isEmpty() || startPoints.isEmpty()) {
            hideNodePreviews(nodeId);
            return null;
        }
        PreviewVectorsPayload payload = PreviewPayloadAdapters.previewVectorsFromVecLists(vectors, startPoints);
        PreviewStyle style = PreviewStyle.from(options, PreviewKind.VECTORS);
        return showPreview(new PreviewRequest(nodeId, payload, style, PreviewBackend.GHOST, null));
    }

    // Plane / frame / path / labels

    public static String showPlaneGrid(String nodeId, PlaneGridPreviewData planeGridData, PreviewOptions options) {
        if (planeGridData == null || planeGridData.getPlane() == null) {
            hideNodePreviews(nodeId);
            return null;
        }
        PreviewPlanePayload payload = new PreviewPlanePayload(planeGridData);
        PreviewStyle style = PreviewStyle.from(options, PreviewKind.PLANE);
        return showPreview(new PreviewRequest(nodeId, payload, style, PreviewBackend.GHOST, null));
    }

    public static String showFrameAxes(String nodeId, FrameAxesPreviewData frameAxesData, PreviewOptions options) {
        if (frameAxesData == null) {
            hideNodePreviews(nodeId);
            return null;
        }
        PreviewFramePayload payload = new PreviewFramePayload(frameAxesData);
        PreviewStyle style = PreviewStyle.from(options, PreviewKind.FRAME);
        return showPreview(new PreviewRequest(nodeId, payload, style, PreviewBackend.GHOST, null));
    }

    public static String showTransformGizmo(String nodeId, TransformGizmoPreviewData gizmoData, PreviewOptions options) {
        if (nodeId == null || nodeId.isBlank()) {
            return null;
        }
        if (gizmoData == null) {
            hideNodePreviewType(nodeId, "transformation_gizmo");
            return null;
        }
        PreviewOptions resolved = options != null ? options : PreviewOptions.createTransformGizmo();
        java.util.List<String> existing = renderer().getPreviewIdsByNodeAndType(nodeId, "transformation_gizmo");
        if (!existing.isEmpty()) {
            String previewId = existing.getFirst();
            renderer().updatePreview(previewId, gizmoData, resolved);
            return previewId;
        }
        return renderer().showPreview(nodeId, "transformation_gizmo", gizmoData, resolved);
    }

    public static String showTextLabels(String nodeId, Object labelData, PreviewOptions options) {
        if (labelData == null) {
            hideNodePreviews(nodeId);
            return null;
        }
        PreviewLabelsPayload payload = new PreviewLabelsPayload(labelData);
        PreviewStyle style = PreviewStyle.from(options, PreviewKind.LABELS);
        return showPreview(new PreviewRequest(nodeId, payload, style, PreviewBackend.GHOST, null));
    }

    // Generic control

    public static void hidePreview(String previewId) {
        renderer().hidePreview(previewId);
    }

    public static void hideNodePreviews(String nodeId) {
        hideNodePreviews(nodeId, null);
    }

    public static void hideNodePreviews(String nodeId, @Nullable ExecutionContext context) {
        if (nodeId == null || nodeId.isBlank()) {
            return;
        }
        clearNodeBookkeeping(nodeId);
        renderer().hidePreviewsByNode(nodeId);
        TrackedPreviewPlacementService service = TrackedPreviewPlacementService.getInstance();
        if (service.hasAnyTrackedPreviews(nodeId)) {
            service.clearTrackedPreviewAcrossWorlds(nodeId, context);
        }
    }

    /**
     * Hides GHOST (and per-node TRACKED_WORLD) previews whose owner is not in {@code liveNodeIds}.
     * Does not hide remaining live nodes — used after delete / graph replace so cleanup does not
     * depend on the removed node still being in the graph.
     */
    public static void hidePreviewsNotOwnedBy(@Nullable Collection<String> liveNodeIds) {
        Set<String> live = liveNodeIds == null ? Set.of() : new HashSet<>(liveNodeIds);
        Set<String> owners = new HashSet<>(renderer().ownerNodeIds());
        owners.addAll(bookkeepingOwnerNodeIds());
        owners.addAll(TrackedPreviewPlacementService.getInstance().snapshotOwnerNodeIds());
        for (String owner : owners) {
            if (owner != null && !live.contains(owner)) {
                hideNodePreviews(owner);
            }
        }
    }

    private static Set<String> bookkeepingOwnerNodeIds() {
        Set<String> ids = new HashSet<>();
        collectOwnerPrefix(LAST_NON_EMPTY_BY_NODE_TYPE.keySet(), ids);
        collectOwnerPrefix(LAST_COALESCE_BY_NODE_TYPE.keySet(), ids);
        ids.addAll(LAST_BLOCKS_BACKEND_BY_NODE.keySet());
        ids.addAll(LAST_TRACKED_WORLD_REQUEST_BY_NODE.keySet());
        return ids;
    }

    private static void collectOwnerPrefix(Set<String> keys, Set<String> into) {
        for (String key : keys) {
            if (key == null) {
                continue;
            }
            int pipe = key.indexOf('|');
            into.add(pipe < 0 ? key : key.substring(0, pipe));
        }
    }

    public static void hideNodePreviewType(String nodeId, String previewType) {
        if (nodeId == null || previewType == null || previewType.isBlank()) {
            return;
        }
        clearTypePreviewState(nodeId, previewType);
    }

    /**
     * Clears GHOST renderer state and PreviewManager bookkeeping.
     * Does <em>not</em> restore TRACKED_WORLD blocks — that would mutate the real world;
     * callers that own editor close / world teardown must call
     * {@link TrackedPreviewPlacementService#clearAllTrackedPreviews} separately.
     */
    public static void clearAllPreviews() {
        LAST_NON_EMPTY_BY_NODE_TYPE.clear();
        LAST_COALESCE_BY_NODE_TYPE.clear();
        LAST_BLOCKS_BACKEND_BY_NODE.clear();
        LAST_TRACKED_WORLD_REQUEST_BY_NODE.clear();
        renderer().clearAllPreviews();
    }

    public static int getActivePreviewCount() {
        return renderer().getActivePreviewCount();
    }

    public static boolean hasActivePreview(String previewId) {
        return renderer().hasActivePreview(previewId);
    }

    public static void updatePreview(String previewId, Object newData) {
        renderer().updatePreview(previewId, newData, null);
    }

    public static void updatePreviewOptions(String previewId, PreviewOptions newOptions) {
        renderer().updatePreview(previewId, null, newOptions);
    }

    public static void setGlobalOpacity(float opacity) {
        renderer().setGlobalOpacity(opacity);
    }

    public static PreviewRenderer.PreviewRenderSettings getSettings() {
        return renderer().getSettings();
    }

    /**
     * Tracked World places a single BlockState at every position.
     * Heterogeneous material/state payloads must use Ghost preview
     * ({@link PreviewManager} falls back to GHOST automatically).
     */
    private static boolean isUniformTrackedWorldPayload(List<PreviewBlock> cells) {
        PreviewBlock first = cells.getFirst();
        String blockId = first.blockId();
        var stateData = first.stateData();
        for (int i = 1; i < cells.size(); i++) {
            PreviewBlock cell = cells.get(i);
            if (!java.util.Objects.equals(blockId, cell.blockId())
                    || !java.util.Objects.equals(stateData, cell.stateData())) {
                return false;
            }
        }
        return true;
    }

    private static int computeTrackedWorldRequestSignature(String worldKey, String blockId, List<BlockPos> positions) {
        int hash = 17;
        hash = 31 * hash + (worldKey != null ? worldKey.hashCode() : 0);
        hash = 31 * hash + (blockId != null ? blockId.hashCode() : 0);

        int size = 0;
        long sumX = 0L;
        long sumY = 0L;
        long sumZ = 0L;
        int xorHash = 0;
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (BlockPos pos : positions) {
            if (pos == null) {
                continue;
            }
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();
            size++;
            sumX += x;
            sumY += y;
            sumZ += z;
            xorHash ^= pos.hashCode();
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        hash = 31 * hash + size;
        hash = 31 * hash + xorHash;
        hash = 31 * hash + Long.hashCode(sumX);
        hash = 31 * hash + Long.hashCode(sumY);
        hash = 31 * hash + Long.hashCode(sumZ);
        hash = 31 * hash + minX;
        hash = 31 * hash + minY;
        hash = 31 * hash + minZ;
        hash = 31 * hash + maxX;
        hash = 31 * hash + maxY;
        hash = 31 * hash + maxZ;
        return hash;
    }

    private record TrackedWorldRequestState(String worldKey, int signature, String previewId) {
        private boolean matches(String currentWorldKey, int currentSignature) {
            return signature == currentSignature && java.util.Objects.equals(worldKey, currentWorldKey);
        }
    }

}
