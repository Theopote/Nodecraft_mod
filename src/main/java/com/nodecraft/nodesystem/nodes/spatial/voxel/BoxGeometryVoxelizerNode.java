package com.nodecraft.nodesystem.nodes.spatial.voxel;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.BoxBlockGenerator;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Converts box geometry into block coordinates.
 */
@NodeInfo(
    id = "spatial.voxel.box_geometry_voxelizer",
    displayName = "Box Geometry To Blocks",
    description = "Voxelizes BoxGeometryData into Minecraft block coordinates",
    category = "spatial.voxel"
)
public class BoxGeometryVoxelizerNode extends BaseNode {

    @NodeProperty(displayName = "Fill Box", category = "Shape", order = 1,
        description = "When disabled, only the outer shell is generated")
    private boolean fillBox = true;

    private static final String INPUT_BOX_GEOMETRY_ID = "input_box_geometry";

    private static final String OUTPUT_BLOCKS_ID = "output_blocks";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_COUNT_ID = "output_count";

    public BoxGeometryVoxelizerNode() {
        super(UUID.randomUUID(), "spatial.voxel.box_geometry_voxelizer");

        addInputPort(new BasePort(INPUT_BOX_GEOMETRY_ID, "Box Geometry", "Box geometry to voxelize", NodeDataType.BOX_GEOMETRY, this));

        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks", "Voxelized block coordinates", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", "Bounding region of the voxelized geometry", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Generated block count", NodeDataType.INTEGER, this));
    }

    @Override
    public String getDescription() {
        return "Voxelizes BoxGeometryData into Minecraft block coordinates";
    }

    @Override
    public String getDisplayName() {
        return "Box Geometry To Blocks";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_BOX_GEOMETRY_ID);
        BlockPosList blocks = new BlockPosList();
        RegionData region = null;

        if (geometryObj instanceof BoxGeometryData geometry) {
            region = geometry.isOriented()
                ? BoxBlockGenerator.createOrientedBoundingRegion(
                    geometry.getCenter(),
                    geometry.getHalfExtents(),
                    geometry.getOrientationMatrix()
                )
                : createAxisAlignedRegion(geometry);

            if (region != null && region.isComplete()) {
                BlockPos minCorner = region.getMinCorner();
                BlockPos maxCorner = region.getMaxCorner();

                if (minCorner != null && maxCorner != null) {
                    if (geometry.isOriented()) {
                        BoxBlockGenerator.populateOrientedBox(
                            blocks,
                            minCorner,
                            maxCorner,
                            geometry.getCenter(),
                            geometry.getHalfExtents(),
                            geometry.getOrientationMatrix(),
                            fillBox
                        );
                    } else {
                        BoxBlockGenerator.populateAxisAlignedBox(blocks, minCorner, maxCorner, fillBox);
                    }
                }
            }
        }

        outputValues.put(OUTPUT_BLOCKS_ID, blocks);
        outputValues.put(OUTPUT_REGION_ID, region);
        outputValues.put(OUTPUT_COUNT_ID, blocks.size());
    }

    private RegionData createAxisAlignedRegion(BoxGeometryData geometry) {
        Vector3d center = geometry.getCenter();
        Vector3d halfExtents = geometry.getHalfExtents();

        BlockPos minCorner = BlockPos.ofFloored(
            center.x - halfExtents.x,
            center.y - halfExtents.y,
            center.z - halfExtents.z
        );
        BlockPos maxCorner = BlockPos.ofFloored(
            center.x + halfExtents.x,
            center.y + halfExtents.y,
            center.z + halfExtents.z
        );
        return new RegionData(minCorner, maxCorner);
    }

    public boolean isFillBox() {
        return fillBox;
    }

    public void setFillBox(boolean fillBox) {
        if (this.fillBox != fillBox) {
            this.fillBox = fillBox;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("fillBox", fillBox);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }

        if (map.get("fillBox") instanceof Boolean fillBoxValue) {
            setFillBox(fillBoxValue);
        }
    }
}
