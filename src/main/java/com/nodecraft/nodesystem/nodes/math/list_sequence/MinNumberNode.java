package com.nodecraft.nodesystem.nodes.math.list_sequence;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.list.min_number",
    displayName = "Min Number",
    description = "Minimum of a DOUBLE_LIST.",
    category = "math.list",
    order = 37
)
public class MinNumberNode extends BaseNode {

    private static final String INPUT_LIST_ID = "input_list";
    private static final String OUTPUT_VALUE_ID = "output_value";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public MinNumberNode() {
        super(UUID.randomUUID(), "math.list.min_number");
        addInputPort(new BasePort(INPUT_LIST_ID, "Numbers", "Input double list", NodeDataType.DOUBLE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALUE_ID, "Min", "Minimum value", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether reduction succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object input = inputValues.get(INPUT_LIST_ID);
        if (!(input instanceof List<?> list) || list.isEmpty()) {
            outputValues.put(OUTPUT_VALUE_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }
        double min = Double.POSITIVE_INFINITY;
        for (Object item : list) {
            if (!(item instanceof Number number) || !Double.isFinite(number.doubleValue())) {
                outputValues.put(OUTPUT_VALUE_ID, Double.NaN);
                outputValues.put(OUTPUT_VALID_ID, false);
                return;
            }
            min = Math.min(min, number.doubleValue());
        }
        outputValues.put(OUTPUT_VALUE_ID, min);
        outputValues.put(OUTPUT_VALID_ID, true);
    }
}
