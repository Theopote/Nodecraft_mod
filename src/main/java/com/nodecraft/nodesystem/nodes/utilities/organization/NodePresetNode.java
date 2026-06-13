package com.nodecraft.nodesystem.nodes.utilities.organization;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "utilities.organization.preset",
    displayName = "Runtime Preset",
    description = "Saves, loads, and deletes named runtime presets in execution context.",
    category = "utilities.organization",
    order = 7
)
public class NodePresetNode extends BaseNode {

    @NodeProperty(displayName = "Default Preset Name", category = "Preset", order = 1)
    private String defaultPresetName = "preset";

    private static final String INPUT_PRESET_NAME_ID = "input_preset_name";
    private static final String INPUT_VALUE_ID = "input_value";
    private static final String INPUT_DEFAULT_VALUE_ID = "input_default_value";
    private static final String INPUT_SAVE_ID = "input_save";
    private static final String INPUT_LOAD_ID = "input_load";
    private static final String INPUT_DELETE_ID = "input_delete";

    private static final String OUTPUT_VALUE_ID = "output_value";
    private static final String OUTPUT_EXISTS_ID = "output_exists";
    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_PRESET_NAMES_ID = "output_preset_names";
    private static final String OUTPUT_MESSAGE_ID = "output_message";

    public NodePresetNode() {
        super(UUID.randomUUID(), "utilities.organization.preset");

        addInputPort(new BasePort(INPUT_PRESET_NAME_ID, "Preset Name", "Preset key", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_VALUE_ID, "Value", "Value to save", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_DEFAULT_VALUE_ID, "Default", "Fallback value when preset is missing", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_SAVE_ID, "Save", "Save preset when true", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_LOAD_ID, "Load", "Load preset when true", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_DELETE_ID, "Delete", "Delete preset when true", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_VALUE_ID, "Value", "Resolved preset value", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_EXISTS_ID, "Exists", "Whether preset exists", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether operation succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_PRESET_NAMES_ID, "Preset Names", "Available preset names", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_MESSAGE_ID, "Message", "Operation result message", NodeDataType.STRING, this));
    }

    @Override
    public String getDisplayName() {
        return "Node Preset";
    }

    @Override
    public String getDescription() {
        return "Saves, loads, and deletes named node presets in execution context.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        String presetName = resolvePresetName(inputValues.get(INPUT_PRESET_NAME_ID));
        Object value = inputValues.get(INPUT_VALUE_ID);
        Object defaultValue = inputValues.get(INPUT_DEFAULT_VALUE_ID);
        boolean save = Boolean.TRUE.equals(inputValues.get(INPUT_SAVE_ID));
        boolean load = Boolean.TRUE.equals(inputValues.get(INPUT_LOAD_ID));
        boolean delete = Boolean.TRUE.equals(inputValues.get(INPUT_DELETE_ID));

        if (context == null) {
            writeResult(defaultValue, false, false, Map.of(), "Missing execution context.");
            return;
        }

        Map<String, Object> presets = getOrCreatePresets(context);

        if (presetName == null || presetName.isBlank()) {
            writeResult(defaultValue, false, false, presets, "Preset name is empty.");
            return;
        }

        if (delete) {
            boolean existed = presets.containsKey(presetName);
            Object removed = presets.remove(presetName);
            writeResult(removed, existed, true, presets, existed ? "Preset deleted." : "Preset not found.");
            return;
        }

        if (save) {
            presets.put(presetName, value);
            writeResult(value, true, true, presets, "Preset saved.");
            return;
        }

        if (load) {
            boolean existed = presets.containsKey(presetName);
            Object loaded = existed ? presets.get(presetName) : defaultValue;
            writeResult(loaded, existed, true, presets, existed ? "Preset loaded." : "Preset missing; returned default.");
            return;
        }

        boolean existed = presets.containsKey(presetName);
        Object passthrough = existed ? presets.get(presetName) : defaultValue;
        writeResult(passthrough, existed, true, presets, "No operation selected; returned current/default value.");
    }

    private void writeResult(Object value, boolean exists, boolean success, Map<String, Object> presets, String message) {
        outputValues.put(OUTPUT_VALUE_ID, value);
        outputValues.put(OUTPUT_EXISTS_ID, exists);
        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_PRESET_NAMES_ID, new ArrayList<>(presets.keySet()));
        outputValues.put(OUTPUT_MESSAGE_ID, message);
    }

    private String resolvePresetName(Object inputName) {
        if (inputName instanceof String name && !name.isBlank()) {
            return name.trim();
        }
        if (defaultPresetName == null || defaultPresetName.isBlank()) {
            return null;
        }
        return defaultPresetName.trim();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getOrCreatePresets(@Nullable ExecutionContext context) {
        Object existing = context.getVariable(GraphIOKeys.NODE_PRESETS_KEY);
        if (existing instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        Map<String, Object> created = new LinkedHashMap<>();
        context.setVariable(GraphIOKeys.NODE_PRESETS_KEY, created);
        return created;
    }
}
