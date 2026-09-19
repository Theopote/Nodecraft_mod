package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.ConeGeometryData;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.Coordinate;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

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

    public ConeByBaseApexRadiusNode() {
        super(UUID.randomUUID(), "geometry.primitives.cone");

        addInputPort(new BasePort(INPUT_BASE_CENTER_ID, "Base Center", "Cone base center point", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_APEX_ID, "Apex", "Cone apex point", NodeDataType.ANY, this));
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
        Object baseInput = inputValues.get(INPUT_BASE_CENTER_ID);
        Object apexInput = inputValues.get(INPUT_APEX_ID);

        Vector3d baseCenter = resolvePoint(baseInput);
        Vector3d apex = resolvePoint(apexInput);
        if (baseCenter == null || apex == null) {
            LineData axisLineInput = null;
            if (baseInput instanceof LineData line) {
                axisLineInput = line;
            } else if (apexInput instanceof LineData line) {
                axisLineInput = line;
            }
            if (axisLineInput != null) {
                Vec3d lineStart = axisLineInput.getStart();
                Vec3d lineEnd = axisLineInput.getEnd();
                if (baseCenter == null) {
                    baseCenter = new Vector3d(lineStart.x, lineStart.y, lineStart.z);
                }
                if (apex == null) {
                    apex = new Vector3d(lineEnd.x, lineEnd.y, lineEnd.z);
                }
            }
        }
        Object radiusObj = inputValues.get(INPUT_RADIUS_ID);

        if (baseCenter == null || apex == null || !(radiusObj instanceof Number radiusNumber)) {
            writeEmptyOutputs();
            return;
        }

        double radius = radiusNumber.doubleValue();
        Vector3d axisVector = new Vector3d(apex).sub(baseCenter);
        double height = axisVector.length();
        if (!Double.isFinite(radius) || radius <= 0.0d || height <= 1.0e-9d) {
            writeEmptyOutputs();
            return;
        }

        ConeGeometryData cone = new ConeGeometryData(baseCenter, apex, radius);
        LineData axisLine = new LineData(
            new Vec3d(baseCenter.x, baseCenter.y, baseCenter.z),
            new Vec3d(apex.x, apex.y, apex.z)
        );

        outputValues.put(OUTPUT_CONE_ID, cone);
        outputValues.put(OUTPUT_GEOMETRY_ID, cone);
        outputValues.put(OUTPUT_AXIS_LINE_ID, axisLine);
        outputValues.put(OUTPUT_AXIS_VECTOR_ID, axisVector);
        outputValues.put(OUTPUT_HEIGHT_ID, height);
        outputValues.put(OUTPUT_RADIUS_ID, radius);
        outputValues.put(OUTPUT_VALID_ID, true);
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

    private Vector3d resolvePoint(Object value) {
        return SpatialValueResolver.resolveVector3d(value);
    }
}
