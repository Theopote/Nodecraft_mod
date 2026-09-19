package com.nodecraft.nodesystem.nodes.transform.deformations;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "transform.deformations.noise_displace",
    displayName = "Noise Displace Point List",
    description = "Applies deterministic pseudo-noise displacement to a point list",
    category = "transform.deformations",
    order = 3
)
public class NoiseDisplacePointListNode extends BaseNode {

    @NodeProperty(displayName = "Amplitude", category = "Noise", order = 1)
    private double amplitude = 1.0d;

    @NodeProperty(displayName = "Frequency", category = "Noise", order = 2)
    private double frequency = 0.25d;

    @NodeProperty(displayName = "Seed", category = "Noise", order = 3)
    private int seed = 0;

    @NodeProperty(displayName = "Offset", category = "Noise", order = 4)
    private Vector3d offset = new Vector3d(0.0d, 0.0d, 0.0d);

    @NodeProperty(displayName = "Axis Weight X", category = "Noise", order = 5)
    private double axisWeightX = 1.0d;

    @NodeProperty(displayName = "Axis Weight Y", category = "Noise", order = 6)
    private double axisWeightY = 1.0d;

    @NodeProperty(displayName = "Axis Weight Z", category = "Noise", order = 7)
    private double axisWeightZ = 1.0d;

    private static final String INPUT_POINTS_ID = "input_points";
    private static final String INPUT_AMPLITUDE_ID = "input_amplitude";
    private static final String INPUT_FREQUENCY_ID = "input_frequency";
    private static final String INPUT_SEED_ID = "input_seed";
    private static final String INPUT_OFFSET_ID = "input_offset";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public NoiseDisplacePointListNode() {
        super(UUID.randomUUID(), "transform.deformations.noise_displace");
        addInputPort(new BasePort(INPUT_POINTS_ID, "Points", "Point list to displace", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_AMPLITUDE_ID, "Amplitude", "Optional displacement amplitude override", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_FREQUENCY_ID, "Frequency", "Optional noise frequency override", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Optional noise seed override", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_OFFSET_ID, "Offset", "Optional noise-space offset override", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Displaced point list", NodeDataType.VECTOR_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of points in the displaced output", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when displacement was applied", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Applies deterministic pseudo-noise displacement to a point list";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object pointsObj = inputValues.get(INPUT_POINTS_ID);
        if (!(pointsObj instanceof List<?> pointsInput)) {
            writeEmptyOutputs();
            return;
        }

        double resolvedAmplitude = resolveDouble(inputValues.get(INPUT_AMPLITUDE_ID), amplitude);
        double resolvedFrequency = resolveDouble(inputValues.get(INPUT_FREQUENCY_ID), frequency);
        int resolvedSeed = resolveInt(inputValues.get(INPUT_SEED_ID), seed);
        Vector3d resolvedOffset = inputValues.get(INPUT_OFFSET_ID) instanceof Vector3d offsetInput
            ? new Vector3d(offsetInput)
            : new Vector3d(offset);
        Vector3d axisWeight = new Vector3d(axisWeightX, axisWeightY, axisWeightZ);
        if (!Double.isFinite(resolvedAmplitude)
            || !Double.isFinite(resolvedFrequency)
            || !DeformationUtils.isFinite(resolvedOffset)
            || !DeformationUtils.isFinite(axisWeight)) {
            writeEmptyOutputs();
            return;
        }

        List<Vector3d> displaced = new ArrayList<>(pointsInput.size());
        for (Object entry : pointsInput) {
            Vector3d point = resolvePoint(entry);
            if (point == null) {
                continue;
            }
            double px = point.x + resolvedOffset.x;
            double py = point.y + resolvedOffset.y;
            double pz = point.z + resolvedOffset.z;
            double nx = noise(px, py, pz, resolvedFrequency, resolvedSeed ^ 0x45d9f3b);
            double ny = noise(px, py, pz, resolvedFrequency, resolvedSeed ^ 0x9e3779b9);
            double nz = noise(px, py, pz, resolvedFrequency, resolvedSeed ^ 0x7f4a7c15);
            displaced.add(new Vector3d(
                point.x + nx * resolvedAmplitude * axisWeightX,
                point.y + ny * resolvedAmplitude * axisWeightY,
                point.z + nz * resolvedAmplitude * axisWeightZ
            ));
        }

        outputValues.put(OUTPUT_POINTS_ID, List.copyOf(displaced));
        outputValues.put(OUTPUT_COUNT_ID, displaced.size());
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
        state.put("axisWeightX", axisWeightX);
        state.put("axisWeightY", axisWeightY);
        state.put("axisWeightZ", axisWeightZ);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        amplitude = DeformationUtils.finiteOrCurrent(map.get("amplitude"), amplitude);
        frequency = DeformationUtils.finiteOrCurrent(map.get("frequency"), frequency);
        if (map.get("seed") instanceof Number value) seed = value.intValue();
        offset.x = DeformationUtils.finiteOrCurrent(map.get("offsetX"), offset.x);
        offset.y = DeformationUtils.finiteOrCurrent(map.get("offsetY"), offset.y);
        offset.z = DeformationUtils.finiteOrCurrent(map.get("offsetZ"), offset.z);
        axisWeightX = DeformationUtils.finiteOrCurrent(map.get("axisWeightX"), axisWeightX);
        axisWeightY = DeformationUtils.finiteOrCurrent(map.get("axisWeightY"), axisWeightY);
        axisWeightZ = DeformationUtils.finiteOrCurrent(map.get("axisWeightZ"), axisWeightZ);
    }

    private void writeEmptyOutputs() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private Vector3d resolvePoint(Object value) {
        Vector3d point = DeformationUtils.resolvePoint(value);
        return DeformationUtils.isFinite(point) ? point : null;
    }

    private double resolveDouble(Object value, double fallback) {
        return DeformationUtils.resolveFiniteDouble(value, fallback);
    }

    private int resolveInt(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private double noise(double x, double y, double z, double freq, int seedValue) {
        double px = x * freq;
        double py = y * freq;
        double pz = z * freq;
        double s = Math.sin(px * 12.9898d + py * 78.233d + pz * 37.719d + seedValue * 0.12345d) * 43758.5453d;
        return (s - Math.floor(s)) * 2.0d - 1.0d;
    }
}
