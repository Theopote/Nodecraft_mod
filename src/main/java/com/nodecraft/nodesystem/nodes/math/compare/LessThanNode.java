package com.nodecraft.nodesystem.nodes.math.compare;

import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Compares whether A is less than B.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.compare.less_than",
    displayName = "Less Than (<)",
    description = "Returns true when A is less than B.",
    category = "math.compare",
    order = 3
)
public class LessThanNode extends OrderingCompareNode {

    public LessThanNode() {
        super(UUID.randomUUID(), "math.compare.less_than");
    }

    @Override
    public String getDescription() {
        return "Returns true when A is less than B.";
    }

    @Override
    public String getDisplayName() {
        return "Less Than (<)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        processOrdering(CompareUtils::compareLess);
    }
}
