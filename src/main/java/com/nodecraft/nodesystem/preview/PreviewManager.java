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

/**
 * High-level helper API for preview rendering.
 * <p>统一入口为 {@link #showPreview(PreviewRequest)}；{@code showPaths} 等便捷方法内部转调协议类型。
 * 新节点请直接构造 {@link com.nodecraft.nodesystem.preview.protocol.PreviewRequest}。
 */
public final class PreviewManager {

    private static final PreviewRenderer RENDERER = PreviewRenderer.getInstance();

    private PreviewManager() {
    }

    // Block highlight

    public static String highlightBlock(String nodeId, Coordinate position) {
        return highlightBlock(nodeId, position, PreviewOptions.createBlockHighlight());
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

    public static String highlightBlocks(String nodeId, List<Coordinate> positions) {
        return highlightBlocks(nodeId, positions, PreviewOptions.createBlockHighlight());
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
                default -> {
                    NodeCraft.LOGGER.warn(
                        "PreviewManager.showPreview: unsupported payload kind={} type={}",
                        kind,
                        payload.getClass().getName()
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

        hideNodePreviews(nodeId);

        if (request.backend() == PreviewBackend.GHOST) {
            if (ctx != null && ctx.getWorld() != null) {
                TrackedPreviewPlacementService.getInstance().clearTrackedPreview(ctx.getWorld(), nodeId);
            }
            return RENDERER.showPreview(nodeId, "ghost_block", blocksPayload, opts);
        }

        if (request.backend() == PreviewBackend.TRACKED_WORLD) {
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
            hideNodePreviews(nodeId);
            return null;
        }
        hideNodePreviews(nodeId);
        return RENDERER.showPreview(nodeId, "points", pointsPayload, opts);
    }

    @Nullable
    private static String showPreviewVectors(String nodeId, PreviewPayload payload, PreviewOptions opts) {
        if (!(payload instanceof PreviewVectorsPayload vectorsPayload)) {
            return null;
        }
        if (vectorsPayload.getVectors().isEmpty()) {
            hideNodePreviews(nodeId);
            return null;
        }
        hideNodePreviews(nodeId);
        return RENDERER.showPreview(nodeId, "vectors", vectorsPayload, opts);
    }

    @Nullable
    private static String showPreviewRegions(String nodeId, PreviewPayload payload, PreviewOptions opts) {
        if (!(payload instanceof PreviewRegionPayload regionPayload)) {
            return null;
        }
        hideNodePreviews(nodeId);
        return RENDERER.showPreview(nodeId, "region_box", regionPayload, opts);
    }

    @Nullable
    private static String showPreviewCurves(String nodeId, PreviewPayload payload, PreviewOptions opts) {
        if (!(payload instanceof PreviewCurvePayload curvePayload)) {
            return null;
        }
        if (curvePayload.getPoints().size() < 2) {
            hideNodePreviews(nodeId);
            return null;
        }
        hideNodePreviews(nodeId);
        return RENDERER.showPreview(nodeId, "paths", curvePayload, opts);
    }

    /**
     * Polyline-style path preview using v1 {@link PreviewCurvePayload}.
     */
    public static String showCurve(String nodeId, PreviewCurvePayload payload, PreviewOptions options) {
        if (payload == null || payload.getPoints().size() < 2) {
            hideNodePreviews(nodeId);
            return null;
        }
        PreviewStyle style = PreviewStyle.fromLegacyPathOptions(options);
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
                hideNodePreviews(nodeId);
            }
            return List.of();
        }
        if (replaceNodePreviews) {
            hideNodePreviews(nodeId);
        }
        PreviewStyle style = PreviewStyle.fromLegacyPathOptions(options);
        PreviewOptions opts = style.toPreviewOptions(PreviewKind.CURVES);
        List<String> ids = new ArrayList<>();
        for (PreviewCurvePayload c : curves) {
            if (c == null || c.getPoints().size() < 2) {
                continue;
            }
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
            hideNodePreviews(nodeId);
            return List.of();
        }
        hideNodePreviews(nodeId);
        PreviewStyle style = PreviewStyle.fromLegacyGeometryOptions(options);
        PreviewOptions opts = style.toPreviewOptions(PreviewKind.GEOMETRY);
        List<String> ids = new ArrayList<>();
        for (GeometryData g : geometries) {
            if (g == null) {
                continue;
            }
            String id = RENDERER.showPreview(nodeId, "geometry_surface", new PreviewGeometryPayload(g), opts);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    @Nullable
    private static String showPreviewGeometry(String nodeId, PreviewPayload payload, PreviewOptions opts) {
        if (!(payload instanceof PreviewGeometryPayload geometryPayload)) {
            return null;
        }
        if (geometryPayload.getGeometry() == null) {
            hideNodePreviews(nodeId);
            return null;
        }
        hideNodePreviews(nodeId);
        return RENDERER.showPreview(nodeId, "geometry_surface", geometryPayload, opts);
    }

    @Nullable
    private static String showPreviewPlaneGrid(String nodeId, PreviewPayload payload, PreviewOptions opts) {
        if (!(payload instanceof PreviewPlanePayload planePayload)) {
            return null;
        }
        if (planePayload.getPlaneGridData() == null || planePayload.getPlaneGridData().getPlane() == null) {
            hideNodePreviews(nodeId);
            return null;
        }
        hideNodePreviews(nodeId);
        return RENDERER.showPreview(nodeId, "plane_grid", planePayload, opts);
    }

    @Nullable
    private static String showPreviewFrameAxes(String nodeId, PreviewPayload payload, PreviewOptions opts) {
        if (!(payload instanceof PreviewFramePayload framePayload)) {
            return null;
        }
        if (framePayload.getFrameData() == null) {
            hideNodePreviews(nodeId);
            return null;
        }
        hideNodePreviews(nodeId);
        return RENDERER.showPreview(nodeId, "frame_axes", framePayload, opts);
    }

    @Nullable
    private static String showPreviewTextLabels(String nodeId, PreviewPayload payload, PreviewOptions opts) {
        if (!(payload instanceof PreviewLabelsPayload labelsPayload)) {
            return null;
        }
        if (labelsPayload.getLabelData() == null) {
            hideNodePreviews(nodeId);
            return null;
        }
        hideNodePreviews(nodeId);
        return RENDERER.showPreview(nodeId, "text_labels", labelsPayload, opts);
    }

    @Nullable
    private static BlockState resolveBlockStateForPreview(String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            return null;
        }
        try {
            Identifier id = Identifier.of(blockId);
            var block = Registries.BLOCK.get(id);
            return block != null ? block.getDefaultState() : null;
        } catch (Exception e) {
            return null;
        }
    }

    // Region box

    public static String showRegionBox(String nodeId, Vec3d min, Vec3d max) {
        return showRegionBox(nodeId, min, max, PreviewOptions.createRegionBox());
    }

    public static String showRegionBox(String nodeId, Vec3d min, Vec3d max, PreviewOptions options) {
        PreviewRegionPayload payload = PreviewPayloadAdapters.regionFromBoxCorners(min, max);
        PreviewStyle style = PreviewStyle.fromLegacyRegionOptions(options);
        return showPreview(new PreviewRequest(nodeId, payload, style, PreviewBackend.GHOST, null));
    }

    // Points

    public static String showPoints(String nodeId, List<Coordinate> points) {
        return showPoints(nodeId, points, PreviewOptions.createPoints());
    }

    public static String showPoints(String nodeId, List<Coordinate> points, PreviewOptions options) {
        if (points == null || points.isEmpty()) {
            hideNodePreviews(nodeId);
            return null;
        }
        PreviewPointsPayload payload = PreviewPayloadAdapters.previewPointsFromCoordinates(points);
        PreviewStyle style = PreviewStyle.fromLegacyPointOptions(options);
        return showPreview(new PreviewRequest(nodeId, payload, style, PreviewBackend.GHOST, null));
    }

    // Vectors

    public static String showVectors(String nodeId, List<Vec3d> vectors, List<Vec3d> startPoints) {
        return showVectors(nodeId, vectors, startPoints, PreviewOptions.createVectorArrows());
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
        PreviewStyle style = PreviewStyle.fromLegacyVectorOptions(options);
        return showPreview(new PreviewRequest(nodeId, payload, style, PreviewBackend.GHOST, null));
    }

    // Plane / frame / path / labels

    public static String showPlaneGrid(String nodeId, PlaneGridPreviewData planeGridData, PreviewOptions options) {
        if (planeGridData == null || planeGridData.getPlane() == null) {
            hideNodePreviews(nodeId);
            return null;
        }
        PreviewPlanePayload payload = new PreviewPlanePayload(planeGridData);
        PreviewStyle style = PreviewStyle.fromLegacyPlaneGridOptions(options);
        return showPreview(new PreviewRequest(nodeId, payload, style, PreviewBackend.GHOST, null));
    }

    public static String showFrameAxes(String nodeId, FrameAxesPreviewData frameAxesData, PreviewOptions options) {
        if (frameAxesData == null) {
            hideNodePreviews(nodeId);
            return null;
        }
        PreviewFramePayload payload = new PreviewFramePayload(frameAxesData);
        PreviewStyle style = PreviewStyle.fromLegacyFrameAxesOptions(options);
        return showPreview(new PreviewRequest(nodeId, payload, style, PreviewBackend.GHOST, null));
    }

    public static String showPaths(String nodeId, Object pathData, PreviewOptions options) {
        if (pathData == null) {
            hideNodePreviews(nodeId);
            return null;
        }
        PreviewCurvePayload curve = PreviewPayloadAdapters.tryCurvePayloadFromPreviewSource(pathData);
        if (curve != null) {
            return showCurve(nodeId, curve, options);
        }
        NodeCraft.LOGGER.warn(
            "PreviewManager.showPaths: unsupported pathData type {}, hiding node previews",
            pathData.getClass().getName()
        );
        hideNodePreviews(nodeId);
        return null;
    }

    public static String showTextLabels(String nodeId, Object labelData, PreviewOptions options) {
        if (labelData == null) {
            hideNodePreviews(nodeId);
            return null;
        }
        PreviewLabelsPayload payload = new PreviewLabelsPayload(labelData);
        PreviewStyle style = PreviewStyle.fromLegacyLabelsOptions(options);
        return showPreview(new PreviewRequest(nodeId, payload, style, PreviewBackend.GHOST, null));
    }

    // Transform gizmo

    public static String showTransformGizmo(String nodeId, Vec3d center) {
        return showTransformGizmo(nodeId, center, PreviewOptions.createTransformGizmo());
    }

    public static String showTransformGizmo(String nodeId, Vec3d center, PreviewOptions options) {
        return RENDERER.showPreview(nodeId, "transformation_gizmo", center, options);
    }

    // Generic control

    public static void hidePreview(String previewId) {
        RENDERER.hidePreview(previewId);
    }

    public static void hideNodePreviews(String nodeId) {
        RENDERER.hidePreviewsByNode(nodeId);
    }

    public static void clearAllPreviews() {
        RENDERER.clearAllPreviews();
    }

    public static int getActivePreviewCount() {
        return RENDERER.getActivePreviewCount();
    }

    public static void updatePreview(String previewId, Object newData) {
        RENDERER.updatePreview(previewId, newData, null);
    }

    public static void updatePreviewOptions(String previewId, PreviewOptions newOptions) {
        RENDERER.updatePreview(previewId, null, newOptions);
    }

    // Global settings

    public static void setGlobalPreviewEnabled(boolean enabled) {
        RENDERER.setGlobalPreviewEnabled(enabled);
    }

    public static void setGlobalOpacity(float opacity) {
        RENDERER.setGlobalOpacity(opacity);
    }

    public static PreviewRenderer.PreviewRenderSettings getSettings() {
        return RENDERER.getSettings();
    }

    // Convenience helpers

    public static PreviewOptions createColoredHighlight(float r, float g, float b, float opacity) {
        return new PreviewOptions()
                .setColor(r, g, b)
                .setOpacity(opacity)
                .wireframeMode()
                .setLineWidth(2.0f);
    }

    public static PreviewOptions createPulsingRegionBox(float r, float g, float b) {
        return new PreviewOptions()
                .setColor(r, g, b)
                .setOpacity(0.4f)
                .enablePulse()
                .setLineWidth(1.5f);
    }

    public static PreviewOptions createTransparentGhostBlocks(float opacity) {
        return new PreviewOptions()
                .ghostBlockMode()
                .setOpacity(opacity);
    }
}
