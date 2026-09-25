package com.nodecraft.nodesystem.nodes.math.fields;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.ScalarFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
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

    private double defaultValue = 0.0d;

    public ScalarFieldConstantNode() {
        super(UUID.randomUUID(), "math.fields.scalar_constant");
        addInputPort(new BasePort(INPUT_VALUE_ID, "Value", "Constant scalar value", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_FIELD_ID, "Field", "Scalar field f(p) = value", NodeDataType.SCALAR_FIELD, this));
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
        Object raw = inputValues.get(INPUT_VALUE_ID);
        double v;
        if (raw instanceof Number number) {
            v = number.doubleValue();
            if (!Double.isFinite(v)) {
                outputValues.put(OUTPUT_FIELD_ID, null);
                return;
            }
        } else {
            v = defaultValue;
        }
        ScalarFieldData field = point -> v;
        outputValues.put(OUTPUT_FIELD_ID, field);
    }
}
