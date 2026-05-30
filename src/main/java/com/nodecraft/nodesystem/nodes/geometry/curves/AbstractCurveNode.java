package com.nodecraft.nodesystem.nodes.geometry.curves;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PlaneProjectionUtils;
import com.nodecraft.nodesystem.util.Curve;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;
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
        return PlaneProjectionUtils.createBasis(plane, PlaneProjectionUtils.resolvePoint(preferredAxisObj));
    }
}