package com.nodecraft.nodesystem.nodes.math.list_sequence;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
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
    effect = NodeEffect.PURE,
    id = "math.list.insert_item",
    displayName = "Insert Item",
    description = "Inserts an item at index (negatives from end). Invalid index → Valid=false.",
    category = "math.list"
)
public class InsertItemNode extends BaseNode {

    private boolean append = true;

    private static final String LIST_T = "T";
    private static final String INPUT_LIST_ID = "input_list";
    private static final String INPUT_INDEX_ID = "input_index";
    private static final String INPUT_VALUE_ID = "input_value";
    private static final String OUTPUT_LIST_ID = "output_list";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public InsertItemNode() {
        super(UUID.randomUUID(), "math.list.insert_item");

        addInputPort(new BasePort(INPUT_LIST_ID, "List", "The list to insert into", NodeDataType.LIST, this)
                .bindListType(LIST_T));
        addInputPort(new BasePort(INPUT_INDEX_ID, "Index", "Insert index (0-based, negatives from end)",
                NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_VALUE_ID, "Value", "The value to insert", NodeDataType.ANY, this)
                .bindListElementType(LIST_T));
        addOutputPort(new BasePort(OUTPUT_LIST_ID, "List", "The list with the inserted item",
                NodeDataType.LIST, this).bindListType(LIST_T));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the insert index was valid",
                NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object inputObj = inputValues.get(INPUT_LIST_ID);
        Object indexObj = inputValues.get(INPUT_INDEX_ID);
        Object valueObj = inputValues.get(INPUT_VALUE_ID);

        if (!(inputObj instanceof List<?> inputList)) {
            writeInvalid();
            return;
        }

        List<Object> result = new ArrayList<>(inputList);
        int size = result.size();
        int index = size;
        if (indexObj instanceof Number number) {
            index = number.intValue();
            if (index < 0) {
                index = size + index;
            }
        } else if (!append) {
            writeInvalid();
            return;
        }

        if (index < 0 || index > size) {
            if (append) {
                index = size;
            } else {
                writeInvalid();
                return;
            }
        }

        result.add(index, valueObj);
        outputValues.put(OUTPUT_LIST_ID, result);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_LIST_ID, List.of());
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    public boolean isAppend() {
        return append;
    }

    public void setAppend(boolean append) {
        if (this.append != append) {
            this.append = append;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("append", isAppend());
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> stateMap)) {
            return;
        }
        Object appendValue = stateMap.get("append");
        if (appendValue instanceof Boolean value) {
            setAppend(value);
        }
        // Legacy allowNegativeIndex ignored — negatives always supported.
    }
}
