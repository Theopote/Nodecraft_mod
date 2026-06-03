package com.nodecraft.nodesystem.nodes.transform.basic_transforms;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    id = "transform.basic_transforms.move_points",
    displayName = "Offset Coordinates",
    description = "Translates a list of block coordinates by a vector",
    category = "transform.basic_transforms",
    order = 1
)
public class OffsetCoordinatesNode extends BaseNode {

    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_OFFSET_VECTOR_ID = "input_offset_vector";

    private static final String OUTPUT_COORDINATES_ID = "output_coordinates";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public OffsetCoordinatesNode() {
        super(UUID.randomUUID(), "transform.basic_transforms.move_points");

        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "The coordinates to offset", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_OFFSET_VECTOR_ID, "Offset Vector", "Vector to translate by", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_COORDINATES_ID, "Coordinates", "Offset coordinates", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the coordinate offset succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Translates a list of coordinates by a vector";
    }

    @Override
    public String getDisplayName() {
        return "Offset Coordinates";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object coordinatesObj = inputValues.get(INPUT_COORDINATES_ID);
        Object offsetObj = inputValues.get(INPUT_OFFSET_VECTOR_ID);

        BlockPosList result = new BlockPosList();
        if (!(coordinatesObj instanceof BlockPosList coordinates) || !(offsetObj instanceof Vector3d offset) || !isFinite(offset)) {
            writeResult(result, false);
            return;
        }

        int offsetX = (int) Math.round(offset.x);
        int offsetY = (int) Math.round(offset.y);
        int offsetZ = (int) Math.round(offset.z);

        for (BlockPos pos : coordinates) {
            result.add(new BlockPos(pos.getX() + offsetX, pos.getY() + offsetY, pos.getZ() + offsetZ));
        }

        writeResult(result, true);
    }

    private void writeResult(BlockPosList result, boolean valid) {
        outputValues.put(OUTPUT_COORDINATES_ID, result);
        outputValues.put(OUTPUT_VALID_ID, valid);
    }

    private boolean isFinite(Vector3d vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
