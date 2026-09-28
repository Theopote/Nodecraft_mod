package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PathData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.datatypes.SurfaceStripData;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.SurfaceInputUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

abstract class AbstractSolidNode extends BaseNode {

    protected static final String OUTPUT_VALID_ID = "output_valid";
    protected static final String OUTPUT_ERROR_ID = "output_error";

    protected AbstractSolidNode(UUID id, String typeName) {
        super(id, typeName);
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

    protected final @Nullable Integer resolveBoundedInteger(String portId, int fallback, int min, int max) {
        return SurfaceInputUtils.resolveOptionalBoundedExactInteger(this, portId, fallback, min, max);
    }

    protected final @Nullable Integer resolvePositiveInteger(String portId, int fallback) {
        return SurfaceInputUtils.resolveOptionalExactPositiveInteger(this, portId, fallback);
    }

    protected final @Nullable Double resolveFiniteDouble(String portId, double fallback) {
        return SurfaceInputUtils.resolveOptionalFiniteDouble(this, portId, fallback);
    }

    protected final @Nullable Double resolvePositiveDouble(String portId, double fallback) {
        return SurfaceInputUtils.resolveOptionalPositiveFiniteDouble(this, portId, fallback);
    }

    protected final @Nullable Double resolveNonNegativeDouble(String portId, double fallback) {
        return SurfaceInputUtils.resolveOptionalNonNegativeFiniteDouble(this, portId, fallback);
    }

    protected final @Nullable Double resolveNormalizedU(String portId, double fallback) {
        return SurfaceInputUtils.resolveNormalizedU(this, portId, fallback);
    }

    protected final @Nullable Vector3d resolveDirection(String portId) {
        return SolidNodeUtils.resolveDirection(inputValues.get(portId));
    }

    protected final @Nullable List<Vector3d> resolvePathVertices(String pathPortId) {
        return PathUtils.resolvePath(inputValues.get(pathPortId));
    }

    protected final @Nullable String validateSurfaceStrip(@Nullable SurfaceStripData strip) {
        return com.nodecraft.nodesystem.util.SurfaceStripValidator.validate(strip);
    }

    protected final void markDirtyIfChanged(@Nullable Object oldValue, @Nullable Object newValue) {
        if (!Objects.equals(oldValue, newValue)) {
            markDirty();
        }
    }

    protected final @Nullable PlaneData resolvePlane(String portId, @Nullable PlaneData fallback) {
        return OptionalPortDrive.resolveOptionalPlane(this, portId, fallback);
    }

    protected final @Nullable PathData pathFromPolyline(@Nullable PolylineData polyline) {
        return polyline == null ? null : PathData.fromPolyline(polyline);
    }

    protected final @Nullable PathData pathFromLine(@Nullable LineData line) {
        return line == null ? null : PathData.fromLine(line);
    }
}
