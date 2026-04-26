package com.nodecraft.nodesystem.nodes.math.scalar_math;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;

import java.util.UUID;

@NodeInfo(
    id = "math.scalar_math.sqrt",
    displayName = "Square Root",
    description = "Computes the square root of a numeric input.",
    category = "math.scalar_math",
    order = 16
)
public class SqrtNode extends BaseNode {

    private static final String INPUT_VALUE_ID = "input_value";
    private static final String OUTPUT_RESULT_ID = "output_result";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SqrtNode() {
        super(UUID.randomUUID(), "math.scalar_math.sqrt");

        addInputPort(new BasePort(INPUT_VALUE_ID, "Value", "Input value", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_RESULT_ID, "Result", "Square root of the input", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the input is numeric and non-negative", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Square Root";
    }

    @Override
    public String getDescription() {
        return "Computes the square root of a numeric input.";
    }

    @Override
    public void processNode(ExecutionContext context) {
        Object valueObj = inputValues.get(INPUT_VALUE_ID);
        if (!(valueObj instanceof Number number)) {
            outputValues.put(OUTPUT_RESULT_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        double value = number.doubleValue();
        if (!Double.isFinite(value) || value < 0.0d) {
            outputValues.put(OUTPUT_RESULT_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        outputValues.put(OUTPUT_RESULT_ID, Math.sqrt(value));
        outputValues.put(OUTPUT_VALID_ID, true);
    }
}

