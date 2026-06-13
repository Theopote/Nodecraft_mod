package com.nodecraft.nodesystem.nodes.math.data_tree;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "math.data_tree.graft_list",
    displayName = "Graft List",
    description = "Converts each list item into its own data tree branch",
    category = "math.data_tree",
    order = 1
)
public class GraftListNode extends BaseNode {
    private static final String INPUT_LIST_ID = "input_list";
    private static final String OUTPUT_TREE_ID = "output_tree";
    private static final String OUTPUT_BRANCH_COUNT_ID = "output_branch_count";

    public GraftListNode() {
        super(UUID.randomUUID(), "math.data_tree.graft_list");
        addInputPort(new BasePort(INPUT_LIST_ID, "List", "List to graft into one branch per item", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_TREE_ID, "Tree", "Grafted data tree", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_BRANCH_COUNT_ID, "Branch Count", "Number of created branches", NodeDataType.INTEGER, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Object> list = DataTreeNodeUtils.resolveList(inputValues.get(INPUT_LIST_ID));
        List<DataTreeData.Branch> branches = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            branches.add(new DataTreeData.Branch(List.of(i), List.of(list.get(i))));
        }
        DataTreeData tree = new DataTreeData(branches);
        outputValues.put(OUTPUT_TREE_ID, tree);
        outputValues.put(OUTPUT_BRANCH_COUNT_ID, tree.getBranchCount());
    }
}
