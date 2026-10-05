package com.nodecraft.nodesystem.nodes.geometry.boolops;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.util.SdfExpressionLimits;
import com.nodecraft.nodesystem.util.SdfInputUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

/**
 * Shared Valid/Error and connection-aware helpers for geometry.sdf nodes.
 */
abstract class AbstractSdfNode extends BaseNode {

    protected static final String OUTPUT_VALID_ID = "output_valid";
    protected static final String OUTPUT_ERROR_ID = "output_error";

    protected AbstractSdfNode(UUID id, String typeName) {
        super(id, typeName);
    }

    protected final void addValidAndErrorOutputs(String validDescription) {
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", validDescription, NodeDataType.BOOLEAN, this));
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

    protected final void putDoubleOutputs(double value, String... outputIds) {
        for (String outputId : outputIds) {
            outputValues.put(outputId, value);
        }
    }

    protected final boolean isPortConnected(String portId) {
        return SdfInputUtils.isConnected(this, portId);
    }

    protected final @Nullable Vector3d resolveOptionalPoint(String portId, @Nullable Vector3d fallback) {
        return SdfInputUtils.resolveOptionalPoint(this, portId, fallback);
    }

    protected final @Nullable Vector3d resolveOptionalVector(String portId, @Nullable Vector3d fallback) {
        return SdfInputUtils.resolveOptionalVector(this, portId, fallback);
    }

    protected final @Nullable Double resolveFiniteDouble(String portId, double fallback) {
        return SdfInputUtils.resolveOptionalFiniteDouble(this, portId, fallback);
    }

    protected final @Nullable Double resolvePositiveDouble(String portId, double fallback) {
        return SdfInputUtils.resolveOptionalPositiveFiniteDouble(this, portId, fallback);
    }

    protected final @Nullable Double resolveNonNegativeDouble(String portId, double fallback) {
        return SdfInputUtils.resolveOptionalNonNegativeFiniteDouble(this, portId, fallback);
    }

    protected final @Nullable Integer resolveOptionalInteger(String portId, int fallback) {
        return SdfInputUtils.resolveOptionalInteger(this, portId, fallback);
    }

    protected final boolean isSdfWithinBudget(@Nullable SignedDistanceFieldData sdf) {
        return SdfExpressionLimits.validate(sdf);
    }

    protected static String sdfBudgetError() {
        return SdfExpressionLimits.BUDGET_EXCEEDED;
    }
}
