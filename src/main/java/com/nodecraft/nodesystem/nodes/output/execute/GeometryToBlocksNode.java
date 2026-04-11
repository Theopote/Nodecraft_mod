package com.nodecraft.nodesystem.nodes.output.execute;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Generic geometry voxelizer for any supported GeometryData subtype.
 */
@NodeInfo(
    id = "output.execute.bake_geometry_to_blocks",
    displayName = "Geometry To Blocks",
    description = "Voxelizes any supported geometry into Minecraft block coordinates",
    category = "output.execute"
)
public class GeometryToBlocksNode extends BaseNode {

    @NodeProperty(displayName = "Fill Geometry", category = "Shape", order = 1,
        description = "When disabled, only the outer shell is generated where supported")
    private boolean fillGeometry = true;

    private static final String INPUT_GEOMETRY_ID = "input_geometry";

    private static final String OUTPUT_BLOCKS_ID = "output_blocks";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_COUNT_ID = "output_count";

    public GeometryToBlocksNode() {
        super(UUID.randomUUID(), "output.execute.bake_geometry_to_blocks");

        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Unified geometry input", NodeDataType.GEOMETRY, this));

        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks", "Voxelized block coordinates", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", "Bounding region of the geometry", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Generated block count", NodeDataType.INTEGER, this));
    }

    @Override
    public String getDescription() {
        return "Voxelizes any supported geometry into Minecraft block coordinates";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_GEOMETRY_ID);
        BlockPosList blocks = new BlockPosList();
        RegionData region = null;

        if (geometryObj instanceof GeometryData geometry) {
            blocks = GeometryVoxelizer.voxelize(geometry, fillGeometry);
            region = GeometryVoxelizer.createBoundingRegion(geometry);
        }

        outputValues.put(OUTPUT_BLOCKS_ID, blocks);
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
