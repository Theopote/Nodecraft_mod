package com.nodecraft.nodesystem.nodes.spatial.voxel;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PrismGeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "spatial.voxel.prism_geometry_voxelizer",
    displayName = "Prism Geometry To Blocks",
    description = "Approximates PrismGeometryData into Minecraft block coordinates using a side lattice bridge",
    category = "spatial.voxel"
)
public class PrismGeometryVoxelizerNode extends BaseNode {

    @NodeProperty(displayName = "Fill Prism", category = "Shape", order = 1)
    private boolean fillPrism = true;

    private static final String INPUT_PRISM_GEOMETRY_ID = "input_prism_geometry";
    private static final String OUTPUT_BLOCKS_ID = "output_blocks";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_COUNT_ID = "output_count";

    public PrismGeometryVoxelizerNode() {
        super(UUID.randomUUID(), "spatial.voxel.prism_geometry_voxelizer");

        addInputPort(new BasePort(INPUT_PRISM_GEOMETRY_ID, "Prism Geometry", "Prism geometry to voxelize", NodeDataType.PRISM_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks", "Voxelized block coordinates", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", "Bounding region of the prism", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Generated block count", NodeDataType.INTEGER, this));
    }

    @Override
    public String getDescription() {
        return "Approximates PrismGeometryData into Minecraft block coordinates using a side lattice bridge";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_PRISM_GEOMETRY_ID);
        BlockPosList blocks = new BlockPosList();
        RegionData region = null;

        if (geometryObj instanceof PrismGeometryData geometry) {
            blocks = GeometryVoxelizer.voxelizePrism(geometry, fillPrism);
            region = GeometryVoxelizer.createBoundingRegion(geometry);
        }

        outputValues.put(OUTPUT_BLOCKS_ID, blocks);
        outputValues.put(OUTPUT_REGION_ID, region);
        outputValues.put(OUTPUT_COUNT_ID, blocks.size());
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("fillPrism", fillPrism);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("fillPrism") instanceof Boolean value) {
            fillPrism = value;
        }
    }
}
