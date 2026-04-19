package com.nodecraft.nodesystem.nodes.world.read;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "world.read.get_heightmap",
    displayName = "Get Heightmap",
    description = "Reads the top Y value for each X/Z column inside a region",
    category = "world.read",
    order = 6
)
public class GetHeightmapNode extends BaseNode {

    private static final String INPUT_REGION_ID = "input_region";
    private static final String INPUT_HEIGHTMAP_TYPE_ID = "input_heightmap_type";

    private static final String OUTPUT_SURFACE_POINTS_ID = "output_surface_points";
    private static final String OUTPUT_HEIGHT_VALUES_ID = "output_height_values";
    private static final String OUTPUT_COLUMN_COUNT_ID = "output_column_count";
    private static final String OUTPUT_HEIGHTMAP_TYPE_ID = "output_resolved_heightmap_type";

    private String heightmapType = "WORLD_SURFACE";

    public GetHeightmapNode() {
        super(UUID.randomUUID(), "world.read.get_heightmap");

        addInputPort(new BasePort(INPUT_REGION_ID, "Region", "Region whose X/Z columns should be sampled", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_HEIGHTMAP_TYPE_ID, "Heightmap Type", "Optional heightmap type name such as WORLD_SURFACE or MOTION_BLOCKING", NodeDataType.STRING, this));

        addOutputPort(new BasePort(OUTPUT_SURFACE_POINTS_ID, "Surface Points", "Top point for each X/Z column", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_HEIGHT_VALUES_ID, "Height Values", "List of sampled top Y values", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_COLUMN_COUNT_ID, "Column Count", "Number of sampled X/Z columns", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_HEIGHTMAP_TYPE_ID, "Resolved Heightmap Type", "Resolved heightmap type id", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Reads the top Y value for each X/Z column inside a region";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        BlockPosList points = new BlockPosList();
        List<Integer> heights = new ArrayList<>();
        int columnCount = 0;

        Object regionObj = inputValues.get(INPUT_REGION_ID);
        Heightmap.Type resolvedType = resolveHeightmapType(inputValues.get(INPUT_HEIGHTMAP_TYPE_ID));

        if (context != null && context.getWorld() != null && regionObj instanceof RegionData region && region.isComplete()) {
            BlockPos min = region.getMinCorner();
            BlockPos max = region.getMaxCorner();
            if (min != null && max != null) {
                for (int x = min.getX(); x <= max.getX(); x++) {
                    for (int z = min.getZ(); z <= max.getZ(); z++) {
                        int topY = context.getWorld().getTopY(resolvedType, x, z) - 1;
                        points.add(new BlockPos(x, topY, z));
                        heights.add(topY);
                        columnCount++;
                    }
                }
            }
        }

        outputValues.put(OUTPUT_SURFACE_POINTS_ID, points);
        outputValues.put(OUTPUT_HEIGHT_VALUES_ID, heights);
        outputValues.put(OUTPUT_COLUMN_COUNT_ID, columnCount);
        outputValues.put(OUTPUT_HEIGHTMAP_TYPE_ID, resolvedType.asString());
    }

    private Heightmap.Type resolveHeightmapType(Object value) {
        String raw = value instanceof String string && !string.isBlank() ? string : heightmapType;
        try {
            return Heightmap.Type.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return Heightmap.Type.WORLD_SURFACE;
        }
    }

    public String getHeightmapType() {
        return heightmapType;
    }

    public void setHeightmapType(String heightmapType) {
        if (heightmapType != null && !heightmapType.isBlank()) {
            this.heightmapType = heightmapType;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        return heightmapType;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof String value && !value.isBlank()) {
            heightmapType = value;
        }
    }
}
