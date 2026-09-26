package com.nodecraft.nodesystem.nodes.transform.placement;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.VectorUtils;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "transform.placement.offset_block_positions",
    displayName = "Offset Block Positions",
    description = "Offsets a list of block positions by a rounded vector",
    category = "transform.placement",
    order = 4
)
public class OffsetBlockPositionsNode extends BaseNode {

    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_OFFSET_VECTOR_ID = "input_offset_vector";

    private static final String OUTPUT_COORDINATES_ID = "output_coordinates";
    private static final String OUTPUT_EFFECTIVE_OFFSET_ID = "output_effective_offset";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public OffsetBlockPositionsNode() {
        super(UUID.randomUUID(), "transform.placement.offset_block_positions");

        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Block Positions", "The block positions to offset", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_OFFSET_VECTOR_ID, "Offset Vector", "Vector to translate by", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_COORDINATES_ID, "Block Positions", "Offset block positions", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_EFFECTIVE_OFFSET_ID, "Effective Offset", "Integer block offset actually applied", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of output block positions", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the block position offset succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Offsets a list of block positions by a rounded vector";
    }

    @Override
    public String getDisplayName() {
        return "Offset Block Positions";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object coordinatesObj = inputValues.get(INPUT_COORDINATES_ID);
        BlockPosList result = new BlockPosList();
        if (!(coordinatesObj instanceof BlockPosList coordinates)) {
            writeResult(result, false, null);
            return;
        }

        Vector3d offset = OptionalPortDrive.resolveOptionalVector(this, INPUT_OFFSET_VECTOR_ID, null);
        if (offset == null) {
            writeResult(result, false, null);
            return;
        }

        // Displacement rounding only — not world-space cell snap.
        int offsetX = (int) Math.round(offset.x);
        int offsetY = (int) Math.round(offset.y);
        int offsetZ = (int) Math.round(offset.z);

        for (BlockPos pos : coordinates) {
            result.add(pos.add(offsetX, offsetY, offsetZ));
        }

        writeResult(result, true, new Vector3d(offsetX, offsetY, offsetZ));
    }

    private void writeResult(BlockPosList result, boolean valid, @Nullable Vector3d effectiveOffset) {
        outputValues.put(OUTPUT_COORDINATES_ID, result);
        outputValues.put(OUTPUT_EFFECTIVE_OFFSET_ID, VectorUtils.toVectorPort(effectiveOffset));
        outputValues.put(OUTPUT_COUNT_ID, result.size());
        outputValues.put(OUTPUT_VALID_ID, valid);
    }
}
