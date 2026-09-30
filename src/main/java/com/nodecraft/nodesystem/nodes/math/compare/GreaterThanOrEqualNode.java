package com.nodecraft.nodesystem.nodes.math.compare;

import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.compare.greater_than_or_equal",
    displayName = "Greater Than or Equal (>=)",
    description = "Returns true when A is greater than or equal to B.",
    category = "math.compare",
    order = 6
)
public class GreaterThanOrEqualNode extends OrderingCompareNode {

    public GreaterThanOrEqualNode() {
        super(UUID.randomUUID(), "math.compare.greater_than_or_equal");
    }

    @Override
    public String getDescription() {
        return "Returns true when A is greater than or equal to B.";
    }

    @Override
    public String getDisplayName() {
        return "Greater Than or Equal (>=)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        processOrdering(CompareUtils::compareGreaterOrEqual);
    }
}
