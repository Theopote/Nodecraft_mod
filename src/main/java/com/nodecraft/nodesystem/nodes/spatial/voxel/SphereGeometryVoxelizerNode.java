package com.nodecraft.nodesystem.nodes.spatial.voxel;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "spatial.voxel.sphere_geometry_voxelizer",
    displayName = "Sphere Geometry To Blocks",
    description = "Voxelizes SphereData into Minecraft block coordinates",
    category = "spatial.voxel"
)
public class SphereGeometryVoxelizerNode extends BaseNode {

    @NodeProperty(displayName = "Fill Sphere", category = "Shape", order = 1)
    private boolean fillSphere = true;

    private static final String INPUT_SPHERE_GEOMETRY_ID = "input_sphere_geometry";
    private static final String OUTPUT_BLOCKS_ID = "output_blocks";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_COUNT_ID = "output_count";

    public SphereGeometryVoxelizerNode() {
        super(UUID.randomUUID(), "spatial.voxel.sphere_geometry_voxelizer");

        addInputPort(new BasePort(INPUT_SPHERE_GEOMETRY_ID, "Sphere Geometry", "Sphere geometry to voxelize", NodeDataType.SPHERE, this));
        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks", "Voxelized block coordinates", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", "Bounding region of the sphere", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Generated block count", NodeDataType.INTEGER, this));
    }

    @Override
    public String getDescription() {
        return "Voxelizes SphereData into Minecraft block coordinates";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_SPHERE_GEOMETRY_ID);
        BlockPosList blocks = new BlockPosList();
        RegionData region = null;

        if (geometryObj instanceof SphereData geometry) {
            blocks = GeometryVoxelizer.voxelizeSphere(geometry, fillSphere);
            region = GeometryVoxelizer.createBoundingRegion(geometry);
        }

        outputValues.put(OUTPUT_BLOCKS_ID, blocks);
        outputValues.put(OUTPUT_REGION_ID, region);
        outputValues.put(OUTPUT_COUNT_ID, blocks.size());
    }

    public boolean isFillSphere() {
        return fillSphere;
    }

    public void setFillSphere(boolean fillSphere) {
        if (this.fillSphere != fillSphere) {
            this.fillSphere = fillSphere;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("fillSphere", fillSphere);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("fillSphere") instanceof Boolean fillValue) {
            setFillSphere(fillValue);
        }
    }
}
