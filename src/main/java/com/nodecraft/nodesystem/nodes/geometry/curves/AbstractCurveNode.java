package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PlaneProjectionUtils;
import com.nodecraft.nodesystem.util.Curve;
import com.nodecraft.nodesystem.util.CurveInputUtils;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

abstract class AbstractCurveNode extends BaseNode {

    protected static final String OUTPUT_VALID_ID = "output_valid";
    protected static final String OUTPUT_ERROR_ID = "output_error";

    protected AbstractCurveNode(UUID id, String typeName) {
        super(id, typeName);
    }

    protected final void addErrorOutputPort() {
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error",
            "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    /** @deprecated Used only by legacy {@code CurveFrameAlongPathNode}. */
    @Deprecated
    protected final double readDoubleInput(String portId, double fallback) {
        Double value = resolveFiniteDouble(portId, fallback);
        return value == null ? fallback : value;
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

    protected final void putBooleanOutputs(boolean value, String... outputIds) {
        for (String outputId : outputIds) {
            outputValues.put(outputId, value);
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

    protected final @Nullable Integer resolveBoundedInteger(String portId, int fallback, int min, int max) {
        return CurveInputUtils.resolveOptionalBoundedExactInteger(this, portId, fallback, min, max);
    }

    protected final @Nullable Integer resolvePositiveInteger(String portId, int fallback) {
        return CurveInputUtils.resolveOptionalExactPositiveInteger(this, portId, fallback);
    }

    protected final @Nullable Double resolveFiniteDouble(String portId, double fallback) {
        return CurveInputUtils.resolveOptionalFiniteDouble(this, portId, fallback);
    }

    protected final @Nullable Double resolvePositiveDouble(String portId, double fallback) {
        return CurveInputUtils.resolveOptionalPositiveFiniteDouble(this, portId, fallback);
    }

    protected final @Nullable Double resolveNonNegativeDouble(String portId, double fallback) {
        return CurveInputUtils.resolveOptionalNonNegativeFiniteDouble(this, portId, fallback);
    }

    protected final void markDirtyIfChanged(@Nullable Object oldValue, @Nullable Object newValue) {
        if (!Objects.equals(oldValue, newValue)) {
            markDirty();
        }
    }

    protected final @Nullable Vector3d resolveInputPoint(@Nullable Object value) {
        return PlaneProjectionUtils.resolvePoint(value);
    }

    protected final @Nullable Vector3d resolveInputVector(@Nullable Object value) {
        return PlaneProjectionUtils.resolveVector(value);
    }

    protected final @Nullable List<Vector3d> resolvePathVertices(String pathPortId) {
        return PathUtils.resolvePath(inputValues.get(pathPortId));
    }

    protected final @Nullable List<Vector3d> resolvePathVertices(String pathPortId, boolean reverse) {
        List<Vector3d> points = resolvePathVertices(pathPortId);
        if (points == null || points.size() < 2) {
            return null;
        }
        if (!reverse) {
            return points;
        }
        List<Vector3d> copy = new ArrayList<>(points.size());
        for (Vector3d point : points) {
            copy.add(new Vector3d(point));
        }
        Collections.reverse(copy);
        return copy;
    }

    protected final Curve buildLinearCurve(List<Vec3d> points) {
        Curve curve = new Curve(Curve.CurveType.LINEAR, 2);
        for (Vec3d point : points) {
            curve.addControlPoint(point);
        }
        return curve;
    }

    protected final @Nullable PlaneProjectionUtils.Basis resolvePlaneBasis(@Nullable Object planeObj,
                                                                            @Nullable Object preferredAxisObj,
                                                                            PlaneData fallbackPlane) {
        PlaneData plane = planeObj instanceof PlaneData p ? p : fallbackPlane;
        return PlaneProjectionUtils.createBasis(plane, resolveInputVector(preferredAxisObj));
    }
}
