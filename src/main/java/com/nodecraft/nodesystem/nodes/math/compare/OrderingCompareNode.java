package com.nodecraft.nodesystem.nodes.math.compare;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.math.ComparisonResult;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

abstract class OrderingCompareNode extends BaseNode {

    static final String INPUT_A_ID = "input_a";
    static final String INPUT_B_ID = "input_b";
    static final String OUTPUT_RESULT_ID = "output_result";
    static final String OUTPUT_VALID_ID = "output_valid";

    @FunctionalInterface
    interface CompareFunction {
        ComparisonResult apply(
                @Nullable Object left,
                @Nullable Object right,
                boolean drivenLeft,
                boolean drivenRight
        );
    }

    OrderingCompareNode(UUID id, String typeId) {
        super(id, typeId);
        addInputPort(new BasePort(INPUT_A_ID, "A", "Left value", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_B_ID, "B", "Right value", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_RESULT_ID, "Result", "Comparison result", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether comparison succeeded", NodeDataType.BOOLEAN, this));
    }

    protected void processOrdering(CompareFunction compare) {
        ComparisonResult result = compare.apply(
                resolveValue(INPUT_A_ID),
                resolveValue(INPUT_B_ID),
                isDriven(INPUT_A_ID),
                isDriven(INPUT_B_ID)
        );
        outputValues.put(OUTPUT_RESULT_ID, result.result());
        outputValues.put(OUTPUT_VALID_ID, result.valid());
    }

    private boolean isDriven(String portId) {
        return OptionalPortDrive.isConnected(this, portId) || inputValues.containsKey(portId);
    }

    private @Nullable Object resolveValue(String portId) {
        if (OptionalPortDrive.isConnected(this, portId)) {
            return getInput(portId);
        }
        return inputValues.get(portId);
    }
}
