package com.nodecraft.nodesystem.nodes.geometry.boolops;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.BoundingBoxOutputWriter;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Calculates an axis-aligned bounding box from block positions or a region.
 */
@NodeInfo(
    id = "geometry.boolean.bounding_box",
    displayName = "Bounding Box",
    description = "Calculates an axis-aligned bounding box from a block list or region",
    category = "geometry.boolean",
    order = 0
)
public class BoundingBoxNode extends BaseNode {

    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_REGION_ID = "input_region";

    private static final String OUTPUT_BOUNDING_BOX_ID = "output_bounding_box";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_MIN_CORNER_ID = "output_min_corner";
    private static final String OUTPUT_MAX_CORNER_ID = "output_max_corner";
    private static final String OUTPUT_SIZE_X_ID = "output_size_x";
    private static final String OUTPUT_SIZE_Y_ID = "output_size_y";
    private static final String OUTPUT_SIZE_Z_ID = "output_size_z";
    private static final String OUTPUT_VOLUME_ID = "output_volume";
    private static final String OUTPUT_CENTER_ID = "output_center";

    public BoundingBoxNode() {
        super(UUID.randomUUID(), "geometry.boolean.bounding_box");

        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Block coordinates to fit", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_REGION_ID, "Region", "Region to convert into a bounding box", NodeDataType.REGION, this));

        addOutputPort(new BasePort(OUTPUT_BOUNDING_BOX_ID, "Bounding Box", "Axis-aligned bounding box data", NodeDataType.BOUNDING_BOX, this));
        addOutputPort(new BasePort(OUTPUT_REGION_ID, "Region", "Bounding box as a region", NodeDataType.REGION, this));
        addOutputPort(new BasePort(OUTPUT_MIN_CORNER_ID, "Min Corner", "Minimum corner", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_MAX_CORNER_ID, "Max Corner", "Maximum corner", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_SIZE_X_ID, "Size X", "Width in blocks", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SIZE_Y_ID, "Size Y", "Height in blocks", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SIZE_Z_ID, "Size Z", "Depth in blocks", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VOLUME_ID, "Volume", "Bounding volume in blocks", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_CENTER_ID, "Center", "Center block of the box", NodeDataType.BLOCK_POS, this));
    }

    @Override
    public String getDescription() {
        return "Calculates an axis-aligned bounding box from a block list or region";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        RegionData region = null;

        Object coordinatesObj = inputValues.get(INPUT_COORDINATES_ID);
        Object regionObj = inputValues.get(INPUT_REGION_ID);

        if (coordinatesObj instanceof BlockPosList coordinates && !coordinates.isEmpty()) {
            region = regionFromCoordinates(coordinates);
        }

        if (region == null && regionObj instanceof RegionData inputRegion && inputRegion.isComplete()) {
            region = inputRegion;
        }

        BoundingBoxOutputWriter.writeOrClear(
            outputValues,
            region,
            OUTPUT_BOUNDING_BOX_ID,
            OUTPUT_REGION_ID,
            OUTPUT_MIN_CORNER_ID,
            OUTPUT_MAX_CORNER_ID,
            OUTPUT_SIZE_X_ID,
            OUTPUT_SIZE_Y_ID,
            OUTPUT_SIZE_Z_ID,
            OUTPUT_VOLUME_ID,
            OUTPUT_CENTER_ID
        );
    }

    private static RegionData regionFromCoordinates(BlockPosList coordinates) {
        BlockPos minCorner = null;
        BlockPos maxCorner = null;

        for (BlockPos pos : coordinates) {
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
