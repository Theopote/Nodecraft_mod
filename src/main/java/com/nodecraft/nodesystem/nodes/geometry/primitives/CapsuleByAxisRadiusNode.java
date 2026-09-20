package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.CompositeGeometryData;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.HemisphereGeometryData;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.primitives.capsule",
    displayName = "Capsule By Axis Radius",
    description = "Constructs analytic capsule geometry from axis endpoints and radius (cylinder + two hemispheres).",
    category = "geometry.primitives",
    order = 14
)
public class CapsuleByAxisRadiusNode extends BaseNode {
    private static final String INPUT_START_ID = "input_start";
    private static final String INPUT_END_ID = "input_end";
    private static final String INPUT_RADIUS_ID = "input_radius";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_CYLINDER_ID = "output_cylinder";
    private static final String OUTPUT_START_HEMISPHERE_ID = "output_start_hemisphere";
    private static final String OUTPUT_END_HEMISPHERE_ID = "output_end_hemisphere";
    private static final String OUTPUT_RADIUS_ID = "output_radius";
    private static final String OUTPUT_AXIS_LENGTH_ID = "output_axis_length";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public CapsuleByAxisRadiusNode() {
        super(UUID.randomUUID(), "geometry.primitives.capsule");
        addInputPort(new BasePort(INPUT_START_ID, "Start", "Capsule axis start", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_END_ID, "End", "Capsule axis end", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Capsule radius", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Unified capsule geometry", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_CYLINDER_ID, "Cylinder", "Capsule middle cylinder", NodeDataType.CYLINDER_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_START_HEMISPHERE_ID, "Start Hemisphere", "Start cap hemisphere", NodeDataType.HEMISPHERE_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_END_HEMISPHERE_ID, "End Hemisphere", "End cap hemisphere", NodeDataType.HEMISPHERE_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_RADIUS_ID, "Radius", "Resolved capsule radius", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_AXIS_LENGTH_ID, "Axis Length", "Distance between start and end points", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when capsule could be constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Constructs analytic capsule geometry from axis endpoints and radius (cylinder + two hemispheres).";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object startInput = inputValues.get(INPUT_START_ID);
        Object endInput = inputValues.get(INPUT_END_ID);

        Vector3d start = resolvePoint(startInput);
        Vector3d end = resolvePoint(endInput);
        if (start == null || end == null) {
            LineData axisLineInput = null;
            if (startInput instanceof LineData line) {
                axisLineInput = line;
            } else if (endInput instanceof LineData line) {
                axisLineInput = line;
            }
            if (axisLineInput != null) {
                Vec3d lineStart = axisLineInput.getStart();
                Vec3d lineEnd = axisLineInput.getEnd();
                if (start == null) {
                    start = new Vector3d(lineStart.x, lineStart.y, lineStart.z);
                }
                if (end == null) {
                    end = new Vector3d(lineEnd.x, lineEnd.y, lineEnd.z);
                }
            }
        }
        Object radiusObj = inputValues.get(INPUT_RADIUS_ID);
        if (start == null || end == null || !(radiusObj instanceof Number rn)) {
            writeInvalid();
            return;
        }

        double radius = rn.doubleValue();
        if (!Double.isFinite(radius) || radius <= 0.0d) {
            writeInvalid();
            return;
        }

        Vector3d axis = new Vector3d(end).sub(start);
        double axisLength = axis.length();
        if (axisLength <= 1.0e-9d) {
            SphereData sphere = new SphereData(start, radius);
            outputValues.put(OUTPUT_GEOMETRY_ID, sphere);
            outputValues.put(OUTPUT_CYLINDER_ID, null);
            outputValues.put(OUTPUT_START_HEMISPHERE_ID, null);
            outputValues.put(OUTPUT_END_HEMISPHERE_ID, null);
            outputValues.put(OUTPUT_RADIUS_ID, radius);
            outputValues.put(OUTPUT_AXIS_LENGTH_ID, 0.0d);
            outputValues.put(OUTPUT_VALID_ID, true);
            return;
        }
        axis.div(axisLength);

        CylinderGeometryData cylinder = new CylinderGeometryData(start, end, radius);
        HemisphereGeometryData startCap = new HemisphereGeometryData(start, new Vector3d(axis).negate(), radius);
        HemisphereGeometryData endCap = new HemisphereGeometryData(end, axis, radius);
        GeometryData geometry = new CompositeGeometryData(List.of(cylinder, startCap, endCap));

        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_CYLINDER_ID, cylinder);
        outputValues.put(OUTPUT_START_HEMISPHERE_ID, startCap);
        outputValues.put(OUTPUT_END_HEMISPHERE_ID, endCap);
        outputValues.put(OUTPUT_RADIUS_ID, radius);
        outputValues.put(OUTPUT_AXIS_LENGTH_ID, axisLength);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_CYLINDER_ID, null);
        outputValues.put(OUTPUT_START_HEMISPHERE_ID, null);
        outputValues.put(OUTPUT_END_HEMISPHERE_ID, null);
        outputValues.put(OUTPUT_RADIUS_ID, 0.0d);
        outputValues.put(OUTPUT_AXIS_LENGTH_ID, 0.0d);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private Vector3d resolvePoint(Object value) {
        return SpatialValueResolver.resolveVector3d(value);
    }
}
