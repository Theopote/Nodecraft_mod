package com.nodecraft.nodesystem.nodes.math.list_sequence;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@NodeInfo(
    id = "math.list_sequence.deduplicate",
    displayName = "Deduplicate List",
    description = "Removes duplicate values from a list while preserving order.",
    category = "math.list_sequence",
    order = 20
)
public class DeduplicateListNode extends BaseNode {

    @NodeProperty(displayName = "Preserve Order", category = "Deduplicate", order = 1)
    private boolean preserveOrder = true;

    private static final String INPUT_LIST_ID = "input_list";

    private static final String OUTPUT_UNIQUE_ID = "output_unique";
    private static final String OUTPUT_REMOVED_ID = "output_removed";
    private static final String OUTPUT_UNIQUE_COUNT_ID = "output_unique_count";
    private static final String OUTPUT_REMOVED_COUNT_ID = "output_removed_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public DeduplicateListNode() {
        super(UUID.randomUUID(), "math.list_sequence.deduplicate");

        addInputPort(new BasePort(INPUT_LIST_ID, "List", "List to deduplicate", NodeDataType.LIST, this));

        addOutputPort(new BasePort(OUTPUT_UNIQUE_ID, "Unique", "List with duplicates removed", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_REMOVED_ID, "Removed", "Values removed as duplicates", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_UNIQUE_COUNT_ID, "Unique Count", "Unique list size", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_REMOVED_COUNT_ID, "Removed Count", "Removed duplicate count", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether input is a valid list", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Deduplicate List";
    }

    @Override
    public String getDescription() {
        return "Removes duplicate values from a list while preserving order.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object input = inputValues.get(INPUT_LIST_ID);
        if (!(input instanceof List<?> list)) {
            outputValues.put(OUTPUT_UNIQUE_ID, List.of());
            outputValues.put(OUTPUT_REMOVED_ID, List.of());
            outputValues.put(OUTPUT_UNIQUE_COUNT_ID, 0);
            outputValues.put(OUTPUT_REMOVED_COUNT_ID, 0);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        List<Object> removed = new ArrayList<>();
        List<Object> unique;
        if (preserveOrder) {
            Set<Object> seen = new LinkedHashSet<>();
            unique = new ArrayList<>();
            for (Object value : list) {
                if (seen.add(value)) {
                    unique.add(value);
                } else {
                    removed.add(value);
                }
            }
        } else {
            Set<Object> seen = new HashSet<>();
            unique = new ArrayList<>();
            for (Object value : list) {
                if (seen.add(value)) {
                    unique.add(value);
                } else {
                    removed.add(value);
                }
            }
        }

        outputValues.put(OUTPUT_UNIQUE_ID, unique);
        outputValues.put(OUTPUT_REMOVED_ID, removed);
        outputValues.put(OUTPUT_UNIQUE_COUNT_ID, unique.size());
        outputValues.put(OUTPUT_REMOVED_COUNT_ID, removed.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("preserveOrder", preserveOrder);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        Object preserveOrderValue = map.get("preserveOrder");
        if (preserveOrderValue instanceof Boolean value) {
            preserveOrder = value;
        }
    }
}
