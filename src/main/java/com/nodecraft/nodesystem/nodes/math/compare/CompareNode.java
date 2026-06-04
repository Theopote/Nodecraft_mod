package com.nodecraft.nodesystem.nodes.math.compare;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Composite compare node that exposes common relation outputs in one place.
 */
@NodeInfo(
    id = "math.compare.compare",
    displayName = "Compare",
    description = "Compares two values and outputs equality and ordering relations.",
    category = "math.compare",
    order = 0
)
public class CompareNode extends BaseNode {

    private static final String INPUT_A_ID = "input_a";
    private static final String INPUT_B_ID = "input_b";
    private static final String INPUT_MODE_ID = "input_mode";

    private static final String OUTPUT_RESULT_ID = "output_result";
    private static final String OUTPUT_EQUAL_ID = "output_equal";
    private static final String OUTPUT_GREATER_ID = "output_greater";
    private static final String OUTPUT_LESS_ID = "output_less";

    public CompareNode() {
        super(UUID.randomUUID(), "math.compare.compare");

        addInputPort(new BasePort(INPUT_A_ID, "A",
            "First value", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_B_ID, "B",
            "Second value", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_MODE_ID, "Mode",
            "Comparison mode (0==, 1!=, 2>, 3<, 4>=, 5<=)", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_RESULT_ID, "Result",
            "Result for the selected comparison mode", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_EQUAL_ID, "A == B",
            "Whether A equals B", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_GREATER_ID, "A > B",
            "Whether A is greater than B", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_LESS_ID, "A < B",
            "Whether A is less than B", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Compares two values and outputs equality and ordering relations.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object valA = inputValues.get(INPUT_A_ID);
        Object valB = inputValues.get(INPUT_B_ID);

        Object modeObj = inputValues.get(INPUT_MODE_ID);
        int mode = modeObj instanceof Number value ? value.intValue() : 0;

        CompareUtils.Relation relation = CompareUtils.compare(valA, valB);
        boolean result = CompareUtils.modeResult(relation, mode);

        outputValues.put(OUTPUT_RESULT_ID, result);
        outputValues.put(OUTPUT_EQUAL_ID, relation.equal());
        outputValues.put(OUTPUT_GREATER_ID, relation.greater());
        outputValues.put(OUTPUT_LESS_ID, relation.less());
    }
}
