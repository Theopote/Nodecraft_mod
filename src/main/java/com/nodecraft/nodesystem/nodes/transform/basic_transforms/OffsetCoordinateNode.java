package com.nodecraft.nodesystem.nodes.transform.basic_transforms;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

/**
 * Offsets one block coordinate by integer X/Y/Z amounts.
 */
@NodeInfo(
    id = "transform.basic_transforms.offset_coordinate",
    displayName = "Offset Coordinate",
    description = "Offsets a single block coordinate by integer X, Y, Z amounts or a rounded vector",
    category = "transform.basic_transforms",
    order = 0
)
public class OffsetCoordinateNode extends BaseNode {

    private static final String INPUT_COORDINATE_ID = "input_coordinate";
    private static final String INPUT_OFFSET_VECTOR_ID = "input_offset_vector";
    private static final String INPUT_OFFSET_X_ID = "input_offset_x";
    private static final String INPUT_OFFSET_Y_ID = "input_offset_y";
    private static final String INPUT_OFFSET_Z_ID = "input_offset_z";

    private static final String OUTPUT_COORDINATE_ID = "output_coordinate";
    private static final String OUTPUT_EFFECTIVE_OFFSET_ID = "output_effective_offset";
    private static final String OUTPUT_X_ID = "output_x";
    private static final String OUTPUT_Y_ID = "output_y";
    private static final String OUTPUT_Z_ID = "output_z";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public OffsetCoordinateNode() {
        super(UUID.randomUUID(), "transform.basic_transforms.offset_coordinate");

        addInputPort(new BasePort(INPUT_COORDINATE_ID, "Coordinate", "Source block coordinate", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_OFFSET_VECTOR_ID, "Offset Vector", "Optional vector offset rounded to integer blocks", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_OFFSET_X_ID, "Offset X", "Integer offset on X", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_OFFSET_Y_ID, "Offset Y", "Integer offset on Y", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_OFFSET_Z_ID, "Offset Z", "Integer offset on Z", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_COORDINATE_ID, "Coordinate", "Offset block coordinate", NodeDataType.BLOCK_POS, this));
        addOutputPort(new BasePort(OUTPUT_EFFECTIVE_OFFSET_ID, "Effective Offset", "Integer block offset actually applied", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_X_ID, "X", "Offset X result", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_Y_ID, "Y", "Offset Y result", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_Z_ID, "Z", "Offset Z result", NodeDataType.INTEGER, this));
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
        Object offsetVectorObj = inputValues.get(INPUT_OFFSET_VECTOR_ID);
        if (offsetVectorObj instanceof Vector3d offsetVector) {
            if (!isFinite(offsetVector)) {
                writeInvalid();
                return;
            }
            offsetX = (int) Math.round(offsetVector.x);
            offsetY = (int) Math.round(offsetVector.y);
            offsetZ = (int) Math.round(offsetVector.z);
        } else {
            offsetX = getInt(INPUT_OFFSET_X_ID);
            offsetY = getInt(INPUT_OFFSET_Y_ID);
            offsetZ = getInt(INPUT_OFFSET_Z_ID);
        }

        BlockPos result = source.add(offsetX, offsetY, offsetZ);

        outputValues.put(OUTPUT_COORDINATE_ID, result);
        outputValues.put(OUTPUT_EFFECTIVE_OFFSET_ID, new Vector3d(offsetX, offsetY, offsetZ));
        outputValues.put(OUTPUT_X_ID, result.getX());
        outputValues.put(OUTPUT_Y_ID, result.getY());
        outputValues.put(OUTPUT_Z_ID, result.getZ());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private int getInt(String portId) {
        Object value = inputValues.get(portId);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private boolean isFinite(Vector3d vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_COORDINATE_ID, null);
        outputValues.put(OUTPUT_EFFECTIVE_OFFSET_ID, new Vector3d());
        outputValues.put(OUTPUT_X_ID, 0);
        outputValues.put(OUTPUT_Y_ID, 0);
        outputValues.put(OUTPUT_Z_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}
