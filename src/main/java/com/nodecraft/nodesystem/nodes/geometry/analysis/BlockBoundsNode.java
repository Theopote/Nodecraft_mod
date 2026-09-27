package com.nodecraft.nodesystem.nodes.geometry.analysis;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoundingBoxData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockListUtils;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

/**
 * Discrete cell envelope → continuous AABB for a block list or region.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "geometry.analysis.block_bounds",
    displayName = "Block Bounds",
    description = "Calculates a continuous AABB from a block list or region cell envelope",
    category = "geometry.analysis",
    order = 0
)
public class BlockBoundsNode extends BaseNode {

    private static final String INPUT_BLOCKS_ID = "input_blocks";
    private static final String INPUT_REGION_ID = "input_region";

    private static final String OUTPUT_BOUNDING_BOX_ID = "output_bounding_box";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_MIN_BLOCK_ID = "output_min_block";
    private static final String OUTPUT_MAX_BLOCK_ID = "output_max_block";
    private static final String OUTPUT_SIZE_X_ID = "output_size_x";
    private static final String OUTPUT_SIZE_Y_ID = "output_size_y";
    private static final String OUTPUT_SIZE_Z_ID = "output_size_z";
    private static final String OUTPUT_VOLUME_ID = "output_volume";
    private static final String OUTPUT_CENTER_ID = "output_center";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public BlockBoundsNode() {
        super(UUID.randomUUID(), "geometry.analysis.block_bounds");

        addInputPort(new BasePort(INPUT_BLOCKS_ID, "Blocks",
            "Block coordinates to fit (exactly one source with Region)",
            NodeDataType.BLOCK_LIST, this, false, false));
        addInputPort(new BasePort(INPUT_REGION_ID, "Region",
            "Region to convert into a bounding box (exactly one source with Blocks)",
            NodeDataType.REGION, this, false, false));

        addOutputPort(new BasePort(OUTPUT_BOUNDING_BOX_ID, "Bounding Box",
            "Continuous cell-envelope AABB", NodeDataType.BOUNDING_BOX, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region",
            "Discrete block region", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_MIN_BLOCK_ID, "Min Block",
            "Minimum block cell", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_MAX_BLOCK_ID, "Max Block",
            "Maximum block cell", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_SIZE_X_ID, "Size X",
            "Width in blocks", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SIZE_Y_ID, "Size Y",
            "Height in blocks", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SIZE_Z_ID, "Size Z",
            "Depth in blocks", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VOLUME_ID, "Volume",
            "Cell count as double (long-safe)", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center",
            "Midpoint of the continuous cell envelope", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when exactly one valid source produced bounds", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error",
            "Failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Calculates a continuous AABB from a block list or region cell envelope";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean blocksConnected = isInputConnected(INPUT_BLOCKS_ID);
        boolean regionConnected = isInputConnected(INPUT_REGION_ID);

        if (blocksConnected && regionConnected) {
            writeInvalid("Exactly one of Blocks or Region must be connected");
            return;
        }
        if (!blocksConnected && !regionConnected) {
            writeInvalid("Connect Blocks or Region");
            return;
        }

        if (blocksConnected) {
            List<BlockPos> blocks = BlockListUtils.resolveStrictBlockList(inputValues.get(INPUT_BLOCKS_ID));
            if (blocks == null) {
                writeInvalid("Blocks must be a strict BLOCK_LIST");
                return;
            }
            if (blocks.isEmpty()) {
                writeInvalid("Blocks list is empty");
                return;
            }
            RegionData region = regionFromBlocks(blocks);
            if (region == null) {
                writeInvalid("Unable to derive region from Blocks");
                return;
            }
            writeValid(region);
            return;
        }

        Object regionObj = inputValues.get(INPUT_REGION_ID);
        if (!(regionObj instanceof RegionData region) || !region.isComplete()) {
            writeInvalid("Region must be a complete REGION");
            return;
        }
        writeValid(region);
    }

    private void writeValid(RegionData region) {
        BlockPos minCorner = region.getMinCorner();
        BlockPos maxCorner = region.getMaxCorner();
        if (minCorner == null || maxCorner == null) {
            writeInvalid("Region corners are incomplete");
            return;
        }

        long sizeXLong = (long) maxCorner.getX() - minCorner.getX() + 1L;
        long sizeYLong = (long) maxCorner.getY() - minCorner.getY() + 1L;
        long sizeZLong = (long) maxCorner.getZ() - minCorner.getZ() + 1L;
        if (sizeXLong <= 0L || sizeYLong <= 0L || sizeZLong <= 0L
            || sizeXLong > Integer.MAX_VALUE
            || sizeYLong > Integer.MAX_VALUE
            || sizeZLong > Integer.MAX_VALUE) {
            writeInvalid("Region size exceeds INTEGER range");
            return;
        }

        BoundingBoxData boundingBox = BoundingBoxData.create(
            new Vector3d(minCorner.getX(), minCorner.getY(), minCorner.getZ()),
            new Vector3d(maxCorner.getX() + 1.0d, maxCorner.getY() + 1.0d, maxCorner.getZ() + 1.0d)
        );
        if (boundingBox == null) {
            writeInvalid("Invalid continuous envelope");
            return;
        }

        int sizeX = (int) sizeXLong;
        int sizeY = (int) sizeYLong;
        int sizeZ = (int) sizeZLong;
        double volume = (double) sizeXLong * (double) sizeYLong * (double) sizeZLong;

        outputValues.put(OUTPUT_BOUNDING_BOX_ID, boundingBox);
        outputValues.put(OUTPUT_REGION_ID, region);
        outputValues.put(OUTPUT_MIN_BLOCK_ID, minCorner.toImmutable());
        outputValues.put(OUTPUT_MAX_BLOCK_ID, maxCorner.toImmutable());
        outputValues.put(OUTPUT_SIZE_X_ID, sizeX);
        outputValues.put(OUTPUT_SIZE_Y_ID, sizeY);
        outputValues.put(OUTPUT_SIZE_Z_ID, sizeZ);
        outputValues.put(OUTPUT_VOLUME_ID, volume);
        outputValues.put(OUTPUT_CENTER_ID, new PointData(boundingBox.center()));
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_BOUNDING_BOX_ID, null);
        outputValues.put(OUTPUT_REGION_ID, null);
        outputValues.put(OUTPUT_MIN_BLOCK_ID, null);
        outputValues.put(OUTPUT_MAX_BLOCK_ID, null);
        outputValues.put(OUTPUT_SIZE_X_ID, 0);
        outputValues.put(OUTPUT_SIZE_Y_ID, 0);
        outputValues.put(OUTPUT_SIZE_Z_ID, 0);
        outputValues.put(OUTPUT_VOLUME_ID, Double.NaN);
        outputValues.put(OUTPUT_CENTER_ID, null);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    private boolean isInputConnected(String inputPortId) {
        return inputPorts.stream()
            .anyMatch(port -> inputPortId.equals(port.getId()) && port.isConnected());
    }

    private static @Nullable RegionData regionFromBlocks(List<BlockPos> blocks) {
        BlockPos minCorner = null;
        BlockPos maxCorner = null;
        for (BlockPos pos : blocks) {
            if (minCorner == null) {
                minCorner = pos.toImmutable();
                maxCorner = pos.toImmutable();
                continue;
            }
            minCorner = new BlockPos(
                Math.min(minCorner.getX(), pos.getX()),
                Math.min(minCorner.getY(), pos.getY()),
                Math.min(minCorner.getZ(), pos.getZ())
            );
            maxCorner = new BlockPos(
                Math.max(maxCorner.getX(), pos.getX()),
                Math.max(maxCorner.getY(), pos.getY()),
                Math.max(maxCorner.getZ(), pos.getZ())
            );
        }
        return minCorner != null && maxCorner != null ? new RegionData(minCorner, maxCorner) : null;
    }
}
