package com.nodecraft.nodesystem.nodes.pattern.linear;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.VectorUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

/**
 * Shared Valid/Error helpers for pattern.linear.
 */
abstract class AbstractPatternLinearNode extends BaseNode {

    protected static final String OUTPUT_VALID_ID = "output_valid";
    protected static final String OUTPUT_ERROR_ID = "output_error";

    private static final Vector3d WORLD_UP = new Vector3d(0.0d, 1.0d, 0.0d);
    private static final Vector3d WORLD_X = new Vector3d(1.0d, 0.0d, 0.0d);

    protected AbstractPatternLinearNode(UUID id, String typeName) {
        super(id, typeName);
    }

    protected AbstractPatternLinearNode(String typeName) {
        super(UUID.randomUUID(), typeName);
    }

    protected final void addValidAndErrorOutputs() {
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when the pattern operation succeeded", NodeDataType.BOOLEAN, this));
        addErrorOutputPort();
    }

    protected final void addErrorOutputPort() {
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error",
            "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    protected final void markInvalid(String error) {
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    protected final void markSuccess() {
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    protected final void putNullOutputs(String... outputIds) {
        for (String outputId : outputIds) {
            outputValues.put(outputId, null);
        }
    }

    protected final void putEmptyListOutputs(String... outputIds) {
        for (String outputId : outputIds) {
            outputValues.put(outputId, List.of());
        }
    }

    protected final void putIntOutputs(int value, String... outputIds) {
        for (String outputId : outputIds) {
            outputValues.put(outputId, value);
        }
    }

    /**
     * Optional Up Vector: unconnected → world Y; connected invalid/zero → null (fail closed).
     */
    static @Nullable Vector3d resolveOptionalUpVector(BaseNode node, String portId) {
        return resolveOptionalNonZeroDirection(node, portId, WORLD_UP);
    }

    /**
     * Optional Direction: unconnected → {@code defaultDirection}; connected invalid/zero → null.
     */
    static @Nullable Vector3d resolveOptionalDirection(
        BaseNode node,
        String portId,
        Vector3d defaultDirection
    ) {
        return resolveOptionalNonZeroDirection(node, portId, defaultDirection);
    }

    private static @Nullable Vector3d resolveOptionalNonZeroDirection(
        BaseNode node,
        String portId,
        Vector3d defaultDirection
    ) {
        Vector3d resolved = OptionalPortDrive.resolveOptionalVector(node, portId, defaultDirection);
        return VectorUtils.safeNormalize(resolved);
    }
}
