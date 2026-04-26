package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.HemisphereGeometryData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "geometry.primitives.hemisphere",
    displayName = "Hemisphere By Center Axis Radius",
    description = "Constructs a solid hemisphere: sphere intersected with the half-space on the +axis side of the center (flat face through center, dome along axis)",
    category = "geometry.primitives",
    order = 13
)
public class HemisphereByCenterAxisRadiusNode extends BaseNode {

    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_AXIS_ID = "input_axis";
    private static final String INPUT_RADIUS_ID = "input_radius";

    private static final String OUTPUT_HEMISPHERE_ID = "output_hemisphere";
    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_AXIS_NORMALIZED_ID = "output_axis_normalized";
    private static final String OUTPUT_RADIUS_ID = "output_radius";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public HemisphereByCenterAxisRadiusNode() {
        super(UUID.randomUUID(), "geometry.primitives.hemisphere");

        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Sphere center (lies on the flat circular face)", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "Axis", "Unit direction from the flat face into the dome (solid uses dot(p - center, axis) >= 0)", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Sphere radius", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_HEMISPHERE_ID, "Hemisphere", "Constructed hemisphere geometry", NodeDataType.HEMISPHERE_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Unified geometry output", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_AXIS_NORMALIZED_ID, "Axis Normalized", "Unit axis stored on the hemisphere", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_RADIUS_ID, "Radius", "Resolved radius", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when a hemisphere could be constructed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Constructs a solid hemisphere: sphere intersected with the half-space on the +axis side of the center (flat face through center, dome along axis)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d center = resolvePoint(inputValues.get(INPUT_CENTER_ID));
        Object axisObj = inputValues.get(INPUT_AXIS_ID);
        Object radiusObj = inputValues.get(INPUT_RADIUS_ID);

        if (center == null || !(axisObj instanceof Vector3d rawAxis) || !(radiusObj instanceof Number radiusNum)) {
            writeEmptyOutputs();
            return;
        }

        Vector3d axis = new Vector3d(rawAxis);
        if (axis.lengthSquared() <= 1.0e-18d) {
            writeEmptyOutputs();
            return;
        }

        double radius = radiusNum.doubleValue();
        if (!Double.isFinite(radius) || radius <= 0.0d) {
            writeEmptyOutputs();
            return;
        }

        try {
            HemisphereGeometryData hemisphere = new HemisphereGeometryData(center, axis, radius);
            Vector3d axisNorm = hemisphere.getAxis();

            outputValues.put(OUTPUT_HEMISPHERE_ID, hemisphere);
            outputValues.put(OUTPUT_GEOMETRY_ID, hemisphere);
            outputValues.put(OUTPUT_AXIS_NORMALIZED_ID, axisNorm);
            outputValues.put(OUTPUT_RADIUS_ID, radius);
            outputValues.put(OUTPUT_VALID_ID, true);
        } catch (IllegalArgumentException ex) {
            writeEmptyOutputs();
        }
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_HEMISPHERE_ID, null);
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_AXIS_NORMALIZED_ID, null);
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
