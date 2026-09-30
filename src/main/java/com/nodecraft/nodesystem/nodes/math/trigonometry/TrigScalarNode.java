package com.nodecraft.nodesystem.nodes.math.trigonometry;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.math.ScalarResult;
import com.nodecraft.nodesystem.util.StrictDoubleUtils;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

abstract class TrigScalarNode extends BaseNode {

    protected static final String OUTPUT_VALID_ID = "output_valid";

    TrigScalarNode(UUID id, String typeId) {
        super(id, typeId);
    }

    protected @Nullable Double requireInput(String portId) {
        return StrictDoubleUtils.requireExactFiniteDouble(inputValues.get(portId));
    }

    protected void emitInvalid(String resultPortId) {
        outputValues.put(resultPortId, Double.NaN);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    protected void emit(String resultPortId, ScalarResult result) {
        outputValues.put(resultPortId, result.value());
        outputValues.put(OUTPUT_VALID_ID, result.valid());
    }
}
