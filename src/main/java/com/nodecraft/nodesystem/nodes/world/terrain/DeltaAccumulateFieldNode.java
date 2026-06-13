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
    id = "world.terrain.delta_accumulate_field",
    displayName = "Delta Accumulate Field",
    description = "Applies or reverts delta fields and accumulates them into a combined terrain delta field.",
    category = "world.terrain",
    order = 13
)
public class DeltaAccumulateFieldNode extends BaseNode {

    public enum DeltaMode {
        APPLY,
        SUBTRACT
    }

    private static final String INPUT_BASE_HEIGHT_FIELD_ID = "input_base_height_field";
    private static final String INPUT_DELTA_FIELD_ID = "input_delta_field";
    private static final String INPUT_ACCUMULATED_DELTA_FIELD_ID = "input_accumulated_delta_field";
    private static final String INPUT_STRENGTH_ID = "input_strength";

    private static final String OUTPUT_HEIGHT_FIELD_ID = "output_height_field";
    private static final String OUTPUT_DELTA_FIELD_ID = "output_delta_field";

    @NodeProperty(displayName = "Mode", category = "Delta", order = 1)
    private DeltaMode mode = DeltaMode.APPLY;

    @NodeProperty(displayName = "Strength", category = "Delta", order = 2)
    private double strength = 1.0d;

    public DeltaAccumulateFieldNode() {
        super(UUID.randomUUID(), "world.terrain.delta_accumulate_field");

        addInputPort(new BasePort(INPUT_BASE_HEIGHT_FIELD_ID, "Base Height Field", "Optional base height field (defaults to 0 when omitted)", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_DELTA_FIELD_ID, "Delta Field", "Input signed height delta field", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_ACCUMULATED_DELTA_FIELD_ID, "Accumulated Delta Field", "Optional existing accumulated delta for chaining", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_STRENGTH_ID, "Strength", "Delta scaling factor", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_HEIGHT_FIELD_ID, "Height Field", "Base field plus accumulated delta", NodeDataType.SCALAR_FIELD, this));
        addOutputPort(new BasePort(OUTPUT_DELTA_FIELD_ID, "Delta Field", "Accumulated signed delta field", NodeDataType.SCALAR_FIELD, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object deltaObj = inputValues.get(INPUT_DELTA_FIELD_ID);
        if (!(deltaObj instanceof ScalarFieldData deltaField)) {
            outputValues.put(OUTPUT_HEIGHT_FIELD_ID, null);
            outputValues.put(OUTPUT_DELTA_FIELD_ID, null);
            return;
        }

        ScalarFieldData baseField = inputValues.get(INPUT_BASE_HEIGHT_FIELD_ID) instanceof ScalarFieldData value
            ? value
            : point -> 0.0d;
        ScalarFieldData accumulatedDeltaField = inputValues.get(INPUT_ACCUMULATED_DELTA_FIELD_ID) instanceof ScalarFieldData value
            ? value
            : null;

        DeltaMode resolvedMode = mode == null ? DeltaMode.APPLY : mode;
        double resolvedStrength = Math.max(0.0d, getInputDouble(INPUT_STRENGTH_ID, strength));

        ScalarFieldData mergedDeltaField = point -> {
            double incomingDelta = sanitizeFinite(deltaField.sampleScalar(point), 0.0d);
            double previousDelta = accumulatedDeltaField == null
                ? 0.0d
                : sanitizeFinite(accumulatedDeltaField.sampleScalar(point), 0.0d);

            double signedDelta = resolvedMode == DeltaMode.SUBTRACT ? -incomingDelta : incomingDelta;
            return sanitizeFinite(previousDelta + signedDelta * resolvedStrength, previousDelta);
        };

        ScalarFieldData updatedHeightField = point -> {
            double baseHeight = sanitizeFinite(baseField.sampleScalar(point), 0.0d);
            double accumulatedDelta = sanitizeFinite(mergedDeltaField.sampleScalar(point), 0.0d);
            return sanitizeFinite(baseHeight + accumulatedDelta, baseHeight);
        };

        outputValues.put(OUTPUT_HEIGHT_FIELD_ID, updatedHeightField);
        outputValues.put(OUTPUT_DELTA_FIELD_ID, mergedDeltaField);
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private double sanitizeFinite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }
}
