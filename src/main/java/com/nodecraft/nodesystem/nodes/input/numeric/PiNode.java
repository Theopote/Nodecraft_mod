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
    id = "input.numeric.pi",
    displayName = "Pi",
    description = "Outputs the mathematical constant Pi.",
    category = "input.numeric",
    order = 20
)
public class PiNode extends BaseNode {

    private static final String OUTPUT_PI_ID = "output_pi";

    public PiNode() {
        super(UUID.randomUUID(), "input.numeric.pi");

        addOutputPort(new BasePort(OUTPUT_PI_ID, "Pi", "The value of Pi", NodeDataType.DOUBLE, this));
        outputValues.put(OUTPUT_PI_ID, Math.PI);
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        if (!outputValues.containsKey(OUTPUT_PI_ID)) {
            outputValues.put(OUTPUT_PI_ID, Math.PI);
        }
    }

    @Override
    public String getDescription() {
        return "Outputs the mathematical constant Pi.";
    }

    @Override
    public String getDisplayName() {
        return "Pi (π)";
    }
}
