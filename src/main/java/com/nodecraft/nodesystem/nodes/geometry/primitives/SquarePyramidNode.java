package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.SquarePyramidGeometryData;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.Vector3;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.primitives.square_pyramid",
    displayName = "Square Pyramid",
    description = "Constructs square pyramid geometry from a base center, base size, height, and plane",
    category = "geometry.primitives",
    order = 14
)
public class SquarePyramidNode extends BaseNode {

    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_BASE_SIZE_ID = "input_base_size";
    private static final String INPUT_HEIGHT_ID = "input_height";
    private static final String INPUT_PLANE_ID = "input_plane";
    private static final String INPUT_X_AXIS_ID = "input_x_axis";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_APEX_ID = "output_apex";
    private static final String OUTPUT_BASE_POINTS_ID = "output_base_points";
    private static final String OUTPUT_BASE_SIZE_ID = "output_base_size";
    private static final String OUTPUT_HEIGHT_ID = "output_height";
    private static final String OUTPUT_PLANE_ID = "output_plane";
    private static final String OUTPUT_VALID_ID = "output_valid";

    @NodeProperty(displayName = "Base Center X", category = "Center", order = 1)
    private double centerX = 0.0d;
    @NodeProperty(displayName = "Base Center Y", category = "Center", order = 2)
    private double centerY = 0.0d;
    @NodeProperty(displayName = "Base Center Z", category = "Center", order = 3)
    private double centerZ = 0.0d;

    @NodeProperty(displayName = "Base Size", category = "Size", order = 10)
    private double baseSize = 5.0d;

    @NodeProperty(displayName = "Height", category = "Size", order = 11)
    private double height = 5.0d;

    public SquarePyramidNode() {
        super(UUID.randomUUID(), "geometry.primitives.square_pyramid");

        addInputPort(new BasePort(INPUT_CENTER_ID, "Base Center", "Center point of the square base", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_BASE_SIZE_ID, "Base Size", "Length of each base edge", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_HEIGHT_ID, "Height", "Distance from base plane to apex", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_PLANE_ID, "Plane", "Construction plane for the square base", NodeDataType.PLANE, this));
        addInputPort(new BasePort(INPUT_X_AXIS_ID, "X Axis", "Optional in-plane axis to rotate the square base", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Square pyramid geometry", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_APEX_ID, "Apex", "Apex point above the base plane", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_BASE_POINTS_ID, "Base Points", "Four square base corners", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BASE_SIZE_ID, "Base Size", "Resolved base size", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_HEIGHT_ID, "Height", "Resolved height", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_PLANE_ID, "Plane", "Resolved base plane", NodeDataType.PLANE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when the pyramid could be constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Constructs square pyramid geometry from a base center, base size, height, and plane";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d center = resolveCenter();
        double resolvedBaseSize = resolveDouble(inputValues.get(INPUT_BASE_SIZE_ID), baseSize);
        double resolvedHeight = resolveDouble(inputValues.get(INPUT_HEIGHT_ID), height);
        PlaneData plane = inputValues.get(INPUT_PLANE_ID) instanceof PlaneData p ? p : PlaneData.XZ_PLANE;
        Vector3d preferredXAxis = resolveDirection(inputValues.get(INPUT_X_AXIS_ID));

        if (center == null) {
            writeEmptyOutputs();
            return;
        }

        double baseSize = resolvedBaseSize;
        double height = resolvedHeight;
        if (!Double.isFinite(baseSize) || !Double.isFinite(height) || baseSize <= 0.0d || height <= 0.0d) {
            writeEmptyOutputs();
            return;
        }

        Basis basis = createBasis(plane, preferredXAxis);
        if (basis == null) {
            writeEmptyOutputs();
            return;
        }

        PlaneData resolvedPlane = new PlaneData(center, basis.normal);
        SquarePyramidGeometryData geometry = new SquarePyramidGeometryData(
            center,
            basis.xAxis,
            basis.yAxis,
            basis.normal,
            baseSize,
            height
        );

        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_APEX_ID, new PointData(geometry.getApex()));
        outputValues.put(OUTPUT_BASE_POINTS_ID, SpatialValueResolver.toPointDataList(geometry.getBaseVertices()));
        outputValues.put(OUTPUT_BASE_SIZE_ID, baseSize);
        outputValues.put(OUTPUT_HEIGHT_ID, height);
        outputValues.put(OUTPUT_PLANE_ID, resolvedPlane);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_APEX_ID, null);
        outputValues.put(OUTPUT_BASE_POINTS_ID, List.of());
        outputValues.put(OUTPUT_BASE_SIZE_ID, 0.0d);
        outputValues.put(OUTPUT_HEIGHT_ID, 0.0d);
        outputValues.put(OUTPUT_PLANE_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private Basis createBasis(PlaneData plane, @Nullable Vector3d preferredXAxis) {
        Vector3d normal = plane.getNormal();
        if (normal.lengthSquared() <= 1.0e-12d) {
            return null;
        }
        normal.normalize();

        Vector3d xAxis = preferredXAxis != null ? new Vector3d(preferredXAxis) : null;
        if (xAxis != null) {
            xAxis.sub(new Vector3d(normal).mul(xAxis.dot(normal)));
        }
        if (xAxis == null || xAxis.lengthSquared() <= 1.0e-12d) {
            xAxis = fallbackAxis(normal);
        }
        if (xAxis.lengthSquared() <= 1.0e-12d) {
            return null;
        }
        xAxis.normalize();

        Vector3d yAxis = new Vector3d(normal).cross(xAxis);
        if (yAxis.lengthSquared() <= 1.0e-12d) {
            return null;
        }
        yAxis.normalize();
        xAxis = new Vector3d(yAxis).cross(normal).normalize();

        return new Basis(xAxis, yAxis, normal);
    }

    private Vector3d fallbackAxis(Vector3d normal) {
        Vector3d reference = Math.abs(normal.z) < 0.99d
            ? new Vector3d(0.0d, 0.0d, 1.0d)
            : new Vector3d(0.0d, 1.0d, 0.0d);
        return reference.sub(new Vector3d(normal).mul(reference.dot(normal)));
    }

    private @Nullable Vector3d resolveDirection(@Nullable Object value) {
        if (value instanceof Vector3d vector) {
            return new Vector3d(vector);
        }
        if (value instanceof Vector3 vector) {
            return new Vector3d(vector.x(), vector.y(), vector.z());
        }
        if (value instanceof Vec3d vector) {
            return new Vector3d(vector.x, vector.y, vector.z);
        }
        return null;
    }

    private Vector3d resolveCenter() {
        Vector3d fromPort = SpatialValueResolver.resolveVector3d(inputValues.get(INPUT_CENTER_ID));
        return fromPort != null ? fromPort : new Vector3d(centerX, centerY, centerZ);
    }

    private static double resolveDouble(@Nullable Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return fallback;
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("centerX", centerX);
        state.put("centerY", centerY);
        state.put("centerZ", centerZ);
        state.put("baseSize", baseSize);
        state.put("height", height);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("centerX") instanceof Number n) centerX = n.doubleValue();
        if (map.get("centerY") instanceof Number n) centerY = n.doubleValue();
        if (map.get("centerZ") instanceof Number n) centerZ = n.doubleValue();
        if (map.get("baseSize") instanceof Number n) baseSize = n.doubleValue();
        if (map.get("height") instanceof Number n) height = n.doubleValue();
    }

    private record Basis(Vector3d xAxis, Vector3d yAxis, Vector3d normal) { }
}
