package com.nodecraft.nodesystem.nodes.pattern.linear;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.Curve;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Repeats a block pattern at each point along a resolved path.
 */
@NodeInfo(
    id = "pattern.linear.along_path",
    displayName = "Along Path",
    description = "Repeats a block pattern at each resolved path point from a line, polyline, curve, or point list",
    category = "pattern.linear",
    order = 1
)
public class AlongPathNode extends BaseNode {

    private static final double EPSILON = 1.0e-9d;

    @NodeProperty(displayName = "Orient To Path", category = "Pattern", order = 1)
    private boolean orientToPath = false;

    @NodeProperty(displayName = "Deduplicate Anchors", category = "Pattern", order = 2)
    private boolean deduplicateAnchors = true;

    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_LINE_ID = "input_line";
    private static final String INPUT_POLYLINE_ID = "input_polyline";
    private static final String INPUT_CURVE_ID = "input_curve";
    private static final String INPUT_PATH_POINTS_ID = "input_path_points";

    private static final String OUTPUT_ARRAY_COORDINATES_ID = "output_array_coordinates";
    private static final String OUTPUT_ANCHORS_ID = "output_anchors";
    private static final String OUTPUT_INSTANCE_COUNT_ID = "output_instance_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public AlongPathNode() {
        super(UUID.randomUUID(), "pattern.linear.along_path");

        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Block pattern to repeat along the path", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_LINE_ID, "Line", "Optional line path", NodeDataType.LINE, this));
        addInputPort(new BasePort(INPUT_POLYLINE_ID, "Polyline", "Optional polyline path", NodeDataType.POLYLINE, this));
        addInputPort(new BasePort(INPUT_CURVE_ID, "Curve", "Optional curve path", NodeDataType.CURVE, this));
        addInputPort(new BasePort(INPUT_PATH_POINTS_ID, "Path Points", "Optional ordered point list fallback", NodeDataType.LIST, this));

