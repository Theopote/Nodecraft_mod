package com.nodecraft.nodesystem.nodes.math.list_sequence;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.math.SequenceOps;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Generates a numeric range from Start to End using Step.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.sequence.range",
    displayName = "Number Sequence",
    description = "Generates a discrete DOUBLE_LIST from Start to End using Step. Not a continuous domain — use Domain Input for intervals.",
    category = "math.sequence",
    order = 1
)
public class MathRangeNode extends BaseNode {

    private static final String INPUT_START_ID = "input_start";
    private static final String INPUT_END_ID = "input_end";
    private static final String INPUT_STEP_ID = "input_step";
    private static final String OUTPUT_NUMBERS_ID = "output_numbers";

    public MathRangeNode() {
        super(UUID.randomUUID(), "math.sequence.range");

        addInputPort(new BasePort(INPUT_START_ID, "Start", "The starting number of the range", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_END_ID, "End", "The ending number of the range", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_STEP_ID, "Step", "The step size between numbers", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_NUMBERS_ID, "Numbers", "The generated double list", NodeDataType.DOUBLE_LIST, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        double start = getValueAsDouble(inputValues.get(INPUT_START_ID), 0.0d);
        double end = getValueAsDouble(inputValues.get(INPUT_END_ID), 10.0d);
        double step = getValueAsDouble(inputValues.get(INPUT_STEP_ID), 1.0d);
        outputValues.put(OUTPUT_NUMBERS_ID, SequenceOps.range(start, end, step));
    }

    private static double getValueAsDouble(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return defaultValue;
    }
}
