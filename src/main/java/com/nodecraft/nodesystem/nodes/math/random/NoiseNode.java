package com.nodecraft.nodesystem.nodes.math.random;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    id = "math.random.noise",
    displayName = "Noise",
    description = "Samples coherent noise from a 3D position and seed.",
    category = "math.random"
)
public class NoiseNode extends BaseNode {

    private static final String INPUT_X_ID = "input_x";
    private static final String INPUT_Y_ID = "input_y";
    private static final String INPUT_Z_ID = "input_z";
    private static final String INPUT_SEED_ID = "input_seed";
    private static final String OUTPUT_NOISE_ID = "output_noise";

    public NoiseNode() {
        super(UUID.randomUUID(), "math.random.noise");
        addInputPort(new BasePort(INPUT_X_ID, "X", "Noise sample X coordinate", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_Y_ID, "Y", "Noise sample Y coordinate", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_Z_ID, "Z", "Noise sample Z coordinate", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Noise seed", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_NOISE_ID, "Noise", "Noise sample value", NodeDataType.DOUBLE, this));
    }

    @Override
    public String getDescription() {
        return "Samples coherent noise from a 3D position and seed.";
    }

    @Override
    public String getDisplayName() {
        return "Noise";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object xObj = inputValues.get(INPUT_X_ID);
        Object yObj = inputValues.get(INPUT_Y_ID);
        Object zObj = inputValues.get(INPUT_Z_ID);
        Object seedObj = inputValues.get(INPUT_SEED_ID);

        double x = xObj instanceof Number ? ((Number) xObj).doubleValue() : 0.0;
        double y = yObj instanceof Number ? ((Number) yObj).doubleValue() : 0.0;
        double z = zObj instanceof Number ? ((Number) zObj).doubleValue() : 0.0;
        long seed = seedObj instanceof Number ? ((Number) seedObj).longValue() : 0L;

        double noise = sampleHashNoise(x, y, z, seed);
        outputValues.put(OUTPUT_NOISE_ID, noise);
    }

    private double sampleHashNoise(double x, double y, double z, long seed) {
        long hash = Double.doubleToLongBits(x * 12.9898 + y * 78.233 + z * 37.719) ^ seed * 0x9E3779B97F4A7C15L;
        hash ^= (hash >>> 33);
        hash *= 0xff51afd7ed558ccdl;
        hash ^= (hash >>> 33);
        hash *= 0xc4ceb9fe1a85ec53l;
        hash ^= (hash >>> 33);
        return ((hash & Long.MAX_VALUE) / (double) Long.MAX_VALUE) * 2.0 - 1.0;
    }
}
