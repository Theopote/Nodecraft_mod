package com.nodecraft.nodesystem.nodes.reference.vectors;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.VectorData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.VectorUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
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
    private static final String OUTPUT_ERROR_ID = "output_error";

    public VectorAdditionNode() {
        super(UUID.randomUUID(), "reference.vectors.vector_addition");

        addInputPort(new BasePort(INPUT_A_ID, "Vector A", "First vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_B_ID, "Vector B", "Second vector", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_SUM_ID, "Sum Vector", "Result A + B", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether both input vectors are valid",
            NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error",
            "Failure reason when Valid is false", NodeDataType.STRING, this));
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
        Vector3d a = VectorUtils.toStrictVectorPortValue(inputValues.get(INPUT_A_ID));
        Vector3d b = VectorUtils.toStrictVectorPortValue(inputValues.get(INPUT_B_ID));
        if (!VectorUtils.isFinite(a)) {
            writeInvalid("Vector A must be a finite VECTOR");
            return;
        }
        if (!VectorUtils.isFinite(b)) {
            writeInvalid("Vector B must be a finite VECTOR");
            return;
        }

        Vector3d result = VectorUtils.safeAdd(a, b);
        VectorData output = VectorUtils.toVectorPort(result);
        if (output == null) {
            writeInvalid("Vector sum is not finite");
            return;
        }

        outputValues.put(OUTPUT_SUM_ID, output);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_SUM_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}
