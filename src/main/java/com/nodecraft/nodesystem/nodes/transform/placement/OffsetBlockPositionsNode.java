package com.nodecraft.nodesystem.nodes.transform.placement;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.PlacementBlockUtils;
import com.nodecraft.nodesystem.util.VectorUtils;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "transform.placement.offset_block_positions",
    displayName = "Offset Block Positions",
    description = "Offsets a list of block positions by a vector rounded with Java Math.round (nearest integer, half ties toward +∞)",
    category = "transform.placement",
    order = 4
)
public class OffsetBlockPositionsNode extends AbstractPlacementNode {

    private static final String INPUT_BLOCK_POSITIONS_ID = "input_block_positions";
    private static final String INPUT_OFFSET_VECTOR_ID = "input_offset_vector";

    private static final String OUTPUT_BLOCK_POSITIONS_ID = "output_block_positions";
    private static final String OUTPUT_EFFECTIVE_OFFSET_ID = "output_effective_offset";
    private static final String OUTPUT_COUNT_ID = "output_count";

    public OffsetBlockPositionsNode() {
        super("transform.placement.offset_block_positions");

        addInputPort(new BasePort(INPUT_BLOCK_POSITIONS_ID, "Block Positions", "The block positions to offset", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_OFFSET_VECTOR_ID, "Offset Vector",
            "Vector rounded with Java Math.round (nearest integer, half ties toward +∞)",
            NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_BLOCK_POSITIONS_ID, "Block Positions", "Offset block positions", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_EFFECTIVE_OFFSET_ID, "Effective Offset", "Integer block offset actually applied", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of output block positions", NodeDataType.INTEGER, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Offsets a list of block positions by a vector rounded with Java Math.round (nearest integer, half ties toward +∞)";
    }

    @Override
    public String getDisplayName() {
        return "Offset Block Positions";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object coordinatesObj = inputValues.get(INPUT_BLOCK_POSITIONS_ID);
        if (!(coordinatesObj instanceof BlockPosList coordinates)) {
            writeInvalid("Missing block position list");
            return;
        }

        String listError = PlacementBlockUtils.validateBlockListSize(coordinates);
        if (listError != null) {
            writeInvalid(listError);
            return;
        }

        Vector3d offset = OptionalPortDrive.resolveOptionalVector(this, INPUT_OFFSET_VECTOR_ID, null);
        if (offset == null) {
            writeInvalid("Offset Vector connected but invalid");
            return;
        }

        Integer offsetX = PlacementBlockUtils.roundOffsetToInt(offset.x);
        Integer offsetY = PlacementBlockUtils.roundOffsetToInt(offset.y);
        Integer offsetZ = PlacementBlockUtils.roundOffsetToInt(offset.z);
        if (offsetX == null || offsetY == null || offsetZ == null) {
            writeInvalid("Offset Vector components out of range");
            return;
        }

        BlockPosList result = new BlockPosList();
        for (BlockPos pos : coordinates) {
            BlockPos shifted = PlacementBlockUtils.addBlockPos(pos, offsetX, offsetY, offsetZ);
            if (shifted == null) {
                writeInvalid("Block position offset overflow");
                return;
            }
            result.add(shifted);
        }

        outputValues.put(OUTPUT_BLOCK_POSITIONS_ID, result);
        outputValues.put(OUTPUT_EFFECTIVE_OFFSET_ID, VectorUtils.toVectorPort(new Vector3d(offsetX, offsetY, offsetZ)));
        outputValues.put(OUTPUT_COUNT_ID, result.size());
        markSuccess();
    }

    private void writeInvalid(String error) {
        putEmptyBlockListOutputs(OUTPUT_BLOCK_POSITIONS_ID);
        outputValues.put(OUTPUT_EFFECTIVE_OFFSET_ID, VectorUtils.toVectorPort(null));
        putIntOutputs(0, OUTPUT_COUNT_ID);
        markInvalid(error);
    }
}
