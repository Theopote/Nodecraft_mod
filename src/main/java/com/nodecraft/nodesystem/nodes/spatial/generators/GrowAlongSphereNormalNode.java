package com.nodecraft.nodesystem.nodes.spatial.generators;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "spatial.generators.grow_along_sphere_normal",
    displayName = "Grow Along Sphere Normal",
    description = "Builds cylinder geometry growing outward from sphere surface points along their normals",
    category = "spatial.generators"
)
public class GrowAlongSphereNormalNode extends BaseNode {

    private static final double EPSILON = 1.0e-9d;

    @NodeProperty(displayName = "Length", category = "Growth", order = 1)
    private double length = 4.0d;

    @NodeProperty(displayName = "Radius", category = "Growth", order = 2)
    private double radius = 0.5d;

    @NodeProperty(displayName = "Use Input Normals", category = "Growth", order = 3)
    private boolean useInputNormals = true;

    @NodeProperty(displayName = "Bidirectional", category = "Growth", order = 4)
    private boolean bidirectional = false;

    private static final String INPUT_SPHERE_ID = "input_sphere";
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

    public GrowAlongSphereNormalNode() {
        super(UUID.randomUUID(), "spatial.generators.grow_along_sphere_normal");

        addInputPort(new BasePort(INPUT_SPHERE_ID, "Sphere", "Sphere geometry used to derive fallback normals", NodeDataType.SPHERE, this));
        addInputPort(new BasePort(INPUT_POINTS_ID, "Points", "Sphere surface points to grow from", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_NORMALS_ID, "Normals", "Optional normals aligned by point index", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_LENGTH_ID, "Length", "Optional growth length override", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Optional cylinder radius override", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Merged cylinder geometry output", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_CYLINDERS_ID, "Cylinders", "List of generated cylinder geometry objects", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_BASE_POINTS_ID, "Base Points", "Base points used for each growth instance", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_TIP_POINTS_ID, "Tip Points", "Tip points reached after growth", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of generated growth instances", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when sphere and point list were resolved", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Builds cylinder geometry growing outward from sphere surface points along their normals";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object sphereObj = inputValues.get(INPUT_SPHERE_ID);
        Object pointsObj = inputValues.get(INPUT_POINTS_ID);
        Object normalsObj = inputValues.get(INPUT_NORMALS_ID);

        if (!(sphereObj instanceof SphereData sphere) || !(pointsObj instanceof List<?> pointsInput)) {
            writeEmptyOutputs();
            return;
        }

        List<?> normalsInput = normalsObj instanceof List<?> list ? list : List.of();
        double resolvedLength = resolvePositiveDouble(inputValues.get(INPUT_LENGTH_ID), length);
        double resolvedRadius = resolvePositiveDouble(inputValues.get(INPUT_RADIUS_ID), radius);
        Vector3d center = sphere.getCenter();

        List<GeometryData> cylinders = new ArrayList<>();
        List<Vector3d> basePoints = new ArrayList<>();
        List<Vector3d> tipPoints = new ArrayList<>();

        for (int i = 0; i < pointsInput.size(); i++) {
            Vector3d basePoint = resolvePoint(pointsInput.get(i));
            if (basePoint == null) {
                continue;
            }

            Vector3d direction = null;
            if (useInputNormals && i < normalsInput.size()) {
                direction = resolvePoint(normalsInput.get(i));
            }
            if (direction == null || direction.lengthSquared() <= EPSILON) {
                direction = new Vector3d(basePoint).sub(center);
            }
            if (direction.lengthSquared() <= EPSILON) {
                continue;
            }
            direction.normalize();

            Vector3d start = bidirectional
                ? new Vector3d(basePoint).sub(new Vector3d(direction).mul(resolvedLength * 0.5d))
                : new Vector3d(basePoint);
            Vector3d end = bidirectional
                ? new Vector3d(basePoint).add(new Vector3d(direction).mul(resolvedLength * 0.5d))
                : new Vector3d(basePoint).add(new Vector3d(direction).mul(resolvedLength));

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
        state.put("useInputNormals", useInputNormals);
        state.put("bidirectional", bidirectional);
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
        if (map.get("useInputNormals") instanceof Boolean value) {
            setUseInputNormals(value);
        }
        if (map.get("bidirectional") instanceof Boolean value) {
            setBidirectional(value);
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

    public boolean isUseInputNormals() {
        return useInputNormals;
    }

    public void setUseInputNormals(boolean useInputNormals) {
        this.useInputNormals = useInputNormals;
        markDirty();
    }

    public boolean isBidirectional() {
        return bidirectional;
    }

    public void setBidirectional(boolean bidirectional) {
        this.bidirectional = bidirectional;
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
