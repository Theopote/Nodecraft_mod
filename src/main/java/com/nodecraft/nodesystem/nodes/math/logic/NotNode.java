package com.nodecraft.nodesystem.nodes.math.logic;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Boolean NOT node.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.logic.not",
    displayName = "NOT",
    description = "Returns the logical negation of the boolean input.",
    category = "math.logic",
    order = 4
)
public class NotNode extends BooleanLogicNode {

    private static final String INPUT_VALUE_ID = "input_value";

    public NotNode() {
        super(UUID.randomUUID(), "math.logic.not");
        addInputPort(new BasePort(INPUT_VALUE_ID, "Value", "Boolean input value", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Returns the logical negation of the boolean input.";
    }

    @Override
    public String getDisplayName() {
        return "NOT";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        processUnary(LogicUtils::not, INPUT_VALUE_ID);
    }
}
