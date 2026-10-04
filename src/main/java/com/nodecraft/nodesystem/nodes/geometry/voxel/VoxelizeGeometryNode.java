package com.nodecraft.nodesystem.nodes.geometry.voxel;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockListUtils;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GeometryVoxelizationResult;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.VoxelInputUtils;
import com.nodecraft.nodesystem.util.VoxelizationStatus;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pure Geometry → BLOCK_LIST voxelization. Does not write the Minecraft world.
 * World writes go through Apply Changes after Material / Block State.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.voxel.voxelize_geometry",
    displayName = "Voxelize Geometry",
    description = "Converts geometry into Minecraft block coordinates (BLOCK_LIST). Pure conversion — does not write the world. Use Apply Changes to place.",
    category = "geometry.voxel",
    order = 0
)
public class VoxelizeGeometryNode extends BaseNode {

    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";
    private static final String OUTPUT_STATUS_ID = "output_status";

    @NodeProperty(displayName = "Fill Geometry", category = "Shape", order = 1,
        description = "When disabled, returns the boundary shell of the final voxelized geometry where supported")
    private boolean fillGeometry = true;

    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_GEOMETRY_TREE_ID = "input_geometry_tree";

    private static final String OUTPUT_BLOCKS_ID = "output_blocks";
    private static final String OUTPUT_BLOCKS_TREE_ID = "output_blocks_tree";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_COUNT_ID = "output_count";

    public VoxelizeGeometryNode() {
        super(UUID.randomUUID(), "geometry.voxel.voxelize_geometry");

        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Unified geometry input", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_TREE_ID, "Geometry Tree", "Optional tree of geometry values to voxelize per branch", NodeDataType.DATA_TREE, this));

        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks", "Voxelized block coordinates", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BLOCKS_TREE_ID, "Blocks Tree", "Voxelized blocks grouped by source geometry tree branch", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region",
            "Inclusive AABB of generated blocks (not the geometry scan envelope)", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Generated block count", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when voxelization succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_STATUS_ID, "Status", "Voxelization status (SUCCESS, OVER_BUDGET, …)", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Converts geometry into Minecraft block coordinates (BLOCK_LIST). Pure conversion — does not write the world.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        if (OptionalPortDrive.isConnected(this, INPUT_GEOMETRY_TREE_ID)) {
            processTreePath();
            return;
        }
        processSingleGeometryPath();
    }

    private void processTreePath() {
        Object treeObj = inputValues.get(INPUT_GEOMETRY_TREE_ID);
        if (!(treeObj instanceof DataTreeData tree)) {
            invalidate(VoxelizationStatus.UNSUPPORTED, "Geometry Tree is connected but invalid");
            return;
        }

        VoxelInputUtils.TreeVoxelizationOutcome outcome = VoxelInputUtils.voxelizeGeometryTree(tree, fillGeometry);
        if (!outcome.success()) {
            invalidate(outcome.status(), outcome.error());
            return;
        }

        writeSuccess(outcome.blocks(), outcome.blocksTree(), outcome.region());
    }

    private void processSingleGeometryPath() {
        Object geometryObj = inputValues.get(INPUT_GEOMETRY_ID);
        if (!(geometryObj instanceof GeometryData geometry)) {
            invalidate(VoxelizationStatus.UNSUPPORTED, "Geometry is missing or invalid");
            return;
        }

        GeometryVoxelizationResult result = GeometryVoxelizer.voxelizeStrict(geometry, fillGeometry);
        if (!result.success()) {
            invalidate(result.status(), result.error().isEmpty() ? "Voxelization failed" : result.error());
            return;
        }

        BlockPosList blocks = result.blocks();
        DataTreeData blocksTree = blocks.isEmpty()
            ? DataTreeData.empty()
            : new DataTreeData(List.of(new DataTreeData.Branch(List.of(0), new ArrayList<>(blocks.getPositions()))));
        RegionData region = BlockListUtils.regionFromOccupiedBlocks(blocks.getPositions());
        writeSuccess(blocks, blocksTree, region);
    }

    private void writeSuccess(BlockPosList blocks, DataTreeData blocksTree, @Nullable RegionData region) {
        outputValues.put(OUTPUT_BLOCKS_ID, blocks);
        outputValues.put(OUTPUT_BLOCKS_TREE_ID, blocksTree);
        outputValues.put(OUTPUT_REGION_ID, region);
        outputValues.put(OUTPUT_COUNT_ID, blocks.size());
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
        outputValues.put(OUTPUT_STATUS_ID, VoxelizationStatus.SUCCESS.name());
    }

    private void invalidate(VoxelizationStatus status, String error) {
        outputValues.put(OUTPUT_BLOCKS_ID, new BlockPosList());
        outputValues.put(OUTPUT_BLOCKS_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_REGION_ID, null);
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
        outputValues.put(OUTPUT_STATUS_ID, status.name());
    }

    public boolean isFillGeometry() {
        return fillGeometry;
    }

    public void setFillGeometry(boolean fillGeometry) {
        if (this.fillGeometry != fillGeometry) {
            this.fillGeometry = fillGeometry;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("fillGeometry", fillGeometry);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }

        if (map.get("fillGeometry") instanceof Boolean fillGeometryValue) {
            setFillGeometry(fillGeometryValue);
        }
    }
}
