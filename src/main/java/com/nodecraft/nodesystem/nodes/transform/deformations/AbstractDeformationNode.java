package com.nodecraft.nodesystem.nodes.transform.deformations;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.util.PointUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

/**
 * Shared Valid/Error helpers for transform.deformations (Graph V78).
 */
abstract class AbstractDeformationNode extends BaseNode {

    protected static final String OUTPUT_VALID_ID = "output_valid";
    protected static final String OUTPUT_ERROR_ID = "output_error";
    protected static final String OUTPUT_POINTS_ID = "output_points";
    protected static final String OUTPUT_COUNT_ID = "output_count";

    protected AbstractDeformationNode(UUID id, String typeName) {
        super(id, typeName);
    }

    protected AbstractDeformationNode(String typeName) {
        super(UUID.randomUUID(), typeName);
    }

    protected final void addValidAndErrorOutputs() {
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when the deformation operation succeeded", NodeDataType.BOOLEAN, this));
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

    protected final void failPointList(String error) {
        markInvalid(error);
        putEmptyListOutputs(OUTPUT_POINTS_ID);
        putIntOutputs(0, OUTPUT_COUNT_ID);
    }

    /**
     * Validates every output point is finite, then commits the list transactionally.
     *
     * @return true when outputs were written successfully
     */
    protected final boolean commitPointList(List<Vector3d> points) {
        for (Vector3d point : points) {
            if (!PointUtils.isFinite(point)) {
                failPointList("Non-finite output point");
                return false;
            }
        }
        outputValues.put(OUTPUT_POINTS_ID, SpatialValueResolver.toPointDataList(points));
        outputValues.put(OUTPUT_COUNT_ID, points.size());
        markSuccess();
        return true;
    }
}
