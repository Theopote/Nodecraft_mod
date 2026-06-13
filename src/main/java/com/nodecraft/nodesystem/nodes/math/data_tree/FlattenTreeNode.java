package com.nodecraft.nodesystem.nodes.math.data_tree;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    id = "math.data_tree.flatten",
    displayName = "Flatten Tree",
    description = "Flattens all data tree branches into a single list",
    category = "math.data_tree",
    order = 2
)
public class FlattenTreeNode extends BaseNode {
    private static final String INPUT_TREE_ID = "input_tree";
    private static final String OUTPUT_LIST_ID = "output_list";
    private static final String OUTPUT_ITEM_COUNT_ID = "output_item_count";

    public FlattenTreeNode() {
        super(UUID.randomUUID(), "math.data_tree.flatten");
        addInputPort(new BasePort(INPUT_TREE_ID, "Tree", "Data tree to flatten", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_LIST_ID, "List", "Flattened list", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_ITEM_COUNT_ID, "Item Count", "Total item count", NodeDataType.INTEGER, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        DataTreeData tree = DataTreeNodeUtils.resolveTree(inputValues.get(INPUT_TREE_ID));
        outputValues.put(OUTPUT_LIST_ID, tree.flatten());
        outputValues.put(OUTPUT_ITEM_COUNT_ID, tree.getItemCount());
    }
}
