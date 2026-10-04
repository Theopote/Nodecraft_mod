package com.nodecraft.nodesystem.nodes.material.basic_assignment;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPaletteData;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
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
    description = "Builds a BLOCK_PALETTE from STRING_LIST block ids and optional DOUBLE_LIST weights",
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
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public CreateBlockPaletteNode() {
        super(UUID.randomUUID(), "material.basic_assignment.create_block_palette");

        addInputPort(new BasePort(INPUT_BLOCK_IDS_ID, "Block IDs", "Ordered block id STRING_LIST", NodeDataType.STRING_LIST, this));
        addInputPort(new BasePort(INPUT_WEIGHTS_ID, "Weights", "Optional weights aligned with Block IDs", NodeDataType.DOUBLE_LIST, this));
        addInputPort(new BasePort(INPUT_BLOCK_A_ID, "Block A", "Optional first block type", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_BLOCK_B_ID, "Block B", "Optional second block type", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_BLOCK_C_ID, "Block C", "Optional third block type", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_BLOCK_D_ID, "Block D", "Optional fourth block type", NodeDataType.BLOCK_TYPE, this));

        addOutputPort(new BasePort(OUTPUT_PALETTE_ID, "Palette", "Typed block palette", NodeDataType.BLOCK_PALETTE, this));
        addOutputPort(new BasePort(OUTPUT_SIZE_ID, "Size", "Number of palette entries", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when inputs are usable", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Validation error when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Builds a BLOCK_PALETTE from STRING_LIST block ids and optional DOUBLE_LIST weights.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        BasicAssignmentUtils.ParseResult<String> blockIdsResult =
            BasicAssignmentUtils.parseStringList(inputValues.get(INPUT_BLOCK_IDS_ID), "Block IDs");
        if (!blockIdsResult.valid()) {
            emitFail(blockIdsResult.error());
            return;
        }

        List<String> blockIds = new ArrayList<>(blockIdsResult.values());
        if (!appendBlock(blockIds, INPUT_BLOCK_A_ID, "Block A")
            || !appendBlock(blockIds, INPUT_BLOCK_B_ID, "Block B")
            || !appendBlock(blockIds, INPUT_BLOCK_C_ID, "Block C")
            || !appendBlock(blockIds, INPUT_BLOCK_D_ID, "Block D")) {
            return;
        }

        if (blockIds.size() > GenerationLimits.MAX_BLOCK_PALETTE_ENTRIES) {
            emitFail("Palette exceeds MAX_BLOCK_PALETTE_ENTRIES");
            return;
        }

        List<Double> weights = null;
        if (isDriven(INPUT_WEIGHTS_ID)) {
            BasicAssignmentUtils.ParseResult<Double> weightsResult =
                BasicAssignmentUtils.parseDoubleList(inputValues.get(INPUT_WEIGHTS_ID), "Weights");
            if (!weightsResult.valid()) {
                emitFail(weightsResult.error());
                return;
            }
            weights = weightsResult.values();
            BasicAssignmentUtils.Validation weightsOk =
                BasicAssignmentUtils.validateWeights(weights, blockIds.size());
            if (!weightsOk.valid()) {
                emitFail(weightsOk.message());
                return;
            }
        }

        BlockPaletteData palette = BasicAssignmentUtils.buildPalette(blockIds, weights);
        emitOk(palette);
    }

    private boolean appendBlock(List<String> blockIds, String portId, String label) {
        if (!isDriven(portId)) {
            return true;
        }
        String canonical = BasicAssignmentUtils.canonicalizePaletteBlockId(
            inputValues.get(portId) instanceof String text ? text : null);
        if (canonical == null) {
            emitFail(label + " connected but invalid");
            return false;
        }
        blockIds.add(canonical);
        return true;
    }

    private boolean isDriven(String portId) {
        return OptionalPortDrive.isConnected(this, portId) || isInputPresent(portId);
    }

    private void emitFail(String message) {
        outputValues.putAll(BasicAssignmentUtils.createPaletteFailResult(message));
    }

    private void emitOk(BlockPaletteData palette) {
        outputValues.putAll(BasicAssignmentUtils.createPaletteOkResult(palette));
    }
}
