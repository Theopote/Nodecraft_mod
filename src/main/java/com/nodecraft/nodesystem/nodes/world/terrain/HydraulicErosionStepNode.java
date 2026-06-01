package com.nodecraft.nodesystem.nodes.world.terrain;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.ScalarFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "world.terrain.hydraulic_erosion_step",
    displayName = "Hydraulic Erosion Step",
    description = "Applies one hydraulic erosion step using flow accumulation as transport energy.",
    category = "world.terrain",
    order = 10
)
public class HydraulicErosionStepNode extends BaseNode {

    private static final String INPUT_HEIGHT_FIELD_ID = "input_height_field";
    private static final String INPUT_ACCUMULATION_FIELD_ID = "input_accumulation_field";
    private static final String INPUT_SLOPE_FIELD_ID = "input_slope_field";
    private static final String INPUT_SEDIMENT_FIELD_ID = "input_sediment_field";
    private static final String INPUT_EROSION_RATE_ID = "input_erosion_rate";
    private static final String INPUT_DEPOSITION_RATE_ID = "input_deposition_rate";
    private static final String INPUT_CAPACITY_ID = "input_capacity";

    private static final String OUTPUT_ERODED_FIELD_ID = "output_eroded_field";
    private static final String OUTPUT_SEDIMENT_FIELD_ID = "output_sediment_field";

    @NodeProperty(displayName = "Erosion Rate", category = "Hydraulic", order = 1)
    private double erosionRate = 0.08d;

    @NodeProperty(displayName = "Capacity", category = "Hydraulic", order = 2)
    private double capacity = 1.0d;

    @NodeProperty(displayName = "Deposition Rate", category = "Hydraulic", order = 3)
    private double depositionRate = 0.06d;

    public HydraulicErosionStepNode() {
        super(UUID.randomUUID(), "world.terrain.hydraulic_erosion_step");

        addInputPort(new BasePort(INPUT_HEIGHT_FIELD_ID, "Height Field", "Input elevation field", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_ACCUMULATION_FIELD_ID, "Accumulation Field", "Flow accumulation or runoff energy", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_SLOPE_FIELD_ID, "Slope Field", "Optional local slope field; when absent slope is derived from height field", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_SEDIMENT_FIELD_ID, "Sediment Field", "Optional incoming suspended sediment field", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_EROSION_RATE_ID, "Erosion Rate", "Single-step hydraulic incision amount", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_DEPOSITION_RATE_ID, "Deposition Rate", "Single-step settling amount when load exceeds carrying capacity", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_CAPACITY_ID, "Capacity", "Sediment carrying capacity scaling", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_ERODED_FIELD_ID, "Eroded Field", "Hydraulically eroded height field", NodeDataType.SCALAR_FIELD, this));
        addOutputPort(new BasePort(OUTPUT_SEDIMENT_FIELD_ID, "Sediment Field", "Estimated transported sediment field", NodeDataType.SCALAR_FIELD, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object heightObj = inputValues.get(INPUT_HEIGHT_FIELD_ID);
        Object accumulationObj = inputValues.get(INPUT_ACCUMULATION_FIELD_ID);
        if (!(heightObj instanceof ScalarFieldData heightField) || !(accumulationObj instanceof ScalarFieldData accumulationField)) {
            outputValues.put(OUTPUT_ERODED_FIELD_ID, null);
            outputValues.put(OUTPUT_SEDIMENT_FIELD_ID, null);
            return;
        }

        ScalarFieldData slopeSourceField = inputValues.get(INPUT_SLOPE_FIELD_ID) instanceof ScalarFieldData value ? value : null;
        ScalarFieldData incomingSedimentField = inputValues.get(INPUT_SEDIMENT_FIELD_ID) instanceof ScalarFieldData value ? value : null;

        double resolvedErosionRate = clamp01(getInputDouble(INPUT_EROSION_RATE_ID, erosionRate));
        double resolvedDepositionRate = clamp01(getInputDouble(INPUT_DEPOSITION_RATE_ID, depositionRate));
        double resolvedCapacity = Math.max(0.0d, getInputDouble(INPUT_CAPACITY_ID, capacity));

        ScalarFieldData derivedSlopeField = slopeSourceField != null ? slopeSourceField : point -> sampleSlopeFromHeight(heightField, point, 1.0d);

        ScalarFieldData sedimentField = point -> {
            HydraulicTerms terms = evaluateTerms(
                point,
                accumulationField,
                derivedSlopeField,
                incomingSedimentField,
                resolvedCapacity,
                resolvedErosionRate,
                resolvedDepositionRate
            );
            return terms.updatedSediment;
        };

        ScalarFieldData erodedField = point -> {
            double baseHeight = heightField.sampleScalar(point);
            HydraulicTerms terms = evaluateTerms(
                point,
                accumulationField,
                derivedSlopeField,
                incomingSedimentField,
                resolvedCapacity,
                resolvedErosionRate,
                resolvedDepositionRate
            );
            return baseHeight - terms.erodedAmount + terms.depositedAmount;
        };

        outputValues.put(OUTPUT_ERODED_FIELD_ID, erodedField);
        outputValues.put(OUTPUT_SEDIMENT_FIELD_ID, sedimentField);
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private HydraulicTerms evaluateTerms(Vector3d point,
                                         ScalarFieldData accumulationField,
                                         ScalarFieldData slopeField,
                                         @Nullable ScalarFieldData incomingSedimentField,
                                         double capacityFactor,
                                         double erosion,
                                         double deposition) {
        double flow = Math.max(0.0d, accumulationField.sampleScalar(point));
        double slope = Math.max(0.0d, slopeField.sampleScalar(point));
        double incomingSediment = incomingSedimentField == null ? 0.0d : Math.max(0.0d, incomingSedimentField.sampleScalar(point));

        // Carrying capacity grows with both flow and slope.
        double carryingCapacity = flow * slope * capacityFactor;

        double eroded = Math.max(0.0d, carryingCapacity - incomingSediment) * erosion;
        double deposited = Math.max(0.0d, incomingSediment - carryingCapacity) * deposition;
        double updatedSediment = Math.max(0.0d, incomingSediment + eroded - deposited);
        return new HydraulicTerms(eroded, deposited, updatedSediment);
    }

    private double sampleSlopeFromHeight(ScalarFieldData heightField, Vector3d point, double step) {
        double x = point.x;
        double y = point.y;
        double z = point.z;

        double left = heightField.sampleScalar(new Vector3d(x - step, y, z));
        double right = heightField.sampleScalar(new Vector3d(x + step, y, z));
        double down = heightField.sampleScalar(new Vector3d(x, y, z - step));
        double up = heightField.sampleScalar(new Vector3d(x, y, z + step));

        double gradX = (right - left) / (2.0d * step);
        double gradZ = (up - down) / (2.0d * step);
        return Math.sqrt(gradX * gradX + gradZ * gradZ);
    }

    private double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private record HydraulicTerms(double erodedAmount, double depositedAmount, double updatedSediment) {
    }
}
