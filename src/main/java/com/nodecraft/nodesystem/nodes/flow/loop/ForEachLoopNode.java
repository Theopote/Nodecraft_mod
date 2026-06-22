package com.nodecraft.nodesystem.nodes.flow.loop;

import com.nodecraft.nodesystem.api.ExecLoopNode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@NodeInfo(
    id = "flow.loop.for_each",
    displayName = "For Each Loop",
    description = "Expands a list into items. Wire exec_body for per-item side effects; legacy list outputs remain for dataflow graphs.",
    category = "flow.loop",
    order = 0
)
public class ForEachLoopNode extends BaseNode implements ExecLoopNode {

    private static final String INPUT_EXEC_ID = "exec_in";
    private static final String INPUT_LIST_ID = "input_list";
    private static final String INPUT_ENABLED_ID = "input_enabled";

    private static final String OUTPUT_EXEC_BODY_ID = "exec_body";
    private static final String OUTPUT_EXEC_COMPLETE_ID = "exec_complete";
    private static final String OUTPUT_ITEM_ID = "output_item";
    private static final String OUTPUT_INDEX_ID = "output_index";
    private static final String OUTPUT_ITEMS_ID = "output_items";
    private static final String OUTPUT_INDICES_ID = "output_indices";
    private static final String OUTPUT_PAIRS_ID = "output_pairs";
    private static final String OUTPUT_FIRST_ITEM_ID = "output_first_item";
    private static final String OUTPUT_LAST_ITEM_ID = "output_last_item";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    private transient List<Object> resolvedItems = List.of();
    private transient Set<String> activeExecOutputs = Set.of();

    public ForEachLoopNode() {
        super(UUID.randomUUID(), "flow.loop.for_each");

        addInputPort(new BasePort(INPUT_EXEC_ID, "Exec In", "Incoming execution pulse", NodeDataType.EXEC, this, true, false));
        addInputPort(new BasePort(INPUT_LIST_ID, "List", "List to iterate", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_ENABLED_ID, "Enabled", "Whether iteration is enabled", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_EXEC_BODY_ID, "Exec Body", "Fires once per list item", NodeDataType.EXEC, this));
        addOutputPort(new BasePort(OUTPUT_EXEC_COMPLETE_ID, "Exec Complete", "Fires after all items are processed", NodeDataType.EXEC, this));
        addOutputPort(new BasePort(OUTPUT_ITEM_ID, "Item", "Current iterated item", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_INDEX_ID, "Index", "Current item index", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_ITEMS_ID, "Items", "Iterated items", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_INDICES_ID, "Indices", "Item indices", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_PAIRS_ID, "Pairs", "List of {index,item} maps", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_FIRST_ITEM_ID, "First Item", "First iterated item", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_LAST_ITEM_ID, "Last Item", "Last iterated item", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of iterated items", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether list input is valid", NodeDataType.BOOLEAN, this));
    }

    @Override
    public Set<String> getActiveExecOutputPortIds() {
        return activeExecOutputs;
    }

    @Override
    public int execLoopIterationCount() {
        return resolvedItems.size();
    }

    @Override
    public void prepareExecLoopIteration(int iterationIndex) {
        outputValues.put(OUTPUT_EXEC_BODY_ID, null);
        outputValues.put(OUTPUT_EXEC_COMPLETE_ID, null);
        activeExecOutputs = Set.of();

        if (iterationIndex < 0 || iterationIndex >= resolvedItems.size()) {
            outputValues.put(OUTPUT_ITEM_ID, null);
            outputValues.put(OUTPUT_INDEX_ID, null);
            syncOutputPorts();
            return;
        }

        outputValues.put(OUTPUT_ITEM_ID, resolvedItems.get(iterationIndex));
        outputValues.put(OUTPUT_INDEX_ID, iterationIndex);
        outputValues.put(OUTPUT_EXEC_BODY_ID, Boolean.TRUE);
        activeExecOutputs = Set.of(OUTPUT_EXEC_BODY_ID);
        syncOutputPorts();
    }

    @Override
    public String execBodyPortId() {
        return OUTPUT_EXEC_BODY_ID;
    }

    @Override
    public String execCompletePortId() {
        return OUTPUT_EXEC_COMPLETE_ID;
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object listObj = inputValues.get(INPUT_LIST_ID);
        boolean enabled = coerceEnabled(inputValues.get(INPUT_ENABLED_ID));

        activeExecOutputs = Set.of();
        outputValues.put(OUTPUT_EXEC_BODY_ID, null);
        outputValues.put(OUTPUT_EXEC_COMPLETE_ID, null);
        outputValues.put(OUTPUT_ITEM_ID, null);
        outputValues.put(OUTPUT_INDEX_ID, null);

        if (!enabled) {
            resolvedItems = List.of();
            writeEmptyOutputs(true);
            return;
        }

        if (!(listObj instanceof List<?> inputList)) {
            resolvedItems = List.of();
            writeEmptyOutputs(false);
            return;
        }

        List<Object> items = new ArrayList<>(inputList);
        resolvedItems = items;
        writeExpandedOutputs(items);
    }

    private void writeEmptyOutputs(boolean valid) {
        outputValues.put(OUTPUT_ITEMS_ID, List.of());
        outputValues.put(OUTPUT_INDICES_ID, List.of());
        outputValues.put(OUTPUT_PAIRS_ID, List.of());
        outputValues.put(OUTPUT_FIRST_ITEM_ID, null);
        outputValues.put(OUTPUT_LAST_ITEM_ID, null);
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, valid);
    }

    private void writeExpandedOutputs(List<Object> items) {
        List<Object> indices = new ArrayList<>(items.size());
        List<Object> pairs = new ArrayList<>(items.size());

        for (int i = 0; i < items.size(); i++) {
            indices.add(i);
            Map<String, Object> pair = new LinkedHashMap<>();
            pair.put("index", i);
            pair.put("item", items.get(i));
            pairs.add(pair);
        }

        Object firstItem = items.isEmpty() ? null : items.getFirst();
        Object lastItem = items.isEmpty() ? null : items.getLast();

        outputValues.put(OUTPUT_ITEMS_ID, items);
        outputValues.put(OUTPUT_INDICES_ID, indices);
        outputValues.put(OUTPUT_PAIRS_ID, pairs);
        outputValues.put(OUTPUT_FIRST_ITEM_ID, firstItem);
        outputValues.put(OUTPUT_LAST_ITEM_ID, lastItem);
        outputValues.put(OUTPUT_COUNT_ID, items.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private boolean coerceEnabled(Object value) {
        switch (value) {
            case null -> {
                return true;
            }
            case Boolean booleanValue -> {
                return booleanValue;
            }
            case Number number -> {
                return number.doubleValue() != 0.0d;
            }
            case String stringValue -> {
                String normalized = stringValue.trim();
                if (normalized.isEmpty()) {
                    return false;
                }
                return switch (normalized.toLowerCase(Locale.ROOT)) {
                    case "true", "yes", "1", "on" -> true;
                    default -> false;
                };
            }
            default -> {
            }
        }
        return true;
    }
}
