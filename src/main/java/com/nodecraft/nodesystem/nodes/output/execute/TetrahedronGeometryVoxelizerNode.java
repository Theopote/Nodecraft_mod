package com.nodecraft.nodesystem.nodes.output.execute;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.TetrahedronGeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    id = "output.execute.bake_tetrahedron_to_blocks",
    displayName = "Tetrahedron Geometry To Blocks",
    description = "Voxelizes TetrahedronGeometryData into Minecraft block coordinates",
    category = "output.execute",
    order = 11
)
public class TetrahedronGeometryVoxelizerNode extends BaseNode {

    private static final String INPUT_TETRAHEDRON_GEOMETRY_ID = "input_tetrahedron_geometry";
    private static final String OUTPUT_BLOCKS_ID = "output_blocks";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_COUNT_ID = "output_count";

    public TetrahedronGeometryVoxelizerNode() {
        super(UUID.randomUUID(), "output.execute.bake_tetrahedron_to_blocks");

        addInputPort(new BasePort(INPUT_TETRAHEDRON_GEOMETRY_ID, "Tetrahedron Geometry", "Tetrahedron geometry to voxelize", NodeDataType.TETRAHEDRON_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks", "Voxelized block coordinates", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", "Bounding region of the tetrahedron", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Generated block count", NodeDataType.INTEGER, this));
    }

    @Override
    public String getDescription() {
        return "Voxelizes TetrahedronGeometryData into Minecraft block coordinates";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_TETRAHEDRON_GEOMETRY_ID);
        BlockPosList blocks = new BlockPosList();
        RegionData region = null;

        if (geometryObj instanceof TetrahedronGeometryData geometry) {
            blocks = GeometryVoxelizer.voxelizeTetrahedron(geometry, true);
            region = GeometryVoxelizer.createBoundingRegion(geometry);
        }

        outputValues.put(OUTPUT_BLOCKS_ID, blocks);
        outputValues.put(OUTPUT_REGION_ID, region);
        outputValues.put(OUTPUT_COUNT_ID, blocks.size());
    }
}
