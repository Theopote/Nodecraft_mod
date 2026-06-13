package com.nodecraft.nodesystem.nodes.math.data_tree;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "math.data_tree.item",
    displayName = "Tree Item",
    description = "Gets one item from a data tree branch by path and index",
    category = "math.data_tree",
    order = 5
)
public class TreeItemNode extends BaseNode {
    @NodeProperty(displayName = "Allow Negative Index", category = "Index", order = 1)
    private boolean allowNegativeIndex = true;

    @NodeProperty(displayName = "Wrap Index", category = "Index", order = 2)
    private boolean wrapIndex = false;

    private static final String INPUT_TREE_ID = "input_tree";
    private static final String INPUT_PATH_ID = "input_path";
    private static final String INPUT_INDEX_ID = "input_index";
    private static final String OUTPUT_ITEM_ID = "output_item";
    private static final String OUTPUT_FOUND_ID = "output_found";

    public TreeItemNode() {
        super(UUID.randomUUID(), "math.data_tree.item");
        addInputPort(new BasePort(INPUT_TREE_ID, "Tree", "Data tree to query", NodeDataType.DATA_TREE, this));
        addInputPort(new BasePort(INPUT_PATH_ID, "Path", "Branch path such as 0, {0}, or {0;1}", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_INDEX_ID, "Index", "Item index inside the branch", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_ITEM_ID, "Item", "Selected item", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_FOUND_ID, "Found", "Whether the item was found", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        DataTreeData tree = DataTreeNodeUtils.resolveTree(inputValues.get(INPUT_TREE_ID));
        List<Integer> path = DataTreeNodeUtils.parsePath(inputValues.get(INPUT_PATH_ID), List.of(0));
        Object indexObj = inputValues.get(INPUT_INDEX_ID);
        DataTreeData.Branch branch = tree.getBranch(path);
        if (branch == null || !(indexObj instanceof Number number)) {
            writeNotFound();
            return;
        }
        int index = DataTreeNodeUtils.normalizeIndex(number.intValue(), branch.items().size(), allowNegativeIndex, wrapIndex);
        if (index < 0) {
            writeNotFound();
            return;
        }
        outputValues.put(OUTPUT_ITEM_ID, branch.items().get(index));
        outputValues.put(OUTPUT_FOUND_ID, true);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("allowNegativeIndex", allowNegativeIndex);
        state.put("wrapIndex", wrapIndex);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("allowNegativeIndex") instanceof Boolean value) allowNegativeIndex = value;
        if (map.get("wrapIndex") instanceof Boolean value) wrapIndex = value;
        markDirty();
    }

    public boolean isAllowNegativeIndex() {
        return allowNegativeIndex;
    }

    public void setAllowNegativeIndex(boolean allowNegativeIndex) {
        this.allowNegativeIndex = allowNegativeIndex;
        markDirty();
    }

    public boolean isWrapIndex() {
        return wrapIndex;
    }

    public void setWrapIndex(boolean wrapIndex) {
        this.wrapIndex = wrapIndex;
        markDirty();
    }

    private void writeNotFound() {
        outputValues.put(OUTPUT_ITEM_ID, null);
        outputValues.put(OUTPUT_FOUND_ID, false);
    }
}
