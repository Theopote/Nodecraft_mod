package com.nodecraft.nodesystem.nodes.math.list_sequence;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.math.NumericListReduction;
import com.nodecraft.nodesystem.math.ScalarResult;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.list.sum_numbers",
    displayName = "Sum Numbers",
    description = "Sums a DOUBLE_LIST.",
    category = "math.list",
    order = 35
)
public class SumNumbersNode extends BaseNode {

    private static final String INPUT_LIST_ID = "input_list";
    private static final String OUTPUT_VALUE_ID = "output_value";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SumNumbersNode() {
        super(UUID.randomUUID(), "math.list.sum_numbers");
        addInputPort(new BasePort(INPUT_LIST_ID, "Numbers", "Input double list", NodeDataType.DOUBLE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALUE_ID, "Sum", "Sum of values", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether reduction succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        NumericListReduction.ParseResult parsed =
                NumericListReduction.parseFiniteNumbers(inputValues.get(INPUT_LIST_ID));
        if (!parsed.valid()) {
            writeInvalid();
            return;
        }
        publish(NumericListReduction.sum(parsed.values()));
    }

    private void publish(ScalarResult result) {
        outputValues.put(OUTPUT_VALUE_ID, result.value());
        outputValues.put(OUTPUT_VALID_ID, result.valid());
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_VALUE_ID, Double.NaN);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}
