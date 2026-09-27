package com.nodecraft.nodesystem.nodes.reference.points;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.PointUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.points.mid_point",
    displayName = "Mid Point",
    description = "Computes the midpoint between two input points",
    category = "reference.points",
    order = 8
)
public class MidpointNode extends BaseNode {

    private static final String INPUT_A_ID = "input_point_a";
    private static final String INPUT_B_ID = "input_point_b";

    private static final String OUTPUT_POINT_ID = "output_midpoint";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public MidpointNode() {
        super(UUID.randomUUID(), "reference.points.mid_point");

        addInputPort(new BasePort(INPUT_A_ID, "Point A",
            "First geometric point",
            NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_B_ID, "Point B",
            "Second geometric point",
            NodeDataType.POINT, this));

        addOutputPort(new BasePort(OUTPUT_POINT_ID, "Mid Point",
            "Midpoint as point data", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when both input points are valid", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error",
            "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Computes the midpoint between two input points";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d pointA = PointUtils.toPointPosition(inputValues.get(INPUT_A_ID));
        Vector3d pointB = PointUtils.toPointPosition(inputValues.get(INPUT_B_ID));

        if (!PointUtils.isFinite(pointA)) {
            writeInvalid("Point A must be a finite POINT");
            return;
        }
        if (!PointUtils.isFinite(pointB)) {
            writeInvalid("Point B must be a finite POINT");
            return;
        }

        Vector3d midpoint = PointUtils.safeMidpoint(pointA, pointB);
        if (midpoint == null) {
            writeInvalid("Midpoint is not finite");
            return;
        }

        outputValues.put(OUTPUT_POINT_ID, new PointData(midpoint));
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_POINT_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}
