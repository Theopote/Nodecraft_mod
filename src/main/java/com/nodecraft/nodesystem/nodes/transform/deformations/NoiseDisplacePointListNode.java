package com.nodecraft.nodesystem.nodes.transform.deformations;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.PointUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import com.nodecraft.nodesystem.util.VectorUtils;
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
    order = 4
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
        addInputPort(new BasePort(INPUT_POINTS_ID, "Points", "Point list to displace", NodeDataType.POINT_LIST, this));
        addInputPort(new BasePort(INPUT_AMPLITUDE_ID, "Amplitude", "Optional displacement amplitude override", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_FREQUENCY_ID, "Frequency", "Optional noise frequency override", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SEED_ID, "Seed", "Optional noise seed override", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_OFFSET_ID, "Offset", "Optional noise-space offset override", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Displaced point list", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of points in the displaced output", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when displacement was applied", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Applies deterministic pseudo-noise displacement to a point list";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Vector3d> pointsInput = PointUtils.resolveStrictPointList(inputValues.get(INPUT_POINTS_ID));
        Double resolvedAmplitude = OptionalPortDrive.resolveOptionalDouble(this, INPUT_AMPLITUDE_ID, amplitude);
        Double resolvedFrequency = OptionalPortDrive.resolveOptionalDouble(this, INPUT_FREQUENCY_ID, frequency);
        Integer resolvedSeed = OptionalPortDrive.resolveOptionalInteger(this, INPUT_SEED_ID, seed);
        Vector3d resolvedOffset = OptionalPortDrive.resolveOptionalVector(this, INPUT_OFFSET_ID, offset);

        if (pointsInput == null
                || resolvedAmplitude == null
                || resolvedFrequency == null
                || resolvedFrequency < 0.0d
                || resolvedSeed == null
                || resolvedOffset == null
                || !VectorUtils.isFinite(new Vector3d(axisWeightX, axisWeightY, axisWeightZ))) {
            writeInvalid();
            return;
        }

        List<Vector3d> displaced = new ArrayList<>(pointsInput.size());
        for (Vector3d point : pointsInput) {
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

        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(displaced));
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
        if (map.get("amplitude") instanceof Number value && Double.isFinite(value.doubleValue())) {
            amplitude = value.doubleValue();
        }
        if (map.get("frequency") instanceof Number value && Double.isFinite(value.doubleValue()) && value.doubleValue() >= 0.0d) {
            frequency = value.doubleValue();
        }
        if (map.get("seed") instanceof Integer value) {
            seed = value;
        }
        if (map.get("offsetX") instanceof Number value && Double.isFinite(value.doubleValue())) {
            offset.x = value.doubleValue();
        }
        if (map.get("offsetY") instanceof Number value && Double.isFinite(value.doubleValue())) {
            offset.y = value.doubleValue();
        }
        if (map.get("offsetZ") instanceof Number value && Double.isFinite(value.doubleValue())) {
            offset.z = value.doubleValue();
        }
        if (map.get("axisWeightX") instanceof Number value && Double.isFinite(value.doubleValue())) {
            axisWeightX = value.doubleValue();
        }
        if (map.get("axisWeightY") instanceof Number value && Double.isFinite(value.doubleValue())) {
            axisWeightY = value.doubleValue();
        }
        if (map.get("axisWeightZ") instanceof Number value && Double.isFinite(value.doubleValue())) {
            axisWeightZ = value.doubleValue();
        }
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_POINTS_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private double noise(double x, double y, double z, double freq, int seedValue) {
        double px = x * freq;
        double py = y * freq;
        double pz = z * freq;
        double s = Math.sin(px * 12.9898d + py * 78.233d + pz * 37.719d + seedValue * 0.12345d) * 43758.5453d;
        return (s - Math.floor(s)) * 2.0d - 1.0d;
    }
}
