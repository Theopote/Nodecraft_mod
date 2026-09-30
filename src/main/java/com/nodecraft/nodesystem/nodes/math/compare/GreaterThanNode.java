package com.nodecraft.nodesystem.nodes.math.compare;

import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Compares whether A is greater than B.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.compare.greater_than",
    displayName = "Greater Than (>)",
    description = "Returns true when A is greater than B.",
    category = "math.compare",
    order = 5
)
public class GreaterThanNode extends OrderingCompareNode {

    public GreaterThanNode() {
        super(UUID.randomUUID(), "math.compare.greater_than");
    }

    @Override
    public String getDescription() {
        return "Returns true when A is greater than B.";
    }

    @Override
    public String getDisplayName() {
        return "Greater Than (>)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        processOrdering(CompareUtils::compareGreater);
    }
}
