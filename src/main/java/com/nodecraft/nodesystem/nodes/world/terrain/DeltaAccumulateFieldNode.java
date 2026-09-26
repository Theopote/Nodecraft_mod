package com.nodecraft.nodesystem.nodes.world.terrain;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.ScalarFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "world.terrain.delta_accumulate_field",
    displayName = "Delta Accumulate Field",
    description = "Applies or reverts delta fields and accumulates them into a combined terrain delta field. "
        + "Delta output stays signed finite (no [-1,1] clamp); height output is clamped to [-1,1]. "
        + "Validates inputs at process time; lazy samples may propagate NaN to materialize consumers.",
    category = "world.terrain",
    order = 12
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
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

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

        addOutputPort(new BasePort(OUTPUT_HEIGHT_FIELD_ID, "Height Field", "Base field plus accumulated delta (normalized [-1,1])", NodeDataType.SCALAR_FIELD, this));
        addOutputPort(new BasePort(OUTPUT_DELTA_FIELD_ID, "Delta Field", "Accumulated signed delta field (finite, unclamped)", NodeDataType.SCALAR_FIELD, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether delta accumulation was set up", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when delta accumulation setup failed", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object deltaObj = inputValues.get(INPUT_DELTA_FIELD_ID);
        if (!(deltaObj instanceof ScalarFieldData deltaField)) {
            publishInvalid("Missing delta field input.");
            return;
        }

        Double resolvedStrengthRaw = TerrainNodeUtils.resolveOptionalFiniteDouble(this, INPUT_STRENGTH_ID, strength);
        if (resolvedStrengthRaw == null) {
            publishInvalid("Strength must be a finite DOUBLE.");
            return;
        }

        ScalarFieldData baseField = inputValues.get(INPUT_BASE_HEIGHT_FIELD_ID) instanceof ScalarFieldData value
            ? value
            : point -> 0.0d;
        ScalarFieldData accumulatedDeltaField = inputValues.get(INPUT_ACCUMULATED_DELTA_FIELD_ID) instanceof ScalarFieldData value
            ? value
            : null;

        DeltaMode resolvedMode = mode == null ? DeltaMode.APPLY : mode;
        double resolvedStrength = Math.max(0.0d, resolvedStrengthRaw);

        ScalarFieldData mergedDeltaField = point -> {
            double incomingDelta = deltaField.sampleScalar(point);
            if (!Double.isFinite(incomingDelta)) {
                return Double.NaN;
            }
            double previousDelta = 0.0d;
            if (accumulatedDeltaField != null) {
                previousDelta = accumulatedDeltaField.sampleScalar(point);
                if (!Double.isFinite(previousDelta)) {
                    return Double.NaN;
                }
            }

            double signedDelta = resolvedMode == DeltaMode.SUBTRACT ? -incomingDelta : incomingDelta;
            return previousDelta + signedDelta * resolvedStrength;
        };

        ScalarFieldData updatedHeightField = point -> {
            double baseHeight = baseField.sampleScalar(point);
            double accumulatedDelta = mergedDeltaField.sampleScalar(point);
            if (!Double.isFinite(baseHeight) || !Double.isFinite(accumulatedDelta)) {
                return Double.NaN;
            }
            return TerrainNodeUtils.clampNormalizedHeight(baseHeight + accumulatedDelta);
        };

        outputValues.put(OUTPUT_HEIGHT_FIELD_ID, updatedHeightField);
        outputValues.put(OUTPUT_DELTA_FIELD_ID, mergedDeltaField);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void publishInvalid(String error) {
        outputValues.put(OUTPUT_HEIGHT_FIELD_ID, null);
        outputValues.put(OUTPUT_DELTA_FIELD_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error);
    }
}
