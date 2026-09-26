package com.nodecraft.nodesystem.nodes.transform.placement;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.VectorUtils;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

/**
 * Offsets one block coordinate by a rounded vector or integer X/Y/Z amounts.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "transform.placement.offset_coordinate",
    displayName = "Offset Coordinate",
    description = "Offsets a single block coordinate by integer X, Y, Z amounts or a rounded vector",
    category = "transform.placement",
    order = 3
)
public class OffsetCoordinateNode extends BaseNode {

    private static final String INPUT_COORDINATE_ID = "input_coordinate";
    private static final String INPUT_OFFSET_VECTOR_ID = "input_offset_vector";
    private static final String INPUT_OFFSET_X_ID = "input_offset_x";
    private static final String INPUT_OFFSET_Y_ID = "input_offset_y";
    private static final String INPUT_OFFSET_Z_ID = "input_offset_z";

    private static final String OUTPUT_COORDINATE_ID = "output_coordinate";
    private static final String OUTPUT_EFFECTIVE_OFFSET_ID = "output_effective_offset";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public OffsetCoordinateNode() {
        super(UUID.randomUUID(), "transform.placement.offset_coordinate");

        addInputPort(new BasePort(INPUT_COORDINATE_ID, "Coordinate", "Source block coordinate", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_OFFSET_VECTOR_ID, "Offset Vector", "Optional vector offset rounded to integer blocks", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_OFFSET_X_ID, "Offset X", "Integer offset on X", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_OFFSET_Y_ID, "Offset Y", "Integer offset on Y", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_OFFSET_Z_ID, "Offset Z", "Integer offset on Z", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_COORDINATE_ID, "Coordinate", "Offset block coordinate", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_EFFECTIVE_OFFSET_ID, "Effective Offset", "Integer block offset actually applied", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the coordinate offset succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Offsets a single block coordinate by integer X, Y, Z amounts or a rounded vector";
    }

    @Override
    public String getDisplayName() {
        return "Offset Coordinate";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object coordinateObj = inputValues.get(INPUT_COORDINATE_ID);
        if (!(coordinateObj instanceof BlockPos source)) {
            writeInvalid();
            return;
        }

        int offsetX;
        int offsetY;
        int offsetZ;
        if (OptionalPortDrive.isConnected(this, INPUT_OFFSET_VECTOR_ID)) {
            Vector3d offsetVector = OptionalPortDrive.resolveOptionalVector(this, INPUT_OFFSET_VECTOR_ID, null);
            if (offsetVector == null) {
                writeInvalid();
                return;
            }
            offsetX = (int) Math.round(offsetVector.x);
            offsetY = (int) Math.round(offsetVector.y);
            offsetZ = (int) Math.round(offsetVector.z);
        } else {
            Integer x = OptionalPortDrive.resolveOptionalInteger(this, INPUT_OFFSET_X_ID, 0);
            Integer y = OptionalPortDrive.resolveOptionalInteger(this, INPUT_OFFSET_Y_ID, 0);
            Integer z = OptionalPortDrive.resolveOptionalInteger(this, INPUT_OFFSET_Z_ID, 0);
            if (x == null || y == null || z == null) {
                writeInvalid();
                return;
            }
            offsetX = x;
            offsetY = y;
            offsetZ = z;
        }

        BlockPos result = source.add(offsetX, offsetY, offsetZ);
        outputValues.put(OUTPUT_COORDINATE_ID, result);
        outputValues.put(OUTPUT_EFFECTIVE_OFFSET_ID, VectorUtils.toVectorPort(new Vector3d(offsetX, offsetY, offsetZ)));
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_COORDINATE_ID, null);
        outputValues.put(OUTPUT_EFFECTIVE_OFFSET_ID, VectorUtils.toVectorPort(null));
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}
