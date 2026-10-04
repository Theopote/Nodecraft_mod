package com.nodecraft.nodesystem.nodes.math.random;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.datatypes.VectorData;
import com.nodecraft.nodesystem.math.RandomOps;
import com.nodecraft.nodesystem.math.VectorSampleResult;
import com.nodecraft.nodesystem.util.RandomInputResolver;
import com.nodecraft.nodesystem.util.VectorUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.random.random_vector",
    displayName = "Random Vector",
    description = "Generates a single deterministic random vector within a bounding box.",
    category = "math.random",
    order = 3
)
public class RandomVectorNode extends RandomSamplingNode {

    private static final String INPUT_MIN_CORNER_ID = "input_min_corner";
    private static final String INPUT_MAX_CORNER_ID = "input_max_corner";
    private static final String INPUT_SEED_ID = "input_seed";
    private static final String OUTPUT_VECTOR_ID = "output_vector";

    public RandomVectorNode() {
        super(UUID.randomUUID(), "math.random.random_vector");
        addInputPort(new BasePort(INPUT_MIN_CORNER_ID, "Min Corner", "Minimum corner of the bounding box", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_MAX_CORNER_ID, "Max Corner", "Maximum corner of the bounding box", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Deterministic seed (missing ≡ 0)", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VECTOR_ID, "Vector", "Random vector", NodeDataType.VECTOR, this));
    }

    @Override
    public String getDescription() {
        return "Generates a single deterministic random vector within a bounding box. Use Random Vectors for multiple.";
    }

    @Override
    public String getDisplayName() {
        return "Random Vector";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Vector3d minCorner = resolveCorner(INPUT_MIN_CORNER_ID, RandomOps.defaultMinCorner());
        if (minCorner == null) {
            emitFailure(OUTPUT_VECTOR_ID, null, "Min Corner must be a finite VECTOR");
            return;
        }
        Vector3d maxCorner = resolveCorner(INPUT_MAX_CORNER_ID, RandomOps.defaultMaxCorner());
        if (maxCorner == null) {
            emitFailure(OUTPUT_VECTOR_ID, null, "Max Corner must be a finite VECTOR");
            return;
        }

        RandomInputResolver.IntegerResolveResult seed = RandomInputResolver.resolveSeed(
                resolveValue(INPUT_SEED_ID), isDriven(INPUT_SEED_ID));
        if (!seed.valid()) {
            emitFailure(OUTPUT_VECTOR_ID, null, "Seed must be an exact Integer");
            return;
        }

        VectorSampleResult result = RandomOps.sampleVectorValidated(
                minCorner, maxCorner, RandomOps.rng(seed.value()));
        if (!result.valid()) {
            emitFailure(OUTPUT_VECTOR_ID, null, result.error());
            return;
        }
        VectorData output = VectorUtils.toVectorPort(result.vector());
        if (output == null) {
            emitFailure(OUTPUT_VECTOR_ID, null, "Vector sample is non-finite");
            return;
        }
        emitSuccess(OUTPUT_VECTOR_ID, output);
    }

    private @Nullable Vector3d resolveCorner(String portId, Vector3d defaultCorner) {
        if (!isDriven(portId)) {
            return new Vector3d(defaultCorner);
        }
        return resolveVectorValue(portId);
    }
}
