package com.nodecraft.nodesystem.nodes.output.execute;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.OctahedronGeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "output.execute.bake_octahedron_to_blocks",
    displayName = "Octahedron Geometry To Blocks",
    description = "Voxelizes OctahedronGeometryData into Minecraft block coordinates",
    category = "output.execute",
    order = 10
)
public class OctahedronGeometryVoxelizerNode extends BaseNode {

    @NodeProperty(displayName = "Fill Octahedron", category = "Shape", order = 1)
    private boolean fillOctahedron = true;

    private static final String INPUT_OCTAHEDRON_GEOMETRY_ID = "input_octahedron_geometry";
    private static final String OUTPUT_BLOCKS_ID = "output_blocks";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_COUNT_ID = "output_count";

    public OctahedronGeometryVoxelizerNode() {
        super(UUID.randomUUID(), "output.execute.bake_octahedron_to_blocks");

        addInputPort(new BasePort(INPUT_OCTAHEDRON_GEOMETRY_ID, "Octahedron Geometry", "Octahedron geometry to voxelize", NodeDataType.OCTAHEDRON_GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks", "Voxelized block coordinates", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", "Bounding region of the octahedron", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Generated block count", NodeDataType.INTEGER, this));
    }

    @Override
    public String getDescription() {
        return "Voxelizes OctahedronGeometryData into Minecraft block coordinates";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_OCTAHEDRON_GEOMETRY_ID);
        BlockPosList blocks = new BlockPosList();
        RegionData region = null;

        if (geometryObj instanceof OctahedronGeometryData geometry) {
            blocks = GeometryVoxelizer.voxelizeOctahedron(geometry, fillOctahedron);
            region = GeometryVoxelizer.createBoundingRegion(geometry);
        }

        outputValues.put(OUTPUT_BLOCKS_ID, blocks);
        outputValues.put(OUTPUT_REGION_ID, region);
        outputValues.put(OUTPUT_COUNT_ID, blocks.size());
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("fillOctahedron", fillOctahedron);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> map && map.get("fillOctahedron") instanceof Boolean value) {
            fillOctahedron = value;
        }
    }
}
