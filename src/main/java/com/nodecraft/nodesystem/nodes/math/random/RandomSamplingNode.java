package com.nodecraft.nodesystem.nodes.math.random;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

abstract class RandomSamplingNode extends BaseNode {

    protected static final String OUTPUT_VALID_ID = "output_valid";
    protected static final String OUTPUT_ERROR_ID = "output_error";

    RandomSamplingNode(UUID id, String typeId) {
        super(id, typeId);
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether sampling succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when sampling failed", NodeDataType.STRING, this));
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

    protected @Nullable Vector3d resolveVectorValue(String portId) {
        Object value = resolveValue(portId);
        if (value instanceof Vector3d vector) {
            return new Vector3d(vector);
        }
        if (value instanceof Vec3d vector) {
            return new Vector3d(vector.x, vector.y, vector.z);
        }
        return null;
    }

    protected void emitFailure(String resultPortId, @Nullable Object failureResult, String error) {
        outputValues.put(resultPortId, failureResult);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    protected void emitSuccess(String resultPortId, @Nullable Object result) {
        outputValues.put(resultPortId, result);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    protected void emitListItemSuccess(
            String itemPortId,
            @Nullable Object item,
            String itemsPortId,
            Object items
    ) {
        outputValues.put(itemPortId, item);
        outputValues.put(itemsPortId, items);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    protected void emitListItemFailure(String itemPortId, String itemsPortId, String error) {
        outputValues.put(itemPortId, null);
        outputValues.put(itemsPortId, java.util.Collections.emptyList());
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}
