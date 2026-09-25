package com.nodecraft.nodesystem.nodes.math.logic;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
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
    effect = NodeEffect.PURE,
    id = "math.logic.or",
    displayName = "OR",
    description = "Returns true when either boolean input is true.",
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
        return "Returns true when either boolean input is true.";
    }

    @Override
    public String getDisplayName() {
        return "OR";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean result = LogicUtils.booleanValue(inputValues.get(INPUT_A_ID))
            || LogicUtils.booleanValue(inputValues.get(INPUT_B_ID));
        outputValues.put(OUTPUT_RESULT_ID, result);
    }
}
