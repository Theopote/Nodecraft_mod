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
 * Boolean AND node.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.logic.and",
    displayName = "AND",
    description = "Returns true only when both boolean inputs are true.",
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
        return "Returns true only when both boolean inputs are true.";
    }

    @Override
    public String getDisplayName() {
        return "AND";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean result = LogicUtils.booleanValue(inputValues.get(INPUT_A_ID))
            && LogicUtils.booleanValue(inputValues.get(INPUT_B_ID));
        outputValues.put(OUTPUT_RESULT_ID, result);
    }
}
