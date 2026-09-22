package com.nodecraft.nodesystem.nodes.material.basic_assignment;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPaletteData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds a typed {@link BlockPaletteData} from block ids and optional weights.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "material.basic_assignment.create_block_palette",
    displayName = "Create Block Palette",
    description = "Builds a BLOCK_PALETTE from block ids and optional weights",
    category = "material.basic_assignment",
    order = 0
)
public class CreateBlockPaletteNode extends BaseNode {

    private static final String INPUT_BLOCK_IDS_ID = "input_block_ids";
    private static final String INPUT_WEIGHTS_ID = "input_weights";
    private static final String INPUT_BLOCK_A_ID = "input_block_a";
    private static final String INPUT_BLOCK_B_ID = "input_block_b";
    private static final String INPUT_BLOCK_C_ID = "input_block_c";
    private static final String INPUT_BLOCK_D_ID = "input_block_d";

    private static final String OUTPUT_PALETTE_ID = "output_palette";
    private static final String OUTPUT_SIZE_ID = "output_size";

    public CreateBlockPaletteNode() {
        super(UUID.randomUUID(), "material.basic_assignment.create_block_palette");

        addInputPort(new BasePort(INPUT_BLOCK_IDS_ID, "Block IDs", "Ordered block id list", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_WEIGHTS_ID, "Weights", "Optional weights aligned with Block IDs", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_BLOCK_A_ID, "Block A", "Optional first block type", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_BLOCK_B_ID, "Block B", "Optional second block type", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_BLOCK_C_ID, "Block C", "Optional third block type", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_BLOCK_D_ID, "Block D", "Optional fourth block type", NodeDataType.BLOCK_TYPE, this));

        addOutputPort(new BasePort(OUTPUT_PALETTE_ID, "Palette", "Typed block palette", NodeDataType.BLOCK_PALETTE, this));
        addOutputPort(new BasePort(OUTPUT_SIZE_ID, "Size", "Number of palette entries", NodeDataType.INTEGER, this));
    }

    @Override
    public String getDescription() {
        return "Builds a BLOCK_PALETTE from block ids and optional weights.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<String> blockIds = new ArrayList<>();
        Object listObj = inputValues.get(INPUT_BLOCK_IDS_ID);
        if (listObj instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof String blockId && !blockId.isBlank()) {
                    blockIds.add(blockId);
                }
            }
        }
        appendBlock(blockIds, INPUT_BLOCK_A_ID);
        appendBlock(blockIds, INPUT_BLOCK_B_ID);
        appendBlock(blockIds, INPUT_BLOCK_C_ID);
        appendBlock(blockIds, INPUT_BLOCK_D_ID);

        List<Double> weights = new ArrayList<>();
        Object weightsObj = inputValues.get(INPUT_WEIGHTS_ID);
        if (weightsObj instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Number number) {
                    weights.add(number.doubleValue());
                } else {
                    weights.add(1.0d);
                }
            }
        }

        BlockPaletteData palette = weights.isEmpty()
            ? BlockPaletteData.ofBlockIds(blockIds)
            : BlockPaletteData.ofBlockIdsAndWeights(blockIds, weights);

        outputValues.put(OUTPUT_PALETTE_ID, palette);
        outputValues.put(OUTPUT_SIZE_ID, palette.size());
    }

    private void appendBlock(List<String> blockIds, String portId) {
        Object value = inputValues.get(portId);
        if (value instanceof String blockId && !blockId.isBlank()) {
            blockIds.add(blockId);
        }
    }
}
