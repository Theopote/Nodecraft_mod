package com.nodecraft.nodesystem.nodes.variable;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "variable.list",
    displayName = "Variable List",
    description = "Lists variables currently available in the execution scope.",
    category = "variable",
    order = 2
)
public class VariableListNode extends BaseNode {

    @NodeProperty(displayName = "Sort Names", category = "Variable", order = 1)
    private boolean sortNames = true;

    private static final String INPUT_PREFIX_ID = "input_prefix";

    private static final String OUTPUT_NAMES_ID = "output_names";
    private static final String OUTPUT_VALUES_ID = "output_values";
    private static final String OUTPUT_ENTRIES_ID = "output_entries";
    private static final String OUTPUT_COUNT_ID = "output_count";

    public VariableListNode() {
        super(UUID.randomUUID(), "variable.list");

        addInputPort(new BasePort(INPUT_PREFIX_ID, "Prefix", "Optional name prefix filter", NodeDataType.STRING, this));

        addOutputPort(new BasePort(OUTPUT_NAMES_ID, "Names", "Variable names", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALUES_ID, "Values", "Variable values", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_ENTRIES_ID, "Entries", "List of {name,value} maps", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of listed variables", NodeDataType.INTEGER, this));
    }

    @Override
    public String getDisplayName() {
        return "Variable List";
    }

    @Override
    public String getDescription() {
        return "Lists variables currently available in the execution scope.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        String prefix = resolvePrefix(inputValues.get(INPUT_PREFIX_ID));
        Map<String, Object> snapshot = VariableScopeBridge.snapshot(context);

        List<Map.Entry<String, Object>> entries = new ArrayList<>(snapshot.entrySet());
        if (sortNames) {
            entries.sort(Comparator.comparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER));
        }

        List<Object> names = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        List<Object> entryList = new ArrayList<>();

        for (Map.Entry<String, Object> entry : entries) {
            String name = entry.getKey();
            if (prefix != null && !prefix.isEmpty() && (name == null || !name.startsWith(prefix))) {
                continue;
            }

            names.add(name);
            values.add(entry.getValue());
            Map<String, Object> entryView = new LinkedHashMap<>();
            entryView.put("name", name);
            entryView.put("value", entry.getValue());
            entryList.add(entryView);
        }

        outputValues.put(OUTPUT_NAMES_ID, names);
        outputValues.put(OUTPUT_VALUES_ID, values);
        outputValues.put(OUTPUT_ENTRIES_ID, entryList);
        outputValues.put(OUTPUT_COUNT_ID, names.size());
    }

    private String resolvePrefix(Object prefixObj) {
        if (prefixObj instanceof String prefix) {
            return prefix;
        }
        return "";
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("sortNames", sortNames);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        Object sortNamesValue = map.get("sortNames");
        if (sortNamesValue instanceof Boolean value) {
            sortNames = value;
        }
    }
}
