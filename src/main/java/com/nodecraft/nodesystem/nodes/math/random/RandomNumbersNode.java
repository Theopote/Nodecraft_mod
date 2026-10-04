package com.nodecraft.nodesystem.nodes.math.random;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.NumericRangeData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.math.RandomOps;
import com.nodecraft.nodesystem.util.RandomInputResolver;
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
    description = "Generates a deterministic list of random doubles within a domain.",
    category = "math.random",
    order = 1
)
public class RandomNumbersNode extends RandomSamplingNode {

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
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Deterministic seed (missing ≡ 0)", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALUES_ID, "Values", "List of random doubles", NodeDataType.DOUBLE_LIST, this));
    }

    @Override
    public String getDescription() {
        return "Generates a deterministic list of random doubles within a domain.";
    }

    @Override
    public String getDisplayName() {
        return "Random Numbers";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        NumericRangeData domain = resolveDomain();
        RandomInputResolver.IntegerResolveResult count = RandomInputResolver.resolveCount(
                resolveValue(INPUT_COUNT_ID), defaultCount, isDriven(INPUT_COUNT_ID));
        RandomInputResolver.IntegerResolveResult seed = RandomInputResolver.resolveSeed(
                resolveValue(INPUT_SEED_ID), isDriven(INPUT_SEED_ID));

        if (domain == null) {
            emitFailure(OUTPUT_VALUES_ID, Collections.emptyList(), "Invalid Domain");
            return;
        }
        if (!count.valid()) {
            emitFailure(OUTPUT_VALUES_ID, Collections.emptyList(), "Count must be an exact Integer");
            return;
        }
        if (!seed.valid()) {
            emitFailure(OUTPUT_VALUES_ID, Collections.emptyList(), "Seed must be an exact Integer");
            return;
        }

        int effectiveCount = count.value();
        if (effectiveCount <= 0) {
            emitSuccess(OUTPUT_VALUES_ID, Collections.emptyList());
            return;
        }

        Random random = RandomOps.rng(seed.value());
        List<Double> values = new ArrayList<>(effectiveCount);
        for (int i = 0; i < effectiveCount; i++) {
            double sample = RandomOps.sampleDouble(domain.lower(), domain.upper(), random);
            if (!Double.isFinite(sample)) {
                emitFailure(OUTPUT_VALUES_ID, Collections.emptyList(), "Domain sample is non-finite");
                return;
            }
            values.add(sample);
        }
        emitSuccess(OUTPUT_VALUES_ID, Collections.unmodifiableList(values));
    }

    private @Nullable NumericRangeData resolveDomain() {
        if (!isDriven(INPUT_DOMAIN_ID)) {
            return NumericRangeData.canonical(defaultStart, defaultEnd);
        }
        Object raw = resolveValue(INPUT_DOMAIN_ID);
        if (!(raw instanceof NumericRangeData range)) {
            return null;
        }
        if (!Double.isFinite(range.start()) || !Double.isFinite(range.end())) {
            return null;
        }
        return range;
    }
}
