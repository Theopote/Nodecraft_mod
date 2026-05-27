package com.nodecraft.nodesystem.nodes.geometry.boolops;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.CapsuleSdfData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "geometry.boolean.sdf_capsule",
    displayName = "SDF Capsule",
    description = "Builds a capsule signed-distance-field primitive from segment endpoints and radius",
    category = "geometry.boolean",
    order = 12
)
public class SdfCapsuleNode extends BaseNode {
    @NodeProperty(displayName = "Default Radius", category = "SDF", order = 1)
    private double defaultRadius = 2.0d;

    private static final String INPUT_START_ID = "input_start";
    private static final String INPUT_END_ID = "input_end";
    private static final String INPUT_RADIUS_ID = "input_radius";

    private static final String OUTPUT_SDF_ID = "output_sdf";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SdfCapsuleNode() {
        super(UUID.randomUUID(), "geometry.boolean.sdf_capsule");
        addInputPort(new BasePort(INPUT_START_ID, "Start", "Segment start point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_END_ID, "End", "Segment end point", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Capsule radius", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SDF_ID, "SDF", "Capsule signed distance field", NodeDataType.SDF, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when endpoints and radius are valid", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Builds a capsule signed-distance-field primitive from segment endpoints and radius";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d start = resolvePoint(inputValues.get(INPUT_START_ID));
        Vector3d end = resolvePoint(inputValues.get(INPUT_END_ID));
        double radius = getInputDouble(INPUT_RADIUS_ID, defaultRadius);
        if (start == null || end == null || radius <= 0.0d) {
            outputValues.put(OUTPUT_SDF_ID, null);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }
        SignedDistanceFieldData sdf = new CapsuleSdfData(start, end, radius);
        outputValues.put(OUTPUT_SDF_ID, sdf);
        outputValues.put(OUTPUT_VALID_ID, true);
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

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }
}
