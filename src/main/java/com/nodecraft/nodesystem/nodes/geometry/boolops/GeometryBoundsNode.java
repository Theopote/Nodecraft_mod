package com.nodecraft.nodesystem.nodes.geometry.boolops;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.GeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BoundingBoxOutputWriter;
import com.nodecraft.nodesystem.util.GeometryVoxelizer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Computes an axis-aligned bounding box for any supported geometry object.
 */
@NodeInfo(
    id = "geometry.boolean.geometry_bounds",
    displayName = "Geometry Bounds",
    description = "Calculates an axis-aligned bounding box from any supported geometry",
    category = "geometry.boolean",
    order = 1
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
        super(UUID.randomUUID(), "geometry.boolean.geometry_bounds");

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
        RegionData region = geometryObj instanceof GeometryData geometry
            ? GeometryVoxelizer.createBoundingRegion(geometry)
            : null;

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
}
