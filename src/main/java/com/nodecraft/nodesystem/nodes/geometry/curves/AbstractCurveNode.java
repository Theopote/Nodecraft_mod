package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PlaneProjectionUtils;
import com.nodecraft.nodesystem.util.Curve;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

abstract class AbstractCurveNode extends BaseNode {

    protected AbstractCurveNode(UUID id, String typeName) {
        super(id, typeName);
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

    protected final void writeInvalidOutputs() {
        putNullOutputs("output_curve", "output_polyline");
        putEmptyListOutputs("output_points");
        putBooleanOutputs(false, "output_valid");
    }

    protected final int readIntInput(String portId, int fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    protected final double readDoubleInput(String portId, double fallback) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.doubleValue() : fallback;
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
