package com.nodecraft.nodesystem.nodes.reference.vectors;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "reference.vectors.lerp_vectors",
    displayName = "Lerp Vectors",
    description = "Linearly interpolates between vector A and B using parameter T.",
    category = "reference.vectors",
    order = 12
)
public class LerpVectorsNode extends BaseNode {

    private static final String INPUT_A_ID = "input_a";
    private static final String INPUT_B_ID = "input_b";
    private static final String INPUT_T_ID = "input_t";

    private static final String OUTPUT_RESULT_ID = "output_result";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public LerpVectorsNode() {
        super(UUID.randomUUID(), "reference.vectors.lerp_vectors");

        addInputPort(new BasePort(INPUT_A_ID, "A", "Start vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_B_ID, "B", "End vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_T_ID, "T", "Interpolation parameter", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_RESULT_ID, "Result", "Interpolated vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether interpolation input is valid", NodeDataType.BOOLEAN, this));
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
        Vector3d a = VectorUtils.toVector(inputValues.get(INPUT_A_ID));
        Vector3d b = VectorUtils.toVector(inputValues.get(INPUT_B_ID));
        Object tObj = inputValues.get(INPUT_T_ID);
        if (!VectorUtils.isFinite(a) || !VectorUtils.isFinite(b) || !(tObj instanceof Number tNumber)) {
            outputValues.put(OUTPUT_RESULT_ID, new Vector3d());
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        double t = tNumber.doubleValue();
        if (!VectorUtils.isFinite(t)) {
            outputValues.put(OUTPUT_RESULT_ID, new Vector3d());
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        Vector3d result = new Vector3d(
            a.x + (b.x - a.x) * t,
            a.y + (b.y - a.y) * t,
            a.z + (b.z - a.z) * t
        );
        outputValues.put(OUTPUT_RESULT_ID, result);
        outputValues.put(OUTPUT_VALID_ID, true);
    }
}
