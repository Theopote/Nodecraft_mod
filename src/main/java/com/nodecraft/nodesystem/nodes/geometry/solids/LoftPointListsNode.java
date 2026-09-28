package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.solids.loft_from_points",
    displayName = "Loft Point Lists",
    description = "Connects two ordered point lists with equal counts and emits source paths, target paths, and loft rail paths",
    category = "geometry.solids",
    order = 6
)
public class LoftPointListsNode extends AbstractSolidNode {

    @NodeProperty(displayName = "Close Source", category = "Loft", order = 1)
    private boolean closeSource = true;

    @NodeProperty(displayName = "Close Target", category = "Loft", order = 2)
    private boolean closeTarget = true;

    private static final String INPUT_SOURCE_POINTS_ID = "input_source_points";
    private static final String INPUT_TARGET_POINTS_ID = "input_target_points";

    private static final String OUTPUT_SOURCE_POINTS_ID = "output_source_points";
    private static final String OUTPUT_TARGET_POINTS_ID = "output_target_points";
    private static final String OUTPUT_SECTION_POINTS_TREE_ID = "output_section_points_tree";
    private static final String OUTPUT_SOURCE_PATH_ID = "output_source_path";
    private static final String OUTPUT_TARGET_PATH_ID = "output_target_path";
    private static final String OUTPUT_SECTION_PATHS_TREE_ID = "output_section_paths_tree";
    private static final String OUTPUT_RAIL_SEGMENTS_ID = "output_rail_segments";
    private static final String OUTPUT_RAIL_SEGMENTS_TREE_ID = "output_rail_segments_tree";
    private static final String OUTPUT_SURFACE_STRIP_ID = "output_surface_strip";
    private static final String OUTPUT_COUNT_ID = "output_count";

