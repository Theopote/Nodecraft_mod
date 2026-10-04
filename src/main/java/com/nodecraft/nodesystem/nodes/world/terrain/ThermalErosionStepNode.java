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
    id = "world.terrain.thermal_erosion_step",
    displayName = "Thermal Erosion Step",
    description = "Applies one thermal weathering step based on local slope exceeding talus angle. "
        + "Unconnected Region defaults to local 64×64 domain. Non-finite materialize → Valid=false. Height output clamped to [-1,1].",
    category = "world.terrain",
    order = 9
)
public class ThermalErosionStepNode extends BaseNode {

    private static final String INPUT_REGION_ID = "input_region";
    private static final String INPUT_HEIGHT_FIELD_ID = "input_height_field";
    private static final String INPUT_TALUS_ID = "input_talus";
    private static final String INPUT_RATE_ID = "input_rate";

    private static final String OUTPUT_HEIGHT_FIELD_ID = "output_height_field";
    private static final String OUTPUT_DELTA_FIELD_ID = "output_delta_field";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    @NodeProperty(displayName = "Talus", category = "Thermal", order = 1)
    private double talus = 0.7d;

    @NodeProperty(displayName = "Rate", category = "Thermal", order = 2)
    private double rate = 0.12d;

    public ThermalErosionStepNode() {
        super(UUID.randomUUID(), "world.terrain.thermal_erosion_step");

        addInputPort(new BasePort(INPUT_REGION_ID, "Region", "Optional raster bounds; unconnected → local 64×64 domain", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_HEIGHT_FIELD_ID, "Height Field", "Input elevation field", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_TALUS_ID, "Talus", "Slope threshold before material starts to creep", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RATE_ID, "Rate", "Single-step thermal smoothing strength", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_HEIGHT_FIELD_ID, "Height Field", "Thermally eroded height field (normalized [-1,1])", NodeDataType.SCALAR_FIELD, this));
        addOutputPort(new BasePort(OUTPUT_DELTA_FIELD_ID, "Delta Field", "Signed height delta after thermal erosion (new - old)", NodeDataType.SCALAR_FIELD, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether thermal erosion succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when thermal erosion failed", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object heightObj = inputValues.get(INPUT_HEIGHT_FIELD_ID);
        if (!(heightObj instanceof ScalarFieldData heightField)) {
            publishInvalid("Missing height field input.");
            return;
        }

        RegionData region = TerrainNodeUtils.resolveOptionalRegion(this, INPUT_REGION_ID);
        if (TerrainNodeUtils.isInvalidRegionMarker(region)) {
            publishInvalid("Connected Region is incomplete or invalid.");
            return;
        }

        Double resolvedTalusRaw = TerrainNodeUtils.resolveOptionalFiniteDouble(this, INPUT_TALUS_ID, talus);
        if (resolvedTalusRaw == null) {
            publishInvalid("Talus must be a finite DOUBLE.");
            return;
        }

        Double resolvedRateRaw = TerrainNodeUtils.resolveOptionalFiniteDouble(this, INPUT_RATE_ID, rate);
        if (resolvedRateRaw == null) {
            publishInvalid("Rate must be a finite DOUBLE.");
            return;
        }

        TerrainGridDomain domain = ScalarFieldGrids.resolveDomain(this, INPUT_REGION_ID, heightField);
        GridScalarFieldData inputGrid = ScalarFieldGrids.materialize(heightField, domain);
        if (inputGrid == null) {
            publishInvalid("Height field materialization failed (non-finite sample or grid over cap).");
            return;
        }

        double resolvedTalus = Math.max(0.0d, resolvedTalusRaw);
        double resolvedRate = clamp01(resolvedRateRaw);
        int step = 1;

        int cellCount = inputGrid.cellCount();
        double[] erodedValues = new double[cellCount];
        double[] deltaValues = new double[cellCount];
        int index = 0;
        for (int z = domain.minBlockZ(); z <= domain.maxBlockZ(); z++) {
            for (int x = domain.minBlockX(); x <= domain.maxBlockX(); x++) {
                double center = inputGrid.getAt(x, z);
                double hxNeg = inputGrid.getAtClamped(x - step, z);
                double hxPos = inputGrid.getAtClamped(x + step, z);
                double hzNeg = inputGrid.getAtClamped(x, z - step);
                double hzPos = inputGrid.getAtClamped(x, z + step);

                double neighborhoodMean = (hxNeg + hxPos + hzNeg + hzPos) * 0.25d;
                double deviation = center - neighborhoodMean;
                double excess = Math.max(0.0d, Math.abs(deviation) - resolvedTalus);
                double eroded = center;
                if (excess > 0.0d) {
                    double direction = Math.signum(deviation);
                    eroded = center - direction * excess * resolvedRate;
                }

                if (!Double.isFinite(eroded)) {
                    publishInvalid("Thermal erosion produced a non-finite height.");
                    return;
                }

                double clampedHeight = TerrainNodeUtils.clampNormalizedHeight(eroded);
                if (!Double.isFinite(clampedHeight)) {
                    publishInvalid("Thermal erosion produced a non-finite height.");
                    return;
                }

                erodedValues[index] = clampedHeight;
                deltaValues[index] = clampedHeight - center;
                index++;
            }
        }

        outputValues.put(OUTPUT_HEIGHT_FIELD_ID, ScalarFieldGrids.buildGrid(domain, erodedValues));
        outputValues.put(OUTPUT_DELTA_FIELD_ID, ScalarFieldGrids.buildGrid(domain, deltaValues));
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void publishInvalid(String error) {
        outputValues.put(OUTPUT_HEIGHT_FIELD_ID, null);
        outputValues.put(OUTPUT_DELTA_FIELD_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error);
    }

    private double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }
}
