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
    id = "math.scalar_math.modulus",
    displayName = "Modulus (%)",
    description = "Returns the remainder of A divided by B.",
    category = "math.scalar_math",
    order = 4
)
public class ModulusNode extends BaseNode {

    private static final String INPUT_A_ID = "input_a";
    private static final String INPUT_B_ID = "input_b";
    private static final String OUTPUT_REMAINDER_ID = "output_remainder";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ModulusNode() {
        super(UUID.randomUUID(), "math.scalar_math.modulus");
        addInputPort(new BasePort(INPUT_A_ID, "A", "Dividend", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_B_ID, "B", "Divisor", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_REMAINDER_ID, "Remainder", "Result of A % B", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether modulus succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Returns the remainder of A divided by B.";
    }

    @Override
    public String getDisplayName() {
        return "Modulus (%)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object valA = inputValues.get(INPUT_A_ID);
        Object valB = inputValues.get(INPUT_B_ID);
        if (!(valA instanceof Number aNumber) || !(valB instanceof Number bNumber)) {
            publish(ScalarResult.invalid());
            return;
        }
        publish(ScalarMathOps.mod(aNumber.doubleValue(), bNumber.doubleValue()));
    }

    private void publish(ScalarResult result) {
        outputValues.put(OUTPUT_REMAINDER_ID, result.value());
        outputValues.put(OUTPUT_VALID_ID, result.valid());
    }
}
