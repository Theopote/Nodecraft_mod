package com.nodecraft.nodesystem.nodes.math.compare;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Compares whether A is less than or equal to B.
 */
@NodeInfo(
    id = "math.compare.less_than_or_equal",
    displayName = "Less Than or Equal (<=)",
    description = "Returns true when A is less than or equal to B.",
    category = "math.compare",
    order = 4
)
public class LessThanOrEqualNode extends BaseNode {

    private static final String INPUT_A_ID = "input_a";
    private static final String INPUT_B_ID = "input_b";
    private static final String OUTPUT_RESULT_ID = "output_result";

    public LessThanOrEqualNode() {
        super(UUID.randomUUID(), "math.compare.less_than_or_equal");
        addInputPort(new BasePort(INPUT_A_ID, "A", "Left value", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_B_ID, "B", "Right value", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_RESULT_ID, "Result", "Whether A is less than or equal to B", NodeDataType.BOOLEAN, this));
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
        Object valA = inputValues.get(INPUT_A_ID);
        Object valB = inputValues.get(INPUT_B_ID);

        CompareUtils.Relation relation = CompareUtils.compare(valA, valB);
        outputValues.put(OUTPUT_RESULT_ID, relation.less() || relation.equal());
    }
}
