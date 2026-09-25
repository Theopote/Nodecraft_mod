package com.nodecraft.nodesystem.nodes.math.scalar_math;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.math.ScalarMathOps;
import com.nodecraft.nodesystem.math.ScalarResult;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.scalar_math.floor",
    displayName = "Floor",
    description = "Rounds a value down to the nearest integer.",
    category = "math.scalar_math",
    order = 12
)
public class FloorNode extends BaseNode {

    private static final String INPUT_VALUE_ID = "input_value";
    private static final String OUTPUT_FLOORED_ID = "output_floored";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public FloorNode() {
        super(UUID.randomUUID(), "math.scalar_math.floor");
        addInputPort(new BasePort(INPUT_VALUE_ID, "Value", "Value to floor", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_FLOORED_ID, "Floored", "The floored value", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether input is a valid finite number", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Rounds a value down to the nearest integer.";
    }

    @Override
    public String getDisplayName() {
        return "Floor";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object valueObj = inputValues.get(INPUT_VALUE_ID);
        if (!(valueObj instanceof Number number)) {
            outputValues.put(OUTPUT_FLOORED_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }
        ScalarResult result = ScalarMathOps.floor(number.doubleValue());
        outputValues.put(OUTPUT_FLOORED_ID, result.value());
        outputValues.put(OUTPUT_VALID_ID, result.valid());
    }
}
