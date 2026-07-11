package com.nodecraft.nodesystem.nodes.world.terrain;

import com.nodecraft.nodesystem.api.NodeDataType;
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
    id = "world.terrain.thermal_erosion_step",
    displayName = "Thermal Erosion Step",
    description = "Applies one thermal weathering step based on local slope exceeding talus angle.",
    category = "world.terrain",
    order = 10
)
public class ThermalErosionStepNode extends BaseNode {

    private static final String INPUT_REGION_ID = "input_region";
    private static final String INPUT_HEIGHT_FIELD_ID = "input_height_field";
    private static final String INPUT_TALUS_ID = "input_talus";
    private static final String INPUT_RATE_ID = "input_rate";

    private static final String OUTPUT_HEIGHT_FIELD_ID = "output_height_field";
    private static final String OUTPUT_DELTA_FIELD_ID = "output_delta_field";

    @NodeProperty(displayName = "Talus", category = "Thermal", order = 1)
    private double talus = 0.7d;

    @NodeProperty(displayName = "Rate", category = "Thermal", order = 2)
    private double rate = 0.12d;

    public ThermalErosionStepNode() {
        super(UUID.randomUUID(), "world.terrain.thermal_erosion_step");

        addInputPort(new BasePort(INPUT_REGION_ID, "Region", "Optional raster bounds; defaults to a safe 64x64 area when omitted", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_HEIGHT_FIELD_ID, "Height Field", "Input elevation field", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_TALUS_ID, "Talus", "Slope threshold before material starts to creep", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RATE_ID, "Rate", "Single-step thermal smoothing strength", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_HEIGHT_FIELD_ID, "Height Field", "Thermally eroded height field", NodeDataType.SCALAR_FIELD, this));
        addOutputPort(new BasePort(OUTPUT_DELTA_FIELD_ID, "Delta Field", "Signed height delta after thermal erosion (new - old)", NodeDataType.SCALAR_FIELD, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object heightObj = inputValues.get(INPUT_HEIGHT_FIELD_ID);
        if (!(heightObj instanceof ScalarFieldData heightField)) {
            outputValues.put(OUTPUT_HEIGHT_FIELD_ID, null);
            outputValues.put(OUTPUT_DELTA_FIELD_ID, null);
            return;
        }

        RegionData region = inputValues.get(INPUT_REGION_ID) instanceof RegionData value ? value : null;
        ScalarFieldGrids.FieldGridBounds bounds = ScalarFieldGrids.resolveBounds(region, heightField);
        GridScalarFieldData inputGrid = ScalarFieldGrids.materialize(heightField, bounds);
        if (inputGrid == null) {
            outputValues.put(OUTPUT_HEIGHT_FIELD_ID, null);
            outputValues.put(OUTPUT_DELTA_FIELD_ID, null);
            return;
        }

        double resolvedTalus = Math.max(0.0d, getInputDouble(INPUT_TALUS_ID, talus));
        double resolvedRate = clamp01(getInputDouble(INPUT_RATE_ID, rate));
        int step = 1;

        int cellCount = inputGrid.cellCount();
        double[] erodedValues = new double[cellCount];
        double[] deltaValues = new double[cellCount];
        int index = 0;
        for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
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

                erodedValues[index] = sanitizeFinite(eroded, center);
                deltaValues[index] = erodedValues[index] - center;
                index++;
            }
        }

        outputValues.put(OUTPUT_HEIGHT_FIELD_ID, ScalarFieldGrids.buildGrid(bounds, erodedValues));
        outputValues.put(OUTPUT_DELTA_FIELD_ID, ScalarFieldGrids.buildGrid(bounds, deltaValues));
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private double sanitizeFinite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }
}
