package com.nodecraft.nodesystem.nodes.reference.vectors;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

/**
 * Subtracts vector B from vector A (A - B).
 */
@NodeInfo(
    id = "reference.vectors.vector_subtraction",
    displayName = "Vector Subtraction (-)",
    description = "Computes the vector difference A - B.",
    category = "reference.vectors",
    order = 7
)
public class VectorSubtractionNode extends BaseNode {

    private static final String INPUT_A_ID = "input_vector_a";
    private static final String INPUT_B_ID = "input_vector_b";

    private static final String OUTPUT_DIFFERENCE_ID = "output_vector_difference";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public VectorSubtractionNode() {
        super(UUID.randomUUID(), "reference.vectors.vector_subtraction");

        addInputPort(new BasePort(INPUT_A_ID, "Vector A", "Minuend vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_B_ID, "Vector B", "Subtrahend vector", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_DIFFERENCE_ID, "Difference Vector", "Result A - B", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether both input vectors are valid",
            NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Outputs the vector difference A - B.";
    }

    @Override
    public String getDisplayName() {
        return "Vector Subtraction (-)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d a = VectorUtils.toVector(inputValues.get(INPUT_A_ID));
        Vector3d b = VectorUtils.toVector(inputValues.get(INPUT_B_ID));
        if (!VectorUtils.isFinite(a) || !VectorUtils.isFinite(b)) {
            outputValues.put(OUTPUT_DIFFERENCE_ID, new Vector3d());
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        outputValues.put(OUTPUT_DIFFERENCE_ID, a.sub(b));
        outputValues.put(OUTPUT_VALID_ID, true);
    }
}
