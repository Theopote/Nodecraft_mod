package com.nodecraft.nodesystem.nodes.math.scalar_math;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.math.ScalarMathOps;
import com.nodecraft.nodesystem.math.ScalarResult;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.scalar_math.division",
    displayName = "Division (/)",
    description = "Outputs the result of A divided by B.",
    category = "math.scalar_math",
    order = 3
)
public class DivisionNode extends BaseNode {

    private static final String INPUT_A_ID = "input_a";
    private static final String INPUT_B_ID = "input_b";
    private static final String OUTPUT_QUOTIENT_ID = "output_quotient";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public DivisionNode() {
        super(UUID.randomUUID(), "math.scalar_math.division");
        addInputPort(new BasePort(INPUT_A_ID, "A", "Dividend", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_B_ID, "B", "Divisor", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_QUOTIENT_ID, "Quotient", "Result of A / B", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether division succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Outputs the result of A divided by B.";
    }

    @Override
    public String getDisplayName() {
        return "Division (/)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object valA = inputValues.get(INPUT_A_ID);
        Object valB = inputValues.get(INPUT_B_ID);
        if (!(valA instanceof Number aNumber) || !(valB instanceof Number bNumber)) {
            publish(ScalarResult.invalid());
            return;
        }
        publish(ScalarMathOps.div(aNumber.doubleValue(), bNumber.doubleValue()));
    }

    private void publish(ScalarResult result) {
        outputValues.put(OUTPUT_QUOTIENT_ID, result.value());
        outputValues.put(OUTPUT_VALID_ID, result.valid());
    }
}
