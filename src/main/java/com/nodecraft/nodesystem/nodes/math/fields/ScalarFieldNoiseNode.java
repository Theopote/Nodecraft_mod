package com.nodecraft.nodesystem.nodes.math.fields;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.ScalarFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    id = "math.fields.scalar_noise",
    displayName = "Scalar Field Noise",
    description = "Builds a deterministic pseudo-noise scalar field over world space.",
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

    public ScalarFieldNoiseNode() {
        super(UUID.randomUUID(), "math.fields.scalar_noise");

        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Noise seed", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_SCALE_ID, "Scale", "Noise frequency scale (larger = finer detail)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_OFFSET_X_ID, "Offset X", "Domain offset X", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_OFFSET_Y_ID, "Offset Y", "Domain offset Y", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_OFFSET_Z_ID, "Offset Z", "Domain offset Z", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_AMPLITUDE_ID, "Amplitude", "Output multiplier", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_FIELD_ID, "Field", "Scalar noise field", NodeDataType.SCALAR_FIELD, this));
    }

    @Override
    public String getDescription() {
        return "Builds a deterministic pseudo-noise scalar field over world space.";
    }

    @Override
    public String getDisplayName() {
        return "Scalar Field Noise";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        long seed = getInputLong(INPUT_SEED_ID, 0L);
        double scale = Math.max(1.0e-9d, getInputDouble(INPUT_SCALE_ID, 1.0d));
        double ox = getInputDouble(INPUT_OFFSET_X_ID, 0.0d);
        double oy = getInputDouble(INPUT_OFFSET_Y_ID, 0.0d);
        double oz = getInputDouble(INPUT_OFFSET_Z_ID, 0.0d);
        double amplitude = getInputDouble(INPUT_AMPLITUDE_ID, 1.0d);

        ScalarFieldData field = point -> {
            double nx = (point.x + ox) * scale;
            double ny = (point.y + oy) * scale;
            double nz = (point.z + oz) * scale;
            return sampleHashNoise(nx, ny, nz, seed) * amplitude;
        };

        outputValues.put(OUTPUT_FIELD_ID, field);
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private long getInputLong(String portId, long fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.longValue() : fallback;
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
