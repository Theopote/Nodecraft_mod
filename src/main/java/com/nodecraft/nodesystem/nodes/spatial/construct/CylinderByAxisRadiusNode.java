package com.nodecraft.nodesystem.nodes.spatial.construct;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "spatial.construct.cylinder_by_axis_radius",
    displayName = "Cylinder By Axis Radius",
    description = "Constructs cylinder geometry from two axis endpoints and a radius",
    category = "spatial.construct"
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

    public CylinderByAxisRadiusNode() {
        super(UUID.randomUUID(), "spatial.construct.cylinder_by_axis_radius");

        addInputPort(new BasePort(INPUT_START_ID, "Start", "Cylinder axis start point", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_END_ID, "End", "Cylinder axis end point", NodeDataType.ANY, this));
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
        Vector3d start = resolvePoint(inputValues.get(INPUT_START_ID));
        Vector3d end = resolvePoint(inputValues.get(INPUT_END_ID));
        Object radiusObj = inputValues.get(INPUT_RADIUS_ID);

        if (start == null || end == null || !(radiusObj instanceof Number radiusNumber)) {
            writeEmptyOutputs();
            return;
        }

        double radius = radiusNumber.doubleValue();
        Vector3d axisVector = new Vector3d(end).sub(start);
        double height = axisVector.length();
        if (!Double.isFinite(radius) || radius < 0.0d || height <= 1.0e-9d) {
            writeEmptyOutputs();
            return;
        }

        CylinderGeometryData cylinder = new CylinderGeometryData(start, end, radius);
        LineData axisLine = new LineData(
            new Vec3d(start.x, start.y, start.z),
            new Vec3d(end.x, end.y, end.z)
        );

        outputValues.put(OUTPUT_CYLINDER_ID, cylinder);
        outputValues.put(OUTPUT_GEOMETRY_ID, cylinder);
        outputValues.put(OUTPUT_AXIS_LINE_ID, axisLine);
        outputValues.put(OUTPUT_AXIS_VECTOR_ID, axisVector);
        outputValues.put(OUTPUT_HEIGHT_ID, height);
        outputValues.put(OUTPUT_RADIUS_ID, radius);
        outputValues.put(OUTPUT_VALID_ID, true);
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

    private Vector3d resolvePoint(Object value) {
        if (value instanceof PointData pointData) {
            return pointData.getPosition();
        }
        if (value instanceof Vector3d vector) {
            return new Vector3d(vector);
        }
        if (value instanceof BlockPos blockPos) {
            return new Vector3d(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        }
        return null;
    }
}
