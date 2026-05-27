package com.nodecraft.nodesystem.nodes.geometry.boolops;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.NoiseDisplacedSdfData;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "geometry.boolean.sdf_noise_displace",
    displayName = "SDF Noise Displace",
    description = "Applies deterministic pseudo-noise displacement to an input SDF",
    category = "geometry.boolean",
    order = 20
)
public class SdfNoiseDisplaceNode extends BaseNode {

    @NodeProperty(displayName = "Amplitude", category = "SDF", order = 1)
    private double amplitude = 1.0d;

    @NodeProperty(displayName = "Frequency", category = "SDF", order = 2)
    private double frequency = 0.25d;

    @NodeProperty(displayName = "Seed", category = "SDF", order = 3)
    private int seed = 0;

    @NodeProperty(displayName = "Offset", category = "SDF", order = 4)
    private Vector3d offset = new Vector3d(0.0d, 0.0d, 0.0d);

    private static final String INPUT_SDF_ID = "input_sdf";
    private static final String INPUT_AMPLITUDE_ID = "input_amplitude";
    private static final String INPUT_FREQUENCY_ID = "input_frequency";
    private static final String INPUT_SEED_ID = "input_seed";

    private static final String OUTPUT_SDF_ID = "output_sdf";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SdfNoiseDisplaceNode() {
        super(UUID.randomUUID(), "geometry.boolean.sdf_noise_displace");

        addInputPort(new BasePort(INPUT_SDF_ID, "SDF", "Source signed distance field", NodeDataType.SDF, this));
        addInputPort(new BasePort(INPUT_AMPLITUDE_ID, "Amplitude", "Optional amplitude override", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_FREQUENCY_ID, "Frequency", "Optional frequency override", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Optional seed override", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_SDF_ID, "SDF", "Noise-displaced SDF", NodeDataType.SDF, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when displacement was applied", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Applies deterministic pseudo-noise displacement to an input SDF";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object sdfObj = inputValues.get(INPUT_SDF_ID);
        if (!(sdfObj instanceof SignedDistanceFieldData sdf)) {
            outputValues.put(OUTPUT_SDF_ID, null);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        double resolvedAmplitude = resolveDouble(inputValues.get(INPUT_AMPLITUDE_ID), amplitude);
        double resolvedFrequency = resolveDouble(inputValues.get(INPUT_FREQUENCY_ID), frequency);
        int resolvedSeed = resolveInt(inputValues.get(INPUT_SEED_ID), seed);

        SignedDistanceFieldData displaced = new NoiseDisplacedSdfData(
            sdf,
            resolvedAmplitude,
            resolvedFrequency,
            resolvedSeed,
            offset
        );
        outputValues.put(OUTPUT_SDF_ID, displaced);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("amplitude", amplitude);
        state.put("frequency", frequency);
        state.put("seed", seed);
        state.put("offsetX", offset.x);
        state.put("offsetY", offset.y);
        state.put("offsetZ", offset.z);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("amplitude") instanceof Number value) amplitude = value.doubleValue();
        if (map.get("frequency") instanceof Number value) frequency = value.doubleValue();
        if (map.get("seed") instanceof Number value) seed = value.intValue();
        if (map.get("offsetX") instanceof Number value) offset.x = value.doubleValue();
        if (map.get("offsetY") instanceof Number value) offset.y = value.doubleValue();
        if (map.get("offsetZ") instanceof Number value) offset.z = value.doubleValue();
    }

    private double resolveDouble(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private int resolveInt(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }
}
