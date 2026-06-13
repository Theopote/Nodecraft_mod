package com.nodecraft.nodesystem.nodes.math.data_tree;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "math.data_tree.branch",
    displayName = "Tree Branch",
    description = "Gets one branch from a data tree by path",
    category = "math.data_tree",
    order = 4
)
public class TreeBranchNode extends BaseNode {
    private static final String INPUT_TREE_ID = "input_tree";
    private static final String INPUT_PATH_ID = "input_path";
    private static final String OUTPUT_BRANCH_ID = "output_branch";
    private static final String OUTPUT_FOUND_ID = "output_found";
    private static final String OUTPUT_ITEM_COUNT_ID = "output_item_count";

    public TreeBranchNode() {
        super(UUID.randomUUID(), "math.data_tree.branch");
        addInputPort(new BasePort(INPUT_TREE_ID, "Tree", "Data tree to query", NodeDataType.DATA_TREE, this));
        addInputPort(new BasePort(INPUT_PATH_ID, "Path", "Branch path such as 0, {0}, or {0;1}", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_BRANCH_ID, "Branch", "Items in the selected branch", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_FOUND_ID, "Found", "Whether the branch was found", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ITEM_COUNT_ID, "Item Count", "Number of items in the branch", NodeDataType.INTEGER, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        DataTreeData tree = DataTreeNodeUtils.resolveTree(inputValues.get(INPUT_TREE_ID));
        List<Integer> path = DataTreeNodeUtils.parsePath(inputValues.get(INPUT_PATH_ID), List.of(0));
        DataTreeData.Branch branch = tree.getBranch(path);
        if (branch == null) {
            outputValues.put(OUTPUT_BRANCH_ID, List.of());
            outputValues.put(OUTPUT_FOUND_ID, false);
            outputValues.put(OUTPUT_ITEM_COUNT_ID, 0);
            return;
        }
        outputValues.put(OUTPUT_BRANCH_ID, branch.items());
        outputValues.put(OUTPUT_FOUND_ID, true);
        outputValues.put(OUTPUT_ITEM_COUNT_ID, branch.items().size());
    }
}
