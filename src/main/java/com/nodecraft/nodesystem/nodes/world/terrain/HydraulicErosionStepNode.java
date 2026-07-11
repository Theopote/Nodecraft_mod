package com.nodecraft.nodesystem.nodes.world.terrain;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.GridScalarFieldData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.ScalarFieldData;
import com.nodecraft.nodesystem.datatypes.VectorFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "world.terrain.hydraulic_erosion_step",
    displayName = "Hydraulic Erosion Step",
    description = "Applies one hydraulic erosion-deposition step with carrying capacity and optional flow-driven sediment transport.",
    category = "world.terrain",
    order = 11
)
public class HydraulicErosionStepNode extends BaseNode {

    private static final String INPUT_REGION_ID = "input_region";
    private static final String INPUT_HEIGHT_FIELD_ID = "input_height_field";
    private static final String INPUT_ACCUMULATION_FIELD_ID = "input_accumulation_field";
    private static final String INPUT_SLOPE_FIELD_ID = "input_slope_field";
    private static final String INPUT_FLOW_FIELD_ID = "input_flow_field";
    private static final String INPUT_SEDIMENT_FIELD_ID = "input_sediment_field";
    private static final String INPUT_EROSION_RATE_ID = "input_erosion_rate";
    private static final String INPUT_DEPOSITION_RATE_ID = "input_deposition_rate";
    private static final String INPUT_CAPACITY_ID = "input_capacity";
    private static final String INPUT_TRANSPORT_EFFICIENCY_ID = "input_transport_efficiency";

    private static final String OUTPUT_ERODED_FIELD_ID = "output_eroded_field";
    private static final String OUTPUT_SEDIMENT_FIELD_ID = "output_sediment_field";
    private static final String OUTPUT_DELTA_FIELD_ID = "output_delta_field";

    private static final Vector3d FLOW_VECTOR = new Vector3d();

    @NodeProperty(displayName = "Erosion Rate", category = "Hydraulic", order = 1)
    private double erosionRate = 0.08d;

    @NodeProperty(displayName = "Capacity", category = "Hydraulic", order = 2)
    private double capacity = 1.0d;

    @NodeProperty(displayName = "Deposition Rate", category = "Hydraulic", order = 3)
    private double depositionRate = 0.06d;

    @NodeProperty(displayName = "Transport Efficiency", category = "Hydraulic", order = 4)
    private double transportEfficiency = 0.35d;

