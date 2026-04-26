package com.nodecraft.nodesystem.nodes.math.fields;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.ScalarFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    id = "math.fields.scalar_constant",
    displayName = "Scalar Field Constant",
    description = "Builds a scalar field that returns a constant value everywhere.",
    category = "math.fields",
    order = 1
)
public class ScalarFieldConstantNode extends BaseNode {

    private static final String INPUT_VALUE_ID = "input_value";
    private static final String OUTPUT_FIELD_ID = "output_field";

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
        double v = getInputDouble(INPUT_VALUE_ID, 0.0d);
        ScalarFieldData field = point -> v;
        outputValues.put(OUTPUT_FIELD_ID, field);
    }

    private double getInputDouble(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }
}
