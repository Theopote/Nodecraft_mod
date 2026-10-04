package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.CapsuleGeometryData;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
import com.nodecraft.nodesystem.datatypes.HemisphereGeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.PrimitiveGeometryValidator;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.primitives.capsule",
    displayName = "Capsule By Axis Radius",
    description = "Constructs analytic capsule geometry from axis endpoints and radius (cylinder + two hemispheres).",
    category = "geometry.primitives",
    order = 8
)
public class CapsuleByAxisRadiusNode extends AbstractPrimitiveNode {

    private static final String INPUT_START_ID = "input_start";
    private static final String INPUT_END_ID = "input_end";
    private static final String INPUT_RADIUS_ID = "input_radius";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_CYLINDER_ID = "output_cylinder";
    private static final String OUTPUT_START_HEMISPHERE_ID = "output_start_hemisphere";
    private static final String OUTPUT_END_HEMISPHERE_ID = "output_end_hemisphere";
    private static final String OUTPUT_RADIUS_ID = "output_radius";
    private static final String OUTPUT_AXIS_LENGTH_ID = "output_axis_length";
    private static final String OUTPUT_AXIS_PATH_ID = "output_axis_path";

    @NodeProperty(displayName = "Start X", category = "Start", order = 1)
    private double startX = 0.0d;
    @NodeProperty(displayName = "Start Y", category = "Start", order = 2)
    private double startY = 0.0d;
    @NodeProperty(displayName = "Start Z", category = "Start", order = 3)
    private double startZ = 0.0d;

    @NodeProperty(displayName = "End X", category = "End", order = 4)
    private double endX = 0.0d;
    @NodeProperty(displayName = "End Y", category = "End", order = 5)
    private double endY = 10.0d;
    @NodeProperty(displayName = "End Z", category = "End", order = 6)
    private double endZ = 0.0d;

    @NodeProperty(displayName = "Radius", category = "Size", order = 10)
    private double radius = 4.0d;

    public CapsuleByAxisRadiusNode() {
        super("geometry.primitives.capsule");

        addInputPort(new BasePort(INPUT_START_ID, "Start", "Capsule axis start", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_END_ID, "End", "Capsule axis end", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Capsule radius", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Unified capsule geometry", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_CYLINDER_ID, "Cylinder", "Capsule middle cylinder", NodeDataType.CYLINDER_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_START_HEMISPHERE_ID, "Start Hemisphere", "Start cap hemisphere", NodeDataType.HEMISPHERE_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_END_HEMISPHERE_ID, "End Hemisphere", "End cap hemisphere", NodeDataType.HEMISPHERE_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_RADIUS_ID, "Radius", "Resolved capsule radius", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_AXIS_LENGTH_ID, "Axis Length", "Distance between start and end points", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_AXIS_PATH_ID, "Axis Path", "Capsule axis path", NodeDataType.PATH, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Constructs analytic capsule geometry from axis endpoints and radius (cylinder + two hemispheres).";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d start = resolveOptionalPoint(INPUT_START_ID, new Vector3d(startX, startY, startZ));
        if (start == null) {
            writeInvalid(isPortConnected(INPUT_START_ID)
                ? "Start input is invalid"
                : "Capsule requires a finite start point");
            return;
        }

        Vector3d end = resolveOptionalPoint(INPUT_END_ID, new Vector3d(endX, endY, endZ));
        if (end == null) {
            writeInvalid(isPortConnected(INPUT_END_ID)
                ? "End input is invalid"
                : "Capsule requires a finite end point");
            return;
        }

        Double resolvedRadius = resolvePositiveDouble(INPUT_RADIUS_ID, radius);
        if (resolvedRadius == null) {
            writeInvalid(isPortConnected(INPUT_RADIUS_ID)
                ? "Radius input must be finite and > 0"
                : "Capsule radius must be finite and > 0");
            return;
        }

        String error = PrimitiveGeometryValidator.validateCapsule(start, end, resolvedRadius);
        if (error != null) {
            writeInvalid(error);
            return;
        }

        Vector3d axis = PrimitiveGeometryValidator.requirePositiveAxis(start, end);
        double axisLength = PrimitiveGeometryValidator.requirePositiveAxisLength(start, end);
        if (axis == null || !Double.isFinite(axisLength)) {
            writeInvalid("Capsule axis length must be > 0");
            return;
        }

        CapsuleGeometryData geometry = new CapsuleGeometryData(start, end, resolvedRadius);
        CylinderGeometryData cylinder = geometry.cylinder();
        HemisphereGeometryData startCap = geometry.startHemisphere();
        HemisphereGeometryData endCap = geometry.endHemisphere();

        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_CYLINDER_ID, cylinder);
        outputValues.put(OUTPUT_START_HEMISPHERE_ID, startCap);
        outputValues.put(OUTPUT_END_HEMISPHERE_ID, endCap);
        outputValues.put(OUTPUT_RADIUS_ID, resolvedRadius);
        outputValues.put(OUTPUT_AXIS_LENGTH_ID, axisLength);
        outputValues.put(OUTPUT_AXIS_PATH_ID, pathFromLine(start, end));
        markSuccess();
    }

    private void writeInvalid(String reason) {
        putNullOutputs(
            OUTPUT_GEOMETRY_ID,
            OUTPUT_CYLINDER_ID,
            OUTPUT_START_HEMISPHERE_ID,
            OUTPUT_END_HEMISPHERE_ID,
            OUTPUT_AXIS_PATH_ID
        );
        putDoubleOutputs(Double.NaN, OUTPUT_RADIUS_ID, OUTPUT_AXIS_LENGTH_ID);
        markInvalid(reason);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("startX", startX);
        state.put("startY", startY);
        state.put("startZ", startZ);
        state.put("endX", endX);
        state.put("endY", endY);
        state.put("endZ", endZ);
        state.put("radius", radius);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        restoreFiniteDouble(map, "startX", v -> startX = v);
        restoreFiniteDouble(map, "startY", v -> startY = v);
        restoreFiniteDouble(map, "startZ", v -> startZ = v);
        restoreFiniteDouble(map, "endX", v -> endX = v);
        restoreFiniteDouble(map, "endY", v -> endY = v);
        restoreFiniteDouble(map, "endZ", v -> endZ = v);
        restoreFiniteDouble(map, "radius", v -> radius = v);
    }
}
