package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.PrimitiveGeometryValidator;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import com.nodecraft.nodesystem.util.VectorUtils;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.primitives.cylinder",
    displayName = "Cylinder By Axis Radius",
    description = "Constructs cylinder geometry from two axis endpoints and a radius",
    category = "geometry.primitives",
    order = 5
)
public class CylinderByAxisRadiusNode extends AbstractPrimitiveNode {

    private static final String INPUT_START_ID = "input_start";
    private static final String INPUT_END_ID = "input_end";
    private static final String INPUT_RADIUS_ID = "input_radius";

    private static final String OUTPUT_CYLINDER_ID = "output_cylinder";
    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_AXIS_PATH_ID = "output_axis_path";
    private static final String OUTPUT_AXIS_VECTOR_ID = "output_axis_vector";
    private static final String OUTPUT_HEIGHT_ID = "output_height";
    private static final String OUTPUT_RADIUS_ID = "output_radius";

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

    public CylinderByAxisRadiusNode() {
        super("geometry.primitives.cylinder");

        addInputPort(new BasePort(INPUT_START_ID, "Start", "Cylinder axis start point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_END_ID, "End", "Cylinder axis end point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Cylinder radius", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_CYLINDER_ID, "Cylinder", "Constructed cylinder geometry", NodeDataType.CYLINDER_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Unified geometry output", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_AXIS_PATH_ID, "Axis Path", "Cylinder axis path", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_AXIS_VECTOR_ID, "Axis Vector", "Cylinder axis vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_HEIGHT_ID, "Height", "Cylinder axis length", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_RADIUS_ID, "Radius", "Resolved radius", NodeDataType.DOUBLE, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Constructs cylinder geometry from two axis endpoints and a radius";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d start = resolveOptionalPoint(INPUT_START_ID, new Vector3d(startX, startY, startZ));
        if (start == null) {
            writeEmptyOutputs(isPortConnected(INPUT_START_ID)
                ? "Start input is invalid"
                : "Cylinder requires a finite start point");
            return;
        }

        Vector3d end = resolveOptionalPoint(INPUT_END_ID, new Vector3d(endX, endY, endZ));
        if (end == null) {
            writeEmptyOutputs(isPortConnected(INPUT_END_ID)
                ? "End input is invalid"
                : "Cylinder requires a finite end point");
            return;
        }

        Double resolvedRadius = resolvePositiveDouble(INPUT_RADIUS_ID, radius);
        if (resolvedRadius == null) {
            writeEmptyOutputs(isPortConnected(INPUT_RADIUS_ID)
                ? "Radius input must be finite and > 0"
                : "Cylinder radius must be finite and > 0");
            return;
        }

        String error = PrimitiveGeometryValidator.validateCylinder(start, end, resolvedRadius);
        if (error != null) {
            writeEmptyOutputs(error);
            return;
        }

        Vector3d axisVector = PrimitiveGeometryValidator.requirePositiveAxis(start, end);
        double height = PrimitiveGeometryValidator.requirePositiveAxisLength(start, end);
        if (axisVector == null || !Double.isFinite(height)) {
            writeEmptyOutputs("Cylinder axis length must be > 0");
            return;
        }
        CylinderGeometryData cylinder = new CylinderGeometryData(start, end, resolvedRadius);

        outputValues.put(OUTPUT_CYLINDER_ID, cylinder);
        outputValues.put(OUTPUT_GEOMETRY_ID, cylinder);
        outputValues.put(OUTPUT_AXIS_PATH_ID, pathFromLine(start, end));
        outputValues.put(OUTPUT_AXIS_VECTOR_ID, VectorUtils.toVectorPort(axisVector));
        outputValues.put(OUTPUT_HEIGHT_ID, height);
        outputValues.put(OUTPUT_RADIUS_ID, resolvedRadius);
        markSuccess();
    }

    private void writeEmptyOutputs(String reason) {
        putNullOutputs(OUTPUT_CYLINDER_ID, OUTPUT_GEOMETRY_ID, OUTPUT_AXIS_PATH_ID, OUTPUT_AXIS_VECTOR_ID);
        putDoubleOutputs(Double.NaN, OUTPUT_HEIGHT_ID, OUTPUT_RADIUS_ID);
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
