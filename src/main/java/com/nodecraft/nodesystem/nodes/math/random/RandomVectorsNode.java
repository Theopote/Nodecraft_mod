package com.nodecraft.nodesystem.nodes.math.random;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.math.RandomOps;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.random.random_vectors",
    displayName = "Random Vectors",
    description = "Generates a deterministic list of random vectors within a bounding box.",
    category = "math.random",
    order = 4
)
public class RandomVectorsNode extends BaseNode {

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
        int count = RandomOps.resolveCount(inputValues.get(INPUT_COUNT_ID), defaultCount);
        Vector3d minCorner = RandomOps.resolveVector(
                inputValues.get(INPUT_MIN_CORNER_ID), RandomOps.defaultMinCorner());
        Vector3d maxCorner = RandomOps.resolveVector(
                inputValues.get(INPUT_MAX_CORNER_ID), RandomOps.defaultMaxCorner());
        int seed = RandomOps.resolveSeed(inputValues.get(INPUT_SEED_ID));
        Random random = RandomOps.rng(seed);

        if (count <= 0) {
            outputValues.put(OUTPUT_VECTORS_ID, Collections.emptyList());
            return;
        }

        List<Vector3d> vectors = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            vectors.add(RandomOps.sampleVector(minCorner, maxCorner, random));
        }
        outputValues.put(OUTPUT_VECTORS_ID, Collections.unmodifiableList(vectors));
    }
}
