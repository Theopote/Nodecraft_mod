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
import com.nodecraft.nodesystem.util.Coordinate;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-level helper API for preview rendering.
 * <p>统一入口为 {@link #showPreview(PreviewRequest)}；{@code showPaths} 等便捷方法内部转调协议类型。
 * 新节点请直接构造 {@link com.nodecraft.nodesystem.preview.protocol.PreviewRequest}。
 */
public final class PreviewManager {

    private static final PreviewRenderer RENDERER = PreviewRenderer.getInstance();
    private static final long EMPTY_INPUT_GRACE_MS = 750L;
    private static final Map<String, Long> LAST_NON_EMPTY_BY_NODE_TYPE = new ConcurrentHashMap<>();
    private static final Map<String, PreviewBackend> LAST_BLOCKS_BACKEND_BY_NODE = new ConcurrentHashMap<>();

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

    private static void clearTypePreviewState(String nodeId, String previewType) {
        LAST_NON_EMPTY_BY_NODE_TYPE.remove(nodeTypeKey(nodeId, previewType));
        RENDERER.hidePreviewsByNodeAndType(nodeId, previewType);
    }

    public static String highlightBlock(String nodeId, Coordinate position, PreviewOptions options) {
        try {
            String previewId = RENDERER.showPreview(nodeId, "block_highlight", position, options);
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
        return RENDERER.showPreview(nodeId, "block_highlight", positions, options);
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
                case SURFACE_STRIP -> {
                    NodeCraft.LOGGER.warn(
                        "PreviewManager.showPreview: SURFACE_STRIP preview is deprecated/unimplemented; use GEOMETRY payload instead"
                    );
                    yield null;
                }
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
            RENDERER.hidePreviewsByNodeAndType(nodeId, "ghost_block");
            TrackedPreviewPlacementService.getInstance().clearTrackedPreviewAcrossWorlds(nodeId);
        }

        if (request.backend() == PreviewBackend.GHOST) {
            if (blocksPayload.getBlocks().isEmpty()) {
                if (withinEmptyGrace(nodeId, "ghost_block")) {
                    return null;
                }
                clearTypePreviewState(nodeId, "ghost_block");
                return null;
            }
            touchNonEmpty(nodeId, "ghost_block");
            // Backend switch safety: entering GHOST should always clear tracked-world remnants immediately.
            TrackedPreviewPlacementService.getInstance().clearTrackedPreviewAcrossWorlds(nodeId);
            return RENDERER.upsertPreview(nodeId, "ghost_block", blocksPayload, opts);
        }

        if (request.backend() == PreviewBackend.TRACKED_WORLD) {
            // Backend switch safety: entering TRACKED_WORLD should clear ghost overlays immediately.
            RENDERER.hidePreviewsByNodeAndType(nodeId, "ghost_block");
            if (ctx == null || ctx.getWorld() == null) {
                NodeCraft.LOGGER.warn("PreviewManager.showPreview TRACKED_WORLD: missing execution context / world");
                return null;
            }
            List<PreviewBlock> cells = blocksPayload.getBlocks();
            if (cells.isEmpty()) {
                TrackedPreviewPlacementService.getInstance().clearTrackedPreview(ctx.getWorld(), nodeId);
                return nodeId + ":tracked:cleared";
            }
            BlockState state = resolveBlockStateForPreview(cells.getFirst().blockId());
            if (state == null) {
                NodeCraft.LOGGER.warn("PreviewManager.showPreview TRACKED_WORLD: invalid block id {}", cells.getFirst().blockId());
                return null;
            }
            List<BlockPos> positions = PreviewPayloadAdapters.toBlockPosList(blocksPayload);
            int placed = TrackedPreviewPlacementService.getInstance().updateTrackedPreview(
                ctx.getWorld(),
                nodeId,
                new ArrayList<>(positions),
                state,
                PlacementMode.OVERWRITE
            );
            return nodeId + ":tracked:" + placed;
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
        return RENDERER.upsertPreview(nodeId, "points", pointsPayload, opts);
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
        return RENDERER.upsertPreview(nodeId, "vectors", vectorsPayload, opts);
    }

    @Nullable
    private static String showPreviewRegions(String nodeId, PreviewPayload payload, PreviewOptions opts) {
        if (!(payload instanceof PreviewRegionPayload regionPayload)) {
            return null;
        }
        touchNonEmpty(nodeId, "region_box");
        return RENDERER.upsertPreview(nodeId, "region_box", regionPayload, opts);
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
        return RENDERER.upsertPreview(nodeId, "paths", curvePayload, opts);
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
            return RENDERER.upsertPreviews(nodeId, "paths", valid, opts);
        }
        List<String> ids = new ArrayList<>();
        for (PreviewCurvePayload c : valid) {
            String id = RENDERER.showPreview(nodeId, "paths", c, opts);
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
        return RENDERER.upsertPreviews(nodeId, "geometry_surface", payloads, opts);
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
        return RENDERER.upsertPreview(nodeId, "geometry_surface", geometryPayload, opts);
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
        return RENDERER.upsertPreview(nodeId, "plane_grid", planePayload, opts);
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
        return RENDERER.upsertPreview(nodeId, "frame_axes", framePayload, opts);
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
        return RENDERER.upsertPreview(nodeId, "text_labels", labelsPayload, opts);
    }

    @Nullable
    private static BlockState resolveBlockStateForPreview(String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            return null;
        }
        try {
            Identifier id = Identifier.of(blockId);
            var block = Registries.BLOCK.get(id);
            return block.getDefaultState();
        } catch (Exception e) {
            return null;
        }
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
        RENDERER.hidePreview(previewId);
    }

    public static void hideNodePreviews(String nodeId) {
        RENDERER.hidePreviewsByNode(nodeId);
        TrackedPreviewPlacementService service = TrackedPreviewPlacementService.getInstance();
        if (service.hasAnyTrackedPreviews(nodeId)) {
            service.clearTrackedPreviewAcrossWorlds(nodeId);
        }
    }

    public static void clearAllPreviews() {
        RENDERER.clearAllPreviews();
    }

    public static int getActivePreviewCount() {
        return RENDERER.getActivePreviewCount();
    }

    public static boolean hasActivePreview(String previewId) {
        return RENDERER.hasActivePreview(previewId);
    }

    public static void updatePreview(String previewId, Object newData) {
        RENDERER.updatePreview(previewId, newData, null);
    }

    public static void updatePreviewOptions(String previewId, PreviewOptions newOptions) {
        RENDERER.updatePreview(previewId, null, newOptions);
    }

    public static void setGlobalOpacity(float opacity) {
        RENDERER.setGlobalOpacity(opacity);
    }

    public static PreviewRenderer.PreviewRenderSettings getSettings() {
        return RENDERER.getSettings();
    }

}
