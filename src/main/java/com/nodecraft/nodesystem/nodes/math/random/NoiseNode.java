package com.nodecraft.nodesystem.nodes.math.random;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.math.RandomOps;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.random.noise",
    displayName = "Noise",
    description = "Samples coherent 3D value noise from a position and seed.",
    category = "math.random",
    order = 5
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
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Deterministic noise seed (missing ≡ 0)", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_NOISE_ID, "Noise", "Coherent noise sample value", NodeDataType.DOUBLE, this));
    }

    @Override
    public String getDescription() {
        return "Samples coherent 3D value noise from a position and seed. Nearby positions produce smoothly related values.";
    }

    @Override
    public String getDisplayName() {
        return "Noise";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        double x = toDouble(inputValues.get(INPUT_X_ID));
        double y = toDouble(inputValues.get(INPUT_Y_ID));
        double z = toDouble(inputValues.get(INPUT_Z_ID));
        int seed = RandomOps.resolveSeed(inputValues.get(INPUT_SEED_ID));
        outputValues.put(OUTPUT_NOISE_ID, RandomOps.valueNoise3(x, y, z, seed));
    }

    private static double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.NaN;
    }
}
