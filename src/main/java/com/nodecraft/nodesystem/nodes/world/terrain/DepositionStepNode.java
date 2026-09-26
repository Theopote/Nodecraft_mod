package com.nodecraft.nodesystem.nodes.world.terrain;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.GridScalarFieldData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.ScalarFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "world.terrain.deposition_step",
    displayName = "Deposition Step",
    description = "Deposits sediment in low-slope and low-energy zones. "
        + "Unconnected Region defaults to local 64×64 domain. Non-finite materialize → Valid=false. Height output clamped to [-1,1].",
    category = "world.terrain",
    order = 11
)
public class DepositionStepNode extends BaseNode {

    private static final String INPUT_REGION_ID = "input_region";
    private static final String INPUT_HEIGHT_FIELD_ID = "input_height_field";
    private static final String INPUT_SEDIMENT_FIELD_ID = "input_sediment_field";
    private static final String INPUT_SLOPE_FIELD_ID = "input_slope_field";
    private static final String INPUT_ACCUMULATION_FIELD_ID = "input_accumulation_field";
    private static final String INPUT_CAPACITY_ID = "input_capacity";
    private static final String INPUT_RATE_ID = "input_rate";

    private static final String OUTPUT_HEIGHT_FIELD_ID = "output_height_field";
    private static final String OUTPUT_SEDIMENT_FIELD_ID = "output_sediment_field";
    private static final String OUTPUT_DELTA_FIELD_ID = "output_delta_field";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    @NodeProperty(displayName = "Rate", category = "Deposition", order = 1)
    private double rate = 0.1d;

    @NodeProperty(displayName = "Capacity", category = "Deposition", order = 2)
    private double capacity = 1.0d;

    public DepositionStepNode() {
        super(UUID.randomUUID(), "world.terrain.deposition_step");

        addInputPort(new BasePort(INPUT_REGION_ID, "Region", "Optional raster bounds; unconnected → local 64×64 domain", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_HEIGHT_FIELD_ID, "Height Field", "Current terrain height field", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_SEDIMENT_FIELD_ID, "Sediment Field", "Transported sediment load", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_SLOPE_FIELD_ID, "Slope Field", "Slope magnitude field", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_ACCUMULATION_FIELD_ID, "Accumulation Field", "Optional flow accumulation field used in carrying capacity", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_CAPACITY_ID, "Capacity", "Sediment carrying capacity scaling", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RATE_ID, "Rate", "Single-step deposition strength", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_HEIGHT_FIELD_ID, "Height Field", "Height field after deposition (normalized [-1,1])", NodeDataType.SCALAR_FIELD, this));
        addOutputPort(new BasePort(OUTPUT_SEDIMENT_FIELD_ID, "Sediment Field", "Sediment field after deposition update", NodeDataType.SCALAR_FIELD, this));
        addOutputPort(new BasePort(OUTPUT_DELTA_FIELD_ID, "Delta Field", "Signed height delta after deposition (new - old)", NodeDataType.SCALAR_FIELD, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether deposition succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when deposition failed", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object heightObj = inputValues.get(INPUT_HEIGHT_FIELD_ID);
        Object sedimentObj = inputValues.get(INPUT_SEDIMENT_FIELD_ID);
        Object slopeObj = inputValues.get(INPUT_SLOPE_FIELD_ID);
        ScalarFieldData accumulationField = inputValues.get(INPUT_ACCUMULATION_FIELD_ID) instanceof ScalarFieldData value
            ? value
            : point -> 1.0d;

        if (!(heightObj instanceof ScalarFieldData heightField)
            || !(sedimentObj instanceof ScalarFieldData sedimentField)
            || !(slopeObj instanceof ScalarFieldData slopeField)) {
            publishInvalid("Missing height, sediment, or slope field input.");
            return;
        }

        RegionData region = TerrainNodeUtils.resolveOptionalRegion(this, INPUT_REGION_ID);
        if (TerrainNodeUtils.isInvalidRegionMarker(region)) {
            publishInvalid("Connected Region is incomplete or invalid.");
            return;
        }

        Double resolvedRateRaw = TerrainNodeUtils.resolveOptionalFiniteDouble(this, INPUT_RATE_ID, rate);
        Double resolvedCapacityRaw = TerrainNodeUtils.resolveOptionalFiniteDouble(this, INPUT_CAPACITY_ID, capacity);
        if (resolvedRateRaw == null || resolvedCapacityRaw == null) {
            publishInvalid("Rate and Capacity must be finite DOUBLEs.");
            return;
        }

        TerrainGridDomain domain = ScalarFieldGrids.resolveDomain(region, heightField);
        GridScalarFieldData heightGrid = ScalarFieldGrids.materialize(heightField, domain);
        GridScalarFieldData sedimentGrid = ScalarFieldGrids.materialize(sedimentField, domain);
        GridScalarFieldData slopeGrid = ScalarFieldGrids.materialize(slopeField, domain);
        GridScalarFieldData accumulationGrid = ScalarFieldGrids.materialize(accumulationField, domain);
        if (heightGrid == null || sedimentGrid == null || slopeGrid == null || accumulationGrid == null) {
            publishInvalid("Field materialization failed (non-finite sample or grid over cap).");
            return;
        }

        double resolvedRate = clamp01(resolvedRateRaw);
        double resolvedCapacity = Math.max(0.0d, resolvedCapacityRaw);

        int cellCount = heightGrid.cellCount();
        double[] heightValues = new double[cellCount];
        double[] sedimentValues = new double[cellCount];
        double[] deltaValues = new double[cellCount];
        int index = 0;
        for (int z = domain.minBlockZ(); z <= domain.maxBlockZ(); z++) {
            for (int x = domain.minBlockX(); x <= domain.maxBlockX(); x++) {
                double baseHeight = heightGrid.getAt(x, z);
                double sediment = Math.max(0.0d, sedimentGrid.getAt(x, z));
                double slope = Math.max(0.0d, slopeGrid.getAt(x, z));
                double flow = Math.max(0.0d, accumulationGrid.getAt(x, z));
                double carryingCapacity = flow * slope * resolvedCapacity;

                double deposition = Math.max(0.0d, sediment - carryingCapacity) * resolvedRate;
                double updatedSediment = Math.max(0.0d, sediment - deposition);
                double depositedHeight = baseHeight + deposition;

                if (!Double.isFinite(depositedHeight) || !Double.isFinite(updatedSediment)) {
                    publishInvalid("Deposition produced a non-finite result.");
                    return;
                }

                double clampedHeight = TerrainNodeUtils.clampNormalizedHeight(depositedHeight);
                if (!Double.isFinite(clampedHeight)) {
                    publishInvalid("Deposition produced a non-finite height.");
                    return;
                }

                heightValues[index] = clampedHeight;
                sedimentValues[index] = updatedSediment;
                deltaValues[index] = clampedHeight - baseHeight;
                index++;
            }
        }

        outputValues.put(OUTPUT_HEIGHT_FIELD_ID, ScalarFieldGrids.buildGrid(domain, heightValues));
        outputValues.put(OUTPUT_SEDIMENT_FIELD_ID, ScalarFieldGrids.buildGrid(domain, sedimentValues));
        outputValues.put(OUTPUT_DELTA_FIELD_ID, ScalarFieldGrids.buildGrid(domain, deltaValues));
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void publishInvalid(String error) {
        outputValues.put(OUTPUT_HEIGHT_FIELD_ID, null);
        outputValues.put(OUTPUT_SEDIMENT_FIELD_ID, null);
        outputValues.put(OUTPUT_DELTA_FIELD_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error);
    }

    private double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }
}
