package com.nodecraft.nodesystem.nodes.math.fields;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.ScalarFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.fields.scalar_constant",
    displayName = "Scalar Field Constant",
    description = "Builds a scalar field that returns a constant value everywhere.",
    category = "math.fields",
    order = 1
)
public class ScalarFieldConstantNode extends BaseNode {

    private static final String INPUT_VALUE_ID = "input_value";
    private static final String OUTPUT_FIELD_ID = "output_field";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    private double defaultValue = 0.0d;

    public ScalarFieldConstantNode() {
        super(UUID.randomUUID(), "math.fields.scalar_constant");
        addInputPort(new BasePort(INPUT_VALUE_ID, "Value", "Constant scalar value", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_FIELD_ID, "Field", "Scalar field f(p) = value", NodeDataType.SCALAR_FIELD, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the field was constructed",
                NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false",
                NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Builds a scalar field that returns a constant value everywhere.";
    }

    @Override
    public String getDisplayName() {
        return "Scalar Field Constant";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Double value = resolveValue();
        if (value == null) {
            writeInvalid(FieldSampleUtils.ERROR_INVALID_INPUT);
            return;
        }
        final double v = value;
        ScalarFieldData field = point -> v;
        outputValues.put(OUTPUT_FIELD_ID, field);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private @Nullable Double resolveValue() {
        if (OptionalPortDrive.isConnected(this, INPUT_VALUE_ID) || isInputPresent(INPUT_VALUE_ID)) {
            Object raw = getInput(INPUT_VALUE_ID);
            if (!(raw instanceof Number number)) {
                return null;
            }
            double v = number.doubleValue();
            return Double.isFinite(v) ? v : null;
        }
        return Double.isFinite(defaultValue) ? defaultValue : null;
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_FIELD_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}
