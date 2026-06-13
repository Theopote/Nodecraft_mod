package com.nodecraft.nodesystem.nodes.output.execute;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Generic geometry voxelizer for any supported GeometryData subtype.
 */
@NodeInfo(
    id = "output.execute.bake_geometry_to_blocks",
    displayName = "Bake Geometry To Blocks",
    description = "Bakes any supported geometry into Minecraft block coordinates for final execution",
    category = "output.execute",
    order = 2
)
public class GeometryToBlocksNode extends BaseNode {

    @NodeProperty(displayName = "Fill Geometry", category = "Shape", order = 1,
        description = "When disabled, only the outer shell is generated where supported")
    private boolean fillGeometry = true;

    private static final String INPUT_GEOMETRY_ID = "input_geometry";
    private static final String INPUT_GEOMETRY_TREE_ID = "input_geometry_tree";

    private static final String OUTPUT_BLOCKS_ID = "output_blocks";
    private static final String OUTPUT_BLOCKS_TREE_ID = "output_blocks_tree";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_COUNT_ID = "output_count";

    public GeometryToBlocksNode() {
        super(UUID.randomUUID(), "output.execute.bake_geometry_to_blocks");

        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Unified geometry input", NodeDataType.GEOMETRY, this));
        addInputPort(new BasePort(INPUT_GEOMETRY_TREE_ID, "Geometry Tree", "Optional tree of geometry values to voxelize per branch", NodeDataType.DATA_TREE, this));

        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks", "Voxelized block coordinates", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BLOCKS_TREE_ID, "Blocks Tree", "Voxelized blocks grouped by source geometry tree branch", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", "Bounding region of the geometry", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Generated block count", NodeDataType.INTEGER, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_GEOMETRY_ID);
        Object geometryTreeObj = inputValues.get(INPUT_GEOMETRY_TREE_ID);
        BlockPosList blocks = new BlockPosList();
        DataTreeData blocksTree = DataTreeData.empty();
        RegionData region = null;

        if (geometryTreeObj instanceof DataTreeData geometryTree && geometryTree.getBranchCount() > 0) {
            List<DataTreeData.Branch> blockBranches = new ArrayList<>();
            GeometryData firstGeometry = null;
            for (DataTreeData.Branch branch : geometryTree.getBranches()) {
                BlockPosList branchBlocks = new BlockPosList();
                for (Object item : branch.items()) {
                    if (item instanceof GeometryData geometry) {
                        if (firstGeometry == null) {
                            firstGeometry = geometry;
                        }
                        branchBlocks.addAll(GeometryVoxelizer.voxelize(geometry, fillGeometry).getPositions());
                    }
                }
                if (!branchBlocks.isEmpty()) {
                    blocks.addAll(branchBlocks.getPositions());
                    blockBranches.add(new DataTreeData.Branch(branch.path(), new ArrayList<>(branchBlocks.getPositions())));
                }
            }
            blocksTree = new DataTreeData(blockBranches);
            if (firstGeometry != null) {
                region = GeometryVoxelizer.createBoundingRegion(firstGeometry);
            }
        } else if (geometryObj instanceof GeometryData geometry) {
            blocks = GeometryVoxelizer.voxelize(geometry, fillGeometry);
            blocksTree = new DataTreeData(List.of(new DataTreeData.Branch(List.of(0), new ArrayList<Object>(blocks.getPositions()))));
            region = GeometryVoxelizer.createBoundingRegion(geometry);
        }

        outputValues.put(OUTPUT_BLOCKS_ID, blocks);
        outputValues.put(OUTPUT_BLOCKS_TREE_ID, blocksTree);
        outputValues.put(OUTPUT_REGION_ID, region);
        outputValues.put(OUTPUT_COUNT_ID, blocks.size());
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
