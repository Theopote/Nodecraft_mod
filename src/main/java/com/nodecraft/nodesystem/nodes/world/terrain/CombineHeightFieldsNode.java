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
    id = "world.terrain.combine_height_fields",
    displayName = "Combine Height Fields",
    description = "Combines base, additive, and subtractive height fields into one normalized [-1,1] output. "
        + "Validates inputs at process time; lazy samples may propagate NaN to materialize consumers.",
    category = "world.terrain",
    order = 4
)
public class CombineHeightFieldsNode extends BaseNode {

    private static final String INPUT_BASE_FIELD_ID = "input_base_field";
    private static final String INPUT_ADD_FIELD_ID = "input_add_field";
    private static final String INPUT_SUBTRACT_FIELD_ID = "input_subtract_field";
    private static final String INPUT_ADD_WEIGHT_ID = "input_add_weight";
    private static final String INPUT_SUBTRACT_WEIGHT_ID = "input_subtract_weight";

    private static final String OUTPUT_HEIGHT_FIELD_ID = "output_height_field";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    @NodeProperty(displayName = "Add Weight", category = "Combine", order = 1)
    private double addWeight = 1.0d;

    @NodeProperty(displayName = "Subtract Weight", category = "Combine", order = 2)
    private double subtractWeight = 1.0d;

    public CombineHeightFieldsNode() {
        super(UUID.randomUUID(), "world.terrain.combine_height_fields");

        addInputPort(new BasePort(INPUT_BASE_FIELD_ID, "Base Field", "Base elevation field", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_ADD_FIELD_ID, "Add Field", "Optional additive elevation field", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_SUBTRACT_FIELD_ID, "Subtract Field", "Optional subtractive elevation field", NodeDataType.SCALAR_FIELD, this));
        addInputPort(new BasePort(INPUT_ADD_WEIGHT_ID, "Add Weight", "Multiplier for additive field", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_SUBTRACT_WEIGHT_ID, "Subtract Weight", "Multiplier for subtractive field", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_HEIGHT_FIELD_ID, "Height Field", "Combined elevation field (normalized [-1,1])", NodeDataType.SCALAR_FIELD, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the combined field was created", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when combine failed", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object baseObj = inputValues.get(INPUT_BASE_FIELD_ID);
        if (!(baseObj instanceof ScalarFieldData baseField)) {
            publishInvalid("Missing base field input.");
            return;
        }

        ScalarFieldData addField = inputValues.get(INPUT_ADD_FIELD_ID) instanceof ScalarFieldData field ? field : null;
        ScalarFieldData subtractField = inputValues.get(INPUT_SUBTRACT_FIELD_ID) instanceof ScalarFieldData field ? field : null;

        Double resolvedAddWeight = TerrainNodeUtils.resolveOptionalFiniteDouble(this, INPUT_ADD_WEIGHT_ID, addWeight);
        if (resolvedAddWeight == null) {
            publishInvalid("Add Weight must be a finite DOUBLE.");
            return;
        }

        Double resolvedSubtractWeight = TerrainNodeUtils.resolveOptionalFiniteDouble(this, INPUT_SUBTRACT_WEIGHT_ID, subtractWeight);
        if (resolvedSubtractWeight == null) {
            publishInvalid("Subtract Weight must be a finite DOUBLE.");
            return;
        }

        double addW = resolvedAddWeight;
        double subW = resolvedSubtractWeight;

        ScalarFieldData combined = point -> {
            double value = baseField.sampleScalar(point);
            if (!Double.isFinite(value)) {
                return Double.NaN;
            }
            if (addField != null) {
                double addSample = addField.sampleScalar(point);
                if (!Double.isFinite(addSample)) {
                    return Double.NaN;
                }
                value += addSample * addW;
            }
            if (subtractField != null) {
                double subSample = subtractField.sampleScalar(point);
                if (!Double.isFinite(subSample)) {
                    return Double.NaN;
                }
                value -= subSample * subW;
            }
            return TerrainNodeUtils.clampNormalizedHeight(value);
        };

        outputValues.put(OUTPUT_HEIGHT_FIELD_ID, combined);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void publishInvalid(String error) {
        outputValues.put(OUTPUT_HEIGHT_FIELD_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error);
    }
}
