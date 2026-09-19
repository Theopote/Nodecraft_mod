package com.nodecraft.nodesystem.nodes.output.execute;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.SdfGeometryData;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import com.nodecraft.nodesystem.util.SdfBoundsEstimator;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.WORLD_WRITE,
    id = "output.execute.sdf_to_blocks",
    displayName = "SDF To Blocks",
    description = "Voxelizes a signed distance field directly into Minecraft block coordinates",
    category = "output.execute",
    order = 3
)
public class SdfToBlocksNode extends BaseNode {
    private static final String INPUT_SDF_ID = "input_sdf";
    private static final String INPUT_MIN_ID = "input_min";
    private static final String INPUT_MAX_ID = "input_max";
    private static final String INPUT_ISO_ID = "input_iso";
    private static final String INPUT_PADDING_ID = "input_padding";

    private static final String OUTPUT_BLOCKS_ID = "output_blocks";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    @NodeProperty(
        displayName = "Fill Solid",
        category = "Voxelize",
        order = 1,
        description = "When disabled, only boundary blocks are emitted"
    )
    private boolean fillSolid = true;

    @NodeProperty(
        displayName = "Auto Bounds",
        category = "Bounds",
        order = 2,
        description = "Estimates sampling bounds from known SDF primitives when explicit bounds are not connected"
    )
    private boolean autoBounds = true;

    @NodeProperty(
        displayName = "Bounds Padding",
        category = "Bounds",
        order = 3,
        description = "Extra margin added around auto-estimated bounds"
    )
    private double boundsPadding = 1.0d;

    public SdfToBlocksNode() {
        super(UUID.randomUUID(), "output.execute.sdf_to_blocks");

        addInputPort(new BasePort(INPUT_SDF_ID, "SDF", "Signed distance field to voxelize", NodeDataType.SDF, this));
        addInputPort(new BasePort(INPUT_MIN_ID, "Bounds Min", "Sampling minimum corner (optional if Auto Bounds is on)", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_MAX_ID, "Bounds Max", "Sampling maximum corner (optional if Auto Bounds is on)", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_ISO_ID, "Iso Value", "Iso-surface threshold (0 is standard SDF surface)", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_PADDING_ID, "Padding", "Bounds padding override (optional)", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks", "Voxelized block coordinates", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", "Sampling region used for voxelization", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "SDF geometry wrapper used during voxelization", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Generated block count", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when SDF and bounds are valid", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object sdfObj = inputValues.get(INPUT_SDF_ID);
        if (!(sdfObj instanceof SignedDistanceFieldData sdf)) {
            writeInvalid();
            return;
        }

        Vector3d min = SpatialValueResolver.resolveVector3d(inputValues.get(INPUT_MIN_ID));
        Vector3d max = SpatialValueResolver.resolveVector3d(inputValues.get(INPUT_MAX_ID));
        if (!isValidBounds(min, max)) {
            if (!autoBounds) {
                writeInvalid();
                return;
            }
            SdfBoundsEstimator.AxisAlignedBounds estimated = SdfBoundsEstimator.estimate(sdf);
            if (estimated == null || !estimated.isValid()) {
                writeInvalid();
                return;
            }
            SdfBoundsEstimator.AxisAlignedBounds expanded = estimated.expanded(resolvePadding());
            min = expanded.min();
            max = expanded.max();
        }

        if (!isValidBounds(min, max)) {
            writeInvalid();
            return;
        }

        double iso = inputValues.get(INPUT_ISO_ID) instanceof Number n ? n.doubleValue() : 0.0d;
        SdfGeometryData geometry = new SdfGeometryData(sdf, min, max, iso);
        BlockPosList blocks = GeometryVoxelizer.voxelizeSdfGeometry(geometry, fillSolid);
        RegionData region = GeometryVoxelizer.createBoundingRegion(geometry);

        outputValues.put(OUTPUT_BLOCKS_ID, blocks);
        outputValues.put(OUTPUT_REGION_ID, region);
        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_COUNT_ID, blocks.size());
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
        outputValues.put(OUTPUT_BLOCKS_ID, new BlockPosList());
        outputValues.put(OUTPUT_REGION_ID, null);
        outputValues.put(OUTPUT_GEOMETRY_ID, null);
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    public boolean isFillSolid() {
        return fillSolid;
    }

    public void setFillSolid(boolean fillSolid) {
        this.fillSolid = fillSolid;
        markDirty();
    }

    public boolean isAutoBounds() {
        return autoBounds;
    }

    public void setAutoBounds(boolean autoBounds) {
        this.autoBounds = autoBounds;
        markDirty();
    }

    public double getBoundsPadding() {
        return boundsPadding;
    }

    public void setBoundsPadding(double boundsPadding) {
        this.boundsPadding = Math.max(0.0d, boundsPadding);
        markDirty();
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("fillSolid", fillSolid);
        state.put("autoBounds", autoBounds);
        state.put("boundsPadding", boundsPadding);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("fillSolid") instanceof Boolean value) {
            fillSolid = value;
        }
        if (map.get("autoBounds") instanceof Boolean value) {
            autoBounds = value;
        }
        if (map.get("boundsPadding") instanceof Number value) {
            boundsPadding = Math.max(0.0d, value.doubleValue());
        }
    }
}
