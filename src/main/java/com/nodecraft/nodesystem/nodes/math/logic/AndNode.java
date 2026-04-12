package com.nodecraft.nodesystem.nodes.math.logic;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Boolean AND node.
 */
@NodeInfo(
    id = "math.logic.and",
    displayName = "AND",
    description = "Returns true only when both inputs evaluate to true.",
    category = "math.logic",
    order = 2
)
public class AndNode extends BaseNode {

    private static final String INPUT_A_ID = "input_a";
    private static final String INPUT_B_ID = "input_b";
    private static final String OUTPUT_RESULT_ID = "output_result";

    public AndNode() {
        super(UUID.randomUUID(), "math.logic.and");
        addInputPort(new BasePort(INPUT_A_ID, "A", "First boolean input", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_B_ID, "B", "Second boolean input", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_RESULT_ID, "Result", "Result of A && B", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Returns true only when both inputs evaluate to true.";
    }

    @Override
    public String getDisplayName() {
        return "AND";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean result = toBoolean(inputValues.get(INPUT_A_ID)) && toBoolean(inputValues.get(INPUT_B_ID));
        outputValues.put(OUTPUT_RESULT_ID, result);
    }

    private boolean toBoolean(@Nullable Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0d;
        }
        return value != null && Boolean.parseBoolean(value.toString());
    }
}
