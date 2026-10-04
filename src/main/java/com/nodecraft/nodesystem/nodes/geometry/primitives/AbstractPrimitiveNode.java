package com.nodecraft.nodesystem.nodes.geometry.primitives;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.util.PrimitiveInputUtils;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleConsumer;

/**
 * Shared Valid/Error and connection-aware helpers for geometry.primitives.
 */
abstract class AbstractPrimitiveNode extends BaseNode {

    protected static final String OUTPUT_VALID_ID = "output_valid";
    protected static final String OUTPUT_ERROR_ID = "output_error";

    protected AbstractPrimitiveNode(UUID id, String typeName) {
        super(id, typeName);
    }

    protected AbstractPrimitiveNode(String typeName) {
        super(UUID.randomUUID(), typeName);
    }

    protected final void addValidAndErrorOutputs() {
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when geometry could be constructed", NodeDataType.BOOLEAN, this));
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

    protected final void putDoubleOutputs(double value, String... outputIds) {
        for (String outputId : outputIds) {
            outputValues.put(outputId, value);
        }
    }

    protected final void putIntOutputs(int value, String... outputIds) {
        for (String outputId : outputIds) {
            outputValues.put(outputId, value);
        }
    }

    protected final PathData pathFromLine(Vector3d start, Vector3d end) {
        return PathData.fromLine(new LineData(
            new Vec3d(start.x, start.y, start.z),
            new Vec3d(end.x, end.y, end.z)
        ));
    }

    protected final @Nullable Vector3d resolveOptionalPoint(String portId, @Nullable Vector3d fallback) {
        return PrimitiveInputUtils.resolveOptionalPoint(this, portId, fallback);
    }

    protected final @Nullable Vector3d resolveOptionalVector(String portId, @Nullable Vector3d fallback) {
        return PrimitiveInputUtils.resolveOptionalVector(this, portId, fallback);
    }

    protected final @Nullable Vector3d resolveOptionalUsableAxis(String portId, @Nullable Vector3d fallback) {
        return PrimitiveInputUtils.resolveOptionalUsableAxis(this, portId, fallback);
    }

    protected final @Nullable PlaneData resolveOptionalPlane(String portId, @Nullable PlaneData fallback) {
        return PrimitiveInputUtils.resolveOptionalPlane(this, portId, fallback);
    }

    protected final @Nullable Double resolveFiniteDouble(String portId, double fallback) {
        return PrimitiveInputUtils.resolveOptionalFiniteDouble(this, portId, fallback);
    }

    protected final @Nullable Double resolvePositiveDouble(String portId, double fallback) {
        return PrimitiveInputUtils.resolveOptionalPositiveFiniteDouble(this, portId, fallback);
    }

    protected final @Nullable Double resolveNonNegativeDouble(String portId, double fallback) {
        return PrimitiveInputUtils.resolveOptionalNonNegativeFiniteDouble(this, portId, fallback);
    }

    protected final @Nullable Matrix3d resolveOrientationOrEuler(
            String orientationPortId,
            double eulerXDeg,
            double eulerYDeg,
            double eulerZDeg
    ) {
        return PrimitiveInputUtils.resolveOrientationOrEuler(
            this, orientationPortId, eulerXDeg, eulerYDeg, eulerZDeg);
    }

    protected final boolean isPortConnected(String portId) {
        return PrimitiveInputUtils.isConnected(this, portId);
    }

    protected static void restoreFiniteDouble(Map<?, ?> map, String key, DoubleConsumer setter) {
        if (map.get(key) instanceof Double value && Double.isFinite(value)) {
            setter.accept(value);
        }
    }

    protected static void restoreInteger(Map<?, ?> map, String key, java.util.function.IntConsumer setter) {
        if (map.get(key) instanceof Integer value) {
            setter.accept(value);
        }
    }
}
