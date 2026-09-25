package com.nodecraft.nodesystem.nodes.material.block_state;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockStateData;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.Locale;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "material.block_state.orient_block_state",
    displayName = "Orient Block State",
    description = "Derives facing, axis, and stair half block-state properties from a direction vector",
    category = "material.block_state",
    order = 1
)
public class OrientBlockStateNode extends BaseNode {

    private static final String INPUT_BASE_STATE_ID = "input_base_state";
    private static final String INPUT_BLOCK_TYPE_ID = "input_block_type";
    private static final String INPUT_VECTOR_ID = "input_vector";
    private static final String INPUT_MODE_ID = "input_mode";
    private static final String INPUT_INCLUDE_WATERLOGGED_ID = "input_include_waterlogged";
    private static final String INPUT_WATERLOGGED_ID = "input_waterlogged";

    private static final String OUTPUT_BLOCK_STATE_ID = "output_block_state";
    private static final String OUTPUT_FACING_ID = "output_facing";
    private static final String OUTPUT_AXIS_ID = "output_axis";
    private static final String OUTPUT_HALF_ID = "output_half";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public OrientBlockStateNode() {
        super(UUID.randomUUID(), "material.block_state.orient_block_state");

        addInputPort(new BasePort(INPUT_BASE_STATE_ID, "Base State", "Optional state data to copy before applying orientation", NodeDataType.BLOCK_STATE_DATA, this));
        addInputPort(new BasePort(INPUT_BLOCK_TYPE_ID, "Block Type", "Optional block id for registry validation of derived properties", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_VECTOR_ID, "Vector", "Direction vector used to derive orientation", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_MODE_ID, "Mode", "facing, horizontal_facing, axis, or stair", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_INCLUDE_WATERLOGGED_ID, "Include Waterlogged", "When true, writes the waterlogged shortcut property", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_WATERLOGGED_ID, "Waterlogged", "Waterlogged value to write when enabled", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_BLOCK_STATE_ID, "Block State", "Oriented block-state property data", NodeDataType.BLOCK_STATE_DATA, this));
        addOutputPort(new BasePort(OUTPUT_FACING_ID, "Facing", "Derived facing value", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_AXIS_ID, "Axis", "Derived axis value", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_HALF_ID, "Half", "Derived stair half value", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when the vector is usable and properties validate when block type is provided", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Validation error when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        BlockStateData state = inputValues.get(INPUT_BASE_STATE_ID) instanceof BlockStateData base
            ? base.copy()
            : new BlockStateData();
        BlockStateValidationUtils.stripIdentityKeys(state);

        Vector3d vector = BlockStateValidationUtils.resolveStrictVector3d(inputValues.get(INPUT_VECTOR_ID));
        if (vector == null || vector.lengthSquared() <= 1.0e-9d) {
            outputValues.put(OUTPUT_BLOCK_STATE_ID, state);
            outputValues.put(OUTPUT_FACING_ID, "");
            outputValues.put(OUTPUT_AXIS_ID, "");
            outputValues.put(OUTPUT_HALF_ID, "");
            outputValues.put(OUTPUT_VALID_ID, false);
            outputValues.put(OUTPUT_ERROR_ID, "Invalid or missing direction vector");
            return;
        }

        vector = new Vector3d(vector).normalize();
        String mode = parseMode(inputValues.get(INPUT_MODE_ID));
        if (mode == null) {
            outputValues.put(OUTPUT_BLOCK_STATE_ID, state);
            outputValues.put(OUTPUT_FACING_ID, "");
            outputValues.put(OUTPUT_AXIS_ID, "");
            outputValues.put(OUTPUT_HALF_ID, "");
            outputValues.put(OUTPUT_VALID_ID, false);
            outputValues.put(OUTPUT_ERROR_ID, "Unknown orientation mode");
            return;
        }
        Direction facing = "horizontal_facing".equals(mode) || "stair".equals(mode)
            ? horizontalFacing(vector)
            : facing(vector);
        String axis = axis(vector);
        String half = vector.y < 0.0d ? "top" : "bottom";

        switch (mode) {
            case "axis" -> state.setProperty("axis", axis);
            case "stair" -> {
                state.setProperty("facing", facing.asString());
                state.setProperty("half", half);
                state.setProperty("shape", state.getProperty("shape", "straight"));
            }
            case "horizontal_facing", "facing" -> state.setProperty("facing", facing.asString());
            default -> state.setProperty("facing", facing.asString());
        }

        if (Boolean.TRUE.equals(inputValues.get(INPUT_INCLUDE_WATERLOGGED_ID))) {
            state.setBooleanProperty("waterlogged", Boolean.TRUE.equals(inputValues.get(INPUT_WATERLOGGED_ID)));
        }

        String blockType = BlockStateValidationUtils.normalizeBlockId(inputValues.get(INPUT_BLOCK_TYPE_ID));
        BlockStateValidationUtils.ValidationResult validation = blockType != null
            ? BlockStateValidationUtils.validateProperties(blockType, state)
            : BlockStateValidationUtils.ValidationResult.ok();

        outputValues.put(OUTPUT_BLOCK_STATE_ID, state);
        outputValues.put(OUTPUT_FACING_ID, facing.asString());
        outputValues.put(OUTPUT_AXIS_ID, axis);
        outputValues.put(OUTPUT_HALF_ID, half);
        outputValues.put(OUTPUT_VALID_ID, validation.valid());
        outputValues.put(OUTPUT_ERROR_ID, validation.message());
    }

    private static Direction facing(Vector3d direction) {
        double absX = Math.abs(direction.x);
        double absY = Math.abs(direction.y);
        double absZ = Math.abs(direction.z);
        if (absX >= absY && absX >= absZ) {
            return direction.x >= 0.0d ? Direction.EAST : Direction.WEST;
        }
        if (absY >= absX && absY >= absZ) {
            return direction.y >= 0.0d ? Direction.UP : Direction.DOWN;
        }
        return direction.z >= 0.0d ? Direction.SOUTH : Direction.NORTH;
    }

    private static Direction horizontalFacing(Vector3d direction) {
        double absX = Math.abs(direction.x);
        double absZ = Math.abs(direction.z);
        if (absX >= absZ) {
            return direction.x >= 0.0d ? Direction.EAST : Direction.WEST;
        }
        return direction.z >= 0.0d ? Direction.SOUTH : Direction.NORTH;
    }

    private static String axis(Vector3d direction) {
        double absX = Math.abs(direction.x);
        double absY = Math.abs(direction.y);
        double absZ = Math.abs(direction.z);
        if (absX >= absY && absX >= absZ) {
            return "x";
        }
        return absY >= absX && absY >= absZ ? "y" : "z";
    }

    private static @Nullable String parseMode(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return "facing";
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "facing", "horizontal_facing", "axis", "stair" -> normalized;
            default -> null;
        };
    }
}
