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

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.random.random_number",
    displayName = "Random Number",
    description = "Generates a single deterministic random double within a domain.",
    category = "math.random",
    order = 0
)
public class RandomNumberNode extends RandomSamplingNode {

    private static final String INPUT_DOMAIN_ID = "input_domain";
    private static final String INPUT_SEED_ID = "input_seed";
    private static final String OUTPUT_RANDOM_ID = "output_random";

    private double defaultStart = 0.0d;
    private double defaultEnd = 1.0d;

    public RandomNumberNode() {
        super(UUID.randomUUID(), "math.random.random_number");
        addInputPort(new BasePort(INPUT_DOMAIN_ID, "Domain", "Domain to sample (uses lower..upper bounds)", NodeDataType.NUMERIC_RANGE, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Deterministic seed (missing ≡ 0)", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_RANDOM_ID, "Random", "Single random value", NodeDataType.DOUBLE, this));
    }

    @Override
    public String getDescription() {
        return "Generates a single deterministic random double within a domain. Use Random Numbers for multiple values.";
    }

    @Override
    public String getDisplayName() {
        return "Random Number";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        NumericRangeData domain = resolveDomain();
        RandomInputResolver.IntegerResolveResult seed = RandomInputResolver.resolveSeed(
                resolveValue(INPUT_SEED_ID), isDriven(INPUT_SEED_ID));

        if (domain == null) {
            emitFailure(OUTPUT_RANDOM_ID, Double.NaN, "Invalid Domain");
            return;
        }
        if (!seed.valid()) {
            emitFailure(OUTPUT_RANDOM_ID, Double.NaN, "Seed must be an exact Integer");
            return;
        }

        double value = RandomOps.sampleDouble(domain.lower(), domain.upper(), RandomOps.rng(seed.value()));
        if (!Double.isFinite(value)) {
            emitFailure(OUTPUT_RANDOM_ID, Double.NaN, "Domain sample is non-finite");
            return;
        }
        emitSuccess(OUTPUT_RANDOM_ID, value);
    }

    private @Nullable NumericRangeData resolveDomain() {
        if (!isDriven(INPUT_DOMAIN_ID)) {
            return NumericRangeData.canonical(defaultStart, defaultEnd);
        }
        Object raw = resolveValue(INPUT_DOMAIN_ID);
        if (!(raw instanceof NumericRangeData range)) {
            return null;
        }
        return NumericRangeData.canonical(range.start(), range.end());
    }
}
