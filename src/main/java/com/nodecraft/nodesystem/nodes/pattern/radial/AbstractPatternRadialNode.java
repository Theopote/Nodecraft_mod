package com.nodecraft.nodesystem.nodes.pattern.radial;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.util.FrameUtils;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.PointUtils;
import com.nodecraft.nodesystem.util.RadialFrameUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import com.nodecraft.nodesystem.util.VectorUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

/**
 * Shared Valid/Error helpers for pattern.radial (Graph V81).
 */
abstract class AbstractPatternRadialNode extends BaseNode {

    protected static final String OUTPUT_VALID_ID = "output_valid";
    protected static final String OUTPUT_ERROR_ID = "output_error";

    protected AbstractPatternRadialNode(UUID id, String typeName) {
        super(id, typeName);
    }

    protected AbstractPatternRadialNode(String typeName) {
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
     * Optional Origin/Center: unconnected → world origin; connected invalid → null.
     */
    static @Nullable Vector3d resolveOptionalOrigin(BaseNode node, String portId) {
        return OptionalPortDrive.resolveOptionalPoint(node, portId, new Vector3d());
    }

    /**
     * Optional Direction/Axis: unconnected → {@code defaultDirection}; connected invalid/zero → null.
     */
    static @Nullable Vector3d resolveOptionalNonZeroDirection(
        BaseNode node,
        String portId,
        Vector3d defaultDirection
    ) {
        Vector3d resolved = OptionalPortDrive.resolveOptionalVector(node, portId, defaultDirection);
        return VectorUtils.safeNormalize(resolved);
    }

    /**
     * Transactional commit for aligned POINT_LIST / VECTOR_LIST / FRAME_LIST layout outputs.
     *
     * @return true when outputs were written successfully
     */
    protected final boolean commitAlignedLayout(
        String pointsId,
        String tangentsId,
        String framesId,
        String countId,
        List<Vector3d> points,
        List<Vector3d> tangents,
        List<FrameData> frames
    ) {
        if (points == null || tangents == null || frames == null
                || points.size() != tangents.size()
                || points.size() != frames.size()) {
            failAlignedLayout(pointsId, tangentsId, framesId, countId, "Layout outputs are misaligned");
            return false;
        }
        for (Vector3d point : points) {
            if (!PointUtils.isFinite(point)) {
                failAlignedLayout(pointsId, tangentsId, framesId, countId, "Non-finite output point");
                return false;
            }
        }
        for (Vector3d tangent : tangents) {
            if (RadialFrameUtils.normalizeTangent(tangent) == null) {
                failAlignedLayout(pointsId, tangentsId, framesId, countId, "Degenerate or non-finite tangent");
                return false;
            }
        }
        for (FrameData frame : frames) {
            if (frame == null
                    || !FrameUtils.isFinite(frame.getOrigin())
                    || !FrameUtils.isUsableAxis(frame.getXAxis())
                    || !FrameUtils.isUsableAxis(frame.getYAxis())
                    || !FrameUtils.isUsableAxis(frame.getZAxis())) {
                failAlignedLayout(pointsId, tangentsId, framesId, countId, "Invalid or non-finite frame");
                return false;
            }
        }

        outputValues.put(pointsId, SpatialValueResolver.toPointDataList(points));
        outputValues.put(tangentsId, List.copyOf(tangents));
        outputValues.put(framesId, List.copyOf(frames));
        putIntOutputs(points.size(), countId);
        markSuccess();
        return true;
    }

    private void failAlignedLayout(
        String pointsId,
        String tangentsId,
        String framesId,
        String countId,
        String error
    ) {
        markInvalid(error);
        putEmptyListOutputs(pointsId, tangentsId, framesId);
        putIntOutputs(0, countId);
    }
}
