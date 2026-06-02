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
 * Adds two vectors (A + B).
 */
@NodeInfo(
    id = "reference.vectors.vector_addition",
    displayName = "Vector Addition (+)",
    description = "Computes the vector sum A + B.",
    category = "reference.vectors",
    order = 6
)
public class VectorAdditionNode extends BaseNode {

    private static final String INPUT_A_ID = "input_vector_a";
    private static final String INPUT_B_ID = "input_vector_b";

    private static final String OUTPUT_SUM_ID = "output_vector_sum";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public VectorAdditionNode() {
        super(UUID.randomUUID(), "reference.vectors.vector_addition");

        addInputPort(new BasePort(INPUT_A_ID, "Vector A", "First vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_B_ID, "Vector B", "Second vector", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_SUM_ID, "Sum Vector", "Result A + B", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether both input vectors are valid",
            NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Outputs the vector sum of A and B.";
    }

    @Override
    public String getDisplayName() {
        return "Vector Addition (+)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d a = VectorUtils.toVector(inputValues.get(INPUT_A_ID));
        Vector3d b = VectorUtils.toVector(inputValues.get(INPUT_B_ID));
        if (!VectorUtils.isFinite(a) || !VectorUtils.isFinite(b)) {
            outputValues.put(OUTPUT_SUM_ID, new Vector3d());
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        outputValues.put(OUTPUT_SUM_ID, a.add(b));
        outputValues.put(OUTPUT_VALID_ID, true);
    }
}
