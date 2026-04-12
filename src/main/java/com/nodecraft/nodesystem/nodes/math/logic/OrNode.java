package com.nodecraft.nodesystem.nodes.math.logic;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Boolean OR node.
 */
@NodeInfo(
    id = "math.logic.or",
    displayName = "OR",
    description = "Returns true when either input evaluates to true.",
    category = "math.logic",
    order = 3
)
public class OrNode extends BaseNode {

    private static final String INPUT_A_ID = "input_a";
    private static final String INPUT_B_ID = "input_b";
    private static final String OUTPUT_RESULT_ID = "output_result";

    public OrNode() {
        super(UUID.randomUUID(), "math.logic.or");
        addInputPort(new BasePort(INPUT_A_ID, "A", "First boolean input", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_B_ID, "B", "Second boolean input", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_RESULT_ID, "Result", "Result of A || B", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Returns true when either input evaluates to true.";
    }

    @Override
    public String getDisplayName() {
        return "OR";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean result = toBoolean(inputValues.get(INPUT_A_ID)) || toBoolean(inputValues.get(INPUT_B_ID));
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
