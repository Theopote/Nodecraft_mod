package com.nodecraft.nodesystem.nodes.transform.placement;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.PlacementBlockUtils;
import com.nodecraft.nodesystem.util.VectorUtils;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * Offsets one block position by a rounded vector or integer X/Y/Z amounts.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "transform.placement.offset_block_position",
    displayName = "Offset Block Position",
    description = "Offsets a single block position by integer X, Y, Z amounts or a rounded vector",
    category = "transform.placement",
    order = 3
)
public class OffsetBlockPositionNode extends AbstractPlacementNode {

    private static final String INPUT_BLOCK_POSITION_ID = "input_block_position";
    private static final String INPUT_OFFSET_VECTOR_ID = "input_offset_vector";
    private static final String INPUT_OFFSET_X_ID = "input_offset_x";
    private static final String INPUT_OFFSET_Y_ID = "input_offset_y";
    private static final String INPUT_OFFSET_Z_ID = "input_offset_z";

    private static final String OUTPUT_BLOCK_POSITION_ID = "output_block_position";
    private static final String OUTPUT_EFFECTIVE_OFFSET_ID = "output_effective_offset";

    public OffsetBlockPositionNode() {
        super("transform.placement.offset_block_position");

        addInputPort(new BasePort(INPUT_BLOCK_POSITION_ID, "Block Position", "Source block position", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_OFFSET_VECTOR_ID, "Offset Vector", "Optional vector offset rounded to integer blocks", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_OFFSET_X_ID, "Offset X", "Integer offset on X", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_OFFSET_Y_ID, "Offset Y", "Integer offset on Y", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_OFFSET_Z_ID, "Offset Z", "Integer offset on Z", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_BLOCK_POSITION_ID, "Block Position", "Offset block position", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_EFFECTIVE_OFFSET_ID, "Effective Offset", "Integer block offset actually applied", NodeDataType.VECTOR, this));
        addValidAndErrorOutputs();
    }

    @Override
    public String getDescription() {
        return "Offsets a single block position by integer X, Y, Z amounts or a rounded vector";
    }

    @Override
    public String getDisplayName() {
        return "Offset Block Position";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object coordinateObj = inputValues.get(INPUT_BLOCK_POSITION_ID);
        if (!(coordinateObj instanceof BlockPos source)) {
            writeInvalid("Missing block position input");
            return;
        }

        boolean vectorConnected = OptionalPortDrive.isConnected(this, INPUT_OFFSET_VECTOR_ID);
        boolean xConnected = OptionalPortDrive.isConnected(this, INPUT_OFFSET_X_ID);
        boolean yConnected = OptionalPortDrive.isConnected(this, INPUT_OFFSET_Y_ID);
        boolean zConnected = OptionalPortDrive.isConnected(this, INPUT_OFFSET_Z_ID);
        if (vectorConnected && (xConnected || yConnected || zConnected)) {
            writeInvalid("Connect Offset Vector or X/Y/Z, not both");
            return;
        }

        int offsetX;
        int offsetY;
        int offsetZ;
        if (vectorConnected) {
            Vector3d offsetVector = OptionalPortDrive.resolveOptionalVector(this, INPUT_OFFSET_VECTOR_ID, null);
            if (offsetVector == null) {
                writeInvalid("Offset Vector connected but invalid");
                return;
            }
            Integer dx = PlacementBlockUtils.roundOffsetToInt(offsetVector.x);
            Integer dy = PlacementBlockUtils.roundOffsetToInt(offsetVector.y);
            Integer dz = PlacementBlockUtils.roundOffsetToInt(offsetVector.z);
            if (dx == null || dy == null || dz == null) {
                writeInvalid("Offset Vector components out of range");
                return;
            }
            offsetX = dx;
            offsetY = dy;
            offsetZ = dz;
        } else {
            Integer x = OptionalPortDrive.resolveOptionalInteger(this, INPUT_OFFSET_X_ID, 0);
            Integer y = OptionalPortDrive.resolveOptionalInteger(this, INPUT_OFFSET_Y_ID, 0);
            Integer z = OptionalPortDrive.resolveOptionalInteger(this, INPUT_OFFSET_Z_ID, 0);
            if (x == null || y == null || z == null) {
                writeInvalid("Offset X/Y/Z connected but invalid");
                return;
            }
            offsetX = x;
            offsetY = y;
            offsetZ = z;
        }

        BlockPos result = PlacementBlockUtils.addBlockPos(source, offsetX, offsetY, offsetZ);
        if (result == null) {
            writeInvalid("Block position offset overflow");
            return;
        }

        outputValues.put(OUTPUT_BLOCK_POSITION_ID, result);
        outputValues.put(OUTPUT_EFFECTIVE_OFFSET_ID, VectorUtils.toVectorPort(new Vector3d(offsetX, offsetY, offsetZ)));
        markSuccess();
    }

    private void writeInvalid(String error) {
        putNullOutputs(OUTPUT_BLOCK_POSITION_ID);
        outputValues.put(OUTPUT_EFFECTIVE_OFFSET_ID, VectorUtils.toVectorPort(null));
        markInvalid(error);
    }
}
