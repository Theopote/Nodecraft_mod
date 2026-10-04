package com.nodecraft.nodesystem.nodes.reference.vectors;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.VectorData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.StrictDoubleUtils;
import com.nodecraft.nodesystem.util.VectorUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "reference.vectors.lerp_vectors",
    displayName = "Lerp Vectors",
    description = "Linearly interpolates between vector A and B using parameter T.",
    category = "reference.vectors",
    order = 13
)
public class LerpVectorsNode extends BaseNode {

    private static final String INPUT_A_ID = "input_a";
    private static final String INPUT_B_ID = "input_b";
    private static final String INPUT_T_ID = "input_t";

    private static final String OUTPUT_RESULT_ID = "output_result";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public LerpVectorsNode() {
        super(UUID.randomUUID(), "reference.vectors.lerp_vectors");

        addInputPort(new BasePort(INPUT_A_ID, "A", "Start vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_B_ID, "B", "End vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_T_ID, "T", "Interpolation parameter", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_RESULT_ID, "Result", "Interpolated vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether interpolation input is valid", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error",
            "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDisplayName() {
        return "Lerp Vectors";
    }

    @Override
    public String getDescription() {
        return "Linearly interpolates between vector A and B using parameter T.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d a = VectorUtils.toStrictVectorPortValue(inputValues.get(INPUT_A_ID));
        Vector3d b = VectorUtils.toStrictVectorPortValue(inputValues.get(INPUT_B_ID));
        Double t = StrictDoubleUtils.requireExactFiniteDouble(inputValues.get(INPUT_T_ID));

        if (!VectorUtils.isFinite(a)) {
            writeInvalid("Vector A must be a finite VECTOR");
            return;
        }
        if (!VectorUtils.isFinite(b)) {
            writeInvalid("Vector B must be a finite VECTOR");
            return;
        }
        if (t == null) {
            writeInvalid("T must be exact finite DOUBLE");
            return;
        }

        Vector3d result = VectorUtils.safeLerp(a, b, t);
        VectorData output = VectorUtils.toVectorPort(result);
        if (output == null) {
            writeInvalid("Lerp result is not finite");
            return;
        }

        outputValues.put(OUTPUT_RESULT_ID, output);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_RESULT_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}
