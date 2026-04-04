package com.nodecraft.nodesystem.nodes.spatial.analysis;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.BoundingBoxData;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

/**
 * Computes an axis-aligned bounding box for any supported geometry object.
 */
@NodeInfo(
    id = "spatial.analysis.geometry_bounds",
    displayName = "Geometry Bounds",
    description = "Calculates an axis-aligned bounding box from any supported geometry",
    category = "spatial.analysis"
)
public class GeometryBoundsNode extends BaseNode {

    private static final String INPUT_GEOMETRY_ID = "input_geometry";

    private static final String OUTPUT_BOUNDING_BOX_ID = "output_bounding_box";
    private static final String OUTPUT_REGION_ID = "output_region";
    private static final String OUTPUT_MIN_CORNER_ID = "output_min_corner";
    private static final String OUTPUT_MAX_CORNER_ID = "output_max_corner";
    private static final String OUTPUT_SIZE_X_ID = "output_size_x";
    private static final String OUTPUT_SIZE_Y_ID = "output_size_y";
    private static final String OUTPUT_SIZE_Z_ID = "output_size_z";
    private static final String OUTPUT_VOLUME_ID = "output_volume";
    private static final String OUTPUT_CENTER_ID = "output_center";

    public GeometryBoundsNode() {
        super(UUID.randomUUID(), "spatial.analysis.geometry_bounds");

        addInputPort(new BasePort(INPUT_GEOMETRY_ID, "Geometry", "Unified geometry input", NodeDataType.GEOMETRY, this));

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
        return "Calculates an axis-aligned bounding box from any supported geometry";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object geometryObj = inputValues.get(INPUT_GEOMETRY_ID);
        if (!(geometryObj instanceof GeometryData geometry)) {
            outputValues.clear();
            return;
        }

        RegionData region = GeometryVoxelizer.createBoundingRegion(geometry);
        if (region == null || !region.isComplete()) {
            outputValues.clear();
            return;
        }

        BlockPos minCorner = region.getMinCorner();
        BlockPos maxCorner = region.getMaxCorner();
        if (minCorner == null || maxCorner == null) {
            outputValues.clear();
            return;
        }

        BoundingBoxData boundingBox = new BoundingBoxData(
            new Vector3d(minCorner.getX(), minCorner.getY(), minCorner.getZ()),
            new Vector3d(maxCorner.getX() + 1.0d, maxCorner.getY() + 1.0d, maxCorner.getZ() + 1.0d)
        );

        int sizeX = maxCorner.getX() - minCorner.getX() + 1;
        int sizeY = maxCorner.getY() - minCorner.getY() + 1;
        int sizeZ = maxCorner.getZ() - minCorner.getZ() + 1;
        int volume = sizeX * sizeY * sizeZ;

        BlockPos center = new BlockPos(
            minCorner.getX() + ((sizeX - 1) / 2),
            minCorner.getY() + ((sizeY - 1) / 2),
            minCorner.getZ() + ((sizeZ - 1) / 2)
        );

        outputValues.put(OUTPUT_BOUNDING_BOX_ID, boundingBox);
        outputValues.put(OUTPUT_REGION_ID, region);
        outputValues.put(OUTPUT_MIN_CORNER_ID, minCorner);
        outputValues.put(OUTPUT_MAX_CORNER_ID, maxCorner);
        outputValues.put(OUTPUT_SIZE_X_ID, sizeX);
        outputValues.put(OUTPUT_SIZE_Y_ID, sizeY);
        outputValues.put(OUTPUT_SIZE_Z_ID, sizeZ);
        outputValues.put(OUTPUT_VOLUME_ID, volume);
        outputValues.put(OUTPUT_CENTER_ID, center);
    }
}
