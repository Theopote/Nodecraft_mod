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
import com.nodecraft.nodesystem.datatypes.VectorFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockSpace;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "world.terrain.hydraulic_erosion_step",
    displayName = "Hydraulic Erosion Step",
    description = "Applies one hydraulic erosion-deposition step with carrying capacity and optional flow-driven sediment transport. "
        + "Unconnected Region defaults to local 64×64 domain. Flow sampled at cell centers; non-finite flow or materialize → Valid=false. "
        + "Height output clamped to [-1,1].",
    category = "world.terrain",
    order = 10
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
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

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

        addInputPort(new BasePort(INPUT_REGION_ID, "Region", "Optional raster bounds; unconnected → local 64×64 domain", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_HEIGHT_FIELD_ID, "Height Field", "Input elevation field", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_ACCUMULATION_FIELD_ID, "Accumulation Field", "Flow accumulation or runoff energy", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_SLOPE_FIELD_ID, "Slope Field", "Optional local slope field; when absent slope is derived from height field", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_FLOW_FIELD_ID, "Flow Field", "Optional flow direction field used for upstream sediment transport sampling", NodeDataType.VECTOR_FIELD, this));
        addInputPort(new BasePort(INPUT_SEDIMENT_FIELD_ID, "Sediment Field", "Optional incoming suspended sediment field", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_EROSION_RATE_ID, "Erosion Rate", "Single-step hydraulic incision amount", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_DEPOSITION_RATE_ID, "Deposition Rate", "Single-step settling amount when load exceeds carrying capacity", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_CAPACITY_ID, "Capacity", "Sediment carrying capacity scaling", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_TRANSPORT_EFFICIENCY_ID, "Transport Efficiency", "Upstream transport blending factor in [0,1]", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_ERODED_FIELD_ID, "Eroded Field", "Hydraulically eroded height field (normalized [-1,1])", NodeDataType.SCALAR_FIELD, this));
        addOutputPort(new BasePort(OUTPUT_SEDIMENT_FIELD_ID, "Sediment Field", "Estimated transported sediment field", NodeDataType.SCALAR_FIELD, this));
        addOutputPort(new BasePort(OUTPUT_DELTA_FIELD_ID, "Delta Field", "Signed height delta after hydraulic step (new - old)", NodeDataType.SCALAR_FIELD, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether hydraulic erosion succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when hydraulic erosion failed", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object heightObj = inputValues.get(INPUT_HEIGHT_FIELD_ID);
        Object accumulationObj = inputValues.get(INPUT_ACCUMULATION_FIELD_ID);
        if (!(heightObj instanceof ScalarFieldData heightField) || !(accumulationObj instanceof ScalarFieldData accumulationField)) {
            publishInvalid("Missing height or accumulation field input.");
            return;
        }

        RegionData region = TerrainNodeUtils.resolveOptionalRegion(this, INPUT_REGION_ID);
        if (TerrainNodeUtils.isInvalidRegionMarker(region)) {
            publishInvalid("Connected Region is incomplete or invalid.");
            return;
        }

        Double resolvedErosionRateRaw = TerrainNodeUtils.resolveOptionalFiniteDouble(this, INPUT_EROSION_RATE_ID, erosionRate);
        Double resolvedDepositionRateRaw = TerrainNodeUtils.resolveOptionalFiniteDouble(this, INPUT_DEPOSITION_RATE_ID, depositionRate);
        Double resolvedCapacityRaw = TerrainNodeUtils.resolveOptionalFiniteDouble(this, INPUT_CAPACITY_ID, capacity);
        Double resolvedTransportEfficiencyRaw = TerrainNodeUtils.resolveOptionalFiniteDouble(this, INPUT_TRANSPORT_EFFICIENCY_ID, transportEfficiency);
        if (resolvedErosionRateRaw == null || resolvedDepositionRateRaw == null
            || resolvedCapacityRaw == null || resolvedTransportEfficiencyRaw == null) {
            publishInvalid("Erosion Rate, Deposition Rate, Capacity, and Transport Efficiency must be finite DOUBLEs.");
            return;
        }

        TerrainGridDomain domain = ScalarFieldGrids.resolveDomain(region, heightField);
        GridScalarFieldData heightGrid = ScalarFieldGrids.materialize(heightField, domain);
        GridScalarFieldData accumulationGrid = ScalarFieldGrids.materialize(accumulationField, domain);
        if (heightGrid == null || accumulationGrid == null) {
            publishInvalid("Height or accumulation materialization failed (non-finite sample or grid over cap).");
            return;
        }

        ScalarFieldData slopeSourceField = inputValues.get(INPUT_SLOPE_FIELD_ID) instanceof ScalarFieldData value ? value : null;
        VectorFieldData flowField = inputValues.get(INPUT_FLOW_FIELD_ID) instanceof VectorFieldData value ? value : null;
        ScalarFieldData incomingSedimentField = inputValues.get(INPUT_SEDIMENT_FIELD_ID) instanceof ScalarFieldData value ? value : null;

        GridScalarFieldData slopeGrid = null;
        if (slopeSourceField != null) {
            slopeGrid = ScalarFieldGrids.materialize(slopeSourceField, domain);
            if (slopeGrid == null) {
                publishInvalid("Slope field materialization failed (non-finite sample or grid over cap).");
                return;
            }
        }

        GridScalarFieldData incomingSedimentGrid = null;
        if (incomingSedimentField != null) {
            incomingSedimentGrid = ScalarFieldGrids.materialize(incomingSedimentField, domain);
            if (incomingSedimentGrid == null) {
                publishInvalid("Sediment field materialization failed (non-finite sample or grid over cap).");
                return;
            }
        }

        double resolvedErosionRate = clamp01(resolvedErosionRateRaw);
        double resolvedDepositionRate = clamp01(resolvedDepositionRateRaw);
        double resolvedCapacity = Math.max(0.0d, resolvedCapacityRaw);
        double resolvedTransportEfficiency = clamp01(resolvedTransportEfficiencyRaw);

        int cellCount = heightGrid.cellCount();
        double[] erodedValues = new double[cellCount];
        double[] sedimentValues = new double[cellCount];
        double[] deltaValues = new double[cellCount];
        Vector3d flowVector = new Vector3d();
        Vector3d samplePoint = new Vector3d();
        int index = 0;
        for (int z = domain.minBlockZ(); z <= domain.maxBlockZ(); z++) {
            for (int x = domain.minBlockX(); x <= domain.maxBlockX(); x++) {
                double baseHeight = heightGrid.getAt(x, z);
                double flow = Math.max(0.0d, accumulationGrid.getAt(x, z));
                double slope = slopeGrid != null
                    ? Math.max(0.0d, slopeGrid.getAt(x, z))
                    : Math.max(0.0d, ScalarFieldGrids.sampleSlopeFromGrid(heightGrid, x, z, 1.0d));
                double incomingSediment = resolveIncomingSediment(
                    x,
                    z,
                    domain.sampleYBlock(),
                    incomingSedimentGrid,
                    flowField,
                    resolvedTransportEfficiency,
                    flowVector,
                    samplePoint
                );
                if (!Double.isFinite(incomingSediment)) {
                    publishInvalid("Non-finite flow vector during hydraulic transport sampling.");
                    return;
                }

                HydraulicTerms terms = evaluateTerms(
                    flow,
                    slope,
                    incomingSediment,
                    resolvedCapacity,
                    resolvedErosionRate,
                    resolvedDepositionRate
                );

                double erodedHeight = baseHeight - terms.erodedAmount + terms.depositedAmount;
                if (!Double.isFinite(erodedHeight) || !Double.isFinite(terms.updatedSediment)) {
                    publishInvalid("Hydraulic erosion produced a non-finite result.");
                    return;
                }

                double clampedHeight = TerrainNodeUtils.clampNormalizedHeight(erodedHeight);
                if (!Double.isFinite(clampedHeight)) {
                    publishInvalid("Hydraulic erosion produced a non-finite height.");
                    return;
                }

                erodedValues[index] = clampedHeight;
                sedimentValues[index] = terms.updatedSediment;
                deltaValues[index] = clampedHeight - baseHeight;
                index++;
            }
        }

        outputValues.put(OUTPUT_ERODED_FIELD_ID, ScalarFieldGrids.buildGrid(domain, erodedValues));
        outputValues.put(OUTPUT_SEDIMENT_FIELD_ID, ScalarFieldGrids.buildGrid(domain, sedimentValues));
        outputValues.put(OUTPUT_DELTA_FIELD_ID, ScalarFieldGrids.buildGrid(domain, deltaValues));
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void publishInvalid(String error) {
        outputValues.put(OUTPUT_ERODED_FIELD_ID, null);
        outputValues.put(OUTPUT_SEDIMENT_FIELD_ID, null);
        outputValues.put(OUTPUT_DELTA_FIELD_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error);
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
                                           double transportEfficiency,
                                           Vector3d flowVector,
                                           Vector3d samplePoint) {
        if (incomingSedimentGrid == null) {
            return 0.0d;
        }

        double local = Math.max(0.0d, incomingSedimentGrid.getAt(x, z));
        if (flowField == null || transportEfficiency <= 1.0e-9d) {
            return local;
        }

        samplePoint.set(
            x + BlockSpace.CELL_CENTER_OFFSET,
            sampleY + BlockSpace.CELL_CENTER_OFFSET,
            z + BlockSpace.CELL_CENTER_OFFSET
        );
        flowVector.set(0.0d, 0.0d, 0.0d);
        flowField.sampleVector(samplePoint, flowVector);
        if (!Double.isFinite(flowVector.x) || !Double.isFinite(flowVector.y) || !Double.isFinite(flowVector.z)) {
            return Double.NaN;
        }

        double len = Math.sqrt(flowVector.x * flowVector.x + flowVector.z * flowVector.z);
        if (len <= 1.0e-9d) {
            return local;
        }

        double ux = flowVector.x / len;
        double uz = flowVector.z / len;
        int upstreamX = (int) Math.round(x - ux);
        int upstreamZ = (int) Math.round(z - uz);
        double upstreamSediment = Math.max(0.0d, incomingSedimentGrid.getAtClamped(upstreamX, upstreamZ));
        return local * (1.0d - transportEfficiency) + upstreamSediment * transportEfficiency;
    }

    private double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private record HydraulicTerms(double erodedAmount, double depositedAmount, double updatedSediment) {
    }
}
