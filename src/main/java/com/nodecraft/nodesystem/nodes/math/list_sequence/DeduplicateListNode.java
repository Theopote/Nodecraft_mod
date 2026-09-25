package com.nodecraft.nodesystem.nodes.math.list_sequence;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.list.deduplicate",
    displayName = "Deduplicate List",
    description = "Removes duplicate values, keeping first occurrence order (preserves element type T).",
    category = "math.list",
    order = 20
)
public class DeduplicateListNode extends BaseNode {

    private static final String LIST_T = "T";
    private static final String INPUT_LIST_ID = "input_list";
    private static final String OUTPUT_UNIQUE_ID = "output_unique";
    private static final String OUTPUT_REMOVED_ID = "output_removed";
    private static final String OUTPUT_UNIQUE_COUNT_ID = "output_unique_count";
    private static final String OUTPUT_REMOVED_COUNT_ID = "output_removed_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public DeduplicateListNode() {
        super(UUID.randomUUID(), "math.list.deduplicate");

        addInputPort(new BasePort(INPUT_LIST_ID, "List", "List to deduplicate", NodeDataType.LIST, this)
                .bindListType(LIST_T));
        addOutputPort(new BasePort(OUTPUT_UNIQUE_ID, "Unique", "List with duplicates removed",
                NodeDataType.LIST, this).bindListType(LIST_T));
        addOutputPort(new BasePort(OUTPUT_REMOVED_ID, "Removed", "Values removed as duplicates",
                NodeDataType.LIST, this).bindListType(LIST_T));
        addOutputPort(new BasePort(OUTPUT_UNIQUE_COUNT_ID, "Unique Count", "Unique list size",
                NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_REMOVED_COUNT_ID, "Removed Count", "Removed duplicate count",
                NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether input is a valid list",
                NodeDataType.BOOLEAN, this));
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

        Set<Object> seen = new LinkedHashSet<>();
        List<Object> unique = new ArrayList<>();
        List<Object> removed = new ArrayList<>();
        for (Object value : list) {
            if (seen.add(value)) {
                unique.add(value);
            } else {
                removed.add(value);
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
        return Map.of();
    }

    @Override
    public void setNodeState(Object state) {
        // Legacy preserveOrder ignored — first-occurrence order is always used.
    }
}
