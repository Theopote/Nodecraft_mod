package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "geometry.solids.loft_from_points",
    displayName = "Loft Point Lists",
    description = "Connects two ordered point lists and emits source paths, target paths, and loft rail segments",
    category = "geometry.solids",
    order = 4
)
public class LoftPointListsNode extends BaseNode {

    @NodeProperty(displayName = "Close Source", category = "Loft", order = 1)
    private boolean closeSource = true;

    @NodeProperty(displayName = "Close Target", category = "Loft", order = 2)
    private boolean closeTarget = true;

    @NodeProperty(displayName = "Wrap Pairs", category = "Loft", order = 3)
    private boolean wrapPairs = false;

    private static final String INPUT_SOURCE_POINTS_ID = "input_source_points";
    private static final String INPUT_TARGET_POINTS_ID = "input_target_points";

    private static final String OUTPUT_SOURCE_POINTS_ID = "output_source_points";
    private static final String OUTPUT_TARGET_POINTS_ID = "output_target_points";
    private static final String OUTPUT_SOURCE_PATH_ID = "output_source_path";
    private static final String OUTPUT_TARGET_PATH_ID = "output_target_path";
    private static final String OUTPUT_RAIL_SEGMENTS_ID = "output_rail_segments";
    private static final String OUTPUT_SURFACE_STRIP_ID = "output_surface_strip";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public LoftPointListsNode() {
        super(UUID.randomUUID(), "geometry.solids.loft_from_points");

        addInputPort(new BasePort(INPUT_SOURCE_POINTS_ID, "Source Points", "Ordered source point list", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_TARGET_POINTS_ID, "Target Points", "Ordered target point list", NodeDataType.LIST, this));

        addOutputPort(new BasePort(OUTPUT_SOURCE_POINTS_ID, "Source Points", "Resolved source point list", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_TARGET_POINTS_ID, "Target Points", "Resolved target point list", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SOURCE_PATH_ID, "Source Path", "Polyline describing the source contour", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_TARGET_PATH_ID, "Target Path", "Polyline describing the target contour", NodeDataType.POLYLINE, this));
        addOutputPort(new BasePort(OUTPUT_RAIL_SEGMENTS_ID, "Rail Segments", "List of line segments connecting source and target pairs", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_SURFACE_STRIP_ID, "Surface Strip", "Reusable strip surface connecting the loft sections", NodeDataType.SURFACE_STRIP, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of loft rail segments", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when both point lists were resolved", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Connects two ordered point lists and emits source paths, target paths, and loft rail segments";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object sourceObj = inputValues.get(INPUT_SOURCE_POINTS_ID);
        Object targetObj = inputValues.get(INPUT_TARGET_POINTS_ID);

        if (!(sourceObj instanceof List<?> sourceInput) || !(targetObj instanceof List<?> targetInput)) {
            writeEmptyOutputs();
            return;
        }

        List<Vector3d> sourcePoints = SolidNodeUtils.resolvePointList(sourceInput);
        List<Vector3d> targetPoints = SolidNodeUtils.resolvePointList(targetInput);
        if (sourcePoints.size() < 2 || targetPoints.size() < 2) {
            writeEmptyOutputs();
            return;
        }

        int pairCount = wrapPairs
            ? Math.max(sourcePoints.size(), targetPoints.size())
            : Math.min(sourcePoints.size(), targetPoints.size());

        if (pairCount < 2) {
            writeEmptyOutputs();
            return;
        }

        List<Vector3d> pairedSourcePoints = new ArrayList<>(pairCount);
        List<Vector3d> pairedTargetPoints = new ArrayList<>(pairCount);
        List<LineData> railSegments = new ArrayList<>(pairCount);
        for (int i = 0; i < pairCount; i++) {
            Vector3d sourcePoint = new Vector3d(sourcePoints.get(wrapPairs ? i % sourcePoints.size() : i));
            Vector3d targetPoint = new Vector3d(targetPoints.get(wrapPairs ? i % targetPoints.size() : i));
            pairedSourcePoints.add(sourcePoint);
            pairedTargetPoints.add(targetPoint);
            railSegments.add(new LineData(
                new Vec3d(sourcePoint.x, sourcePoint.y, sourcePoint.z),
                new Vec3d(targetPoint.x, targetPoint.y, targetPoint.z)
            ));
        }

        PolylineData sourcePath = SolidNodeUtils.createPolyline(sourcePoints, closeSource);
        PolylineData targetPath = SolidNodeUtils.createPolyline(targetPoints, closeTarget);
        SurfaceStripData surfaceStrip = new SurfaceStripData(
            List.of(List.copyOf(pairedSourcePoints), List.copyOf(pairedTargetPoints)),
            List.of(closeSource, closeTarget)
        );

        outputValues.put(OUTPUT_SOURCE_POINTS_ID, List.copyOf(sourcePoints));
        outputValues.put(OUTPUT_TARGET_POINTS_ID, List.copyOf(targetPoints));
        outputValues.put(OUTPUT_SOURCE_PATH_ID, sourcePath);
        outputValues.put(OUTPUT_TARGET_PATH_ID, targetPath);
        outputValues.put(OUTPUT_RAIL_SEGMENTS_ID, List.copyOf(railSegments));
        outputValues.put(OUTPUT_SURFACE_STRIP_ID, surfaceStrip);
        outputValues.put(OUTPUT_COUNT_ID, railSegments.size());
        outputValues.put(OUTPUT_VALID_ID, sourcePath != null && targetPath != null);
    }

    public boolean isCloseSource() {
        return closeSource;
    }

    public void setCloseSource(boolean closeSource) {
        this.closeSource = closeSource;
        markDirty();
    }

    public boolean isCloseTarget() {
        return closeTarget;
    }

    public void setCloseTarget(boolean closeTarget) {
        this.closeTarget = closeTarget;
        markDirty();
    }

    public boolean isWrapPairs() {
        return wrapPairs;
    }

    public void setWrapPairs(boolean wrapPairs) {
        this.wrapPairs = wrapPairs;
        markDirty();
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("closeSource", closeSource);
        state.put("closeTarget", closeTarget);
        state.put("wrapPairs", wrapPairs);
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
        if (map.get("wrapPairs") instanceof Boolean value) {
            setWrapPairs(value);
        }
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_SOURCE_POINTS_ID, List.of());
        outputValues.put(OUTPUT_TARGET_POINTS_ID, List.of());
        outputValues.put(OUTPUT_SOURCE_PATH_ID, null);
        outputValues.put(OUTPUT_TARGET_PATH_ID, null);
        outputValues.put(OUTPUT_RAIL_SEGMENTS_ID, List.of());
        outputValues.put(OUTPUT_SURFACE_STRIP_ID, null);
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

}
