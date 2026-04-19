package com.nodecraft.nodesystem.nodes.world.read;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "world.read.get_surface_blocks",
    displayName = "Get Surface Blocks",
    description = "Gets the top visible block for each X/Z column inside a region",
    category = "world.read",
    order = 7
)
public class GetSurfaceBlocksNode extends BaseNode {

    private static final String INPUT_REGION_ID = "input_region";
    private static final String INPUT_HEIGHTMAP_TYPE_ID = "input_heightmap_type";
    private static final String INPUT_EXCLUDE_AIR_ID = "input_exclude_air";

    private static final String OUTPUT_SURFACE_BLOCKS_ID = "output_surface_blocks";
    private static final String OUTPUT_SURFACE_POSITIONS_ID = "output_surface_positions";
    private static final String OUTPUT_BLOCK_TYPES_ID = "output_block_types";
    private static final String OUTPUT_COUNT_ID = "output_count";

    private String heightmapType = "WORLD_SURFACE";
    private boolean excludeAir = true;

    public GetSurfaceBlocksNode() {
        super(UUID.randomUUID(), "world.read.get_surface_blocks");

        addInputPort(new BasePort(INPUT_REGION_ID, "Region", "Region whose X/Z columns should be sampled", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_HEIGHTMAP_TYPE_ID, "Heightmap Type", "Optional heightmap type name", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_EXCLUDE_AIR_ID, "Exclude Air", "Whether columns resolving to air should be excluded", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_SURFACE_BLOCKS_ID, "Surface Blocks", "Top block state for each sampled column", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_SURFACE_POSITIONS_ID, "Surface Positions", "Block positions of sampled surface blocks", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_TYPES_ID, "Block Types", "Registry ids of sampled surface blocks", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of sampled surface blocks", NodeDataType.INTEGER, this));
    }

    @Override
    public String getDescription() {
        return "Gets the top visible block for each X/Z column inside a region";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<BlockState> blocks = new ArrayList<>();
        BlockPosList positions = new BlockPosList();
        List<String> blockTypes = new ArrayList<>();
        int count = 0;

        Object regionObj = inputValues.get(INPUT_REGION_ID);
        Heightmap.Type resolvedType = resolveHeightmapType(inputValues.get(INPUT_HEIGHTMAP_TYPE_ID));
        boolean resolvedExcludeAir = inputValues.get(INPUT_EXCLUDE_AIR_ID) instanceof Boolean value ? value : excludeAir;

        if (context != null && context.getWorld() != null && regionObj instanceof RegionData region && region.isComplete()) {
            BlockPos min = region.getMinCorner();
            BlockPos max = region.getMaxCorner();
            if (min != null && max != null) {
                for (int x = min.getX(); x <= max.getX(); x++) {
                    for (int z = min.getZ(); z <= max.getZ(); z++) {
                        int topY = context.getWorld().getTopY(resolvedType, x, z) - 1;
                        BlockPos pos = new BlockPos(x, topY, z);
                        BlockState state = context.getWorld().getBlockState(pos);
                        if (resolvedExcludeAir && state.isAir()) {
                            continue;
                        }
                        blocks.add(state);
                        positions.add(pos);
                        blockTypes.add(Registries.BLOCK.getId(state.getBlock()).toString());
                        count++;
                    }
                }
            }
        }

        outputValues.put(OUTPUT_SURFACE_BLOCKS_ID, blocks);
        outputValues.put(OUTPUT_SURFACE_POSITIONS_ID, positions);
        outputValues.put(OUTPUT_BLOCK_TYPES_ID, blockTypes);
        outputValues.put(OUTPUT_COUNT_ID, count);
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

    public boolean isExcludeAir() {
        return excludeAir;
    }

    public void setExcludeAir(boolean excludeAir) {
        this.excludeAir = excludeAir;
        markDirty();
    }

    @Override
    public Object getNodeState() {
        return new Object[]{heightmapType, excludeAir};
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Object[] values && values.length >= 2) {
            if (values[0] instanceof String type && !type.isBlank()) {
                heightmapType = type;
            }
            if (values[1] instanceof Boolean flag) {
                excludeAir = flag;
            }
        }
    }
}
