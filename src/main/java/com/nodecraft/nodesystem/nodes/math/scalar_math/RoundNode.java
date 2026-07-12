package com.nodecraft.nodesystem.nodes.math.scalar_math;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    id = "math.scalar_math.round",
    displayName = "Round",
    description = "Rounds a value to the nearest integer.",
    category = "math.scalar_math",
    order = 14
)
public class RoundNode extends BaseNode {

    private static final String INPUT_VALUE_ID = "input_value";
    private static final String OUTPUT_ROUNDED_ID = "output_rounded";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public RoundNode() {
        super(UUID.randomUUID(), "math.scalar_math.round");
        addInputPort(new BasePort(INPUT_VALUE_ID, "Value", "Value to round", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_ROUNDED_ID, "Rounded", "The rounded value", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether input is a valid finite number", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Rounds a value to the nearest integer.";
    }

    @Override
    public String getDisplayName() {
        return "Round";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object valueObj = inputValues.get(INPUT_VALUE_ID);
        if (!(valueObj instanceof Number number)) {
            outputValues.put(OUTPUT_ROUNDED_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        double value = number.doubleValue();
        if (!Double.isFinite(value)) {
            outputValues.put(OUTPUT_ROUNDED_ID, Double.NaN);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        outputValues.put(OUTPUT_ROUNDED_ID, (double) Math.round(value));
        outputValues.put(OUTPUT_VALID_ID, true);
    }
}
