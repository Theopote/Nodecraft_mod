package com.nodecraft.nodesystem.nodes.world.terrain;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.ScalarFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "world.terrain.sample_field_on_region",
    displayName = "Sample Field On Region",
    description = "Samples a scalar field on a regular X/Z lattice of block cell centers inside a region.",
    category = "world.terrain",
    order = 18
)
public class SampleFieldOnRegionNode extends BaseNode {

    private static final String INPUT_REGION_ID = "input_region";
    private static final String INPUT_FIELD_ID = "input_field";
    private static final String INPUT_STEP_ID = "input_step";
    private static final String INPUT_MAX_SAMPLES_ID = "input_max_samples";

    private static final String OUTPUT_SAMPLE_POINTS_ID = "output_sample_points";
    private static final String OUTPUT_SAMPLE_VALUES_ID = "output_sample_values";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_STEP_USED_ID = "output_step_used";
    private static final String OUTPUT_HIT_LIMIT_ID = "output_hit_limit";
    private static final String OUTPUT_COMPLETE_ID = "output_complete";
    private static final String OUTPUT_STOPPED_REASON_ID = "output_stopped_reason";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    @NodeProperty(displayName = "Step", category = "Sampling", order = 1)
    private int step = 1;

    @NodeProperty(displayName = "Max Samples", category = "Safety", order = 2)
    private int maxSamples = TerrainNodeUtils.DEFAULT_MAX_SAMPLES;

    public SampleFieldOnRegionNode() {
        super(UUID.randomUUID(), "world.terrain.sample_field_on_region");

        addInputPort(new BasePort(INPUT_REGION_ID, "Region",
            "Optional sampling bounds; defaults to a safe 64x64 area (−32..31, sample Y=64) when omitted",
            NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_FIELD_ID, "Field",
            "Scalar field to sample", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_STEP_ID, "Step",
            "Grid step in blocks", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_MAX_SAMPLES_ID, "Max Samples",
            "Maximum samples to emit before stopping (hard-capped)", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_SAMPLE_POINTS_ID, "Sample Points",
            "Sampled lattice cell-center points", NodeDataType.POINT_LIST, this));
        addOutputPort(new BasePort(OUTPUT_SAMPLE_VALUES_ID, "Values",
            "Scalar values aligned with sampled points", NodeDataType.DOUBLE_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count",
            "Number of sampled points", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_STEP_USED_ID, "Step Used",
            "Actual sampling step", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_HIT_LIMIT_ID, "Hit Limit",
            "True when Max Samples stopped the scan", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_COMPLETE_ID, "Complete",
            "False when Hit Limit stopped the scan early", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_STOPPED_REASON_ID, "Stopped Reason",
            "Reason sampling stopped early", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "Whether sampling succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error",
            "Error message when sampling failed", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Integer resolvedStep = TerrainNodeUtils.resolveOptionalExactInteger(this, INPUT_STEP_ID, step);
        if (resolvedStep == null || resolvedStep < 1) {
            writeInvalid("Step must be an exact INTEGER >= 1.", 1);
            return;
        }

        Integer resolvedMaxSamples = TerrainNodeUtils.resolveUserBudgetExactInteger(
            this, INPUT_MAX_SAMPLES_ID, maxSamples, GenerationLimits.MAX_TERRAIN_SAMPLES);
        if (resolvedMaxSamples == null) {
            writeInvalid("Max Samples must be an exact INTEGER between 1 and "
                + GenerationLimits.MAX_TERRAIN_SAMPLES + ".", resolvedStep);
            return;
        }

        if (!(inputValues.get(INPUT_FIELD_ID) instanceof ScalarFieldData field)) {
            writeInvalid("Missing scalar field input.", resolvedStep);
            return;
        }

        RegionData region = TerrainNodeUtils.resolveOptionalRegion(this, INPUT_REGION_ID);
        if (TerrainNodeUtils.isInvalidRegionMarker(region)) {
            writeInvalid("Region is connected but incomplete or invalid.", resolvedStep);
            return;
        }
        TerrainGridDomain domain = TerrainNodeUtils.localDomainFromRegion(region);

        List<Vector3d> points = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        Vector3d samplePoint = new Vector3d();
        boolean hitLimit = false;
        String stoppedReason = "";

        int x = domain.minBlockX();
        while (true) {
            int z = domain.minBlockZ();
            while (true) {
                if (values.size() >= resolvedMaxSamples) {
                    hitLimit = true;
                    stoppedReason = "max_samples";
                    break;
                }
                domain.cellCenterSamplePoint(x, z, samplePoint);
                double sampledValue = field.sampleScalar(samplePoint);
                if (!Double.isFinite(sampledValue)) {
                    writeInvalid("Field returned a non-finite sample.", resolvedStep);
                    return;
                }
                points.add(new Vector3d(samplePoint));
                values.add(sampledValue);

                Integer nextZ = TerrainGridDomain.safeNextAxis(z, resolvedStep, domain.maxBlockZ());
                if (nextZ == null) {
                    break;
                }
                z = nextZ;
            }
            if (hitLimit) {
                break;
            }
            Integer nextX = TerrainGridDomain.safeNextAxis(x, resolvedStep, domain.maxBlockX());
            if (nextX == null) {
                break;
            }
            x = nextX;
        }

        List<PointData> samplePoints = SpatialValueResolver.toPointDataList(points);
        outputValues.put(OUTPUT_SAMPLE_POINTS_ID, samplePoints);
        outputValues.put(OUTPUT_SAMPLE_VALUES_ID, List.copyOf(values));
        outputValues.put(OUTPUT_COUNT_ID, values.size());
        outputValues.put(OUTPUT_STEP_USED_ID, resolvedStep);
        outputValues.put(OUTPUT_HIT_LIMIT_ID, hitLimit);
        outputValues.put(OUTPUT_COMPLETE_ID, !hitLimit);
        outputValues.put(OUTPUT_STOPPED_REASON_ID, stoppedReason);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error, int stepUsed) {
        outputValues.put(OUTPUT_SAMPLE_POINTS_ID, List.of());
        outputValues.put(OUTPUT_SAMPLE_VALUES_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_STEP_USED_ID, Math.max(1, stepUsed));
        outputValues.put(OUTPUT_HIT_LIMIT_ID, false);
        outputValues.put(OUTPUT_COMPLETE_ID, false);
        outputValues.put(OUTPUT_STOPPED_REASON_ID, "");
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error);
    }
}
