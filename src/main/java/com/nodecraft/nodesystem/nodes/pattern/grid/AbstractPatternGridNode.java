package com.nodecraft.nodesystem.nodes.pattern.grid;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.PointUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import com.nodecraft.nodesystem.util.VectorUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

/**
 * Shared Valid/Error helpers for pattern.grid (Graph V80).
 */
abstract class AbstractPatternGridNode extends BaseNode {

    protected static final String OUTPUT_VALID_ID = "output_valid";
    protected static final String OUTPUT_ERROR_ID = "output_error";

    protected AbstractPatternGridNode(UUID id, String typeName) {
        super(id, typeName);
    }

    protected AbstractPatternGridNode(String typeName) {
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
     * Optional Origin: unconnected → world origin; connected invalid → null (fail closed).
     */
    static @Nullable Vector3d resolveOptionalOrigin(BaseNode node, String portId) {
        return OptionalPortDrive.resolveOptionalPoint(node, portId, new Vector3d());
    }

    /**
     * Optional Direction: unconnected → {@code defaultDirection}; connected invalid/zero → null.
     */
    static @Nullable Vector3d resolveOptionalNonZeroDirection(
        BaseNode node,
        String portId,
        Vector3d defaultDirection
    ) {
        Vector3d resolved = OptionalPortDrive.resolveOptionalVector(node, portId, defaultDirection);
        if (resolved == null || !VectorUtils.isNonZero(resolved)) {
            return null;
        }
        return new Vector3d(resolved).normalize();
    }

    /**
     * Validates every point is finite, then commits POINT_LIST + Count transactionally.
     *
     * @return true when outputs were written successfully
     */
    protected final boolean commitPointList(String pointsId, String countId, List<Vector3d> points) {
        for (Vector3d point : points) {
            if (!PointUtils.isFinite(point)) {
                markInvalid("Non-finite output point");
                putEmptyListOutputs(pointsId);
                putIntOutputs(0, countId);
                return false;
            }
        }
        outputValues.put(pointsId, SpatialValueResolver.toPointDataList(points));
        putIntOutputs(points.size(), countId);
        markSuccess();
        return true;
    }
}
