package com.nodecraft.nodesystem.nodes.math.list_sequence;

import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.ListElementKind;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.PortTypeResolver;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.list.group_list",
    displayName = "Group List",
    description = "Groups list items by parallel keys (same length) into a DATA_TREE. Null items forbidden.",
    category = "math.list"
)
public class GroupListNode extends BaseNode {

    public static final String ERROR_INVALID_INPUT = "invalid_input";
    public static final String ERROR_LENGTH_MISMATCH = "length_mismatch";
    public static final String ERROR_NULL_ITEM = "null_item";
    public static final String ERROR_NULL_KEY = "null_key";

    private boolean skipInvalidKeys = false;

    private static final String INPUT_LIST_ID = "input_list";
    private static final String INPUT_KEYS_ID = "input_keys";
    private static final String OUTPUT_TREE_ID = "output_tree";
    private static final String OUTPUT_KEYS_ID = "output_unique_keys";
    private static final String OUTPUT_COUNT_ID = "output_group_count";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public GroupListNode() {
        super(UUID.randomUUID(), "math.list.group_list");

        IPort listInput = new BasePort(INPUT_LIST_ID, "List",
                "The list to group", NodeDataType.LIST, this).bindListType("T");
        addInputPort(listInput);

        IPort keysInput = new BasePort(INPUT_KEYS_ID, "Keys",
                "Keys to group by (must match List length)", NodeDataType.LIST, this);
        addInputPort(keysInput);

        IPort treeOutput = new BasePort(OUTPUT_TREE_ID, "Tree",
                "Grouped items as a data tree (one branch per unique key)", NodeDataType.DATA_TREE, this)
                .bindListType("T");
        addOutputPort(treeOutput);

        IPort keysOutput = new BasePort(OUTPUT_KEYS_ID, "Unique Keys",
                "List of unique keys found", NodeDataType.LIST, this);
        addOutputPort(keysOutput);

        IPort countOutput = new BasePort(OUTPUT_COUNT_ID, "Group Count",
                "Number of groups created", NodeDataType.INTEGER, this);
        addOutputPort(countOutput);

        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether grouping succeeded",
                NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when grouping failed",
                NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object listObj = inputValues.get(INPUT_LIST_ID);
        Object keysObj = inputValues.get(INPUT_KEYS_ID);
        ListElementKind elementKind = resolveListElementKind();

        if (!(listObj instanceof List<?> inputList) || !(keysObj instanceof List<?> keysList)) {
            writeFailure(elementKind, ERROR_INVALID_INPUT);
            return;
        }

        if (inputList.size() != keysList.size()) {
            writeFailure(elementKind, ERROR_LENGTH_MISMATCH);
            return;
        }

        for (Object item : inputList) {
            if (item == null) {
                writeFailure(elementKind, ERROR_NULL_ITEM);
                return;
            }
        }

        if (!skipInvalidKeys) {
            for (Object key : keysList) {
                if (key == null) {
                    writeFailure(elementKind, ERROR_NULL_KEY);
                    return;
                }
            }
        }

        Map<Object, List<Object>> groups = new HashMap<>();
        List<Object> uniqueKeys = new ArrayList<>();

        for (int i = 0; i < inputList.size(); i++) {
            Object item = inputList.get(i);
            Object key = keysList.get(i);

            if (key == null && skipInvalidKeys) {
                continue;
            }

            if (!groups.containsKey(key)) {
                List<Object> group = new ArrayList<>();
                group.add(item);
                groups.put(key, group);
                uniqueKeys.add(key);
            } else {
                groups.get(key).add(item);
            }
        }

        List<DataTreeData.Branch> branches = new ArrayList<>(uniqueKeys.size());
        for (int i = 0; i < uniqueKeys.size(); i++) {
            Object key = uniqueKeys.get(i);
            branches.add(new DataTreeData.Branch(List.of(i), groups.get(key)));
        }

        outputValues.put(OUTPUT_TREE_ID, new DataTreeData(branches, elementKind));
        outputValues.put(OUTPUT_KEYS_ID, uniqueKeys);
        outputValues.put(OUTPUT_COUNT_ID, uniqueKeys.size());
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeFailure(ListElementKind elementKind, String error) {
        outputValues.put(OUTPUT_TREE_ID, DataTreeData.empty(elementKind));
        outputValues.put(OUTPUT_KEYS_ID, List.of());
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error);
    }

    private ListElementKind resolveListElementKind() {
        for (IPort port : getInputPorts()) {
            if (port != null && INPUT_LIST_ID.equals(port.getId())) {
                NodeDataType effective = PortTypeResolver.resolveEffectiveType(port);
                if (effective != null && effective.isListType()) {
                    return effective.getListElementKind();
                }
            }
        }
        return ListElementKind.UNCONSTRAINED;
    }

    public boolean isSkipInvalidKeys() {
        return skipInvalidKeys;
    }

    public void setSkipInvalidKeys(boolean skip) {
        if (this.skipInvalidKeys != skip) {
            this.skipInvalidKeys = skip;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("skipInvalidKeys", isSkipInvalidKeys());
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> stateMap)) {
            return;
        }
        Object skip = stateMap.get("skipInvalidKeys");
        if (skip instanceof Boolean value) {
            setSkipInvalidKeys(value);
        }
    }
}
