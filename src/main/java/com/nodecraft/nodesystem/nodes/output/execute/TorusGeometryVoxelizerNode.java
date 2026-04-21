package com.nodecraft.nodesystem.nodes.output.execute;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.TorusGeometryData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @deprecated Use GeometryToBlocksNode (output.execute.bake_geometry_to_blocks) for all GeometryData-based bake workflows.
 * Converts torus geometry into block coordinates.
 */
@NodeInfo(
    id = "output.execute.bake_torus_to_blocks",
    displayName = "Bake Torus To Blocks",
    description = "Bakes torus geometry into Minecraft block coordinates for final execution",
    category = "output.execute",
    order = 12
)
@Deprecated
public class TorusGeometryVoxelizerNode extends BaseNode {

    @NodeProperty(displayName = "Fill Torus", category = "Shape", order = 1,
        description = "When disabled, only the outer shell is generated")
    private boolean fillTorus = true;

    private static final String INPUT_TORUS_GEOMETRY_ID = "input_torus_geometry";
    private static final String OUTPUT_BLOCKS_ID = "output_blocks";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_COUNT_ID = "output_count";

    public TorusGeometryVoxelizerNode() {
        super(UUID.randomUUID(), "output.execute.bake_torus_to_blocks");

        addInputPort(new BasePort(INPUT_TORUS_GEOMETRY_ID, "Torus Geometry", "Torus geometry to voxelize", NodeDataType.TORUS_GEOMETRY, this));

        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks", "Voxelized block coordinates", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", "Bounding region of the torus", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Generated block count", NodeDataType.INTEGER, this));
    }

    @Override
    public String getDescription() {
        return "Voxelizes TorusGeometryData into Minecraft block coordinates";
    }

    @Override
    public String getDisplayName() {
        return "Torus Geometry To Blocks";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_TORUS_GEOMETRY_ID);
        BlockPosList blocks = new BlockPosList();
        RegionData region = null;

        if (geometryObj instanceof TorusGeometryData geometry) {
            blocks = GeometryVoxelizer.voxelizeTorus(geometry, fillTorus);
            region = GeometryVoxelizer.createBoundingRegion(geometry);
        }

        outputValues.put(OUTPUT_BLOCKS_ID, blocks);
        outputValues.put(OUTPUT_REGION_ID, region);
        outputValues.put(OUTPUT_COUNT_ID, blocks.size());
    }

    public boolean isFillTorus() {
        return fillTorus;
    }

    public void setFillTorus(boolean fillTorus) {
        if (this.fillTorus != fillTorus) {
            this.fillTorus = fillTorus;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("fillTorus", fillTorus);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }

        if (map.get("fillTorus") instanceof Boolean fillTorusValue) {
            setFillTorus(fillTorusValue);
        }
    }
}
