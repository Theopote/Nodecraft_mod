package com.nodecraft.nodesystem.nodes.math.random;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@NodeInfo(
    id = "math.random.random_number",
    displayName = "Random Number",
    description = "Generates random numbers within a specified range.",
    category = "math.random",
    order = 0
)
public class RandomNumberNode extends BaseNode {

    private static final String INPUT_COUNT_ID = "input_count";
    private static final String INPUT_MIN_ID = "input_min";
    private static final String INPUT_MAX_ID = "input_max";
    private static final String INPUT_SEED_ID = "input_seed";
    private static final String OUTPUT_RANDOM_ID = "output_random";

    public RandomNumberNode() {
        super(UUID.randomUUID(), "math.random.random_number");
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count", "Number of random values to generate", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_MIN_ID, "Min", "Minimum random value (inclusive)", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_MAX_ID, "Max", "Maximum random value (exclusive)", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Optional seed for the random generator", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_RANDOM_ID, "Random", "The generated random number(s)", NodeDataType.ANY, this));
    }

    @Override
    public String getDescription() {
        return "Generates random numbers with optional Count, Min, Max, and Seed.";
    }

    @Override
    public String getDisplayName() {
        return "Random Number";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        int count = GenerationLimits.clampNonNegativeCount(getValueAsInt(inputValues.get(INPUT_COUNT_ID), 1));
        double min = getValueAsDouble(inputValues.get(INPUT_MIN_ID), 0.0);
        double max = getValueAsDouble(inputValues.get(INPUT_MAX_ID), 1.0);
        Object seedVal = inputValues.get(INPUT_SEED_ID);

        if (min > max) {
            double temp = min;
            min = max;
            max = temp;
        }

        Random random = seedVal instanceof Number
            ? new Random(((Number) seedVal).longValue())
            : new Random();

        if (count <= 0) {
            outputValues.put(OUTPUT_RANDOM_ID, Collections.emptyList());
        } else if (count == 1) {
            outputValues.put(OUTPUT_RANDOM_ID, min + random.nextDouble() * (max - min));
        } else {
            List<Double> randomNumbers = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                randomNumbers.add(min + random.nextDouble() * (max - min));
            }
            outputValues.put(OUTPUT_RANDOM_ID, Collections.unmodifiableList(randomNumbers));
        }
    }

    private double getValueAsDouble(Object value, double defaultValue) {
        return value instanceof Number ? ((Number) value).doubleValue() : defaultValue;
    }

    private int getValueAsInt(Object value, int defaultValue) {
        if (value instanceof Number) {
            double doubleVal = ((Number) value).doubleValue();
            if (doubleVal >= Integer.MIN_VALUE && doubleVal <= Integer.MAX_VALUE) {
                return (int) Math.round(doubleVal);
            }
        }
        return defaultValue;
    }
}
