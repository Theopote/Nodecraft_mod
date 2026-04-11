package com.nodecraft.nodesystem.nodes.math.random;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@NodeInfo(
    id = "math.random.random_vector",
    displayName = "Random Vector",
    description = "Generates random vectors within a specified bounding box.",
    category = "math.random"
)
public class RandomVectorNode extends BaseNode {

    private static final String INPUT_COUNT_ID = "input_count";
    private static final String INPUT_MIN_CORNER_ID = "input_min_corner";
    private static final String INPUT_MAX_CORNER_ID = "input_max_corner";
    private static final String INPUT_SEED_ID = "input_seed";
    private static final String OUTPUT_RANDOM_ID = "output_random_vector";

    public RandomVectorNode() {
        super(UUID.randomUUID(), "math.random.random_vector");
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count", "Number of random vectors", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_MIN_CORNER_ID, "Min Corner", "Minimum corner of the bounding box", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_MAX_CORNER_ID, "Max Corner", "Maximum corner of the bounding box", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Optional random seed", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_RANDOM_ID, "Random", "The generated random vector(s)", NodeDataType.ANY, this));
    }

    @Override
    public String getDescription() {
        return "Generates random vectors within a bounding box.";
    }

    @Override
    public String getDisplayName() {
        return "Random Vector";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        int count = getValueAsInt(inputValues.get(INPUT_COUNT_ID), 1);
        Vec3d minCorner = getValueAsVec3d(inputValues.get(INPUT_MIN_CORNER_ID), Vec3d.ZERO);
        Vec3d maxCorner = getValueAsVec3d(inputValues.get(INPUT_MAX_CORNER_ID), new Vec3d(1, 1, 1));
        Object seedVal = inputValues.get(INPUT_SEED_ID);

        double minX = Math.min(minCorner.x, maxCorner.x);
        double minY = Math.min(minCorner.y, maxCorner.y);
        double minZ = Math.min(minCorner.z, maxCorner.z);
        double maxX = Math.max(minCorner.x, maxCorner.x);
        double maxY = Math.max(minCorner.y, maxCorner.y);
        double maxZ = Math.max(minCorner.z, maxCorner.z);

        Random random = seedVal instanceof Number
            ? new Random(((Number) seedVal).longValue())
            : new Random();

        if (count <= 0) {
            outputValues.put(OUTPUT_RANDOM_ID, Collections.emptyList());
        } else if (count == 1) {
            outputValues.put(OUTPUT_RANDOM_ID, new Vec3d(
                minX + random.nextDouble() * (maxX - minX),
                minY + random.nextDouble() * (maxY - minY),
                minZ + random.nextDouble() * (maxZ - minZ)
            ));
        } else {
            List<Vec3d> randomVectors = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                randomVectors.add(new Vec3d(
                    minX + random.nextDouble() * (maxX - minX),
                    minY + random.nextDouble() * (maxY - minY),
                    minZ + random.nextDouble() * (maxZ - minZ)
                ));
            }
            outputValues.put(OUTPUT_RANDOM_ID, Collections.unmodifiableList(randomVectors));
        }
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

    private Vec3d getValueAsVec3d(Object value, Vec3d defaultValue) {
        return value instanceof Vec3d ? (Vec3d) value : defaultValue;
    }
}
