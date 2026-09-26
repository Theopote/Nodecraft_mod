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

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.points.vector_between_points",
    displayName = "Vector Between Points",
    description = "Computes the displacement vector from one geometric point to another (To − From)",
    category = "reference.points",
    order = 10
)
public class VectorBetweenPointsNode extends BaseNode {

    private static final String INPUT_FROM_ID = "input_from";
    private static final String INPUT_TO_ID = "input_to";

    private static final String OUTPUT_VECTOR_ID = "output_vector";
    private static final String OUTPUT_LENGTH_ID = "output_length";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public VectorBetweenPointsNode() {
        super(UUID.randomUUID(), "reference.points.vector_between_points");

        addInputPort(new BasePort(INPUT_FROM_ID, "From",
            "Start geometric point",
            NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_TO_ID, "To",
            "End geometric point",
            NodeDataType.POINT, this));

        addOutputPort(new BasePort(OUTPUT_VECTOR_ID, "Vector",
            "Displacement vector from From to To", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_LENGTH_ID, "Length",
            "Distance between From and To", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when both input points are valid", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Vector Between Points";
    }

    @Override
    public String getDescription() {
        return "Computes the displacement vector from one geometric point to another (To − From)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d from = PointUtils.toPointPosition(inputValues.get(INPUT_FROM_ID));
        Vector3d to = PointUtils.toPointPosition(inputValues.get(INPUT_TO_ID));

        if (!PointUtils.isFinite(from) || !PointUtils.isFinite(to)) {
            outputValues.put(OUTPUT_VECTOR_ID, null);
            outputValues.put(OUTPUT_LENGTH_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        Vector3d vector = new Vector3d(to).sub(from);
        outputValues.put(OUTPUT_VECTOR_ID, vector);
        outputValues.put(OUTPUT_LENGTH_ID, vector.length());
        outputValues.put(OUTPUT_VALID_ID, true);
    }
}
