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
    id = "world.terrain.combine_height_fields",
    displayName = "Combine Height Fields",
    description = "Combines base, additive, and subtractive height fields into one output field.",
    category = "world.terrain",
    order = 3
)
public class CombineHeightFieldsNode extends BaseNode {

    private static final String INPUT_BASE_FIELD_ID = "input_base_field";
    private static final String INPUT_ADD_FIELD_ID = "input_add_field";
    private static final String INPUT_SUBTRACT_FIELD_ID = "input_subtract_field";
    private static final String INPUT_ADD_WEIGHT_ID = "input_add_weight";
    private static final String INPUT_SUBTRACT_WEIGHT_ID = "input_subtract_weight";

    private static final String OUTPUT_HEIGHT_FIELD_ID = "output_height_field";

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

        addOutputPort(new BasePort(OUTPUT_HEIGHT_FIELD_ID, "Height Field", "Combined elevation field", NodeDataType.SCALAR_FIELD, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object baseObj = inputValues.get(INPUT_BASE_FIELD_ID);
        if (!(baseObj instanceof ScalarFieldData baseField)) {
            outputValues.put(OUTPUT_HEIGHT_FIELD_ID, null);
            return;
        }

        ScalarFieldData addField = inputValues.get(INPUT_ADD_FIELD_ID) instanceof ScalarFieldData field ? field : null;
        ScalarFieldData subtractField = inputValues.get(INPUT_SUBTRACT_FIELD_ID) instanceof ScalarFieldData field ? field : null;

        double resolvedAddWeight = getInputDouble(INPUT_ADD_WEIGHT_ID, addWeight);
        double resolvedSubtractWeight = getInputDouble(INPUT_SUBTRACT_WEIGHT_ID, subtractWeight);

        ScalarFieldData combined = point -> {
            double value = baseField.sampleScalar(point);
            if (addField != null) {
                value += addField.sampleScalar(point) * resolvedAddWeight;
            }
            if (subtractField != null) {
                value -= subtractField.sampleScalar(point) * resolvedSubtractWeight;
            }
            return value;
        };

        outputValues.put(OUTPUT_HEIGHT_FIELD_ID, combined);
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }
}
