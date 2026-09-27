package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.ConeGeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.PrimitiveGeometryValidator;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.primitives.cone",
    displayName = "Cone By Base Apex Radius",
    description = "Constructs cone geometry from a base center, apex point, and base radius",
    category = "geometry.primitives",
    order = 6
)
public class ConeByBaseApexRadiusNode extends AbstractPrimitiveNode {

    private static final String INPUT_BASE_CENTER_ID = "input_base_center";
    private static final String INPUT_APEX_ID = "input_apex";
    private static final String INPUT_RADIUS_ID = "input_radius";

    private static final String OUTPUT_CONE_ID = "output_cone";
    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_AXIS_PATH_ID = "output_axis_path";
    private static final String OUTPUT_AXIS_VECTOR_ID = "output_axis_vector";
    private static final String OUTPUT_HEIGHT_ID = "output_height";
    private static final String OUTPUT_RADIUS_ID = "output_radius";

    @NodeProperty(displayName = "Base X", category = "Base", order = 1)
    private double baseX = 0.0d;
    @NodeProperty(displayName = "Base Y", category = "Base", order = 2)
    private double baseY = 0.0d;
    @NodeProperty(displayName = "Base Z", category = "Base", order = 3)
    private double baseZ = 0.0d;

    @NodeProperty(displayName = "Apex X", category = "Apex", order = 4)
    private double apexX = 0.0d;
    @NodeProperty(displayName = "Apex Y", category = "Apex", order = 5)
    private double apexY = 10.0d;
    @NodeProperty(displayName = "Apex Z", category = "Apex", order = 6)
    private double apexZ = 0.0d;

    @NodeProperty(displayName = "Base Radius", category = "Size", order = 10)
    private double radius = 4.0d;

    public ConeByBaseApexRadiusNode() {
        super("geometry.primitives.cone");

        addInputPort(new BasePort(INPUT_BASE_CENTER_ID, "Base Center", "Cone base center point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_APEX_ID, "Apex", "Cone apex point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Base Radius", "Cone base radius", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_CONE_ID, "Cone", "Constructed cone geometry", NodeDataType.CONE_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Unified geometry output", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_AXIS_PATH_ID, "Axis Path", "Cone axis path", NodeDataType.PATH, this));
        addOutputPort(new BasePort(OUTPUT_AXIS_VECTOR_ID, "Axis Vector", "Cone axis vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_HEIGHT_ID, "Height", "Cone axis length", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_RADIUS_ID, "Base Radius", "Resolved base radius", NodeDataType.DOUBLE, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Constructs cone geometry from a base center, apex point, and base radius";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d baseCenter = resolveOptionalPoint(INPUT_BASE_CENTER_ID, new Vector3d(baseX, baseY, baseZ));
        if (baseCenter == null) {
            writeEmptyOutputs(isPortConnected(INPUT_BASE_CENTER_ID)
                ? "Base center input is invalid"
                : "Cone requires a finite base center");
            return;
        }

        Vector3d apex = resolveOptionalPoint(INPUT_APEX_ID, new Vector3d(apexX, apexY, apexZ));
        if (apex == null) {
            writeEmptyOutputs(isPortConnected(INPUT_APEX_ID)
                ? "Apex input is invalid"
                : "Cone requires a finite apex");
            return;
        }

        Double resolvedRadius = resolvePositiveDouble(INPUT_RADIUS_ID, radius);
        if (resolvedRadius == null) {
            writeEmptyOutputs(isPortConnected(INPUT_RADIUS_ID)
                ? "Base radius input must be finite and > 0"
                : "Cone base radius must be finite and > 0");
            return;
        }

        String error = PrimitiveGeometryValidator.validateCone(baseCenter, apex, resolvedRadius);
        if (error != null) {
            writeEmptyOutputs(error);
            return;
        }

        Vector3d axisVector = new Vector3d(apex).sub(baseCenter);
        double height = axisVector.length();
        ConeGeometryData cone = new ConeGeometryData(baseCenter, apex, resolvedRadius);

        outputValues.put(OUTPUT_CONE_ID, cone);
        outputValues.put(OUTPUT_GEOMETRY_ID, cone);
        outputValues.put(OUTPUT_AXIS_PATH_ID, pathFromLine(baseCenter, apex));
        outputValues.put(OUTPUT_AXIS_VECTOR_ID, axisVector);
        outputValues.put(OUTPUT_HEIGHT_ID, height);
        outputValues.put(OUTPUT_RADIUS_ID, resolvedRadius);
        markSuccess();
    }

    private void writeEmptyOutputs(String reason) {
        putNullOutputs(OUTPUT_CONE_ID, OUTPUT_GEOMETRY_ID, OUTPUT_AXIS_PATH_ID, OUTPUT_AXIS_VECTOR_ID);
        putDoubleOutputs(0.0d, OUTPUT_HEIGHT_ID, OUTPUT_RADIUS_ID);
        markInvalid(reason);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("baseX", baseX);
        state.put("baseY", baseY);
        state.put("baseZ", baseZ);
        state.put("apexX", apexX);
        state.put("apexY", apexY);
        state.put("apexZ", apexZ);
        state.put("radius", radius);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("baseX") instanceof Number n) baseX = n.doubleValue();
        if (map.get("baseY") instanceof Number n) baseY = n.doubleValue();
        if (map.get("baseZ") instanceof Number n) baseZ = n.doubleValue();
        if (map.get("apexX") instanceof Number n) apexX = n.doubleValue();
        if (map.get("apexY") instanceof Number n) apexY = n.doubleValue();
        if (map.get("apexZ") instanceof Number n) apexZ = n.doubleValue();
        if (map.get("radius") instanceof Number n) radius = n.doubleValue();
    }
}
