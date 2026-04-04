package com.nodecraft.nodesystem.nodes.spatial.generators;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoundingBoxData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Generates a filled or hollow box from a region or bounding box.
 */
@NodeInfo(
    id = "spatial.generators.region_box_blocks",
    displayName = "Region To Box (Blocks)",
    description = "Generates a filled or hollow box from a region or bounding box input",
    category = "spatial.generators"
)
public class RegionBoxBlocksNode extends BaseNode {

    @NodeProperty(displayName = "Fill Box", category = "Shape", order = 1,
        description = "When disabled, only the outer shell is generated")
    private boolean fillBox = true;

    @NodeProperty(displayName = "Prefer Bounding Box", category = "Input", order = 10,
        description = "When enabled, the bounding box input has priority over the region input")
    private boolean preferBoundingBox = true;

    private static final String INPUT_REGION_ID = "input_region";
    private static final String INPUT_BOUNDING_BOX_ID = "input_bounding_box";

    private static final String OUTPUT_BLOCKS_ID = "output_blocks";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_MIN_CORNER_ID = "output_min_corner";
    private static final String OUTPUT_MAX_CORNER_ID = "output_max_corner";
    private static final String OUTPUT_COUNT_ID = "output_count";

    public RegionBoxBlocksNode() {
        super(UUID.randomUUID(), "spatial.generators.region_box_blocks");

        addInputPort(new BasePort(INPUT_REGION_ID, "Region", "Region used to define the box", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_BOUNDING_BOX_ID, "Bounding Box", "Bounding box data used to define the box", NodeDataType.BOUNDING_BOX, this));

        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks", "Blocks forming the box", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", "Resolved region", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_MIN_CORNER_ID, "Min Corner", "Minimum corner of the box", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_MAX_CORNER_ID, "Max Corner", "Maximum corner of the box", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Generated block count", NodeDataType.INTEGER, this));
    }

    @Override
    public String getDescription() {
        return "Generates a filled or hollow box from a region or bounding box input";
    }

    @Override
    public String getDisplayName() {
        return "Region To Box (Blocks)";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        RegionData region = resolveRegion(
            inputValues.get(INPUT_REGION_ID),
            inputValues.get(INPUT_BOUNDING_BOX_ID)
        );

        BlockPosList blocks = new BlockPosList();
        BlockPos minCorner = null;
        BlockPos maxCorner = null;

        if (region != null && region.isComplete()) {
            minCorner = region.getMinCorner();
            maxCorner = region.getMaxCorner();

            if (minCorner != null && maxCorner != null) {
                populateBlocks(blocks, minCorner, maxCorner);
            }
        }

        outputValues.put(OUTPUT_BLOCKS_ID, blocks);
        outputValues.put(OUTPUT_REGION_ID, region);
        outputValues.put(OUTPUT_MIN_CORNER_ID, minCorner);
        outputValues.put(OUTPUT_MAX_CORNER_ID, maxCorner);
        outputValues.put(OUTPUT_COUNT_ID, blocks.size());
    }

    private RegionData resolveRegion(Object regionObj, Object boundingBoxObj) {
        if (preferBoundingBox) {
            RegionData fromBoundingBox = regionFromBoundingBox(boundingBoxObj);
            if (fromBoundingBox != null) {
                return fromBoundingBox;
            }
            return regionFromRegion(regionObj);
        }

        RegionData fromRegion = regionFromRegion(regionObj);
        if (fromRegion != null) {
            return fromRegion;
        }
        return regionFromBoundingBox(boundingBoxObj);
    }

    private RegionData regionFromRegion(Object regionObj) {
        if (regionObj instanceof RegionData region && region.isComplete()) {
            return new RegionData(region.getMinCorner(), region.getMaxCorner());
        }
        return null;
    }

    private RegionData regionFromBoundingBox(Object boundingBoxObj) {
        if (!(boundingBoxObj instanceof BoundingBoxData boundingBox)) {
            return null;
        }

        Vector3d min = boundingBox.getMin();
        Vector3d max = boundingBox.getMax();

        BlockPos minCorner = BlockPos.ofFloored(min.x, min.y, min.z);
        BlockPos maxCorner = BlockPos.ofFloored(max.x - 1.0e-9d, max.y - 1.0e-9d, max.z - 1.0e-9d);
        return new RegionData(minCorner, maxCorner);
    }

    private void populateBlocks(BlockPosList blocks, BlockPos minCorner, BlockPos maxCorner) {
        for (int x = minCorner.getX(); x <= maxCorner.getX(); x++) {
            for (int y = minCorner.getY(); y <= maxCorner.getY(); y++) {
                for (int z = minCorner.getZ(); z <= maxCorner.getZ(); z++) {
                    if (fillBox || isShellBlock(x, y, z, minCorner, maxCorner)) {
                        blocks.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
    }

    private boolean isShellBlock(int x, int y, int z, BlockPos minCorner, BlockPos maxCorner) {
        return x == minCorner.getX() || x == maxCorner.getX()
            || y == minCorner.getY() || y == maxCorner.getY()
            || z == minCorner.getZ() || z == maxCorner.getZ();
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

    public boolean isPreferBoundingBox() {
        return preferBoundingBox;
    }

    public void setPreferBoundingBox(boolean preferBoundingBox) {
        if (this.preferBoundingBox != preferBoundingBox) {
            this.preferBoundingBox = preferBoundingBox;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("fillBox", fillBox);
        state.put("preferBoundingBox", preferBoundingBox);
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
        if (map.get("preferBoundingBox") instanceof Boolean preferBoundingBoxValue) {
            setPreferBoundingBox(preferBoundingBoxValue);
        }
    }
}