        addOutputPort(new BasePort(OUTPUT_ARRAY_COORDINATES_ID, "Array Coordinates", "Repeated coordinates positioned along the resolved path", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_ANCHORS_ID, "Anchors", "Resolved block anchors used for each instance", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_INSTANCE_COUNT_ID, "Instance Count", "Number of placed instances along the path", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when both a source pattern and path were resolved", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Repeats a block pattern at each resolved path point from a line, polyline, curve, or point list";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object coordinatesObj = inputValues.get(INPUT_COORDINATES_ID);
        if (!(coordinatesObj instanceof BlockPosList source) || source.isEmpty()) {
            writeEmptyOutputs();
            return;
        }

        List<Vector3d> pathPoints = resolvePathPoints();
        if (pathPoints.isEmpty()) {
            writeEmptyOutputs();
            return;
        }

        List<BlockPos> sourcePositions = source.getPositions();
        BlockPos sourceOrigin = sourcePositions.get(0);
        BlockPosList result = new BlockPosList();
        BlockPosList anchors = new BlockPosList();

        for (int anchorIndex = 0; anchorIndex < pathPoints.size(); anchorIndex++) {
            Vector3d anchorPoint = pathPoints.get(anchorIndex);
            BlockPos anchorPos = BlockPos.ofFloored(anchorPoint.x, anchorPoint.y, anchorPoint.z);
            anchors.add(anchorPos);

            double yawRadians = orientToPath ? computeYaw(pathPoints, anchorIndex) : 0.0d;
            double sin = Math.sin(yawRadians);
            double cos = Math.cos(yawRadians);

            for (BlockPos sourcePos : sourcePositions) {
                int localX = sourcePos.getX() - sourceOrigin.getX();
                int localY = sourcePos.getY() - sourceOrigin.getY();
                int localZ = sourcePos.getZ() - sourceOrigin.getZ();

                int rotatedX = (int) Math.round(localX * cos - localZ * sin);
                int rotatedZ = (int) Math.round(localX * sin + localZ * cos);

                result.add(anchorPos.add(rotatedX, localY, rotatedZ));
            }
        }

        outputValues.put(OUTPUT_ARRAY_COORDINATES_ID, result);
        outputValues.put(OUTPUT_ANCHORS_ID, anchors);
        outputValues.put(OUTPUT_INSTANCE_COUNT_ID, anchors.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("orientToPath", orientToPath);
        state.put("deduplicateAnchors", deduplicateAnchors);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("orientToPath") instanceof Boolean value) {
            setOrientToPath(value);
        }
        if (map.get("deduplicateAnchors") instanceof Boolean value) {
            setDeduplicateAnchors(value);
        }
    }

    public boolean isOrientToPath() {
        return orientToPath;
    }

    public void setOrientToPath(boolean orientToPath) {
        this.orientToPath = orientToPath;
        markDirty();
    }

    public boolean isDeduplicateAnchors() {
        return deduplicateAnchors;
    }

    public void setDeduplicateAnchors(boolean deduplicateAnchors) {
        this.deduplicateAnchors = deduplicateAnchors;
        markDirty();
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_ARRAY_COORDINATES_ID, new BlockPosList());
        outputValues.put(OUTPUT_ANCHORS_ID, new BlockPosList());
        outputValues.put(OUTPUT_INSTANCE_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private List<Vector3d> resolvePathPoints() {
        Object lineObj = inputValues.get(INPUT_LINE_ID);
        Object polylineObj = inputValues.get(INPUT_POLYLINE_ID);
        Object curveObj = inputValues.get(INPUT_CURVE_ID);
        Object pathPointsObj = inputValues.get(INPUT_PATH_POINTS_ID);

        List<Vector3d> resolved = new ArrayList<>();
        if (lineObj instanceof LineData line) {
            resolved.add(fromVec3d(line.getStart()));
            resolved.add(fromVec3d(line.getEnd()));
        } else if (polylineObj instanceof PolylineData polyline) {
            for (Vec3d point : polyline.getPoints()) {
                resolved.add(fromVec3d(point));
            }
        } else if (curveObj instanceof Curve curve) {
            for (Vec3d point : curve.getSamplePoints()) {
                resolved.add(fromVec3d(point));
            }
        } else if (pathPointsObj instanceof List<?> list) {
            for (Object entry : list) {
                Vector3d point = resolvePoint(entry);
                if (point != null) {
                    resolved.add(point);
                }
            }
        }

        if (!deduplicateAnchors) {
            return resolved;
        }

        Set<BlockPos> unique = new LinkedHashSet<>();
        List<Vector3d> deduplicated = new ArrayList<>();
        for (Vector3d point : resolved) {
            BlockPos blockPos = BlockPos.ofFloored(point.x, point.y, point.z);
            if (unique.add(blockPos)) {
                deduplicated.add(new Vector3d(blockPos.getX(), blockPos.getY(), blockPos.getZ()));
            }
        }
        return deduplicated;
    }

    private Vector3d resolvePoint(Object value) {
        if (value instanceof PointData pointData) {
            return pointData.getPosition();
        }
        if (value instanceof Vector3d vector) {
            return new Vector3d(vector);
        }
        if (value instanceof BlockPos blockPos) {
            return new Vector3d(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        }
        return null;
    }

    private Vector3d fromVec3d(Vec3d point) {
        return new Vector3d(point.x, point.y, point.z);
    }

    private double computeYaw(List<Vector3d> points, int index) {
        Vector3d tangent;
        if (index == 0 && points.size() > 1) {
            tangent = new Vector3d(points.get(1)).sub(points.get(0));
        } else if (index == points.size() - 1 && points.size() > 1) {
            tangent = new Vector3d(points.get(index)).sub(points.get(index - 1));
        } else if (points.size() > 2) {
            tangent = new Vector3d(points.get(index + 1)).sub(points.get(index - 1));
        } else {
            tangent = new Vector3d(1.0d, 0.0d, 0.0d);
        }

        tangent.y = 0.0d;
        if (tangent.lengthSquared() <= EPSILON) {
            return 0.0d;
        }
        tangent.normalize();
        return Math.atan2(tangent.z, tangent.x);
    }
}
