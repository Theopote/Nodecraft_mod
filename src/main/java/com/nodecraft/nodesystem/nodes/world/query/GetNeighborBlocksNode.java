package com.nodecraft.nodesystem.nodes.world.query;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.WORLD_READ,
    id = "world.query.get_neighbors",
    displayName = "Get Neighbor Blocks",
    description = "Returns axis-ray neighbors or cube-volume neighbors around a center position.",
    category = "world.query",
    order = 10
)
public class GetNeighborBlocksNode extends BaseNode {

    private static final String INPUT_CENTER_ID = "input_center";
    private static final String INPUT_INCLUDE_DIAGONALS_ID = "input_include_diagonals";
    private static final String INPUT_RADIUS_ID = "input_radius";

    private static final String OUTPUT_COORDINATES_ID = "output_coordinates";
    private static final String OUTPUT_BLOCK_IDS_ID = "output_block_ids";
    private static final String OUTPUT_BLOCK_INFOS_ID = "output_block_infos";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    private static final int[][] OFFSETS_6 = new int[][] {
        {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    public GetNeighborBlocksNode() {
        super(UUID.randomUUID(), "world.query.get_neighbors");

        addInputPort(new BasePort(INPUT_CENTER_ID, "Center", "Center block position", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_INCLUDE_DIAGONALS_ID, "Include Diagonals", "Use cube-volume 26-neighbor mode instead of six axis rays", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_RADIUS_ID, "Radius", "Neighbor radius in blocks; six-neighbor mode returns axis rays, diagonal mode returns the surrounding cube volume", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_COORDINATES_ID, "Coordinates", "Neighbor block positions", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_IDS_ID, "Block IDs", "Neighbor block ids", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_INFOS_ID, "Block Infos", "Neighbor info map list", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Neighbor count", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether neighbor query was executed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Returns axis-ray neighbors or cube-volume neighbors around a center position.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        if (context == null || context.getWorld() == null || !(inputValues.get(INPUT_CENTER_ID) instanceof BlockPos center)) {
            writeInvalid();
            return;
        }
        boolean diagonals = inputValues.get(INPUT_INCLUDE_DIAGONALS_ID) instanceof Boolean b && b;
        int radius = inputValues.get(INPUT_RADIUS_ID) instanceof Number n ? Math.max(1, n.intValue()) : 1;

        List<BlockPos> neighbors = diagonals ? build26(center, radius) : build6(center, radius);
        BlockPosList coordinates = new BlockPosList();
        List<String> ids = new ArrayList<>(neighbors.size());
        List<Map<String, Object>> infos = new ArrayList<>(neighbors.size());

        for (BlockPos pos : neighbors) {
            BlockState state = context.getWorld().getBlockState(pos);
            String id = Registries.BLOCK.getId(state.getBlock()).toString();
            coordinates.add(pos.toImmutable());
            ids.add(id);

            Map<String, Object> info = new LinkedHashMap<>();
            info.put("pos", pos.toImmutable());
            info.put("block_id", id);
            info.put("is_air", state.isAir());
            info.put("light_level", context.getWorld().getLightLevel(pos));
            infos.add(info);
        }

        outputValues.put(OUTPUT_COORDINATES_ID, coordinates);
        outputValues.put(OUTPUT_BLOCK_IDS_ID, ids);
        outputValues.put(OUTPUT_BLOCK_INFOS_ID, infos);
        outputValues.put(OUTPUT_COUNT_ID, neighbors.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private List<BlockPos> build6(BlockPos center, int radius) {
        List<BlockPos> out = new ArrayList<>();
        for (int r = 1; r <= radius; r++) {
            for (int[] d : OFFSETS_6) {
                out.add(center.add(d[0] * r, d[1] * r, d[2] * r).toImmutable());
            }
        }
        return out;
    }

    private List<BlockPos> build26(BlockPos center, int radius) {
        List<BlockPos> out = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    out.add(center.add(x, y, z).toImmutable());
                }
            }
        }
        return out;
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_COORDINATES_ID, new BlockPosList());
        outputValues.put(OUTPUT_BLOCK_IDS_ID, List.of());
        outputValues.put(OUTPUT_BLOCK_INFOS_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}
