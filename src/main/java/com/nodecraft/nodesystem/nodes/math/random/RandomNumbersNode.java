package com.nodecraft.nodesystem.nodes.math.random;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.NumericRangeData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.NumericDomainResolver;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.random.random_numbers",
    displayName = "Random Numbers",
    description = "Generates a list of random doubles within a domain.",
    category = "math.random",
    order = 1
)
public class RandomNumbersNode extends BaseNode {

    private static final String INPUT_DOMAIN_ID = "input_domain";
    private static final String INPUT_COUNT_ID = "input_count";
    private static final String INPUT_SEED_ID = "input_seed";
    private static final String OUTPUT_VALUES_ID = "output_values";

    private double defaultStart = 0.0d;
    private double defaultEnd = 1.0d;
    private int defaultCount = 10;

    public RandomNumbersNode() {
        super(UUID.randomUUID(), "math.random.random_numbers");
        addInputPort(new BasePort(INPUT_DOMAIN_ID, "Domain", "Domain to sample (uses lower..upper bounds)", NodeDataType.NUMERIC_RANGE, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count", "Number of random values to generate", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Optional seed for the random generator", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALUES_ID, "Values", "List of random doubles", NodeDataType.LIST, this));
    }

    @Override
    public String getDescription() {
        return "Generates a list of random doubles within a domain.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        int count = GenerationLimits.clampNonNegativeCount(getValueAsInt(inputValues.get(INPUT_COUNT_ID), defaultCount));
        NumericRangeData domain = NumericDomainResolver.resolveDomain(
            inputValues.get(INPUT_DOMAIN_ID), defaultStart, defaultEnd);
        double min = domain.lower();
        double max = domain.upper();
        Object seedVal = inputValues.get(INPUT_SEED_ID);

        Random random = seedVal instanceof Number
            ? new Random(((Number) seedVal).longValue())
            : new Random();

        if (count <= 0) {
            outputValues.put(OUTPUT_VALUES_ID, Collections.emptyList());
            return;
        }

        List<Double> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(min + random.nextDouble() * (max - min));
        }
        outputValues.put(OUTPUT_VALUES_ID, Collections.unmodifiableList(values));
    }

    private int getValueAsInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            double doubleVal = number.doubleValue();
            if (doubleVal >= Integer.MIN_VALUE && doubleVal <= Integer.MAX_VALUE) {
                return (int) Math.round(doubleVal);
            }
        }
        return defaultValue;
    }
}
