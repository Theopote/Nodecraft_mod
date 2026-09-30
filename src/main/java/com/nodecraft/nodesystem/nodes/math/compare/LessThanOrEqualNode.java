package com.nodecraft.nodesystem.nodes.math.compare;

import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.compare.less_than_or_equal",
    displayName = "Less Than or Equal (<=)",
    description = "Returns true when A is less than or equal to B.",
    category = "math.compare",
    order = 4
)
public class LessThanOrEqualNode extends OrderingCompareNode {

    public LessThanOrEqualNode() {
        super(UUID.randomUUID(), "math.compare.less_than_or_equal");
    }

    @Override
    public String getDescription() {
        return "Returns true when A is less than or equal to B.";
    }

    @Override
    public String getDisplayName() {
        return "Less Than or Equal (<=)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        processOrdering(CompareUtils::compareLessOrEqual);
    }
}
