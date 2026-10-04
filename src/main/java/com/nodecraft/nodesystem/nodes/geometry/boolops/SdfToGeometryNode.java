package com.nodecraft.nodesystem.nodes.geometry.boolops;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.SdfGeometryData;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.FrameUtils;
import com.nodecraft.nodesystem.util.SdfBoundsEstimator;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.boolean.sdf_to_geometry",
    displayName = "SDF To Geometry",
    description = "Wraps an SDF into GeometryData with explicit or auto-estimated sampling bounds for block baking",
    category = "geometry.sdf",
    order = 16
)
public class SdfToGeometryNode extends AbstractSdfNode {
    private static final String INPUT_SDF_ID = "input_sdf";
    private static final String INPUT_MIN_ID = "input_min";
    private static final String INPUT_MAX_ID = "input_max";
    private static final String INPUT_ISO_ID = "input_iso";
    private static final String INPUT_PADDING_ID = "input_padding";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";

    @NodeProperty(
        displayName = "Auto Bounds",
        category = "Bounds",
        order = 1,
        description = "When enabled and Min/Max are both unconnected, estimates sampling bounds from the SDF"
    )
    private boolean autoBounds = true;

    @NodeProperty(
        displayName = "Bounds Padding",
        category = "Bounds",
        order = 2,
        description = "Extra margin (blocks) added around auto-estimated bounds"
    )
    private double boundsPadding = 1.0d;

    public SdfToGeometryNode() {
        super(UUID.randomUUID(), "geometry.boolean.sdf_to_geometry");
        addInputPort(new BasePort(INPUT_SDF_ID, "SDF", "Signed distance field to wrap", NodeDataType.SDF, this));
        addInputPort(new BasePort(INPUT_MIN_ID, "Bounds Min",
            "Sampling minimum corner (optional if Auto Bounds is on)", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_MAX_ID, "Bounds Max",
            "Sampling maximum corner (optional if Auto Bounds is on)", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_ISO_ID, "Iso Value",
            "Iso-surface threshold (0 is standard SDF surface)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_PADDING_ID, "Padding",
            "Bounds padding override (optional)", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry",
            "Geometry wrapper consumable by geometry voxelizer", NodeDataType.GEOMETRY, this));
        addValidAndErrorOutputs("True when SDF and bounds are valid");
    }

    @Override
    public String getDescription() {
        return "Wraps an SDF into GeometryData with explicit or auto-estimated sampling bounds for block baking";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object sdfObj = inputValues.get(INPUT_SDF_ID);
        if (!(sdfObj instanceof SignedDistanceFieldData sdf)) {
            writeFailure("SDF input is required");
            return;
        }

        Double iso = resolveFiniteDouble(INPUT_ISO_ID, 0.0d);
        if (iso == null) {
            writeFailure("Iso Value must be finite");
            return;
        }

        boolean minConnected = isPortConnected(INPUT_MIN_ID);
        boolean maxConnected = isPortConnected(INPUT_MAX_ID);
        Vector3d min;
        Vector3d max;

        if (!minConnected && !maxConnected) {
            if (!autoBounds) {
                writeFailure("Bounds Min/Max are required when Auto Bounds is off");
                return;
            }
            Double padding = resolveNonNegativeDouble(INPUT_PADDING_ID, boundsPadding);
            if (padding == null) {
                writeFailure("Padding must be finite and >= 0");
                return;
            }
            SdfBoundsEstimator.AxisAlignedBounds estimated = SdfBoundsEstimator.estimate(sdf);
            if (estimated == null || !estimated.isValid()) {
                writeFailure("Failed to auto-estimate SDF bounds");
                return;
            }
            SdfBoundsEstimator.AxisAlignedBounds expanded = estimated.expanded(padding);
            min = expanded.min();
            max = expanded.max();
        } else if (minConnected && maxConnected) {
            min = resolveOptionalPoint(INPUT_MIN_ID, null);
            max = resolveOptionalPoint(INPUT_MAX_ID, null);
            if (min == null || max == null) {
                writeFailure("Connected Bounds Min/Max must be finite points");
                return;
            }
        } else {
            writeFailure("Bounds Min and Bounds Max must both be connected, or both unconnected");
            return;
        }

        if (!isValidBounds(min, max)) {
            writeFailure("Bounds Min must be <= Bounds Max on every axis");
            return;
        }

        GeometryData geometry = new SdfGeometryData(sdf, min, max, iso);
        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        markSuccess();
    }

    private static boolean isValidBounds(@Nullable Vector3d min, @Nullable Vector3d max) {
        return FrameUtils.isFinite(min) && FrameUtils.isFinite(max)
            && min.x <= max.x && min.y <= max.y && min.z <= max.z;
    }

    private void writeFailure(String error) {
        putNullOutputs(OUTPUT_GEOMETRY_ID);
        markInvalid(error);
    }

    public boolean isAutoBounds() {
        return autoBounds;
    }

    public void setAutoBounds(boolean autoBounds) {
        this.autoBounds = autoBounds;
    }

    public double getBoundsPadding() {
        return boundsPadding;
    }

    public void setBoundsPadding(double boundsPadding) {
        if (!Double.isFinite(boundsPadding) || boundsPadding < 0.0d) {
            return;
        }
        this.boundsPadding = boundsPadding;
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("autoBounds", autoBounds);
        state.put("boundsPadding", boundsPadding);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("autoBounds") instanceof Boolean value) {
            autoBounds = value;
        }
        if (map.get("boundsPadding") instanceof Number value) {
            setBoundsPadding(value.doubleValue());
        }
    }
}
