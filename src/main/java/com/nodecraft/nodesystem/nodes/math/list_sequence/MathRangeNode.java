package com.nodecraft.nodesystem.nodes.math.list_sequence;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Generates a numeric range from Start to End using Step.
 */
@NodeInfo(
    id = "math.sequence.range",
    displayName = "Range",
    description = "Generates a numeric sequence from Start to End using Step.",
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

        addInputPort(new BasePort(INPUT_START_ID, "Start", "The starting number of the range", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_END_ID, "End", "The ending number of the range", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_STEP_ID, "Step", "The step size between numbers", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_NUMBERS_ID, "Numbers", "The generated list of numbers", NodeDataType.ANY, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        double start = getValueAsDouble(inputValues.get(INPUT_START_ID), 0.0);
        double end = getValueAsDouble(inputValues.get(INPUT_END_ID), 10.0);
        double step = getValueAsDouble(inputValues.get(INPUT_STEP_ID), 1.0);

        List<Double> numbers = new ArrayList<>();
        if (!Double.isFinite(start) || !Double.isFinite(end) || !Double.isFinite(step)) {
            outputValues.put(OUTPUT_NUMBERS_ID, Collections.unmodifiableList(numbers));
            return;
        }

        if (Math.abs(step) < 1e-10) {
            if (start <= end) {
                numbers.add(start);
            }
            outputValues.put(OUTPUT_NUMBERS_ID, Collections.unmodifiableList(numbers));
            return;
        }

        if (step > 0) {
            if (start <= end) {
                for (double current = start; current <= end + step * 0.001; current += step) {
                    numbers.add(current);
                }
            }
        } else if (start >= end) {
            for (double current = start; current >= end + step * 0.001; current += step) {
                numbers.add(current);
            }
        }

        outputValues.put(OUTPUT_NUMBERS_ID, Collections.unmodifiableList(numbers));
    }

    private double getValueAsDouble(Object value, double defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }
}

