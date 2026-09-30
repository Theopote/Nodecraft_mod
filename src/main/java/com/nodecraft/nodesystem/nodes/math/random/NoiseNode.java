package com.nodecraft.nodesystem.nodes.math.random;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.math.RandomOps;
import com.nodecraft.nodesystem.util.RandomInputResolver;
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
public class NoiseNode extends RandomSamplingNode {

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
        Double x = RandomInputResolver.resolveDouble(resolveValue(INPUT_X_ID), 0.0d, isDriven(INPUT_X_ID));
        Double y = RandomInputResolver.resolveDouble(resolveValue(INPUT_Y_ID), 0.0d, isDriven(INPUT_Y_ID));
        Double z = RandomInputResolver.resolveDouble(resolveValue(INPUT_Z_ID), 0.0d, isDriven(INPUT_Z_ID));
        RandomInputResolver.IntegerResolveResult seed = RandomInputResolver.resolveSeed(
                resolveValue(INPUT_SEED_ID), isDriven(INPUT_SEED_ID));

        if (x == null) {
            emitFailure(OUTPUT_NOISE_ID, Double.NaN, "X must be an exact finite Double");
            return;
        }
        if (y == null) {
            emitFailure(OUTPUT_NOISE_ID, Double.NaN, "Y must be an exact finite Double");
            return;
        }
        if (z == null) {
            emitFailure(OUTPUT_NOISE_ID, Double.NaN, "Z must be an exact finite Double");
            return;
        }
        if (!seed.valid()) {
            emitFailure(OUTPUT_NOISE_ID, Double.NaN, "Seed must be an exact Integer");
            return;
        }

        double noise = RandomOps.valueNoise3(x, y, z, seed.value());
        if (!Double.isFinite(noise)) {
            emitFailure(OUTPUT_NOISE_ID, Double.NaN, "Noise sample is non-finite");
            return;
        }
        emitSuccess(OUTPUT_NOISE_ID, noise);
    }
}
