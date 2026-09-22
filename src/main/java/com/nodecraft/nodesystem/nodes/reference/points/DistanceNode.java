package com.nodecraft.nodesystem.nodes.reference.points;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.points.distance_between_points",
    displayName = "Distance Between Points",
    description = "Computes the distance between two input points",
    category = "reference.points",
    order = 6
)
public class DistanceNode extends BaseNode {

    private static final String INPUT_A_ID = "input_point_a";
    private static final String INPUT_B_ID = "input_point_b";

    private static final String OUTPUT_DISTANCE_ID = "output_distance";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public DistanceNode() {
        super(UUID.randomUUID(), "reference.points.distance_between_points");

        addInputPort(new BasePort(INPUT_A_ID, "Point A",
            "First geometric point",
            NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_B_ID, "Point B",
            "Second geometric point",
            NodeDataType.POINT, this));

        addOutputPort(new BasePort(OUTPUT_DISTANCE_ID, "Distance",
            "Distance between point A and point B", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when both input points are valid", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Computes the distance between two input points";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d pointA = PointUtils.resolvePoint(inputValues.get(INPUT_A_ID));
        Vector3d pointB = PointUtils.resolvePoint(inputValues.get(INPUT_B_ID));

        if (!PointUtils.isFinite(pointA) || !PointUtils.isFinite(pointB)) {
            outputValues.put(OUTPUT_DISTANCE_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        outputValues.put(OUTPUT_DISTANCE_ID, pointA.distance(pointB));
        outputValues.put(OUTPUT_VALID_ID, true);
    }
}
