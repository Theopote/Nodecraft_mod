package com.nodecraft.nodesystem.nodes.math.logic;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.math.ComparisonResult;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

abstract class BooleanLogicNode extends BaseNode {

    static final String OUTPUT_RESULT_ID = "output_result";
    static final String OUTPUT_VALID_ID = "output_valid";

    @FunctionalInterface
    interface UnaryEval {
        ComparisonResult apply(@Nullable Object value, boolean driven);
    }

    @FunctionalInterface
    interface BinaryEval {
        ComparisonResult apply(
                @Nullable Object left,
                @Nullable Object right,
                boolean drivenLeft,
                boolean drivenRight
        );
    }

    BooleanLogicNode(UUID id, String typeId) {
        super(id, typeId);
        addOutputPort(new BasePort(OUTPUT_RESULT_ID, "Result", "Boolean result", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether evaluation succeeded", NodeDataType.BOOLEAN, this));
    }

    protected void processUnary(UnaryEval evaluate, String inputPortId) {
        emitComparison(evaluate.apply(resolveValue(inputPortId), isDriven(inputPortId)));
    }

    protected void processBinary(BinaryEval evaluate, String inputAId, String inputBId) {
        emitComparison(evaluate.apply(
                resolveValue(inputAId),
                resolveValue(inputBId),
                isDriven(inputAId),
                isDriven(inputBId)
        ));
    }

    protected boolean isDriven(String portId) {
        return OptionalPortDrive.isConnected(this, portId) || inputValues.containsKey(portId);
    }

    protected @Nullable Object resolveValue(String portId) {
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
