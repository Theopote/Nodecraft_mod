package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.HemisphereGeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.primitives.hemisphere",
    displayName = "Hemisphere By Center Axis Radius",
    description = "Constructs a solid hemisphere: sphere intersected with the half-space on the +axis side of the center (flat face through center, dome along axis)",
    category = "geometry.primitives",
    order = 9
)
public class HemisphereByCenterAxisRadiusNode extends AbstractPrimitiveNode {

    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_AXIS_ID = "input_axis";
    private static final String INPUT_RADIUS_ID = "input_radius";

    private static final String OUTPUT_HEMISPHERE_ID = "output_hemisphere";
    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_AXIS_NORMALIZED_ID = "output_axis_normalized";
    private static final String OUTPUT_RADIUS_ID = "output_radius";

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

    @NodeProperty(displayName = "Radius", category = "Size", order = 10)
    private double radius = 5.0d;

    public HemisphereByCenterAxisRadiusNode() {
        super("geometry.primitives.hemisphere");

        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Sphere center (lies on the flat circular face)", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "Axis", "Unit direction from the flat face into the dome (solid uses dot(p - center, axis) >= 0)", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Sphere radius", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_HEMISPHERE_ID, "Hemisphere", "Constructed hemisphere geometry", NodeDataType.HEMISPHERE_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Unified geometry output", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_AXIS_NORMALIZED_ID, "Axis Normalized", "Unit axis stored on the hemisphere", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_RADIUS_ID, "Radius", "Resolved radius", NodeDataType.DOUBLE, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Constructs a solid hemisphere: sphere intersected with the half-space on the +axis side of the center (flat face through center, dome along axis)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d center = resolveOptionalPoint(INPUT_CENTER_ID, new Vector3d(centerX, centerY, centerZ));
        if (center == null) {
            writeEmptyOutputs(isPortConnected(INPUT_CENTER_ID)
                ? "Center input is invalid"
                : "Hemisphere requires a finite center");
            return;
        }

        Vector3d axis = resolveOptionalUsableAxis(INPUT_AXIS_ID, new Vector3d(axisX, axisY, axisZ));
        if (axis == null) {
            writeEmptyOutputs(isPortConnected(INPUT_AXIS_ID)
                ? "Axis input must be a usable direction"
                : "Hemisphere requires a usable axis");
            return;
        }

        Double resolvedRadius = resolvePositiveDouble(INPUT_RADIUS_ID, radius);
        if (resolvedRadius == null) {
            writeEmptyOutputs(isPortConnected(INPUT_RADIUS_ID)
                ? "Radius input must be finite and > 0"
                : "Hemisphere radius must be finite and > 0");
            return;
        }

        HemisphereGeometryData hemisphere = new HemisphereGeometryData(center, axis, resolvedRadius);
        outputValues.put(OUTPUT_HEMISPHERE_ID, hemisphere);
        outputValues.put(OUTPUT_GEOMETRY_ID, hemisphere);
        outputValues.put(OUTPUT_AXIS_NORMALIZED_ID, hemisphere.axis());
        outputValues.put(OUTPUT_RADIUS_ID, resolvedRadius);
        markSuccess();
    }

    private void writeEmptyOutputs(String reason) {
        putNullOutputs(OUTPUT_HEMISPHERE_ID, OUTPUT_GEOMETRY_ID, OUTPUT_AXIS_NORMALIZED_ID);
        putDoubleOutputs(0.0d, OUTPUT_RADIUS_ID);
        markInvalid(reason);
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
        state.put("radius", radius);
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
        if (map.get("radius") instanceof Number n) radius = n.doubleValue();
    }
}
