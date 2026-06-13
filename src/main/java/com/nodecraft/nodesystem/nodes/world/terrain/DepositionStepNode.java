package com.nodecraft.nodesystem.nodes.world.terrain;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.ScalarFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    id = "world.terrain.deposition_step",
    displayName = "Deposition Step",
    description = "Deposits sediment in low-slope and low-energy zones.",
    category = "world.terrain",
    order = 12
)
public class DepositionStepNode extends BaseNode {

    private static final String INPUT_HEIGHT_FIELD_ID = "input_height_field";
    private static final String INPUT_SEDIMENT_FIELD_ID = "input_sediment_field";
    private static final String INPUT_SLOPE_FIELD_ID = "input_slope_field";
    private static final String INPUT_ACCUMULATION_FIELD_ID = "input_accumulation_field";
    private static final String INPUT_CAPACITY_ID = "input_capacity";
    private static final String INPUT_RATE_ID = "input_rate";

    private static final String OUTPUT_HEIGHT_FIELD_ID = "output_height_field";
    private static final String OUTPUT_SEDIMENT_FIELD_ID = "output_sediment_field";
    private static final String OUTPUT_DELTA_FIELD_ID = "output_delta_field";

    @NodeProperty(displayName = "Rate", category = "Deposition", order = 1)
    private double rate = 0.1d;

    @NodeProperty(displayName = "Capacity", category = "Deposition", order = 2)
    private double capacity = 1.0d;

    public DepositionStepNode() {
        super(UUID.randomUUID(), "world.terrain.deposition_step");

        addInputPort(new BasePort(INPUT_HEIGHT_FIELD_ID, "Height Field", "Current terrain height field", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_SEDIMENT_FIELD_ID, "Sediment Field", "Transported sediment load", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_SLOPE_FIELD_ID, "Slope Field", "Slope magnitude field", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_ACCUMULATION_FIELD_ID, "Accumulation Field", "Optional flow accumulation field used in carrying capacity", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_CAPACITY_ID, "Capacity", "Sediment carrying capacity scaling", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RATE_ID, "Rate", "Single-step deposition strength", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_HEIGHT_FIELD_ID, "Height Field", "Height field after deposition", NodeDataType.SCALAR_FIELD, this));
        addOutputPort(new BasePort(OUTPUT_SEDIMENT_FIELD_ID, "Sediment Field", "Sediment field after deposition update", NodeDataType.SCALAR_FIELD, this));
        addOutputPort(new BasePort(OUTPUT_DELTA_FIELD_ID, "Delta Field", "Signed height delta after deposition (new - old)", NodeDataType.SCALAR_FIELD, this));
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
            outputValues.put(OUTPUT_HEIGHT_FIELD_ID, null);
            outputValues.put(OUTPUT_SEDIMENT_FIELD_ID, null);
            outputValues.put(OUTPUT_DELTA_FIELD_ID, null);
            return;
        }

        double resolvedRate = clamp01(getInputDouble(INPUT_RATE_ID, rate));
        double resolvedCapacity = Math.max(0.0d, getInputDouble(INPUT_CAPACITY_ID, capacity));

        ScalarFieldData updatedSedimentField = point -> {
            double sediment = Math.max(0.0d, sanitizeFinite(sedimentField.sampleScalar(point), 0.0d));
            double slope = Math.max(0.0d, sanitizeFinite(slopeField.sampleScalar(point), 0.0d));
            double flow = Math.max(0.0d, sanitizeFinite(accumulationField.sampleScalar(point), 0.0d));
            double carryingCapacity = flow * slope * resolvedCapacity;

            // Deposit only the overload above local carrying capacity.
            double deposition = Math.max(0.0d, sediment - carryingCapacity) * resolvedRate;
            return Math.max(0.0d, sediment - deposition);
        };

        ScalarFieldData depositedField = point -> {
            double baseHeight = sanitizeFinite(heightField.sampleScalar(point), 0.0d);
            double sediment = Math.max(0.0d, sanitizeFinite(sedimentField.sampleScalar(point), 0.0d));
            double slope = Math.max(0.0d, sanitizeFinite(slopeField.sampleScalar(point), 0.0d));
            double flow = Math.max(0.0d, sanitizeFinite(accumulationField.sampleScalar(point), 0.0d));

            double carryingCapacity = flow * slope * resolvedCapacity;
            double overload = Math.max(0.0d, sediment - carryingCapacity);
            double deposition = overload * resolvedRate;

            return sanitizeFinite(baseHeight + deposition, baseHeight);
        };

        ScalarFieldData deltaField = point -> {
            double before = sanitizeFinite(heightField.sampleScalar(point), 0.0d);
            double after = sanitizeFinite(depositedField.sampleScalar(point), before);
            return after - before;
        };

        outputValues.put(OUTPUT_HEIGHT_FIELD_ID, depositedField);
        outputValues.put(OUTPUT_SEDIMENT_FIELD_ID, updatedSedimentField);
        outputValues.put(OUTPUT_DELTA_FIELD_ID, deltaField);
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
