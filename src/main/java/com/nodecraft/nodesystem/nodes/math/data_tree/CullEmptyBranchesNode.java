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
    id = "math.data_tree.cull_empty",
    displayName = "Cull Empty Branches",
    description = "Removes empty branches from a data tree",
    category = "math.data_tree",
    order = 12
)
public class CullEmptyBranchesNode extends BaseNode {
    private static final String INPUT_TREE_ID = "input_tree";
    private static final String OUTPUT_TREE_ID = "output_tree";
    private static final String OUTPUT_REMOVED_COUNT_ID = "output_removed_count";
    private static final String OUTPUT_BRANCH_COUNT_ID = "output_branch_count";

    public CullEmptyBranchesNode() {
        super(UUID.randomUUID(), "math.data_tree.cull_empty");
        addInputPort(new BasePort(INPUT_TREE_ID, "Tree", "Data tree to clean", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_TREE_ID, "Tree", "Tree without empty branches", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_REMOVED_COUNT_ID, "Removed Count", "Number of removed empty branches", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_BRANCH_COUNT_ID, "Branch Count", "Number of remaining branches", NodeDataType.INTEGER, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        DataTreeData tree = DataTreeNodeUtils.resolveTree(inputValues.get(INPUT_TREE_ID));
        List<DataTreeData.Branch> branches = new ArrayList<>();
        int removed = 0;
        for (DataTreeData.Branch branch : tree.getBranches()) {
            if (branch.items().isEmpty()) {
                removed++;
            } else {
                branches.add(branch);
            }
        }
        DataTreeData culled = new DataTreeData(branches);
        outputValues.put(OUTPUT_TREE_ID, culled);
        outputValues.put(OUTPUT_REMOVED_COUNT_ID, removed);
        outputValues.put(OUTPUT_BRANCH_COUNT_ID, culled.getBranchCount());
    }
}
