package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
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
    id = "geometry.primitives.cylinder",
    displayName = "Cylinder By Axis Radius",
    description = "Constructs cylinder geometry from two axis endpoints and a radius",
    category = "geometry.primitives",
    order = 5
)
public class CylinderByAxisRadiusNode extends BaseNode {

    private static final String INPUT_START_ID = "input_start";
    private static final String INPUT_END_ID = "input_end";
    private static final String INPUT_RADIUS_ID = "input_radius";

    private static final String OUTPUT_CYLINDER_ID = "output_cylinder";
    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_AXIS_LINE_ID = "output_axis_line";
    private static final String OUTPUT_AXIS_VECTOR_ID = "output_axis_vector";
    private static final String OUTPUT_HEIGHT_ID = "output_height";
    private static final String OUTPUT_RADIUS_ID = "output_radius";
    private static final String OUTPUT_VALID_ID = "output_valid";

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
        super(UUID.randomUUID(), "geometry.primitives.cylinder");

        addInputPort(new BasePort(INPUT_START_ID, "Start", "Cylinder axis start point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_END_ID, "End", "Cylinder axis end point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Cylinder radius", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_CYLINDER_ID, "Cylinder", "Constructed cylinder geometry", NodeDataType.CYLINDER_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Unified geometry output", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_AXIS_LINE_ID, "Axis Line", "Cylinder axis line", NodeDataType.LINE, this));
        addOutputPort(new BasePort(OUTPUT_AXIS_VECTOR_ID, "Axis Vector", "Cylinder axis vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_HEIGHT_ID, "Height", "Cylinder axis length", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_RADIUS_ID, "Radius", "Resolved radius", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a cylinder could be constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Constructs cylinder geometry from two axis endpoints and a radius";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d start = resolvePoint(inputValues.get(INPUT_START_ID), startX, startY, startZ);
        Vector3d end = resolvePoint(inputValues.get(INPUT_END_ID), endX, endY, endZ);
        double resolvedRadius = resolveRadius();

        if (start == null || end == null || !Double.isFinite(resolvedRadius) || resolvedRadius <= 0.0d) {
            writeEmptyOutputs();
            return;
        }

        Vector3d axisVector = new Vector3d(end).sub(start);
        double height = axisVector.length();
        if (height <= 1.0e-9d) {
            writeEmptyOutputs();
            return;
        }

        CylinderGeometryData cylinder = new CylinderGeometryData(start, end, resolvedRadius);
        LineData axisLine = new LineData(
            new Vec3d(start.x, start.y, start.z),
            new Vec3d(end.x, end.y, end.z)
        );

        outputValues.put(OUTPUT_CYLINDER_ID, cylinder);
        outputValues.put(OUTPUT_GEOMETRY_ID, cylinder);
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
        outputValues.put(OUTPUT_CYLINDER_ID, null);
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
        if (map.get("startX") instanceof Number n) startX = n.doubleValue();
        if (map.get("startY") instanceof Number n) startY = n.doubleValue();
        if (map.get("startZ") instanceof Number n) startZ = n.doubleValue();
        if (map.get("endX") instanceof Number n) endX = n.doubleValue();
        if (map.get("endY") instanceof Number n) endY = n.doubleValue();
        if (map.get("endZ") instanceof Number n) endZ = n.doubleValue();
        if (map.get("radius") instanceof Number n) radius = n.doubleValue();
    }
}
