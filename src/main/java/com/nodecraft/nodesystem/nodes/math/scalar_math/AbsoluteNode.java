package com.nodecraft.nodesystem.nodes.math.scalar_math;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    id = "math.scalar_math.absolute",
    displayName = "Absolute (Abs)",
    description = "Returns the absolute value of the input.",
    category = "math.scalar_math",
    order = 7
)
public class AbsoluteNode extends BaseNode {

    private static final String INPUT_VALUE_ID = "input_value";
    private static final String OUTPUT_ABSOLUTE_ID = "output_absolute";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public AbsoluteNode() {
        super(UUID.randomUUID(), "math.scalar_math.absolute");
        addInputPort(new BasePort(INPUT_VALUE_ID, "Value", "Input number", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_ABSOLUTE_ID, "Absolute", "Result |Value|", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether input is a valid finite number", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Returns the absolute value of the input.";
    }

    @Override
    public String getDisplayName() {
        return "Absolute (Abs)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object val = inputValues.get(INPUT_VALUE_ID);
        if (!(val instanceof Number number)) {
            outputValues.put(OUTPUT_ABSOLUTE_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        double value = number.doubleValue();
        if (!Double.isFinite(value)) {
            outputValues.put(OUTPUT_ABSOLUTE_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        outputValues.put(OUTPUT_ABSOLUTE_ID, Math.abs(value));
        outputValues.put(OUTPUT_VALID_ID, true);
    }
}
