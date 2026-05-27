package com.nodecraft.nodesystem.nodes.geometry.boolops;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "geometry.boolean.sdf_sample_point",
    displayName = "SDF Sample Point",
    description = "Samples signed distance at a query point and reports inside/outside state",
    category = "geometry.boolean",
    order = 17
)
public class SdfSamplePointNode extends BaseNode {
    private static final String INPUT_SDF_ID = "input_sdf";
    private static final String INPUT_POINT_ID = "input_point";

    private static final String OUTPUT_DISTANCE_ID = "output_distance";
    private static final String OUTPUT_INSIDE_ID = "output_inside";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SdfSamplePointNode() {
        super(UUID.randomUUID(), "geometry.boolean.sdf_sample_point");

        addInputPort(new BasePort(INPUT_SDF_ID, "SDF", "Signed distance field input", NodeDataType.SDF, this));
        addInputPort(new BasePort(INPUT_POINT_ID, "Point", "Query point", NodeDataType.POINT, this));

        addOutputPort(new BasePort(OUTPUT_DISTANCE_ID, "Distance", "Signed distance at query point", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_INSIDE_ID, "Inside", "True when distance <= 0", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when sampling succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Samples signed distance at a query point and reports inside/outside state";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object sdfObj = inputValues.get(INPUT_SDF_ID);
        Vector3d point = resolvePoint(inputValues.get(INPUT_POINT_ID));
        if (!(sdfObj instanceof SignedDistanceFieldData sdf) || point == null) {
            outputValues.put(OUTPUT_DISTANCE_ID, 0.0d);
            outputValues.put(OUTPUT_INSIDE_ID, false);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        double d = sdf.sampleDistance(point);
        outputValues.put(OUTPUT_DISTANCE_ID, d);
        outputValues.put(OUTPUT_INSIDE_ID, d <= 0.0d);
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
}
