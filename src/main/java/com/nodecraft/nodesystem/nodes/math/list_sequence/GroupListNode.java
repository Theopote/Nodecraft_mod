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
    description = "Groups list items by parallel keys into a DATA_TREE (one branch per unique key).",
    category = "math.list"
)
public class GroupListNode extends BaseNode {

    private boolean skipInvalidKeys = false;

    private static final String INPUT_LIST_ID = "input_list";
    private static final String INPUT_KEYS_ID = "input_keys";
    private static final String OUTPUT_TREE_ID = "output_tree";
    private static final String OUTPUT_KEYS_ID = "output_unique_keys";
    private static final String OUTPUT_COUNT_ID = "output_group_count";

    public GroupListNode() {
        super(UUID.randomUUID(), "math.list.group_list");

        IPort listInput = new BasePort(INPUT_LIST_ID, "List",
                "The list to group", NodeDataType.LIST, this).bindListType("T");
        addInputPort(listInput);

        IPort keysInput = new BasePort(INPUT_KEYS_ID, "Keys",
                "List of keys to group by", NodeDataType.LIST, this);
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
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object listObj = inputValues.get(INPUT_LIST_ID);
        Object keysObj = inputValues.get(INPUT_KEYS_ID);

        Map<Object, List<Object>> groups = new HashMap<>();
        List<Object> uniqueKeys = new ArrayList<>();

        if (listObj instanceof List<?> inputList && keysObj instanceof List<?> keysList) {
            for (int i = 0; i < inputList.size(); i++) {
                Object item = inputList.get(i);

                Object key = null;
                if (i < keysList.size()) {
                    key = keysList.get(i);
                }

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
        }

        List<DataTreeData.Branch> branches = new ArrayList<>(uniqueKeys.size());
        for (int i = 0; i < uniqueKeys.size(); i++) {
            Object key = uniqueKeys.get(i);
            branches.add(new DataTreeData.Branch(List.of(i), groups.get(key)));
        }

        outputValues.put(OUTPUT_TREE_ID, new DataTreeData(branches, resolveListElementKind()));
        outputValues.put(OUTPUT_KEYS_ID, uniqueKeys);
        outputValues.put(OUTPUT_COUNT_ID, uniqueKeys.size());
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
