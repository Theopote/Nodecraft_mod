package com.nodecraft.nodesystem.nodes.input.values;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "input.values.dropdown",
    displayName = "Value List",
    description = "Selects one value from a string option list and outputs index + text value.",
    category = "input.values",
    order = 10
)
public class DropdownSelectorNode extends BaseNode {

    @NodeProperty(displayName = "Options", category = "Value", order = 1,
        description = "Comma-separated options used when Options port is unconnected")
    private String options = "Option A, Option B, Option C";

    @NodeProperty(displayName = "Selected Index", category = "Value", order = 2)
    private int selectedIndex = 0;

    private static final String INPUT_INDEX_ID = "input_index";
    private static final String INPUT_OPTIONS_ID = "input_options";

    private static final String OUTPUT_INDEX_ID = "output_index";
    private static final String OUTPUT_VALUE_ID = "output_value";
    private static final String OUTPUT_OPTIONS_ID = "output_options";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public DropdownSelectorNode() {
        super(UUID.randomUUID(), "input.values.dropdown");
        addInputPort(new BasePort(INPUT_INDEX_ID, "Index",
            "Optional selected index override; connected out-of-range fails closed",
            NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_OPTIONS_ID, "Options",
            "Optional string-list options override; connected invalid fails closed (no CSV fallback)",
            NodeDataType.STRING_LIST, this));

        addOutputPort(new BasePort(OUTPUT_INDEX_ID, "Index", "Selected option index", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALUE_ID, "Value", "Selected option text", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_OPTIONS_ID, "Options", "Resolved option list", NodeDataType.STRING_LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid",
            "True when options are available and optional Index/Options drives are valid",
            NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error",
            "Why selection failed when Valid is false",
            NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<String> optionList = OptionalPortDrive.resolveOptionalStringList(
            this,
            INPUT_OPTIONS_ID,
            parseCsvOptions(options)
        );
        if (optionList == null) {
            publishInvalid("Options is connected but null or invalid.");
            return;
        }
        if (optionList.isEmpty()) {
            publishInvalid("Options list is empty.");
            return;
        }

        Integer index = OptionalPortDrive.resolveOptionalInteger(this, INPUT_INDEX_ID, selectedIndex);
        if (index == null) {
            publishInvalid("Index is connected but null or invalid.");
            return;
        }

        boolean indexConnected = OptionalPortDrive.isConnected(this, INPUT_INDEX_ID);
        int resolvedIndex;
        if (indexConnected) {
            if (index < 0 || index >= optionList.size()) {
                publishInvalid("Index (" + index + ") is out of range for " + optionList.size() + " options.");
                return;
            }
            resolvedIndex = index;
        } else {
            // UI property path: normalize into range.
            resolvedIndex = Math.max(0, Math.min(optionList.size() - 1, index));
        }
        String value = optionList.get(resolvedIndex);

        outputValues.put(OUTPUT_INDEX_ID, resolvedIndex);
        outputValues.put(OUTPUT_VALUE_ID, value);
        outputValues.put(OUTPUT_OPTIONS_ID, List.copyOf(optionList));
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void publishInvalid(String error) {
        outputValues.put(OUTPUT_INDEX_ID, 0);
        outputValues.put(OUTPUT_VALUE_ID, "");
        outputValues.put(OUTPUT_OPTIONS_ID, List.of());
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("options", options);
        state.put("selectedIndex", selectedIndex);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("options") instanceof String text) {
            setOptions(text);
        }
        if (map.get("selectedIndex") instanceof Integer i) {
            setSelectedIndex(i);
        }
    }

    public String getOptions() {
        return options;
    }

    public void setOptions(String value) {
        String resolved = value != null ? value : "";
        if (!resolved.equals(options)) {
            options = resolved;
            markDirty();
        }
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public void setSelectedIndex(int value) {
        int resolved = Math.max(0, value);
        if (selectedIndex != resolved) {
            selectedIndex = resolved;
            markDirty();
        }
    }

    private static List<String> parseCsvOptions(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        String normalized = raw.replace('\n', ',').replace(';', ',');
        for (String token : normalized.split(",")) {
            String text = token.trim();
            if (!text.isEmpty()) {
                out.add(text);
            }
        }
        return out;
    }
}
