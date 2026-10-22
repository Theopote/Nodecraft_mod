package com.nodecraft.nodesystem.nodes.material.block_state;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockStateData;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "material.block_state.build_block_state",
    displayName = "Build Block State",
    description = "Builds and validates block-state property data from a block type, base state, and overrides",
    category = "material.block_state",
    order = 0
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
    private static final String OUTPUT_PROPERTY_COUNT_ID = "output_property_count";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    @NodeProperty(
            displayName = "Properties Text",
            category = "State",
            order = 1,
            description = "Compact block-state overrides, e.g. facing=north,waterlogged=false"
    )
    private String propertiesText = "";

    public BuildBlockStateNode() {
        super(UUID.randomUUID(), "material.block_state.build_block_state");

        addInputPort(new BasePort(INPUT_BASE_STATE_ID, "Base State", "Optional state data to copy before applying overrides", NodeDataType.BLOCK_STATE_DATA, this));
        addInputPort(new BasePort(INPUT_BLOCK_TYPE_ID, "Block Type", "Block id used for registry validation, e.g. minecraft:oak_stairs", NodeDataType.BLOCK_TYPE, this));
        addInputPort(new BasePort(INPUT_PROPERTY_NAME_ID, "Property", "Dynamic property name, e.g. facing or axis", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_PROPERTY_VALUE_ID, "Value", "Dynamic property value, e.g. north or x", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_FACING_ID, "Facing", "Shortcut for the facing property", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_AXIS_ID, "Axis", "Shortcut for the axis property", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_HALF_ID, "Half", "Shortcut for the half property", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_WATERLOGGED_ID, "Waterlogged", "Shortcut for the waterlogged property", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_BLOCK_STATE_ID, "Block State", "Composed block-state property data", NodeDataType.BLOCK_STATE_DATA, this));
        addOutputPort(new BasePort(OUTPUT_PROPERTY_COUNT_ID, "Property Count", "Number of state property entries", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when block type is known and all properties are valid", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Validation error when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        BlockStateData state;
        if (isDriven(INPUT_BASE_STATE_ID)) {
            if (!(inputValues.get(INPUT_BASE_STATE_ID) instanceof BlockStateData base)) {
                emitInvalid("Base State connected but invalid");
                return;
            }
            state = BlockStateValidationUtils.stripIdentityKeys(base);
        } else {
            state = new BlockStateData();
        }

        String blockType = BlockStateValidationUtils.normalizeBlockId(inputValues.get(INPUT_BLOCK_TYPE_ID));

        BlockStateValidationUtils.PropertiesTextResult textResult =
            BlockStateValidationUtils.applyPropertiesText(state, propertiesText);
        if (!textResult.valid()) {
            emitInvalid(textResult.error());
            return;
        }
        state = textResult.state();

        PropertyPairResult pair = applyDynamicPropertyPair(state);
        if (!pair.valid()) {
            emitInvalid(pair.error());
            return;
        }
        state = pair.state();

        ShortcutResult shortcuts = applyShortcuts(state);
        if (!shortcuts.valid()) {
            emitInvalid(shortcuts.error());
            return;
        }
        state = shortcuts.state();

        emit(state, BlockStateValidationUtils.validateProperties(blockType, state));
    }

    private void emit(BlockStateData state, BlockStateValidationUtils.ValidationResult validation) {
        if (!validation.valid()) {
            emitInvalid(validation.message());
            return;
        }
        outputValues.put(OUTPUT_BLOCK_STATE_ID, state);
        outputValues.put(OUTPUT_PROPERTY_COUNT_ID, BlockStateValidationUtils.propertyCount(state));
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void emitInvalid(String error) {
        outputValues.put(OUTPUT_BLOCK_STATE_ID, null);
        outputValues.put(OUTPUT_PROPERTY_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    private PropertyPairResult applyDynamicPropertyPair(BlockStateData state) {
        boolean nameDriven = isDriven(INPUT_PROPERTY_NAME_ID);
        boolean valueDriven = isDriven(INPUT_PROPERTY_VALUE_ID);
        if (!nameDriven && !valueDriven) {
            return PropertyPairResult.ok(state);
        }
        if (nameDriven != valueDriven) {
            return PropertyPairResult.fail("Property and Value must both be connected or both unconnected");
        }
        Object nameObj = inputValues.get(INPUT_PROPERTY_NAME_ID);
        Object valueObj = inputValues.get(INPUT_PROPERTY_VALUE_ID);
        if (!(nameObj instanceof String name) || name.isBlank()
            || !(valueObj instanceof String value) || value.isBlank()) {
            return PropertyPairResult.fail("Property and Value must be non-blank strings");
        }
        return PropertyPairResult.ok(BlockStateValidationUtils.putProperty(state, name, value));
    }

    private ShortcutResult applyShortcuts(BlockStateData state) {
        BlockStateData current = state;
        ShortcutResult facing = applyStringShortcut(current, INPUT_FACING_ID, "facing", "Facing");
        if (!facing.valid()) {
            return facing;
        }
        current = facing.state();
        ShortcutResult axis = applyStringShortcut(current, INPUT_AXIS_ID, "axis", "Axis");
        if (!axis.valid()) {
            return axis;
        }
        current = axis.state();
        ShortcutResult half = applyStringShortcut(current, INPUT_HALF_ID, "half", "Half");
        if (!half.valid()) {
            return half;
        }
        current = half.state();

        if (!isDriven(INPUT_WATERLOGGED_ID)) {
            return ShortcutResult.ok(current);
        }
        if (!(inputValues.get(INPUT_WATERLOGGED_ID) instanceof Boolean waterlogged)) {
            return ShortcutResult.fail("Waterlogged connected but invalid");
        }
        return ShortcutResult.ok(current.withBooleanProperty("waterlogged", waterlogged));
    }

    private ShortcutResult applyStringShortcut(BlockStateData state, String portId, String property, String label) {
        if (!isDriven(portId)) {
            return ShortcutResult.ok(state);
        }
        Object valueObj = inputValues.get(portId);
        if (!(valueObj instanceof String value) || value.isBlank()) {
            return ShortcutResult.fail(label + " connected but invalid");
        }
        return ShortcutResult.ok(BlockStateValidationUtils.putProperty(state, property, value));
    }

    private boolean isDriven(String portId) {
        return OptionalPortDrive.isConnected(this, portId) || isInputPresent(portId);
    }

    public String getPropertiesText() {
        return propertiesText;
    }

    public void setPropertiesText(String propertiesText) {
        this.propertiesText = propertiesText == null ? "" : propertiesText;
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("propertiesText", propertiesText);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> map && map.get("propertiesText") instanceof String text) {
            setPropertiesText(text);
        }
    }

    private record PropertyPairResult(boolean valid, BlockStateData state, String error) {
        static PropertyPairResult ok(BlockStateData state) {
            return new PropertyPairResult(true, state, "");
        }

        static PropertyPairResult fail(String error) {
            return new PropertyPairResult(false, new BlockStateData(), error);
        }
    }

    private record ShortcutResult(boolean valid, BlockStateData state, String error) {
        static ShortcutResult ok(BlockStateData state) {
            return new ShortcutResult(true, state, "");
        }

        static ShortcutResult fail(String error) {
            return new ShortcutResult(false, new BlockStateData(), error);
        }
    }
}
