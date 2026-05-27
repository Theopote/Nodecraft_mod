package com.nodecraft.nodesystem.nodes.geometry.boolops;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.SdfGeometryData;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.SdfBoundsEstimator;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "geometry.boolean.sdf_to_geometry",
    displayName = "SDF To Geometry",
    description = "Wraps an SDF into GeometryData with explicit or auto-estimated sampling bounds for block baking",
    category = "geometry.boolean",
    order = 16
)
public class SdfToGeometryNode extends BaseNode {
    private static final String INPUT_SDF_ID = "input_sdf";
    private static final String INPUT_MIN_ID = "input_min";
    private static final String INPUT_MAX_ID = "input_max";
    private static final String INPUT_ISO_ID = "input_iso";
    private static final String INPUT_PADDING_ID = "input_padding";

    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_VALID_ID = "output_valid";

    @NodeProperty(
        displayName = "Auto Bounds",
        category = "Bounds",
        order = 1,
        description = "When enabled, estimates sampling bounds from the SDF when Min/Max are not connected"
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
        addInputPort(new BasePort(INPUT_MIN_ID, "Bounds Min", "Sampling minimum corner (optional if Auto Bounds is on)", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_MAX_ID, "Bounds Max", "Sampling maximum corner (optional if Auto Bounds is on)", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_ISO_ID, "Iso Value", "Iso-surface threshold (0 is standard SDF surface)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_PADDING_ID, "Padding", "Bounds padding override (optional)", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Geometry wrapper consumable by geometry voxelizer", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when SDF and bounds are valid", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Wraps an SDF into GeometryData with explicit or auto-estimated sampling bounds for block baking";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object sdfObj = inputValues.get(INPUT_SDF_ID);
        if (!(sdfObj instanceof SignedDistanceFieldData sdf)) {
            writeInvalid();
            return;
        }

        double iso = inputValues.get(INPUT_ISO_ID) instanceof Number n ? n.doubleValue() : 0.0d;
        Vector3d min = resolvePoint(inputValues.get(INPUT_MIN_ID));
        Vector3d max = resolvePoint(inputValues.get(INPUT_MAX_ID));

        if (!isValidBounds(min, max)) {
            if (!autoBounds) {
                writeInvalid();
                return;
            }
            double padding = resolvePadding();
            SdfBoundsEstimator.AxisAlignedBounds estimated = SdfBoundsEstimator.estimate(sdf);
            if (estimated == null || !estimated.isValid()) {
                writeInvalid();
                return;
            }
            SdfBoundsEstimator.AxisAlignedBounds expanded = estimated.expanded(padding);
            min = expanded.min();
            max = expanded.max();
        }

        if (!isValidBounds(min, max)) {
            writeInvalid();
            return;
        }

        GeometryData geometry = new SdfGeometryData(sdf, min, max, iso);
        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private double resolvePadding() {
        if (inputValues.get(INPUT_PADDING_ID) instanceof Number n) {
            return Math.max(0.0d, n.doubleValue());
        }
        return Math.max(0.0d, boundsPadding);
    }

    private static boolean isValidBounds(@Nullable Vector3d min, @Nullable Vector3d max) {
        return min != null && max != null && min.x <= max.x && min.y <= max.y && min.z <= max.z;
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    private Vector3d resolvePoint(Object value) {
        if (value instanceof PointData pointData) {
            return pointData.getPosition();
        }
        if (value instanceof Vector3d vector) {
            return new Vector3d(vector);
        }
        if (value instanceof BlockPos blockPos) {
            return new Vector3d(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        }
        return null;
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
        this.boundsPadding = Math.max(0.0d, boundsPadding);
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
            boundsPadding = Math.max(0.0d, value.doubleValue());
        }
    }
}
