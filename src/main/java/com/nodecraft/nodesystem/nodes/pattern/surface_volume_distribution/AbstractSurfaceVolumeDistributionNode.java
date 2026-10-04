package com.nodecraft.nodesystem.nodes.pattern.surface_volume_distribution;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.PointUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import com.nodecraft.nodesystem.util.VectorUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

/**
 * Shared Valid/Error/Complete helpers for pattern.surface_volume_distribution.
 */
abstract class AbstractSurfaceVolumeDistributionNode extends BaseNode {

    protected static final String OUTPUT_VALID_ID = "output_valid";
    protected static final String OUTPUT_ERROR_ID = "output_error";
    protected static final String OUTPUT_COMPLETE_ID = "output_complete";

    protected AbstractSurfaceVolumeDistributionNode(UUID id, String typeName) {
        super(id, typeName);
    }

    protected AbstractSurfaceVolumeDistributionNode(String typeName) {
        super(UUID.randomUUID(), typeName);
    }

    protected final void addValidErrorAndCompleteOutputs() {
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when the sampling operation executed legally", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error",
            "Failure reason when Valid is false", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_COMPLETE_ID, "Complete",
            "True when emitted Count equals the requested Count / Target Count", NodeDataType.BOOLEAN, this));
    }

    protected final void markInvalid(String error) {
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
        outputValues.put(OUTPUT_COMPLETE_ID, false);
    }

    protected final void markSuccess(boolean complete) {
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
        outputValues.put(OUTPUT_COMPLETE_ID, complete);
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
     * Resolves Count/Target Count: unconnected → property; connected invalid → null;
     * {@code <= 0} or {@code > MAX_LAYOUT_INSTANCES} → null with reason via {@link #failCount}.
     */
    protected final @Nullable Integer resolveLayoutCount(String portId, int propertyFallback) {
        Integer resolved = OptionalPortDrive.resolveOptionalInteger(this, portId, propertyFallback);
        if (resolved == null) {
            return null;
        }
        if (resolved <= 0 || resolved > GenerationLimits.MAX_LAYOUT_INSTANCES) {
            return null;
        }
        return resolved;
    }

    protected final String countFailureReason(String portId, int propertyFallback) {
        Integer resolved = OptionalPortDrive.resolveOptionalInteger(this, portId, propertyFallback);
        if (resolved == null) {
            return "Count connected but invalid";
        }
        if (resolved <= 0) {
            return "Count must be >= 1";
        }
        if (resolved > GenerationLimits.MAX_LAYOUT_INSTANCES) {
            return "Count exceeds MAX_LAYOUT_INSTANCES";
        }
        return "Invalid Count";
    }

    protected final @Nullable Integer resolveSeed(String portId, int propertyFallback) {
        return OptionalPortDrive.resolveOptionalInteger(this, portId, propertyFallback);
    }

    protected final @Nullable Double resolveNonNegativeFinite(
        String portId,
        double propertyFallback
    ) {
        Double resolved = OptionalPortDrive.resolveOptionalDouble(this, portId, propertyFallback);
        if (resolved == null || !Double.isFinite(resolved) || resolved < 0.0d) {
            return null;
        }
        return resolved;
    }

    protected final @Nullable Double resolveMinDistance(String portId, double propertyFallback) {
        Double resolved = resolveNonNegativeFinite(portId, propertyFallback);
        if (resolved == null || !VectorUtils.isFiniteSquaredDistance(resolved)) {
            return null;
        }
        return resolved;
    }

    protected final @Nullable Double resolvePositiveFinite(
        String portId,
        double propertyFallback
    ) {
        Double resolved = OptionalPortDrive.resolveOptionalDouble(this, portId, propertyFallback);
        if (resolved == null || !Double.isFinite(resolved) || resolved <= 0.0d) {
            return null;
        }
        return resolved;
    }

    /**
     * Commits POINT_LIST + Count with finite checks.
     *
     * @return true when outputs were written successfully
     */
    protected final boolean commitPointList(
        String pointsId,
        String countId,
        List<Vector3d> points,
        boolean complete
    ) {
        if (points == null) {
            markInvalid("Missing point list");
            putEmptyListOutputs(pointsId);
            putIntOutputs(0, countId);
            return false;
        }
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
        markSuccess(complete);
        return true;
    }

    /**
     * Commits aligned POINT_LIST + VECTOR_LIST (normals) + Count.
     */
    protected final boolean commitPointNormalLists(
        String pointsId,
        String normalsId,
        String countId,
        List<Vector3d> points,
        List<Vector3d> normals,
        boolean complete
    ) {
        if (points == null || normals == null || points.size() != normals.size()) {
            markInvalid("Points and normals are misaligned");
            putEmptyListOutputs(pointsId, normalsId);
            putIntOutputs(0, countId);
            return false;
        }
        for (Vector3d point : points) {
            if (!PointUtils.isFinite(point)) {
                markInvalid("Non-finite output point");
                putEmptyListOutputs(pointsId, normalsId);
                putIntOutputs(0, countId);
                return false;
            }
        }
        for (Vector3d normal : normals) {
            if (normal == null || !VectorUtils.isFinite(normal) || !VectorUtils.isNonZero(normal)) {
                markInvalid("Degenerate or non-finite normal");
                putEmptyListOutputs(pointsId, normalsId);
                putIntOutputs(0, countId);
                return false;
            }
            double length = VectorUtils.safeLength(normal);
            if (!Double.isFinite(length) || Math.abs(length - 1.0d) > VectorUtils.EPS) {
                markInvalid("Normal must be unit length");
                putEmptyListOutputs(pointsId, normalsId);
                putIntOutputs(0, countId);
                return false;
            }
        }
        outputValues.put(pointsId, SpatialValueResolver.toPointDataList(points));
        outputValues.put(normalsId, VectorUtils.toVectorPortList(normals));
        putIntOutputs(points.size(), countId);
        markSuccess(complete);
        return true;
    }
}
