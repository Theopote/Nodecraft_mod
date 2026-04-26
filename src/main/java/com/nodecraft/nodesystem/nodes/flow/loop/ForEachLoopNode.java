package com.nodecraft.nodesystem.nodes.flow.loop;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
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
    id = "flow.loop.for_each",
    displayName = "For Each Loop",
    description = "Iterates over a list and exposes derived iteration data.",
    category = "flow.loop",
    order = 0
)
public class ForEachLoopNode extends BaseNode {

    private static final String INPUT_LIST_ID = "input_list";
    private static final String INPUT_ENABLED_ID = "input_enabled";

    private static final String OUTPUT_ITEMS_ID = "output_items";
    private static final String OUTPUT_INDICES_ID = "output_indices";
    private static final String OUTPUT_PAIRS_ID = "output_pairs";
    private static final String OUTPUT_FIRST_ITEM_ID = "output_first_item";
    private static final String OUTPUT_LAST_ITEM_ID = "output_last_item";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ForEachLoopNode() {
        super(UUID.randomUUID(), "flow.loop.for_each");

        addInputPort(new BasePort(INPUT_LIST_ID, "List", "List to iterate", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_ENABLED_ID, "Enabled", "Whether iteration is enabled", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_ITEMS_ID, "Items", "Iterated items", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_INDICES_ID, "Indices", "Item indices", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_PAIRS_ID, "Pairs", "List of {index,item} maps", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_FIRST_ITEM_ID, "First Item", "First iterated item", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_LAST_ITEM_ID, "Last Item", "Last iterated item", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of iterated items", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether list input is valid", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "For Each Loop";
    }

    @Override
    public String getDescription() {
        return "Iterates over a list and exposes derived iteration data.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object listObj = inputValues.get(INPUT_LIST_ID);
        boolean enabled = !Boolean.FALSE.equals(inputValues.get(INPUT_ENABLED_ID));

        if (!enabled) {
            outputValues.put(OUTPUT_ITEMS_ID, List.of());
            outputValues.put(OUTPUT_INDICES_ID, List.of());
            outputValues.put(OUTPUT_PAIRS_ID, List.of());
            outputValues.put(OUTPUT_FIRST_ITEM_ID, null);
            outputValues.put(OUTPUT_LAST_ITEM_ID, null);
            outputValues.put(OUTPUT_COUNT_ID, 0);
            outputValues.put(OUTPUT_VALID_ID, true);
            return;
        }

        if (!(listObj instanceof List<?> inputList)) {
            outputValues.put(OUTPUT_ITEMS_ID, List.of());
            outputValues.put(OUTPUT_INDICES_ID, List.of());
            outputValues.put(OUTPUT_PAIRS_ID, List.of());
            outputValues.put(OUTPUT_FIRST_ITEM_ID, null);
            outputValues.put(OUTPUT_LAST_ITEM_ID, null);
            outputValues.put(OUTPUT_COUNT_ID, 0);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        List<Object> items = new ArrayList<>(inputList);
        List<Object> indices = new ArrayList<>(items.size());
        List<Object> pairs = new ArrayList<>(items.size());

        for (int i = 0; i < items.size(); i++) {
            indices.add(i);
            Map<String, Object> pair = new LinkedHashMap<>();
            pair.put("index", i);
            pair.put("item", items.get(i));
            pairs.add(pair);
        }

        Object firstItem = items.isEmpty() ? null : items.get(0);
        Object lastItem = items.isEmpty() ? null : items.get(items.size() - 1);

        outputValues.put(OUTPUT_ITEMS_ID, items);
        outputValues.put(OUTPUT_INDICES_ID, indices);
        outputValues.put(OUTPUT_PAIRS_ID, pairs);
        outputValues.put(OUTPUT_FIRST_ITEM_ID, firstItem);
        outputValues.put(OUTPUT_LAST_ITEM_ID, lastItem);
        outputValues.put(OUTPUT_COUNT_ID, items.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }
}
