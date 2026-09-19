package com.nodecraft.nodesystem.nodes.reference.points;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.points.mid_point",
    displayName = "Mid Point",
    description = "Computes the midpoint between two input points",
    category = "reference.points",
    order = 5
)
public class MidpointNode extends BaseNode {

    private static final String INPUT_A_ID = "input_point_a";
    private static final String INPUT_B_ID = "input_point_b";

    private static final String OUTPUT_POINT_ID = "output_midpoint";
    private static final String OUTPUT_VECTOR_ID = "output_vector";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public MidpointNode() {
        super(UUID.randomUUID(), "reference.points.mid_point");

        addInputPort(new BasePort(INPUT_A_ID, "Point A",
            "First point. Supports Point, Vector, Position, or Block Coordinate.",
            NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_B_ID, "Point B",
            "Second point. Supports Point, Vector, Position, or Block Coordinate.",
            NodeDataType.ANY, this));

        addOutputPort(new BasePort(OUTPUT_POINT_ID, "Mid Point",
            "Midpoint as point data", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_VECTOR_ID, "Vector",
            "Midpoint as a Vector3d position", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when both input points are valid", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Computes the midpoint between two input points";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d pointA = PointUtils.resolvePoint(inputValues.get(INPUT_A_ID));
        Vector3d pointB = PointUtils.resolvePoint(inputValues.get(INPUT_B_ID));

        if (!PointUtils.isFinite(pointA) || !PointUtils.isFinite(pointB)) {
            outputValues.put(OUTPUT_POINT_ID, null);
            outputValues.put(OUTPUT_VECTOR_ID, null);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        Vector3d midpoint = new Vector3d(
            (pointA.x + pointB.x) * 0.5d,
            (pointA.y + pointB.y) * 0.5d,
            (pointA.z + pointB.z) * 0.5d
        );

        outputValues.put(OUTPUT_POINT_ID, new PointData(midpoint));
        outputValues.put(OUTPUT_VECTOR_ID, midpoint);
        outputValues.put(OUTPUT_VALID_ID, true);
    }
}
