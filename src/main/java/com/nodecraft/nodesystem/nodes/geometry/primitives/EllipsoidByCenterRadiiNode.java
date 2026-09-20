package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.EllipsoidGeometryData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.primitives.ellipsoid",
    displayName = "Ellipsoid By Center Radii",
    description = "Constructs ellipsoid geometry from a center point and X/Y/Z radii",
    category = "geometry.primitives",
    order = 9
)
public class EllipsoidByCenterRadiiNode extends BaseNode {

    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_RADIUS_X_ID = "input_radius_x";
    private static final String INPUT_RADIUS_Y_ID = "input_radius_y";
    private static final String INPUT_RADIUS_Z_ID = "input_radius_z";

    private static final String OUTPUT_ELLIPSOID_ID = "output_ellipsoid";
    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_RADII_ID = "output_radii";
    private static final String OUTPUT_VALID_ID = "output_valid";

    @NodeProperty(displayName = "Center X", category = "Center", order = 1)
    private double centerX = 0.0d;
    @NodeProperty(displayName = "Center Y", category = "Center", order = 2)
    private double centerY = 0.0d;
    @NodeProperty(displayName = "Center Z", category = "Center", order = 3)
    private double centerZ = 0.0d;

    @NodeProperty(displayName = "Radius X", category = "Size", order = 10)
    private double radiusX = 5.0d;
    @NodeProperty(displayName = "Radius Y", category = "Size", order = 11)
    private double radiusY = 5.0d;
    @NodeProperty(displayName = "Radius Z", category = "Size", order = 12)
    private double radiusZ = 5.0d;

    public EllipsoidByCenterRadiiNode() {
        super(UUID.randomUUID(), "geometry.primitives.ellipsoid");

        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Ellipsoid center point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_RADIUS_X_ID, "Radius X", "Ellipsoid X radius", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RADIUS_Y_ID, "Radius Y", "Ellipsoid Y radius", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RADIUS_Z_ID, "Radius Z", "Ellipsoid Z radius", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_ELLIPSOID_ID, "Ellipsoid", "Constructed ellipsoid geometry", NodeDataType.ELLIPSOID_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Unified geometry output", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Resolved ellipsoid center", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_RADII_ID, "Radii", "Resolved ellipsoid radii vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when an ellipsoid could be constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Constructs ellipsoid geometry from a center point and X/Y/Z radii";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d center = resolveCenter();
        double rx = resolveDouble(inputValues.get(INPUT_RADIUS_X_ID), radiusX);
        double ry = resolveDouble(inputValues.get(INPUT_RADIUS_Y_ID), radiusY);
        double rz = resolveDouble(inputValues.get(INPUT_RADIUS_Z_ID), radiusZ);

        if (center == null) {
            writeEmptyOutputs();
            return;
        }

        Vector3d radii = new Vector3d(rx, ry, rz);
        if (!Double.isFinite(radii.x) || !Double.isFinite(radii.y) || !Double.isFinite(radii.z)
            || radii.x <= 0.0d || radii.y <= 0.0d || radii.z <= 0.0d) {
            writeEmptyOutputs();
            return;
        }

        EllipsoidGeometryData ellipsoid = new EllipsoidGeometryData(center, radii);
        outputValues.put(OUTPUT_ELLIPSOID_ID, ellipsoid);
        outputValues.put(OUTPUT_GEOMETRY_ID, ellipsoid);
        outputValues.put(OUTPUT_CENTER_ID, new PointData(center));
        outputValues.put(OUTPUT_RADII_ID, radii);
        outputValues.put(OUTPUT_VALID_ID, true);
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

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_ELLIPSOID_ID, null);
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_CENTER_ID, null);
        outputValues.put(OUTPUT_RADII_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("centerX", centerX);
        state.put("centerY", centerY);
        state.put("centerZ", centerZ);
        state.put("radiusX", radiusX);
        state.put("radiusY", radiusY);
        state.put("radiusZ", radiusZ);
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
        if (map.get("radiusX") instanceof Number n) radiusX = n.doubleValue();
        if (map.get("radiusY") instanceof Number n) radiusY = n.doubleValue();
        if (map.get("radiusZ") instanceof Number n) radiusZ = n.doubleValue();
    }
}
