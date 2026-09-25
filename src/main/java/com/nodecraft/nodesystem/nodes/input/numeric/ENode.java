package com.nodecraft.nodesystem.nodes.input.numeric;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "input.numeric.e",
    displayName = "E",
    description = "Outputs the mathematical constant e (approximately 2.718281828...).",
    category = "input.numeric",
    order = 21
)
public class ENode extends BaseNode {

    private static final String OUTPUT_VALUE_ID = "output_value";

    public ENode() {
        super(UUID.randomUUID(), "input.numeric.e");

        addOutputPort(new BasePort(OUTPUT_VALUE_ID, "Value", "The value of e", NodeDataType.DOUBLE, this));
        outputValues.put(OUTPUT_VALUE_ID, Math.E);
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        if (!outputValues.containsKey(OUTPUT_VALUE_ID)) {
            outputValues.put(OUTPUT_VALUE_ID, Math.E);
        }
    }

    @Override
    public String getDisplayName() {
        return "E";
    }

    @Override
    public String getDescription() {
        return "Outputs the mathematical constant e.";
    }
}
