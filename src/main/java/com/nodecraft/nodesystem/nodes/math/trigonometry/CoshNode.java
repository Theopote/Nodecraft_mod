package com.nodecraft.nodesystem.nodes.math.trigonometry;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.math.TrigMathOps;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.trigonometry.cosh",
    displayName = "Cosh",
    description = "Computes the hyperbolic cosine of the input value.",
    category = "math.trigonometry",
    order = 12
)
public class CoshNode extends TrigScalarNode {

    private static final String INPUT_VALUE_ID = "input_value";
    private static final String OUTPUT_RESULT_ID = "output_result";

    public CoshNode() {
        super(UUID.randomUUID(), "math.trigonometry.cosh");

        addInputPort(new BasePort(INPUT_VALUE_ID, "Value", "Input value", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_RESULT_ID, "Result", "cosh(value)", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether input is a valid finite number", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Cosh";
    }

    @Override
    public String getDescription() {
        return "Computes the hyperbolic cosine of the input value.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Double value = requireInput(INPUT_VALUE_ID);
        if (value == null) {
            emitInvalid(OUTPUT_RESULT_ID);
            return;
        }
        emit(OUTPUT_RESULT_ID, TrigMathOps.cosh(value));
    }
}
