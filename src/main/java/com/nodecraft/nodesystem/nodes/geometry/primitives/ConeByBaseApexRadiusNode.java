package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.ConeGeometryData;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.primitives.cone",
    displayName = "Cone By Base Apex Radius",
    description = "Constructs cone geometry from a base center, apex point, and base radius",
    category = "geometry.primitives",
    order = 8
)
public class ConeByBaseApexRadiusNode extends BaseNode {

    private static final String INPUT_BASE_CENTER_ID = "input_base_center";
    private static final String INPUT_APEX_ID = "input_apex";
    private static final String INPUT_RADIUS_ID = "input_radius";

    private static final String OUTPUT_CONE_ID = "output_cone";
    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_AXIS_LINE_ID = "output_axis_line";
    private static final String OUTPUT_AXIS_VECTOR_ID = "output_axis_vector";
    private static final String OUTPUT_HEIGHT_ID = "output_height";
    private static final String OUTPUT_RADIUS_ID = "output_radius";
    private static final String OUTPUT_VALID_ID = "output_valid";

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
        super(UUID.randomUUID(), "geometry.primitives.cone");

        addInputPort(new BasePort(INPUT_BASE_CENTER_ID, "Base Center", "Cone base center point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_APEX_ID, "Apex", "Cone apex point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Base Radius", "Cone base radius", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_CONE_ID, "Cone", "Constructed cone geometry", NodeDataType.CONE_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Unified geometry output", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_AXIS_LINE_ID, "Axis Line", "Cone axis line", NodeDataType.LINE, this));
        addOutputPort(new BasePort(OUTPUT_AXIS_VECTOR_ID, "Axis Vector", "Cone axis vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_HEIGHT_ID, "Height", "Cone axis length", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_RADIUS_ID, "Base Radius", "Resolved base radius", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a cone could be constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Constructs cone geometry from a base center, apex point, and base radius";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d baseCenter = resolvePoint(inputValues.get(INPUT_BASE_CENTER_ID), baseX, baseY, baseZ);
        Vector3d apex = resolvePoint(inputValues.get(INPUT_APEX_ID), apexX, apexY, apexZ);
        double resolvedRadius = resolveRadius();

        if (baseCenter == null || apex == null || !Double.isFinite(resolvedRadius) || resolvedRadius <= 0.0d) {
            writeEmptyOutputs();
            return;
        }

        Vector3d axisVector = new Vector3d(apex).sub(baseCenter);
        double height = axisVector.length();
        if (height <= 1.0e-9d) {
            writeEmptyOutputs();
            return;
        }

        ConeGeometryData cone = new ConeGeometryData(baseCenter, apex, resolvedRadius);
        LineData axisLine = new LineData(
            new Vec3d(baseCenter.x, baseCenter.y, baseCenter.z),
            new Vec3d(apex.x, apex.y, apex.z)
        );

        outputValues.put(OUTPUT_CONE_ID, cone);
        outputValues.put(OUTPUT_GEOMETRY_ID, cone);
        outputValues.put(OUTPUT_AXIS_LINE_ID, axisLine);
        outputValues.put(OUTPUT_AXIS_VECTOR_ID, axisVector);
        outputValues.put(OUTPUT_HEIGHT_ID, height);
        outputValues.put(OUTPUT_RADIUS_ID, resolvedRadius);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private Vector3d resolvePoint(@Nullable Object value, double fx, double fy, double fz) {
        Vector3d fromPort = SpatialValueResolver.resolveVector3d(value);
        return fromPort != null ? fromPort : new Vector3d(fx, fy, fz);
    }

    private double resolveRadius() {
        Object radiusObj = inputValues.get(INPUT_RADIUS_ID);
        if (radiusObj instanceof Number number) {
            return number.doubleValue();
        }
        return radius;
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_CONE_ID, null);
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_AXIS_LINE_ID, null);
        outputValues.put(OUTPUT_AXIS_VECTOR_ID, null);
        outputValues.put(OUTPUT_HEIGHT_ID, 0.0d);
        outputValues.put(OUTPUT_RADIUS_ID, 0.0d);
        outputValues.put(OUTPUT_VALID_ID, false);
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
