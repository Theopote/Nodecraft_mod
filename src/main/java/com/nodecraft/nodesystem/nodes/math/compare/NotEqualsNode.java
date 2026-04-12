package com.nodecraft.nodesystem.nodes.math.compare;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Compares two values for inequality.
 */
@NodeInfo(
    id = "math.compare.not_equals",
    displayName = "Not Equals (!=)",
    description = "Returns true when A does not equal B.",
    category = "math.compare",
    order = 2
)
public class NotEqualsNode extends BaseNode {

    private static final String INPUT_A_ID = "input_a";
    private static final String INPUT_B_ID = "input_b";
    private static final String OUTPUT_RESULT_ID = "output_result";

    public NotEqualsNode() {
        super(UUID.randomUUID(), "math.compare.not_equals");
        addInputPort(new BasePort(INPUT_A_ID, "A", "Left value", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_B_ID, "B", "Right value", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_RESULT_ID, "Result", "Whether A does not equal B", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Returns true when A does not equal B.";
    }

    @Override
    public String getDisplayName() {
        return "Not Equals (!=)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object valA = inputValues.get(INPUT_A_ID);
        Object valB = inputValues.get(INPUT_B_ID);

        boolean result;
        if (valA == null && valB == null) {
            result = false;
        } else if (valA == null || valB == null) {
            result = true;
        } else if (valA instanceof Number && valB instanceof Number) {
            double numA = ((Number) valA).doubleValue();
            double numB = ((Number) valB).doubleValue();
            result = Math.abs(numA - numB) >= 1e-10;
        } else if (valA instanceof String || valB instanceof String) {
            result = !Objects.toString(valA, "").equals(Objects.toString(valB, ""));
        } else {
            result = !Objects.equals(valA, valB);
        }

        outputValues.put(OUTPUT_RESULT_ID, result);
    }
}
