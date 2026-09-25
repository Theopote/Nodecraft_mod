package com.nodecraft.nodesystem.nodes.math.list_sequence;

import com.nodecraft.nodesystem.api.IPort;
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
    id = "math.list.filter_list",
    displayName = "Filter List",
    description = "Filters a list with a BOOLEAN_LIST mask of equal length (preserves element type T).",
    category = "math.list"
)
public class FilterListNode extends BaseNode {

    private boolean invert = false;

    private static final String LIST_T = "T";
    private static final String INPUT_LIST_ID = "input_list";
    private static final String INPUT_CONDITION_ID = "input_condition";
    private static final String OUTPUT_LIST_ID = "output_list";
    private static final String OUTPUT_REMOVED_ID = "output_removed";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public FilterListNode() {
        super(UUID.randomUUID(), "math.list.filter_list");

        addInputPort(new BasePort(INPUT_LIST_ID, "List", "The list to filter", NodeDataType.LIST, this)
                .bindListType(LIST_T));
        addInputPort(new BasePort(INPUT_CONDITION_ID, "Mask",
                "Boolean mask (one boolean per item, same length)", NodeDataType.BOOLEAN_LIST, this));
        addOutputPort(new BasePort(OUTPUT_LIST_ID, "Filtered", "Items kept by the mask", NodeDataType.LIST, this)
                .bindListType(LIST_T));
        addOutputPort(new BasePort(OUTPUT_REMOVED_ID, "Removed", "Items removed by the mask", NodeDataType.LIST, this)
                .bindListType(LIST_T));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Number of kept items", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether mask length matched the list",
                NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object listObj = inputValues.get(INPUT_LIST_ID);
        Object conditionObj = inputValues.get(INPUT_CONDITION_ID);

        if (!(listObj instanceof List<?> inputList) || !(conditionObj instanceof List<?> conditionList)) {
            writeInvalid();
            return;
        }
        if (conditionList.size() != inputList.size()) {
            writeInvalid();
            return;
        }

        List<Object> filtered = new ArrayList<>();
        List<Object> removed = new ArrayList<>();
        for (int i = 0; i < inputList.size(); i++) {
            Object item = inputList.get(i);
            Object condObj = conditionList.get(i);
            if (!(condObj instanceof Boolean keepRaw)) {
                writeInvalid();
                return;
            }
            boolean keep = invert ? !keepRaw : keepRaw;
            if (keep) {
                filtered.add(item);
            } else {
                removed.add(item);
            }
        }

        outputValues.put(OUTPUT_LIST_ID, filtered);
        outputValues.put(OUTPUT_REMOVED_ID, removed);
        outputValues.put(OUTPUT_COUNT_ID, filtered.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_LIST_ID, List.of());
        outputValues.put(OUTPUT_REMOVED_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    public boolean isInvert() {
        return invert;
    }

    public void setInvert(boolean invert) {
        if (this.invert != invert) {
            this.invert = invert;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("invert", isInvert());
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> stateMap)) {
            return;
        }
        Object invertObj = stateMap.get("invert");
        if (invertObj instanceof Boolean value) {
            setInvert(value);
        }
    }
}
