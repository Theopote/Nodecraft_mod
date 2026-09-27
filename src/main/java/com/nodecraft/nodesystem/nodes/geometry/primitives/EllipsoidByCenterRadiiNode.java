package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.EllipsoidGeometryData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.primitives.ellipsoid",
    displayName = "Ellipsoid By Center Radii",
    description = "Constructs ellipsoid geometry from a center point and X/Y/Z radii",
    category = "geometry.primitives",
    order = 11
)
public class EllipsoidByCenterRadiiNode extends AbstractPrimitiveNode {

    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_RADIUS_X_ID = "input_radius_x";
    private static final String INPUT_RADIUS_Y_ID = "input_radius_y";
    private static final String INPUT_RADIUS_Z_ID = "input_radius_z";

    private static final String OUTPUT_ELLIPSOID_ID = "output_ellipsoid";
    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_RADII_ID = "output_radii";

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
        super("geometry.primitives.ellipsoid");

        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Ellipsoid center point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_RADIUS_X_ID, "Radius X", "Ellipsoid X radius", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RADIUS_Y_ID, "Radius Y", "Ellipsoid Y radius", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RADIUS_Z_ID, "Radius Z", "Ellipsoid Z radius", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_ELLIPSOID_ID, "Ellipsoid", "Constructed ellipsoid geometry", NodeDataType.ELLIPSOID_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Unified geometry output", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Resolved ellipsoid center", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_RADII_ID, "Radii", "Resolved ellipsoid radii vector", NodeDataType.VECTOR, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Constructs ellipsoid geometry from a center point and X/Y/Z radii";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d center = resolveOptionalPoint(INPUT_CENTER_ID, new Vector3d(centerX, centerY, centerZ));
        if (center == null) {
            writeEmptyOutputs(isPortConnected(INPUT_CENTER_ID)
                ? "Center input is invalid"
                : "Ellipsoid requires a finite center");
            return;
        }

        Double rx = resolvePositiveDouble(INPUT_RADIUS_X_ID, radiusX);
        Double ry = resolvePositiveDouble(INPUT_RADIUS_Y_ID, radiusY);
        Double rz = resolvePositiveDouble(INPUT_RADIUS_Z_ID, radiusZ);
        if (rx == null || ry == null || rz == null) {
            writeEmptyOutputs("Ellipsoid radii must be finite and > 0");
            return;
        }

        Vector3d radii = new Vector3d(rx, ry, rz);
        EllipsoidGeometryData ellipsoid = new EllipsoidGeometryData(center, radii);
        outputValues.put(OUTPUT_ELLIPSOID_ID, ellipsoid);
        outputValues.put(OUTPUT_GEOMETRY_ID, ellipsoid);
        outputValues.put(OUTPUT_CENTER_ID, new PointData(center));
        outputValues.put(OUTPUT_RADII_ID, radii);
        markSuccess();
    }

    private void writeEmptyOutputs(String reason) {
        putNullOutputs(OUTPUT_ELLIPSOID_ID, OUTPUT_GEOMETRY_ID, OUTPUT_CENTER_ID, OUTPUT_RADII_ID);
        markInvalid(reason);
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
