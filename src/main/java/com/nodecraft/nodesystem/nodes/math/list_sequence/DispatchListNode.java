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
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.list.dispatch_list",
    displayName = "Dispatch List",
    description = "Splits a list with a BOOLEAN_LIST mask of equal length (preserves element type T).",
    category = "math.list"
)
public class DispatchListNode extends BaseNode {

    private static final String LIST_T = "T";
    private static final String INPUT_LIST_ID = "input_list";
    private static final String INPUT_CONDITION_ID = "input_condition";
    private static final String OUTPUT_TRUE_LIST_ID = "output_true";
    private static final String OUTPUT_FALSE_LIST_ID = "output_false";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public DispatchListNode() {
        super(UUID.randomUUID(), "math.list.dispatch_list");

        addInputPort(new BasePort(INPUT_LIST_ID, "List", "The list to split", NodeDataType.LIST, this)
                .bindListType(LIST_T));
        addInputPort(new BasePort(INPUT_CONDITION_ID, "Mask",
                "Boolean mask (one boolean per item, same length)", NodeDataType.BOOLEAN_LIST, this));
        addOutputPort(new BasePort(OUTPUT_TRUE_LIST_ID, "True List",
                "Items for which the mask was true", NodeDataType.LIST, this).bindListType(LIST_T));
        addOutputPort(new BasePort(OUTPUT_FALSE_LIST_ID, "False List",
                "Items for which the mask was false", NodeDataType.LIST, this).bindListType(LIST_T));
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

        List<Object> trueList = new ArrayList<>();
        List<Object> falseList = new ArrayList<>();
        for (int i = 0; i < inputList.size(); i++) {
            Object condObj = conditionList.get(i);
            if (!(condObj instanceof Boolean condition)) {
                writeInvalid();
                return;
            }
            if (condition) {
                trueList.add(inputList.get(i));
            } else {
                falseList.add(inputList.get(i));
            }
        }

        outputValues.put(OUTPUT_TRUE_LIST_ID, trueList);
        outputValues.put(OUTPUT_FALSE_LIST_ID, falseList);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_TRUE_LIST_ID, List.of());
        outputValues.put(OUTPUT_FALSE_LIST_ID, List.of());
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    @Override
    public Object getNodeState() {
        return java.util.Map.of();
    }

    @Override
    public void setNodeState(Object state) {
        // Legacy useDefaultValue / defaultValue ignored.
    }
}
