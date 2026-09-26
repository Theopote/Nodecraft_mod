package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.TorusGeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import com.nodecraft.nodesystem.util.Vector3;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.primitives.torus",
    displayName = "Torus By Center Axis Radii",
    description = "Constructs torus geometry from a center point, symmetry axis direction, major radius, and tube (minor) radius",
    category = "geometry.primitives",
    order = 6
)
public class TorusByCenterAxisRadiiNode extends BaseNode {

    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_AXIS_ID = "input_axis";
    private static final String INPUT_MAJOR_RADIUS_ID = "input_major_radius";
    private static final String INPUT_MINOR_RADIUS_ID = "input_minor_radius";

    private static final String OUTPUT_TORUS_ID = "output_torus";
    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_AXIS_NORMALIZED_ID = "output_axis_normalized";
    private static final String OUTPUT_MAJOR_RADIUS_ID = "output_major_radius";
    private static final String OUTPUT_MINOR_RADIUS_ID = "output_minor_radius";
    private static final String OUTPUT_VALID_ID = "output_valid";

    @NodeProperty(displayName = "Center X", category = "Center", order = 1)
    private double centerX = 0.0d;
    @NodeProperty(displayName = "Center Y", category = "Center", order = 2)
    private double centerY = 0.0d;
    @NodeProperty(displayName = "Center Z", category = "Center", order = 3)
    private double centerZ = 0.0d;

    @NodeProperty(displayName = "Axis X", category = "Axis", order = 4)
    private double axisX = 0.0d;
    @NodeProperty(displayName = "Axis Y", category = "Axis", order = 5)
    private double axisY = 1.0d;
    @NodeProperty(displayName = "Axis Z", category = "Axis", order = 6)
    private double axisZ = 0.0d;

    @NodeProperty(displayName = "Major Radius", category = "Size", order = 10)
    private double majorRadius = 5.0d;

    @NodeProperty(displayName = "Minor Radius", category = "Size", order = 11)
    private double minorRadius = 1.0d;

    public TorusByCenterAxisRadiiNode() {
        super(UUID.randomUUID(), "geometry.primitives.torus");

        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Torus center point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "Axis", "Symmetry axis direction (tube runs around this axis)", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_MAJOR_RADIUS_ID, "Major Radius", "Distance from center to tube center", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_MINOR_RADIUS_ID, "Minor Radius", "Tube cross-section radius", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_TORUS_ID, "Torus", "Constructed torus geometry", NodeDataType.TORUS_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Unified geometry output", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_AXIS_NORMALIZED_ID, "Axis Normalized", "Unit axis direction stored on the torus", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_MAJOR_RADIUS_ID, "Major Radius", "Resolved major radius", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_MINOR_RADIUS_ID, "Minor Radius", "Resolved minor radius", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a torus could be constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Constructs torus geometry from a center point, symmetry axis direction, major radius, and tube (minor) radius";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d center = resolveCenter();
        Vector3d axis = resolveAxis();
        double major = resolveDouble(inputValues.get(INPUT_MAJOR_RADIUS_ID), majorRadius);
        double minor = resolveDouble(inputValues.get(INPUT_MINOR_RADIUS_ID), minorRadius);

        if (center == null || axis == null
                || !Double.isFinite(major) || !Double.isFinite(minor)
                || major <= 0.0d || minor <= 0.0d
                || axis.length() <= 1.0e-9d) {
            writeEmptyOutputs();
            return;
        }

        TorusGeometryData torus = new TorusGeometryData(center, axis, major, minor);
        outputValues.put(OUTPUT_TORUS_ID, torus);
        outputValues.put(OUTPUT_GEOMETRY_ID, torus);
        outputValues.put(OUTPUT_AXIS_NORMALIZED_ID, torus.axis());
        outputValues.put(OUTPUT_MAJOR_RADIUS_ID, major);
        outputValues.put(OUTPUT_MINOR_RADIUS_ID, minor);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private Vector3d resolveCenter() {
        Vector3d fromPort = SpatialValueResolver.resolveVector3d(inputValues.get(INPUT_CENTER_ID));
        return fromPort != null ? fromPort : new Vector3d(centerX, centerY, centerZ);
    }

    private Vector3d resolveAxis() {
        Object value = inputValues.get(INPUT_AXIS_ID);
        // VECTOR only — never treat a Point / Plane / Line as an axis.
        if (value instanceof Vector3d vector) {
            return new Vector3d(vector);
        }
        if (value instanceof Vector3 vector) {
            return new Vector3d(vector.x(), vector.y(), vector.z());
        }
        if (value instanceof Vec3d vector) {
            return new Vector3d(vector.x, vector.y, vector.z);
        }
        return new Vector3d(axisX, axisY, axisZ);
    }

    private static double resolveDouble(@Nullable Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return fallback;
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_TORUS_ID, null);
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_AXIS_NORMALIZED_ID, null);
        outputValues.put(OUTPUT_MAJOR_RADIUS_ID, 0.0d);
        outputValues.put(OUTPUT_MINOR_RADIUS_ID, 0.0d);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("centerX", centerX);
        state.put("centerY", centerY);
        state.put("centerZ", centerZ);
        state.put("axisX", axisX);
        state.put("axisY", axisY);
        state.put("axisZ", axisZ);
        state.put("majorRadius", majorRadius);
        state.put("minorRadius", minorRadius);
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
        if (map.get("axisX") instanceof Number n) axisX = n.doubleValue();
        if (map.get("axisY") instanceof Number n) axisY = n.doubleValue();
        if (map.get("axisZ") instanceof Number n) axisZ = n.doubleValue();
        if (map.get("majorRadius") instanceof Number n) majorRadius = n.doubleValue();
        if (map.get("minorRadius") instanceof Number n) minorRadius = n.doubleValue();
    }
}
