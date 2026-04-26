package com.nodecraft.nodesystem.nodes.input.values;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "input.values.dropdown",
    displayName = "Dropdown Selector",
    description = "Selects one value from user-defined option list and outputs index + text value.",
    category = "input.values",
    order = 10
)
public class DropdownSelectorNode extends BaseNode {

    @NodeProperty(displayName = "Options", category = "Value", order = 1)
    private String options = "Option A, Option B, Option C";

    @NodeProperty(displayName = "Selected Index", category = "Value", order = 2)
    private int selectedIndex = 0;

    private static final String INPUT_INDEX_ID = "input_index";
    private static final String INPUT_OPTIONS_ID = "input_options";

    private static final String OUTPUT_INDEX_ID = "output_index";
    private static final String OUTPUT_VALUE_ID = "output_value";
    private static final String OUTPUT_OPTIONS_ID = "output_options";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public DropdownSelectorNode() {
        super(UUID.randomUUID(), "input.values.dropdown");
        addInputPort(new BasePort(INPUT_INDEX_ID, "Index", "Optional selected index override", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_OPTIONS_ID, "Options", "Optional list/string options override", NodeDataType.ANY, this));

        addOutputPort(new BasePort(OUTPUT_INDEX_ID, "Index", "Selected option index", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALUE_ID, "Value", "Selected option text", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_OPTIONS_ID, "Options", "Resolved option list", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when options are available", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDescription() {
        return "Selects one value from user-defined option list and outputs index + text value.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<String> optionList = resolveOptions(inputValues.get(INPUT_OPTIONS_ID));
        if (optionList.isEmpty()) {
            outputValues.put(OUTPUT_INDEX_ID, 0);
            outputValues.put(OUTPUT_VALUE_ID, "");
            outputValues.put(OUTPUT_OPTIONS_ID, List.of());
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        int index = inputValues.get(INPUT_INDEX_ID) instanceof Number n ? n.intValue() : selectedIndex;
        index = Math.max(0, Math.min(optionList.size() - 1, index));
        String value = optionList.get(index);

        outputValues.put(OUTPUT_INDEX_ID, index);
        outputValues.put(OUTPUT_VALUE_ID, value);
        outputValues.put(OUTPUT_OPTIONS_ID, List.copyOf(optionList));
        outputValues.put(OUTPUT_VALID_ID, true);
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
            options = text;
        }
        if (map.get("selectedIndex") instanceof Number n) {
            selectedIndex = n.intValue();
        }
    }

    private List<String> resolveOptions(Object value) {
        if (value instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                if (item == null) {
                    continue;
                }
                String text = String.valueOf(item).trim();
                if (!text.isEmpty()) {
                    out.add(text);
                }
            }
            return out;
        }

        String raw = value instanceof String text ? text : options;
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