    public HydraulicErosionStepNode() {
        super(UUID.randomUUID(), "world.terrain.hydraulic_erosion_step");

        addInputPort(new BasePort(INPUT_REGION_ID, "Region", "Optional raster bounds; defaults to a safe 64x64 area when omitted", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_HEIGHT_FIELD_ID, "Height Field", "Input elevation field", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_ACCUMULATION_FIELD_ID, "Accumulation Field", "Flow accumulation or runoff energy", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_SLOPE_FIELD_ID, "Slope Field", "Optional local slope field; when absent slope is derived from height field", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_FLOW_FIELD_ID, "Flow Field", "Optional flow direction field used for upstream sediment transport sampling", NodeDataType.VECTOR_FIELD, this));
        addInputPort(new BasePort(INPUT_SEDIMENT_FIELD_ID, "Sediment Field", "Optional incoming suspended sediment field", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_EROSION_RATE_ID, "Erosion Rate", "Single-step hydraulic incision amount", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_DEPOSITION_RATE_ID, "Deposition Rate", "Single-step settling amount when load exceeds carrying capacity", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_CAPACITY_ID, "Capacity", "Sediment carrying capacity scaling", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_TRANSPORT_EFFICIENCY_ID, "Transport Efficiency", "Upstream transport blending factor in [0,1]", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_ERODED_FIELD_ID, "Eroded Field", "Hydraulically eroded height field", NodeDataType.SCALAR_FIELD, this));
        addOutputPort(new BasePort(OUTPUT_SEDIMENT_FIELD_ID, "Sediment Field", "Estimated transported sediment field", NodeDataType.SCALAR_FIELD, this));
        addOutputPort(new BasePort(OUTPUT_DELTA_FIELD_ID, "Delta Field", "Signed height delta after hydraulic step (new - old)", NodeDataType.SCALAR_FIELD, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object heightObj = inputValues.get(INPUT_HEIGHT_FIELD_ID);
        Object accumulationObj = inputValues.get(INPUT_ACCUMULATION_FIELD_ID);
        if (!(heightObj instanceof ScalarFieldData heightField) || !(accumulationObj instanceof ScalarFieldData accumulationField)) {
            outputValues.put(OUTPUT_ERODED_FIELD_ID, null);
            outputValues.put(OUTPUT_SEDIMENT_FIELD_ID, null);
            outputValues.put(OUTPUT_DELTA_FIELD_ID, null);
            return;
        }

        RegionData region = inputValues.get(INPUT_REGION_ID) instanceof RegionData value ? value : null;
        ScalarFieldGrids.FieldGridBounds bounds = ScalarFieldGrids.resolveBounds(region, heightField);
        GridScalarFieldData heightGrid = ScalarFieldGrids.materialize(heightField, bounds);
        GridScalarFieldData accumulationGrid = ScalarFieldGrids.materialize(accumulationField, bounds);
        if (heightGrid == null || accumulationGrid == null) {
            outputValues.put(OUTPUT_ERODED_FIELD_ID, null);
            outputValues.put(OUTPUT_SEDIMENT_FIELD_ID, null);
            outputValues.put(OUTPUT_DELTA_FIELD_ID, null);
            return;
        }

        ScalarFieldData slopeSourceField = inputValues.get(INPUT_SLOPE_FIELD_ID) instanceof ScalarFieldData value ? value : null;
        VectorFieldData flowField = inputValues.get(INPUT_FLOW_FIELD_ID) instanceof VectorFieldData value ? value : null;
        ScalarFieldData incomingSedimentField = inputValues.get(INPUT_SEDIMENT_FIELD_ID) instanceof ScalarFieldData value ? value : null;

        GridScalarFieldData slopeGrid = slopeSourceField != null
            ? ScalarFieldGrids.materialize(slopeSourceField, bounds)
            : null;
        GridScalarFieldData incomingSedimentGrid = incomingSedimentField != null
            ? ScalarFieldGrids.materialize(incomingSedimentField, bounds)
            : null;

        double resolvedErosionRate = clamp01(getInputDouble(INPUT_EROSION_RATE_ID, erosionRate));
        double resolvedDepositionRate = clamp01(getInputDouble(INPUT_DEPOSITION_RATE_ID, depositionRate));
        double resolvedCapacity = Math.max(0.0d, getInputDouble(INPUT_CAPACITY_ID, capacity));
        double resolvedTransportEfficiency = clamp01(getInputDouble(INPUT_TRANSPORT_EFFICIENCY_ID, transportEfficiency));

        int cellCount = heightGrid.cellCount();
        double[] erodedValues = new double[cellCount];
        double[] sedimentValues = new double[cellCount];
        double[] deltaValues = new double[cellCount];
        int index = 0;
        for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                double baseHeight = heightGrid.getAt(x, z);
                double flow = Math.max(0.0d, accumulationGrid.getAt(x, z));
                double slope = slopeGrid != null
                    ? Math.max(0.0d, slopeGrid.getAt(x, z))
                    : Math.max(0.0d, ScalarFieldGrids.sampleSlopeFromGrid(heightGrid, x, z, 1.0d));
                double incomingSediment = resolveIncomingSediment(
                    x,
                    z,
                    bounds.sampleY(),
                    incomingSedimentGrid,
                    flowField,
                    resolvedTransportEfficiency
                );

                HydraulicTerms terms = evaluateTerms(
                    flow,
                    slope,
                    incomingSediment,
                    resolvedCapacity,
                    resolvedErosionRate,
                    resolvedDepositionRate
                );

                double erodedHeight = sanitizeFinite(baseHeight - terms.erodedAmount + terms.depositedAmount, baseHeight);
                erodedValues[index] = erodedHeight;
                sedimentValues[index] = terms.updatedSediment;
                deltaValues[index] = erodedHeight - baseHeight;
                index++;
            }
        }

        outputValues.put(OUTPUT_ERODED_FIELD_ID, ScalarFieldGrids.buildGrid(bounds, erodedValues));
        outputValues.put(OUTPUT_SEDIMENT_FIELD_ID, ScalarFieldGrids.buildGrid(bounds, sedimentValues));
        outputValues.put(OUTPUT_DELTA_FIELD_ID, ScalarFieldGrids.buildGrid(bounds, deltaValues));
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private HydraulicTerms evaluateTerms(double flow,
                                         double slope,
                                         double incomingSediment,
                                         double capacityFactor,
                                         double erosion,
                                         double deposition) {
        double carryingCapacity = flow * slope * capacityFactor;
        double eroded = Math.max(0.0d, carryingCapacity - incomingSediment) * erosion;
        double deposited = Math.max(0.0d, incomingSediment - carryingCapacity) * deposition;
        double updatedSediment = Math.max(0.0d, incomingSediment + eroded - deposited);
        return new HydraulicTerms(eroded, deposited, updatedSediment);
    }

    private double resolveIncomingSediment(int x,
                                           int z,
                                           int sampleY,
                                           @Nullable GridScalarFieldData incomingSedimentGrid,
                                           @Nullable VectorFieldData flowField,
                                           double transportEfficiency) {
        if (incomingSedimentGrid == null) {
            return 0.0d;
        }

        double local = Math.max(0.0d, incomingSedimentGrid.getAt(x, z));
        if (flowField == null || transportEfficiency <= 1.0e-9d) {
            return local;
        }

        FLOW_VECTOR.set(0.0d, 0.0d, 0.0d);
        flowField.sampleVector(new Vector3d(x, sampleY, z), FLOW_VECTOR);
        double len = Math.sqrt(FLOW_VECTOR.x * FLOW_VECTOR.x + FLOW_VECTOR.z * FLOW_VECTOR.z);
        if (len <= 1.0e-9d) {
            return local;
        }

        double ux = FLOW_VECTOR.x / len;
        double uz = FLOW_VECTOR.z / len;
        int upstreamX = (int) Math.round(x - ux);
        int upstreamZ = (int) Math.round(z - uz);
        double upstreamSediment = Math.max(0.0d, incomingSedimentGrid.getAtClamped(upstreamX, upstreamZ));
        return local * (1.0d - transportEfficiency) + upstreamSediment * transportEfficiency;
    }

    private double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private double sanitizeFinite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private record HydraulicTerms(double erodedAmount, double depositedAmount, double updatedSediment) {
    }
}
