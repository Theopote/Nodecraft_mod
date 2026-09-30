package com.nodecraft.nodesystem.nodes.math.compare;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.math.ComparisonResult;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.compare.equals",
    displayName = "Equals (==)",
    description = "Returns true when A equals B.",
    category = "math.compare",
    order = 1
)
public class EqualsNode extends BaseNode {

    private static final String INPUT_A_ID = "input_a";
    private static final String INPUT_B_ID = "input_b";
    private static final String OUTPUT_RESULT_ID = "output_result";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public EqualsNode() {
        super(UUID.randomUUID(), "math.compare.equals");
        addInputPort(new BasePort(INPUT_A_ID, "A", "Left value", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_B_ID, "B", "Right value", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_RESULT_ID, "Result", "Whether A equals B", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether comparison succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Returns true when A equals B.";
    }

    @Override
    public String getDisplayName() {
        return "Equals (==)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        ComparisonResult result = CompareUtils.compareEqual(
                resolveAnyValue(INPUT_A_ID),
                resolveAnyValue(INPUT_B_ID),
                isDriven(INPUT_A_ID),
                isDriven(INPUT_B_ID)
        );
        emitComparison(result);
    }

    private boolean isDriven(String portId) {
        return OptionalPortDrive.isConnected(this, portId) || inputValues.containsKey(portId);
    }

    private @Nullable Object resolveAnyValue(String portId) {
        if (OptionalPortDrive.isConnected(this, portId)) {
            return getInput(portId);
        }
        return inputValues.get(portId);
    }

    private void emitComparison(ComparisonResult result) {
        outputValues.put(OUTPUT_RESULT_ID, result.result());
        outputValues.put(OUTPUT_VALID_ID, result.valid());
    }
}
