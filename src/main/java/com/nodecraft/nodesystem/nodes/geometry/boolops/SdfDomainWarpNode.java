package com.nodecraft.nodesystem.nodes.geometry.boolops;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DomainWarpedSdfData;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.boolean.sdf_domain_warp",
    displayName = "SDF Domain Warp",
    description = "Applies coordinate-space noise warping before sampling an input SDF",
    category = "geometry.sdf",
    order = 23
)
public class SdfDomainWarpNode extends AbstractSdfNode {
    @NodeProperty(displayName = "Warp Amplitude", category = "SDF", order = 1)
    private double warpAmplitude = 1.0d;

    @NodeProperty(displayName = "Warp Frequency", category = "SDF", order = 2)
    private double warpFrequency = 0.2d;

    @NodeProperty(displayName = "Seed", category = "SDF", order = 3)
    private int seed = 0;

    @NodeProperty(displayName = "Offset", category = "SDF", order = 4)
    private Vector3d offset = new Vector3d(0.0d, 0.0d, 0.0d);

    private static final String INPUT_SDF_ID = "input_sdf";
    private static final String INPUT_WARP_AMPLITUDE_ID = "input_warp_amplitude";
    private static final String INPUT_WARP_FREQUENCY_ID = "input_warp_frequency";
    private static final String INPUT_SEED_ID = "input_seed";
    private static final String OUTPUT_SDF_ID = "output_sdf";

    public SdfDomainWarpNode() {
        super(UUID.randomUUID(), "geometry.boolean.sdf_domain_warp");
        addInputPort(new BasePort(INPUT_SDF_ID, "SDF", "Source signed distance field", NodeDataType.SDF, this));
        addInputPort(new BasePort(INPUT_WARP_AMPLITUDE_ID, "Warp Amplitude", "Optional warp amplitude override", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_WARP_FREQUENCY_ID, "Warp Frequency", "Optional warp frequency override", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Optional seed override", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SDF_ID, "SDF", "Domain-warped SDF", NodeDataType.SDF, this));
        addValidAndErrorOutputs("True when domain warp was applied");
    }

    @Override
    public String getDescription() {
        return "Applies coordinate-space noise warping before sampling an input SDF";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object sdfObj = inputValues.get(INPUT_SDF_ID);
        if (!(sdfObj instanceof SignedDistanceFieldData sdf)) {
            writeFailure("SDF input is required");
            return;
        }

        Double resolvedAmplitude = resolveNonNegativeDouble(INPUT_WARP_AMPLITUDE_ID, warpAmplitude);
        Double resolvedFrequency = resolvePositiveDouble(INPUT_WARP_FREQUENCY_ID, warpFrequency);
        Integer resolvedSeed = resolveOptionalInteger(INPUT_SEED_ID, seed);
        if (resolvedAmplitude == null) {
            writeFailure("Warp Amplitude must be finite and >= 0");
            return;
        }
        if (resolvedFrequency == null) {
            writeFailure("Warp Frequency must be finite and > 0");
            return;
        }
        if (resolvedSeed == null) {
            writeFailure("Seed must be an exact integer");
            return;
        }
        if (!com.nodecraft.nodesystem.util.VectorUtils.isFinite(offset)) {
            writeFailure("Offset must be finite");
            return;
        }

        SignedDistanceFieldData warped = new DomainWarpedSdfData(
            sdf,
            resolvedAmplitude,
            resolvedFrequency,
            resolvedSeed,
            offset
        );
        outputValues.put(OUTPUT_SDF_ID, warped);
        markSuccess();
    }

    private void writeFailure(String error) {
        putNullOutputs(OUTPUT_SDF_ID);
        markInvalid(error);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("warpAmplitude", warpAmplitude);
        state.put("warpFrequency", warpFrequency);
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
        if (map.get("warpAmplitude") instanceof Number value) warpAmplitude = value.doubleValue();
        if (map.get("warpFrequency") instanceof Number value) warpFrequency = value.doubleValue();
        if (map.get("seed") instanceof Number value) seed = value.intValue();
        applyOffsetComponent(map, "offsetX", 0);
        applyOffsetComponent(map, "offsetY", 1);
        applyOffsetComponent(map, "offsetZ", 2);
    }

    private void applyOffsetComponent(Map<?, ?> map, String key, int axis) {
        if (!(map.get(key) instanceof Number value)) {
            return;
        }
        double component = value.doubleValue();
        if (axis == 0) {
            offset.x = component;
        } else if (axis == 1) {
            offset.y = component;
        } else {
            offset.z = component;
        }
    }
}
