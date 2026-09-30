package com.nodecraft.nodesystem.nodes.math.random;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.math.RandomOps;
import com.nodecraft.nodesystem.math.VectorSampleResult;
import com.nodecraft.nodesystem.util.RandomInputResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.Collections;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.random.random_vectors",
    displayName = "Random Vectors",
    description = "Generates a deterministic list of random vectors within a bounding box.",
    category = "math.random",
    order = 4
)
public class RandomVectorsNode extends RandomSamplingNode {

    private static final String INPUT_MIN_CORNER_ID = "input_min_corner";
    private static final String INPUT_MAX_CORNER_ID = "input_max_corner";
    private static final String INPUT_COUNT_ID = "input_count";
    private static final String INPUT_SEED_ID = "input_seed";
    private static final String OUTPUT_VECTORS_ID = "output_vectors";

    private int defaultCount = 10;

    public RandomVectorsNode() {
        super(UUID.randomUUID(), "math.random.random_vectors");
        addInputPort(new BasePort(INPUT_MIN_CORNER_ID, "Min Corner", "Minimum corner of the bounding box", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_MAX_CORNER_ID, "Max Corner", "Maximum corner of the bounding box", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count", "Number of random vectors to generate", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Deterministic seed (missing ≡ 0)", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VECTORS_ID, "Vectors", "List of random vectors", NodeDataType.VECTOR_LIST, this));
    }

    @Override
    public String getDescription() {
        return "Generates a deterministic list of random vectors within a bounding box.";
    }

    @Override
    public String getDisplayName() {
        return "Random Vectors";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d minCorner = resolveCorner(INPUT_MIN_CORNER_ID, RandomOps.defaultMinCorner());
        if (minCorner == null) {
            emitFailure(OUTPUT_VECTORS_ID, Collections.emptyList(), "Min Corner must be a finite VECTOR");
            return;
        }
        Vector3d maxCorner = resolveCorner(INPUT_MAX_CORNER_ID, RandomOps.defaultMaxCorner());
        if (maxCorner == null) {
            emitFailure(OUTPUT_VECTORS_ID, Collections.emptyList(), "Max Corner must be a finite VECTOR");
            return;
        }

        RandomInputResolver.IntegerResolveResult count = RandomInputResolver.resolveCount(
                resolveValue(INPUT_COUNT_ID), defaultCount, isDriven(INPUT_COUNT_ID));
        RandomInputResolver.IntegerResolveResult seed = RandomInputResolver.resolveSeed(
                resolveValue(INPUT_SEED_ID), isDriven(INPUT_SEED_ID));

        if (!count.valid()) {
            emitFailure(OUTPUT_VECTORS_ID, Collections.emptyList(), "Count must be an exact Integer");
            return;
        }
        if (!seed.valid()) {
            emitFailure(OUTPUT_VECTORS_ID, Collections.emptyList(), "Seed must be an exact Integer");
            return;
        }

        VectorSampleResult.ListResult result = RandomOps.sampleVectorsValidated(
                minCorner, maxCorner, count.value(), RandomOps.rng(seed.value()));
        if (!result.valid()) {
            emitFailure(OUTPUT_VECTORS_ID, Collections.emptyList(), result.error());
            return;
        }
        emitSuccess(OUTPUT_VECTORS_ID, Collections.unmodifiableList(result.vectors()));
    }

    private @Nullable Vector3d resolveCorner(String portId, Vector3d defaultCorner) {
        if (!isDriven(portId)) {
            return new Vector3d(defaultCorner);
        }
        return resolveVectorValue(portId);
    }
}
