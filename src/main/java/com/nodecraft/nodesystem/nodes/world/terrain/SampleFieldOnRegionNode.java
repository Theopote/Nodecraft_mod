package com.nodecraft.nodesystem.nodes.world.terrain;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.ScalarFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "world.terrain.sample_field_on_region",
    displayName = "Sample Field On Region",
    description = "Samples a scalar field on a regular X/Z lattice inside a region.",
    category = "world.terrain",
    order = 2
)
public class SampleFieldOnRegionNode extends BaseNode {

    private static final String INPUT_REGION_ID = "input_region";
    private static final String INPUT_FIELD_ID = "input_field";
    private static final String INPUT_STEP_ID = "input_step";

    private static final String OUTPUT_POINTS_ID = "output_points";
    private static final String OUTPUT_VALUES_ID = "output_values";
    private static final String OUTPUT_COUNT_ID = "output_count";

    private static final int DEFAULT_MIN_X = -2048;
    private static final int DEFAULT_MAX_X = 2048;
    private static final int DEFAULT_MIN_Z = -2048;
    private static final int DEFAULT_MAX_Z = 2048;
    private static final int DEFAULT_BASE_Y = 64;

    @NodeProperty(displayName = "Step", category = "Sampling", order = 1)
    private int step = 1;

    public SampleFieldOnRegionNode() {
        super(UUID.randomUUID(), "world.terrain.sample_field_on_region");

        addInputPort(new BasePort(INPUT_REGION_ID, "Region", "Optional sampling bounds; defaults to a broad range when omitted", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_FIELD_ID, "Field", "Scalar field to sample", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_STEP_ID, "Step", "Grid step in blocks", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_POINTS_ID, "Points", "Sampled lattice points", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALUES_ID, "Values", "Scalar values aligned with sampled points", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of sampled points", NodeDataType.INTEGER, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object regionObj = inputValues.get(INPUT_REGION_ID);
        Object fieldObj = inputValues.get(INPUT_FIELD_ID);
        int resolvedStep = Math.max(1, getInputInt(INPUT_STEP_ID, step));

        if (!(fieldObj instanceof ScalarFieldData field)) {
            outputValues.put(OUTPUT_POINTS_ID, new BlockPosList());
            outputValues.put(OUTPUT_VALUES_ID, List.of());
            outputValues.put(OUTPUT_COUNT_ID, 0);
            return;
        }

        RegionBounds bounds = resolveBounds(regionObj instanceof RegionData value ? value : null);

        int baseY = bounds.baseY;
        BlockPosList points = new BlockPosList();
        List<Double> values = new ArrayList<>();

        Vector3d samplePoint = new Vector3d();
        for (int x = bounds.minX; x <= bounds.maxX; x += resolvedStep) {
            for (int z = bounds.minZ; z <= bounds.maxZ; z += resolvedStep) {
                samplePoint.set(x, baseY, z);
                double sampledValue = sanitizeFinite(field.sampleScalar(samplePoint), 0.0d);
                points.add(new BlockPos(x, baseY, z));
                values.add(sampledValue);
            }
        }

        outputValues.put(OUTPUT_POINTS_ID, points);
        outputValues.put(OUTPUT_VALUES_ID, List.copyOf(values));
        outputValues.put(OUTPUT_COUNT_ID, values.size());
    }

    private int getInputInt(String portId, int fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private double sanitizeFinite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private RegionBounds resolveBounds(@Nullable RegionData region) {
        if (region == null || !region.isComplete()) {
            return new RegionBounds(DEFAULT_MIN_X, DEFAULT_MAX_X, DEFAULT_MIN_Z, DEFAULT_MAX_Z, DEFAULT_BASE_Y);
        }

        BlockPos min = region.getMinCorner();
        BlockPos max = region.getMaxCorner();
        if (min == null || max == null) {
            return new RegionBounds(DEFAULT_MIN_X, DEFAULT_MAX_X, DEFAULT_MIN_Z, DEFAULT_MAX_Z, DEFAULT_BASE_Y);
        }

        return new RegionBounds(min.getX(), max.getX(), min.getZ(), max.getZ(), min.getY());
    }

    private record RegionBounds(int minX, int maxX, int minZ, int maxZ, int baseY) {
    }
}
