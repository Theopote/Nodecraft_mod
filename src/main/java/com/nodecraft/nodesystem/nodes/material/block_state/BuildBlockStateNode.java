package com.nodecraft.nodesystem.nodes.material.block_state;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockStateData;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "material.block_state.build_block_state",
    displayName = "Build BlockState",
    description = "Builds block-state key/value data from a block id, base state, and dynamic property override",
    category = "material.block_state",
    order = 6
)
public class BuildBlockStateNode extends BaseNode {

    private static final String INPUT_BASE_STATE_ID = "input_base_state";
    private static final String INPUT_BLOCK_TYPE_ID = "input_block_type";
    private static final String INPUT_PROPERTY_NAME_ID = "input_property_name";
    private static final String INPUT_PROPERTY_VALUE_ID = "input_property_value";
    private static final String INPUT_FACING_ID = "input_facing";
    private static final String INPUT_AXIS_ID = "input_axis";
    private static final String INPUT_HALF_ID = "input_half";
    private static final String INPUT_WATERLOGGED_ID = "input_waterlogged";

    private static final String OUTPUT_BLOCK_STATE_ID = "output_block_state";
    private static final String OUTPUT_BLOCK_INFO_ID = "output_block_info";
    private static final String OUTPUT_PROPERTY_COUNT_ID = "output_property_count";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public BuildBlockStateNode() {
        super(UUID.randomUUID(), "material.block_state.build_block_state");

        addInputPort(new BasePort(INPUT_BASE_STATE_ID, "Base State", "Optional state data to copy before applying overrides", NodeDataType.BLOCK_STATE_DATA, this));
        addInputPort(new BasePort(INPUT_BLOCK_TYPE_ID, "Block Type", "Block id to include, e.g. minecraft:oak_stairs", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_PROPERTY_NAME_ID, "Property", "Dynamic property name, e.g. facing or axis", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_PROPERTY_VALUE_ID, "Value", "Dynamic property value, e.g. north or x", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_FACING_ID, "Facing", "Shortcut for the facing property", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "Axis", "Shortcut for the axis property", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_HALF_ID, "Half", "Shortcut for the half property", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_WATERLOGGED_ID, "Waterlogged", "Shortcut for the waterlogged property", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_BLOCK_STATE_ID, "Block State", "Composed block-state data", NodeDataType.BLOCK_STATE_DATA, this));
        addOutputPort(new BasePort(OUTPUT_BLOCK_INFO_ID, "Block Info", "Block-state data usable by world write nodes", NodeDataType.BLOCK_INFO, this));
        addOutputPort(new BasePort(OUTPUT_PROPERTY_COUNT_ID, "Property Count", "Number of emitted state entries including block id", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when all resolvable properties are valid for the selected block", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Validation warnings or errors", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        BlockStateData state = inputValues.get(INPUT_BASE_STATE_ID) instanceof BlockStateData base
            ? base.copy()
            : new BlockStateData();

        String blockType = normalizeBlockId(inputValues.get(INPUT_BLOCK_TYPE_ID));
        if (blockType != null) {
            state.setProperty("blockId", blockType);
        }

        putString(state, INPUT_PROPERTY_NAME_ID, INPUT_PROPERTY_VALUE_ID);
        putShortcut(state, "facing", inputValues.get(INPUT_FACING_ID));
        putShortcut(state, "axis", inputValues.get(INPUT_AXIS_ID));
        putShortcut(state, "half", inputValues.get(INPUT_HALF_ID));
        if (inputValues.get(INPUT_WATERLOGGED_ID) instanceof Boolean waterlogged) {
            state.setBooleanProperty("waterlogged", waterlogged);
        }

        ValidationResult validation = validateState(blockType, state);
        outputValues.put(OUTPUT_BLOCK_STATE_ID, state);
        outputValues.put(OUTPUT_BLOCK_INFO_ID, state);
        outputValues.put(OUTPUT_PROPERTY_COUNT_ID, state.size());
        outputValues.put(OUTPUT_VALID_ID, validation.valid());
        outputValues.put(OUTPUT_ERROR_ID, validation.message());
    }

    private void putString(BlockStateData state, String namePortId, String valuePortId) {
        Object nameObj = inputValues.get(namePortId);
        Object valueObj = inputValues.get(valuePortId);
        if (!(nameObj instanceof String name) || !(valueObj instanceof String value)) {
            return;
        }
        putProperty(state, name, value);
    }

    private void putShortcut(BlockStateData state, String property, Object valueObj) {
        if (valueObj instanceof String value) {
            putProperty(state, property, value);
        }
    }

    private void putProperty(BlockStateData state, String rawName, String rawValue) {
        if (rawName == null || rawValue == null) {
            return;
        }
        String name = rawName.trim().toLowerCase(Locale.ROOT);
        String value = rawValue.trim().toLowerCase(Locale.ROOT);
        if (!name.isEmpty() && !value.isEmpty()) {
            state.setProperty(name, value);
        }
    }

    private static @Nullable String normalizeBlockId(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim().toLowerCase(Locale.ROOT);
        return trimmed.contains(":") ? trimmed : "minecraft:" + trimmed;
    }

    private ValidationResult validateState(@Nullable String blockType, BlockStateData state) {
        if (blockType == null) {
            return new ValidationResult(true, "");
        }

        Block block;
        try {
            block = Registries.BLOCK.get(Identifier.of(blockType));
        } catch (Throwable e) {
            return new ValidationResult(true, "Block registry unavailable; skipped property validation.");
        }

        List<String> errors = new ArrayList<>();
        for (String key : state.keySet()) {
            if ("blockId".equals(key) || "id".equals(key)) {
                continue;
            }
            Property<?> property = findProperty(block, key);
            if (property == null) {
                errors.add("Unsupported property '" + key + "' for " + blockType);
                continue;
            }
            String value = state.get(key);
            if (property.parse(value).isEmpty()) {
                errors.add("Invalid value '" + value + "' for property '" + key + "'");
            }
        }
        return new ValidationResult(errors.isEmpty(), String.join("; ", errors));
    }

    private static @Nullable Property<?> findProperty(Block block, String name) {
        for (Property<?> property : block.getDefaultState().getProperties()) {
            if (property.getName().equals(name)) {
                return property;
            }
        }
        return null;
    }

    private record ValidationResult(boolean valid, String message) {
    }
}
