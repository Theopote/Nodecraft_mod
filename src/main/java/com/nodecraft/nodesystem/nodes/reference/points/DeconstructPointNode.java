package com.nodecraft.nodesystem.nodes.reference.points;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.PointUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

/**
 * Extracts continuous X/Y/Z components from a geometric point.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.points.deconstruct_point",
    displayName = "Deconstruct Point",
    description = "Extracts X, Y, and Z double components from a geometric point",
    category = "reference.points",
    order = 5
)
public class DeconstructPointNode extends BaseNode {

    private static final String INPUT_POINT_ID = "input_point";

    private static final String OUTPUT_X_ID = "output_x";
    private static final String OUTPUT_Y_ID = "output_y";
    private static final String OUTPUT_Z_ID = "output_z";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public DeconstructPointNode() {
        super(UUID.randomUUID(), "reference.points.deconstruct_point");

        addInputPort(new BasePort(INPUT_POINT_ID, "Point",
            "Geometric point to deconstruct",
            NodeDataType.POINT, this));

        addOutputPort(new BasePort(OUTPUT_X_ID, "X", "X component", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_Y_ID, "Y", "Y component", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_Z_ID, "Z", "Z component", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the point input is valid",
            NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Extracts X, Y, and Z double components from a geometric point";
    }

    @Override
    public String getDisplayName() {
        return "Deconstruct Point";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d point = PointUtils.toPointPosition(inputValues.get(INPUT_POINT_ID));
        if (!PointUtils.isFinite(point)) {
            outputValues.put(OUTPUT_X_ID, Double.NaN);
            outputValues.put(OUTPUT_Y_ID, Double.NaN);
            outputValues.put(OUTPUT_Z_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        outputValues.put(OUTPUT_X_ID, point.x);
        outputValues.put(OUTPUT_Y_ID, point.y);
        outputValues.put(OUTPUT_Z_ID, point.z);
        outputValues.put(OUTPUT_VALID_ID, true);
    }
}
