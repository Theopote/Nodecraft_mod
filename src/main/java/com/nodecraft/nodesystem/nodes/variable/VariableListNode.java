package com.nodecraft.nodesystem.nodes.variable;

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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.CONTEXT_READ,
    id = "variable.list",
    displayName = "Variable List",
    description = "Lists user variables currently available in the execution scope.",
    category = "variable",
    order = 2
)
public class VariableListNode extends BaseNode {

    @NodeProperty(displayName = "Sort Names", category = "Variable", order = 1)
    private boolean sortNames = true;

    private static final String INPUT_PREFIX_ID = "input_prefix";

    private static final String OUTPUT_NAMES_ID = "output_names";
    private static final String OUTPUT_VALUES_ID = "output_values";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public VariableListNode() {
        super(UUID.randomUUID(), "variable.list");

        addInputPort(new BasePort(INPUT_PREFIX_ID, "Prefix", "Optional name prefix filter", NodeDataType.STRING, this));

        addOutputPort(new BasePort(OUTPUT_NAMES_ID, "Names", "Variable names", NodeDataType.STRING_LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALUES_ID, "Values", "Variable values", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of listed variables", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether prefix resolved correctly", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why listing failed", NodeDataType.STRING, this));
    }

    @Override
    public String getDisplayName() {
        return "Variable List";
    }

    @Override
    public String getDescription() {
        return "Lists user variables currently available in the execution scope.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        String prefix = OptionalPortDrive.resolveOptionalString(this, INPUT_PREFIX_ID, "");
        if (prefix == null) {
            writeFailure("Prefix is connected but null or invalid.");
            return;
        }

        Map<String, Object> snapshot = VariableScopeBridge.snapshot(context);

        List<Map.Entry<String, Object>> entries = new ArrayList<>(snapshot.entrySet());
        if (sortNames) {
            entries.sort(Comparator.comparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER));
        }

        List<Object> names = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        for (Map.Entry<String, Object> entry : entries) {
            String name = entry.getKey();
            if (VariableScopeBridge.isInternalVariableName(name)) {
                continue;
            }
            if (!prefix.isEmpty() && (name == null || !name.startsWith(prefix))) {
                continue;
            }

            names.add(name);
            values.add(entry.getValue());
        }

        outputValues.put(OUTPUT_NAMES_ID, names);
        outputValues.put(OUTPUT_VALUES_ID, values);
        outputValues.put(OUTPUT_COUNT_ID, names.size());
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeFailure(String error) {
        outputValues.put(OUTPUT_NAMES_ID, List.of());
        outputValues.put(OUTPUT_VALUES_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
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
