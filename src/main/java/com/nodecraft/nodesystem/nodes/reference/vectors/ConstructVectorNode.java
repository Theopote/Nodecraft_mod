package com.nodecraft.nodesystem.nodes.reference.vectors;

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
    id = "reference.vectors.construct_vector",
    displayName = "Construct Vector",
    description = "Constructs a vector from X, Y, and Z components",
    category = "reference.vectors",
    order = 2
)
public class ConstructVectorNode extends BaseNode {

    private static final String INPUT_X_ID = "input_x";
    private static final String INPUT_Y_ID = "input_y";
    private static final String INPUT_Z_ID = "input_z";

    private static final String OUTPUT_VECTOR_ID = "output_vector";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ConstructVectorNode() {
        super(UUID.randomUUID(), "reference.vectors.construct_vector");

        addInputPort(new BasePort(INPUT_X_ID, "X", "X component input", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_Y_ID, "Y", "Y component input", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_Z_ID, "Z", "Z component input", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_VECTOR_ID, "Vector", "Constructed vector", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether all resolved components are finite numbers",
            NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Construct Vector";
    }

    @Override
    public String getDescription() {
        return "Constructs a vector from X, Y, and Z components";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        double x = toDouble(inputValues.get(INPUT_X_ID));
        double y = toDouble(inputValues.get(INPUT_Y_ID));
        double z = toDouble(inputValues.get(INPUT_Z_ID));
        boolean valid = Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);

        outputValues.put(OUTPUT_VECTOR_ID, valid ? new Vector3d(x, y, z) : null);
        outputValues.put(OUTPUT_VALID_ID, valid);
    }

    private double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.NaN;
    }
}
