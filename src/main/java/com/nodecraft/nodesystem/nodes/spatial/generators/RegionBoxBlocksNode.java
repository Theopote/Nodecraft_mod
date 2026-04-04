package com.nodecraft.nodesystem.nodes.spatial.generators;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoundingBoxData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BoxBlockGenerator;
import com.nodecraft.nodesystem.util.BlockPosList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
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
    private static final String OUTPUT_GEOMETRY_ID = "output_geometry";
    private static final String OUTPUT_BOX_GEOMETRY_ID = "output_box_geometry";

    public RegionBoxBlocksNode() {
        super(UUID.randomUUID(), "spatial.generators.region_box_blocks");

        addInputPort(new BasePort(INPUT_REGION_ID, "Region", "Region used to define the box", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_BOUNDING_BOX_ID, "Bounding Box", "Bounding box data used to define the box", NodeDataType.BOUNDING_BOX, this));

        addOutputPort(new BasePort(OUTPUT_BLOCKS_ID, "Blocks", "Blocks forming the box", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", "Resolved region", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_MIN_CORNER_ID, "Min Corner", "Minimum corner of the box", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_MAX_CORNER_ID, "Max Corner", "Maximum corner of the box", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Generated block count", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_GEOMETRY_ID, "Geometry", "Unified geometry output", NodeDataType.GEOMETRY, this));
        addOutputPort(new BasePort(OUTPUT_BOX_GEOMETRY_ID, "Box Geometry", "Resolved box geometry", NodeDataType.BOX_GEOMETRY, this));
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
        BoxGeometryData geometry = null;

        if (region != null && region.isComplete()) {
            minCorner = region.getMinCorner();
            maxCorner = region.getMaxCorner();

            if (minCorner != null && maxCorner != null) {
                geometry = createGeometry(minCorner, maxCorner);
                populateBlocks(blocks, minCorner, maxCorner);
            }
        }

        outputValues.put(OUTPUT_BLOCKS_ID, blocks);
        outputValues.put(OUTPUT_REGION_ID, region);
        outputValues.put(OUTPUT_MIN_CORNER_ID, minCorner);
        outputValues.put(OUTPUT_MAX_CORNER_ID, maxCorner);
        outputValues.put(OUTPUT_COUNT_ID, blocks.size());
        outputValues.put(OUTPUT_GEOMETRY_ID, geometry);
        outputValues.put(OUTPUT_BOX_GEOMETRY_ID, geometry);
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
        return BoxBlockGenerator.regionFromBoundingBox(boundingBox);
    }

    private void populateBlocks(BlockPosList blocks, BlockPos minCorner, BlockPos maxCorner) {
        BoxBlockGenerator.populateAxisAlignedBox(blocks, minCorner, maxCorner, fillBox);
    }

    private BoxGeometryData createGeometry(BlockPos minCorner, BlockPos maxCorner) {
        Vector3d center = new Vector3d(
            (minCorner.getX() + maxCorner.getX()) / 2.0d,
            (minCorner.getY() + maxCorner.getY()) / 2.0d,
            (minCorner.getZ() + maxCorner.getZ()) / 2.0d
        );
        Vector3d halfExtents = new Vector3d(
            (maxCorner.getX() - minCorner.getX()) / 2.0d,
            (maxCorner.getY() - minCorner.getY()) / 2.0d,
            (maxCorner.getZ() - minCorner.getZ()) / 2.0d
        );
        return new BoxGeometryData(center, halfExtents, new Matrix3d().identity(), false);
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
