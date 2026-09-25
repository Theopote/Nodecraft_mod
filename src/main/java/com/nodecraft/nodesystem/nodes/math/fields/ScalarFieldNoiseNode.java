package com.nodecraft.nodesystem.nodes.math.fields;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.ScalarFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.math.FieldMath;
import com.nodecraft.nodesystem.math.RandomOps;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.fields.scalar_noise",
    displayName = "Scalar Field Noise",
    description = "Builds a deterministic coherent noise scalar field over world space.",
    category = "math.fields",
    order = 3
)
public class ScalarFieldNoiseNode extends BaseNode {

    private static final String INPUT_SEED_ID = "input_seed";
    private static final String INPUT_SCALE_ID = "input_scale";
    private static final String INPUT_OFFSET_X_ID = "input_offset_x";
    private static final String INPUT_OFFSET_Y_ID = "input_offset_y";
    private static final String INPUT_OFFSET_Z_ID = "input_offset_z";
    private static final String INPUT_AMPLITUDE_ID = "input_amplitude";

    private static final String OUTPUT_FIELD_ID = "output_field";

    private double defaultScale = 1.0d;
    private double defaultAmplitude = 1.0d;

    public ScalarFieldNoiseNode() {
        super(UUID.randomUUID(), "math.fields.scalar_noise");

        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Deterministic noise seed (missing ≡ 0)", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SCALE_ID, "Scale", "Noise frequency scale (larger = finer detail)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_OFFSET_X_ID, "Offset X", "Domain offset X", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_OFFSET_Y_ID, "Offset Y", "Domain offset Y", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_OFFSET_Z_ID, "Offset Z", "Domain offset Z", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_AMPLITUDE_ID, "Amplitude", "Output multiplier", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_FIELD_ID, "Field", "Scalar noise field", NodeDataType.SCALAR_FIELD, this));
    }

    @Override
    public String getDescription() {
        return "Builds a deterministic coherent noise scalar field over world space (same kernel as Random Noise).";
    }

    @Override
    public String getDisplayName() {
        return "Scalar Field Noise";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        int seed = RandomOps.resolveSeed(inputValues.get(INPUT_SEED_ID));
        double scale = FieldMath.resolveFinite(inputValues.get(INPUT_SCALE_ID), defaultScale);
        double ox = FieldMath.resolveFinite(inputValues.get(INPUT_OFFSET_X_ID), 0.0d);
        double oy = FieldMath.resolveFinite(inputValues.get(INPUT_OFFSET_Y_ID), 0.0d);
        double oz = FieldMath.resolveFinite(inputValues.get(INPUT_OFFSET_Z_ID), 0.0d);
        double amplitude = FieldMath.resolveFinite(inputValues.get(INPUT_AMPLITUDE_ID), defaultAmplitude);

        ScalarFieldData field = point -> {
            double nx = (point.x + ox) * scale;
            double ny = (point.y + oy) * scale;
            double nz = (point.z + oz) * scale;
            double noise = RandomOps.valueNoise3(nx, ny, nz, seed);
            if (!Double.isFinite(noise) || !Double.isFinite(amplitude)) {
                return Double.NaN;
            }
            return noise * amplitude;
        };

        outputValues.put(OUTPUT_FIELD_ID, field);
    }
}
