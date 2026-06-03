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
    id = "reference.vectors.component_minmax",
    displayName = "Vector Component Min/Max",
    description = "Computes per-component min and max between vectors A and B.",
    category = "reference.vectors",
    order = 15
)
public class VectorComponentMinMaxNode extends BaseNode {

    private static final String INPUT_A_ID = "input_a";
    private static final String INPUT_B_ID = "input_b";

    private static final String OUTPUT_MIN_ID = "output_min";
    private static final String OUTPUT_MAX_ID = "output_max";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public VectorComponentMinMaxNode() {
        super(UUID.randomUUID(), "reference.vectors.component_minmax");

        addInputPort(new BasePort(INPUT_A_ID, "A", "First vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_B_ID, "B", "Second vector", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_MIN_ID, "Min", "Per-component minimum", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_MAX_ID, "Max", "Per-component maximum", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether input vectors are valid", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Vector Component Min/Max";
    }

    @Override
    public String getDescription() {
        return "Computes per-component min and max between vectors A and B.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d a = VectorUtils.toVector(inputValues.get(INPUT_A_ID));
        Vector3d b = VectorUtils.toVector(inputValues.get(INPUT_B_ID));
        if (!VectorUtils.isFinite(a) || !VectorUtils.isFinite(b)) {
            outputValues.put(OUTPUT_MIN_ID, new Vector3d());
            outputValues.put(OUTPUT_MAX_ID, new Vector3d());
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        Vector3d min = new Vector3d(
            Math.min(a.x, b.x),
            Math.min(a.y, b.y),
            Math.min(a.z, b.z)
        );
        Vector3d max = new Vector3d(
            Math.max(a.x, b.x),
            Math.max(a.y, b.y),
            Math.max(a.z, b.z)
        );

        outputValues.put(OUTPUT_MIN_ID, min);
        outputValues.put(OUTPUT_MAX_ID, max);
        outputValues.put(OUTPUT_VALID_ID, true);
    }
}
