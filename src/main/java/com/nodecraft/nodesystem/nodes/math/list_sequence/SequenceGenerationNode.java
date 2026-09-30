package com.nodecraft.nodesystem.nodes.math.list_sequence;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.math.SequenceResult;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.StrictDoubleUtils;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

abstract class SequenceGenerationNode extends BaseNode {

    protected static final String OUTPUT_VALID_ID = "output_valid";
    protected static final String OUTPUT_ERROR_ID = "output_error";

    SequenceGenerationNode(UUID id, String typeId) {
        super(id, typeId);
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether generation succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when generation failed", NodeDataType.STRING, this));
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

    protected void emitListSuccess(String resultPortId, List<?> values) {
        outputValues.put(resultPortId, values);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    protected void emitListFailure(String resultPortId, String error) {
        outputValues.put(resultPortId, Collections.emptyList());
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    protected void emitSequenceResult(String resultPortId, SequenceResult result) {
        if (result.valid()) {
            emitListSuccess(resultPortId, result.values());
        } else {
            emitListFailure(resultPortId, result.error());
        }
    }

    /** Undriven → default; driven (connected or injected) → exact finite {@link Double}. */
    protected @Nullable Double resolveStrictDouble(String portId, double defaultValue) {
        if (!isDriven(portId)) {
            return defaultValue;
        }
        return StrictDoubleUtils.requireExactFiniteDouble(resolveValue(portId));
    }
}
