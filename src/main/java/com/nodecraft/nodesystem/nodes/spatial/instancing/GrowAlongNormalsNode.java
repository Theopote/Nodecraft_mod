package com.nodecraft.nodesystem.nodes.spatial.instancing;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Legacy compatibility instancing node that grows cylinder geometry along point-normal pairs.
 */
@NodeInfo(
    id = "spatial.instancing.grow_along_normals",
    displayName = "Grow Along Normals",
    description = "Builds cylinder geometry along point-normal pairs for generic growth and instancing workflows",
    category = "spatial.instancing"
)
public class GrowAlongNormalsNode extends BaseNode {

    private static final double EPSILON = 1.0e-9d;

    @NodeProperty(displayName = "Length", category = "Growth", order = 1)
    private double length = 4.0d;

    @NodeProperty(displayName = "Radius", category = "Growth", order = 2)
    private double radius = 0.5d;

    @NodeProperty(displayName = "Bidirectional", category = "Growth", order = 3)
    private boolean bidirectional = false;

    @NodeProperty(displayName = "Normalize Normals", category = "Growth", order = 4)
    private boolean normalizeNormals = true;

    private static final String INPUT_POINTS_ID = "input_points";
    private static final String INPUT_NORMALS_ID = "input_normals";
    private static final String INPUT_LENGTH_ID = "input_length";
    private static final String INPUT_RADIUS_ID = "input_radius";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_CYLINDERS_ID = "output_cylinders";
    private static final String OUTPUT_BASE_POINTS_ID = "output_base_points";
    private static final String OUTPUT_TIP_POINTS_ID = "output_tip_points";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public GrowAlongNormalsNode() {
        super(UUID.randomUUID(), "spatial.instancing.grow_along_normals");

        addInputPort(new BasePort(INPUT_POINTS_ID, "Points", "Base points for each growth instance", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_NORMALS_ID, "Normals", "Normals aligned by point index", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_LENGTH_ID, "Length", "Optional growth length override", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Optional cylinder radius override", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Merged cylinder geometry output", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_CYLINDERS_ID, "Cylinders", "List of generated cylinder geometry objects", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_BASE_POINTS_ID, "Base Points", "Base points used for each growth instance", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_TIP_POINTS_ID, "Tip Points", "Tip points reached after growth", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of generated growth instances", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when both point and normal lists were resolved", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Builds cylinder geometry along point-normal pairs for generic growth and instancing workflows";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object pointsObj = inputValues.get(INPUT_POINTS_ID);
        Object normalsObj = inputValues.get(INPUT_NORMALS_ID);

        if (!(pointsObj instanceof List<?> pointsInput) || !(normalsObj instanceof List<?> normalsInput)) {
            writeEmptyOutputs();
            return;
        }

        double resolvedLength = resolvePositiveDouble(inputValues.get(INPUT_LENGTH_ID), length);
        double resolvedRadius = resolvePositiveDouble(inputValues.get(INPUT_RADIUS_ID), radius);

        List<GeometryData> cylinders = new ArrayList<>();
        List<Vector3d> basePoints = new ArrayList<>();
        List<Vector3d> tipPoints = new ArrayList<>();

        int count = Math.min(pointsInput.size(), normalsInput.size());
        for (int i = 0; i < count; i++) {
            Vector3d basePoint = resolvePoint(pointsInput.get(i));
            Vector3d direction = resolvePoint(normalsInput.get(i));
            if (basePoint == null || direction == null || direction.lengthSquared() <= EPSILON) {
                continue;
            }

            Vector3d axis = new Vector3d(direction);
            if (normalizeNormals) {
                axis.normalize();
            }
            if (axis.lengthSquared() <= EPSILON) {
                continue;
            }

            Vector3d start = bidirectional
                ? new Vector3d(basePoint).sub(new Vector3d(axis).mul(resolvedLength * 0.5d))
                : new Vector3d(basePoint);
            Vector3d end = bidirectional
                ? new Vector3d(basePoint).add(new Vector3d(axis).mul(resolvedLength * 0.5d))
                : new Vector3d(basePoint).add(new Vector3d(axis).mul(resolvedLength));

            cylinders.add(new CylinderGeometryData(start, end, resolvedRadius));
            basePoints.add(start);
            tipPoints.add(end);
        }

        GeometryData geometry = null;
        if (cylinders.size() == 1) {
            geometry = cylinders.get(0);
        } else if (!cylinders.isEmpty()) {
            geometry = new CompositeGeometryData(cylinders);
        }

        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_CYLINDERS_ID, List.copyOf(cylinders));
        outputValues.put(OUTPUT_BASE_POINTS_ID, List.copyOf(basePoints));
        outputValues.put(OUTPUT_TIP_POINTS_ID, List.copyOf(tipPoints));
        outputValues.put(OUTPUT_COUNT_ID, cylinders.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("length", length);
        state.put("radius", radius);
        state.put("bidirectional", bidirectional);
        state.put("normalizeNormals", normalizeNormals);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("length") instanceof Number value) {
            setLength(value.doubleValue());
        }
        if (map.get("radius") instanceof Number value) {
            setRadius(value.doubleValue());
        }
        if (map.get("bidirectional") instanceof Boolean value) {
            setBidirectional(value);
        }
        if (map.get("normalizeNormals") instanceof Boolean value) {
            setNormalizeNormals(value);
        }
    }

    public double getLength() {
        return length;
    }

    public void setLength(double length) {
        this.length = Math.max(0.0d, length);
        markDirty();
    }

    public double getRadius() {
        return radius;
    }

    public void setRadius(double radius) {
        this.radius = Math.max(0.0d, radius);
        markDirty();
    }

    public boolean isBidirectional() {
        return bidirectional;
    }

    public void setBidirectional(boolean bidirectional) {
        this.bidirectional = bidirectional;
        markDirty();
    }

    public boolean isNormalizeNormals() {
        return normalizeNormals;
    }

    public void setNormalizeNormals(boolean normalizeNormals) {
        this.normalizeNormals = normalizeNormals;
        markDirty();
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_CYLINDERS_ID, List.of());
        outputValues.put(OUTPUT_BASE_POINTS_ID, List.of());
        outputValues.put(OUTPUT_TIP_POINTS_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private double resolvePositiveDouble(Object value, double fallback) {
        double resolved = fallback;
        if (value instanceof Number number) {
            resolved = number.doubleValue();
        }
        if (!Double.isFinite(resolved)) {
            return 0.0d;
        }
        return Math.max(0.0d, resolved);
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
}