    public LoftPointListsNode() {
        super(UUID.randomUUID(), "geometry.solids.loft_from_points");

        addInputPort(new BasePort(INPUT_SOURCE_POINTS_ID, "Source Points", "Ordered source point list", NodeDataType.POINT_LIST, this));
        addInputPort(new BasePort(INPUT_TARGET_POINTS_ID, "Target Points", "Ordered target point list", NodeDataType.POINT_LIST, this));

        addOutputPort(new BasePort(OUTPUT_SOURCE_POINTS_ID, "Source Points", "Resolved source point list", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_TARGET_POINTS_ID, "Target Points", "Resolved target point list", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_POINTS_TREE_ID, "Section Points Tree", "Paired source and target points keyed as {0} and {1}", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_SOURCE_PATH_ID, "Source Path", "Path describing the source contour", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_TARGET_PATH_ID, "Target Path", "Path describing the target contour", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_SECTION_PATHS_TREE_ID, "Section Paths Tree", "Source and target paths keyed as {0} and {1}", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_RAIL_SEGMENTS_ID, "Rail Paths", "Paths connecting corresponding source and target pairs", NodeDataType.PATH_LIST, this));
        addOutputPort(new BasePort(OUTPUT_RAIL_SEGMENTS_TREE_ID, "Rail Paths Tree", "Loft rail paths keyed by rail index", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_SURFACE_STRIP_ID, "Surface Strip", "Reusable strip surface connecting the loft sections", NodeDataType.SURFACE_STRIP, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of loft rail paths", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when both point lists were resolved with equal counts", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    @Override
    public String getDescription() {
        return "Connects two ordered point lists with equal counts and emits source paths, target paths, and loft rail paths";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> sourcePoints = SolidNodeUtils.resolveStrictPointList(inputValues.get(INPUT_SOURCE_POINTS_ID));
        List<Vector3d> targetPoints = SolidNodeUtils.resolveStrictPointList(inputValues.get(INPUT_TARGET_POINTS_ID));
        if (sourcePoints == null || targetPoints == null) {
            invalidate("Source/target point list is missing or contains non-PointData / non-finite entries");
            return;
        }
        if (sourcePoints.size() < 2 || targetPoints.size() < 2) {
            invalidate("Source and target point lists each require at least two points");
            return;
        }
        if (sourcePoints.size() != targetPoints.size()) {
            invalidate("Source and target point lists must have equal counts (STRICT pairing)");
            return;
        }

        int pairCount = sourcePoints.size();
        List<Vector3d> pairedSourcePoints = new ArrayList<>(pairCount);
        List<Vector3d> pairedTargetPoints = new ArrayList<>(pairCount);
        List<PathData> railPaths = new ArrayList<>(pairCount);
        for (int i = 0; i < pairCount; i++) {
            Vector3d sourcePoint = new Vector3d(sourcePoints.get(i));
            Vector3d targetPoint = new Vector3d(targetPoints.get(i));
            pairedSourcePoints.add(sourcePoint);
            pairedTargetPoints.add(targetPoint);
            PathData rail = PathUtils.toPathData(List.of(sourcePoint, targetPoint));
            if (rail == null) {
                invalidate("Rail path at index " + i + " is invalid");
                return;
            }
            railPaths.add(rail);
        }

        PathData sourcePath = PathUtils.toPathData(sourcePoints);
        if (sourcePath == null) {
            invalidate("Source path is invalid");
            return;
        }
        PathData targetPath = PathUtils.toPathData(targetPoints);
        if (targetPath == null) {
            invalidate("Target path is invalid");
            return;
        }

        SurfaceStripData surfaceStrip;
        try {
            surfaceStrip = new SurfaceStripData(
                List.of(List.copyOf(pairedSourcePoints), List.copyOf(pairedTargetPoints)),
                List.of(closeSource, closeTarget)
            );
        } catch (IllegalArgumentException ex) {
            invalidate(ex.getMessage() == null ? "Surface strip is invalid" : ex.getMessage());
            return;
        }
        String stripError = validateSurfaceStrip(surfaceStrip);
        if (stripError != null) {
            invalidate(stripError);
            return;
        }

        outputValues.put(OUTPUT_SOURCE_POINTS_ID, SpatialValueResolver.toPointDataList(sourcePoints));
        outputValues.put(OUTPUT_TARGET_POINTS_ID, SpatialValueResolver.toPointDataList(targetPoints));
        outputValues.put(OUTPUT_SECTION_POINTS_TREE_ID, SolidDataTreeUtils.indexedGroupTree(List.of(pairedSourcePoints, pairedTargetPoints)));
        outputValues.put(OUTPUT_SOURCE_PATH_ID, sourcePath);
        outputValues.put(OUTPUT_TARGET_PATH_ID, targetPath);
        outputValues.put(OUTPUT_SECTION_PATHS_TREE_ID, SolidDataTreeUtils.indexedValueTree(List.of(sourcePath, targetPath)));
        outputValues.put(OUTPUT_RAIL_SEGMENTS_ID, List.copyOf(railPaths));
        outputValues.put(OUTPUT_RAIL_SEGMENTS_TREE_ID, SolidDataTreeUtils.indexedValueTree(railPaths));
        outputValues.put(OUTPUT_SURFACE_STRIP_ID, surfaceStrip);
        outputValues.put(OUTPUT_COUNT_ID, railPaths.size());
        markSuccess();
    }

    public boolean isCloseSource() {
        return closeSource;
    }

    public void setCloseSource(boolean closeSource) {
        markDirtyIfChanged(this.closeSource, closeSource);
        this.closeSource = closeSource;
    }

    public boolean isCloseTarget() {
        return closeTarget;
    }

    public void setCloseTarget(boolean closeTarget) {
        markDirtyIfChanged(this.closeTarget, closeTarget);
        this.closeTarget = closeTarget;
    }

    /** @deprecated wrap pairing removed in Graph V72 */
    @Deprecated
    public boolean isWrapPairs() {
        return false;
    }

    /** @deprecated wrap pairing removed in Graph V72 */
    @Deprecated
    public void setWrapPairs(boolean wrapPairs) {
        markDirty();
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("closeSource", closeSource);
        state.put("closeTarget", closeTarget);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("closeSource") instanceof Boolean value) {
            setCloseSource(value);
        }
        if (map.get("closeTarget") instanceof Boolean value) {
            setCloseTarget(value);
        }
    }

    private void invalidate(String error) {
        putEmptyListOutputs(OUTPUT_SOURCE_POINTS_ID, OUTPUT_TARGET_POINTS_ID, OUTPUT_RAIL_SEGMENTS_ID);
        putNullOutputs(OUTPUT_SOURCE_PATH_ID, OUTPUT_TARGET_PATH_ID, OUTPUT_SURFACE_STRIP_ID);
        outputValues.put(OUTPUT_SECTION_POINTS_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_SECTION_PATHS_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_RAIL_SEGMENTS_TREE_ID, DataTreeData.empty());
        putIntOutputs(0, OUTPUT_COUNT_ID);
        markInvalid(error);
    }
}
