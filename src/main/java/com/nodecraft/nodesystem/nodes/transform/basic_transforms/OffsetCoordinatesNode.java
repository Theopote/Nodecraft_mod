package com.nodecraft.nodesystem.nodes.transform.basic_transforms;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "transform.basic_transforms.offset_coordinates",
    displayName = "Offset Coordinates",
    description = "Offsets a list of block coordinates by a rounded vector",
    category = "transform.basic_transforms",
    order = 1
)
public class OffsetCoordinatesNode extends BaseNode {

    public enum RoundingMode {
        ROUND, FLOOR, CEIL
    }

    @NodeProperty(displayName = "Rounding Mode", category = "Coordinate Transform", order = 1)
    private RoundingMode roundingMode = RoundingMode.ROUND;

    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_OFFSET_VECTOR_ID = "input_offset_vector";

    private static final String OUTPUT_COORDINATES_ID = "output_coordinates";
    private static final String OUTPUT_EFFECTIVE_OFFSET_ID = "output_effective_offset";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public OffsetCoordinatesNode() {
        super(UUID.randomUUID(), "transform.basic_transforms.offset_coordinates");

        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "The coordinates to offset", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_OFFSET_VECTOR_ID, "Offset Vector", "Vector to translate by", NodeDataType.VECTOR, this));

        addOutputPort(new BasePort(OUTPUT_COORDINATES_ID, "Coordinates", "Offset coordinates", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_EFFECTIVE_OFFSET_ID, "Effective Offset", "Integer block offset actually applied", NodeDataType.VECTOR, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of output coordinates", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the coordinate offset succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Offsets a list of block coordinates by a rounded vector";
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

        int offsetX = roundToBlock(offset.x);
        int offsetY = roundToBlock(offset.y);
        int offsetZ = roundToBlock(offset.z);

        for (BlockPos pos : coordinates) {
            result.add(new BlockPos(pos.getX() + offsetX, pos.getY() + offsetY, pos.getZ() + offsetZ));
        }

        writeResult(result, true, new Vector3d(offsetX, offsetY, offsetZ));
    }

    private void writeResult(BlockPosList result, boolean valid) {
        writeResult(result, valid, new Vector3d());
    }

    private void writeResult(BlockPosList result, boolean valid, Vector3d effectiveOffset) {
        outputValues.put(OUTPUT_COORDINATES_ID, result);
        outputValues.put(OUTPUT_EFFECTIVE_OFFSET_ID, effectiveOffset);
        outputValues.put(OUTPUT_COUNT_ID, result.size());
        outputValues.put(OUTPUT_VALID_ID, valid);
    }

    private int roundToBlock(double value) {
        RoundingMode mode = roundingMode == null ? RoundingMode.ROUND : roundingMode;
        return switch (mode) {
            case FLOOR -> (int) Math.floor(value);
            case CEIL -> (int) Math.ceil(value);
            case ROUND -> (int) Math.round(value);
        };
    }

    private boolean isFinite(Vector3d vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    public RoundingMode getRoundingMode() {
        return roundingMode;
    }

    public void setRoundingMode(RoundingMode roundingMode) {
        if (roundingMode != null && this.roundingMode != roundingMode) {
            this.roundingMode = roundingMode;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("roundingMode", roundingMode.name());
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> stateMap)) {
            return;
        }
        if (stateMap.get("roundingMode") instanceof String roundingName) {
            try {
                setRoundingMode(RoundingMode.valueOf(roundingName));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
