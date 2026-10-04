package com.nodecraft.nodesystem.nodes.math.list_sequence;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.ListIndexResolver;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.list.remove_item",
    displayName = "Remove Item",
    description = "Removes an item by index or value (preserves element type T).",
    category = "math.list"
)
public class RemoveItemNode extends BaseNode {

    private boolean useIndex = true;
    private boolean removeAllMatches = false;

    private static final String LIST_T = "T";
    private static final String INPUT_LIST_ID = "input_list";
    private static final String INPUT_INDEX_ID = "input_index";
    private static final String INPUT_VALUE_ID = "input_value";
    private static final String OUTPUT_LIST_ID = "output_list";
    private static final String OUTPUT_REMOVED_ID = "output_removed";
    private static final String OUTPUT_COUNT_ID = "output_remove_count";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public static final String ERROR_INVALID_INDEX = "invalid_index";
    public static final String ERROR_INVALID_INPUT = "invalid_input";

    public RemoveItemNode() {
        super(UUID.randomUUID(), "math.list.remove_item");

        addInputPort(new BasePort(INPUT_LIST_ID, "List", "The list to remove from", NodeDataType.LIST, this)
                .bindListType(LIST_T));
        addInputPort(new BasePort(INPUT_INDEX_ID, "Index", "Index to remove (0-based, negatives from end)",
                NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_VALUE_ID, "Value", "Value to remove when not using index",
                NodeDataType.ANY, this).bindListElementType(LIST_T));
        addOutputPort(new BasePort(OUTPUT_LIST_ID, "List", "The list after removal", NodeDataType.LIST, this)
                .bindListType(LIST_T));
        addOutputPort(new BasePort(OUTPUT_REMOVED_ID, "Removed", "First removed item", NodeDataType.ANY, this)
                .bindListElementType(LIST_T));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Remove Count", "Number of items removed",
                NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the remove operation was in-range / matched",
                NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false",
                NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object inputObj = inputValues.get(INPUT_LIST_ID);
        Object valueObj = inputValues.get(INPUT_VALUE_ID);
        boolean hasValueInput = inputValues.containsKey(INPUT_VALUE_ID);

        if (!(inputObj instanceof List<?> inputList)) {
            writeEmpty(false, ERROR_INVALID_INPUT);
            return;
        }

        List<Object> result = new ArrayList<>(inputList);
        Object removedItem = null;
        int removeCount = 0;
        boolean valid = false;

        if (useIndex) {
            ListIndexResolver.IndexResolveResult indexResult = ListIndexResolver.resolveRequiredIndex(
                    resolveValue(INPUT_INDEX_ID),
                    isDriven(INPUT_INDEX_ID)
            );
            if (!indexResult.valid()) {
                writeEmpty(false, ERROR_INVALID_INDEX);
                return;
            }
            int size = result.size();
            int index = ListIndexResolver.normalizeNegativeFromEnd(indexResult.index(), size);
            if (index < 0 || index >= size) {
                writeEmpty(false, ERROR_INVALID_INDEX);
                return;
            }
            removedItem = result.remove(index);
            removeCount = 1;
            valid = true;
        } else if (hasValueInput) {
            if (removeAllMatches) {
                List<Object> remaining = new ArrayList<>();
                for (Object item : result) {
                    if (Objects.equals(item, valueObj)) {
                        if (removedItem == null) {
                            removedItem = item;
                        }
                        removeCount++;
                    } else {
                        remaining.add(item);
                    }
                }
                result = remaining;
                valid = removeCount > 0;
            } else {
                int index = result.indexOf(valueObj);
                if (index >= 0) {
                    removedItem = result.remove(index);
                    removeCount = 1;
                    valid = true;
                }
            }
        }

        writeResult(result, removedItem, removeCount, valid, valid ? "" : ERROR_INVALID_INPUT);
    }

    private boolean isDriven(String portId) {
        return OptionalPortDrive.isConnected(this, portId) || inputValues.containsKey(portId);
    }

    private @Nullable Object resolveValue(String portId) {
        if (OptionalPortDrive.isConnected(this, portId)) {
            return getInput(portId);
        }
        return inputValues.get(portId);
    }

    private void writeEmpty(boolean valid, String error) {
        writeResult(List.of(), null, 0, valid, error);
    }

    private void writeResult(List<Object> list, Object removed, int count, boolean valid, String error) {
        outputValues.put(OUTPUT_LIST_ID, list);
        outputValues.put(OUTPUT_REMOVED_ID, removed);
        outputValues.put(OUTPUT_COUNT_ID, count);
        outputValues.put(OUTPUT_VALID_ID, valid);
        outputValues.put(OUTPUT_ERROR_ID, valid || error == null ? "" : error);
    }

    public boolean isUseIndex() {
        return useIndex;
    }

    public void setUseIndex(boolean useIndex) {
        if (this.useIndex != useIndex) {
            this.useIndex = useIndex;
            markDirty();
        }
    }

    public boolean isRemoveAllMatches() {
        return removeAllMatches;
    }

    public void setRemoveAllMatches(boolean removeAll) {
        if (this.removeAllMatches != removeAll) {
            this.removeAllMatches = removeAll;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("useIndex", isUseIndex());
        state.put("removeAllMatches", isRemoveAllMatches());
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> stateMap)) {
            return;
        }
        Object useIdx = stateMap.get("useIndex");
        if (useIdx instanceof Boolean value) {
            setUseIndex(value);
        }
        Object removeAll = stateMap.get("removeAllMatches");
        if (removeAll instanceof Boolean value) {
            setRemoveAllMatches(value);
        }
    }
}
