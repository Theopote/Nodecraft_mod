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
    id = "math.data_tree.simplify",
    displayName = "Simplify Tree",
    description = "Removes the common leading path prefix from all data tree branches",
    category = "math.data_tree",
    order = 9
)
public class SimplifyTreeNode extends BaseNode {
    private static final String INPUT_TREE_ID = "input_tree";
    private static final String OUTPUT_TREE_ID = "output_tree";
    private static final String OUTPUT_REMOVED_PREFIX_ID = "output_removed_prefix";
    private static final String OUTPUT_BRANCH_COUNT_ID = "output_branch_count";

    public SimplifyTreeNode() {
        super(UUID.randomUUID(), "math.data_tree.simplify");
        addInputPort(new BasePort(INPUT_TREE_ID, "Tree", "Data tree to simplify", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_TREE_ID, "Tree", "Simplified data tree", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_REMOVED_PREFIX_ID, "Removed Prefix", "Common path prefix removed from every branch", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_BRANCH_COUNT_ID, "Branch Count", "Number of output branches", NodeDataType.INTEGER, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        DataTreeData tree = DataTreeNodeUtils.resolveTree(inputValues.get(INPUT_TREE_ID));
        List<Integer> prefix = commonPrefix(tree);
        List<DataTreeData.Branch> branches = new ArrayList<>(tree.getBranchCount());
        for (DataTreeData.Branch branch : tree.getBranches()) {
            List<Integer> path = branch.path();
            List<Integer> simplifiedPath = path.size() <= prefix.size()
                ? List.of()
                : List.copyOf(path.subList(prefix.size(), path.size()));
            branches.add(new DataTreeData.Branch(simplifiedPath, branch.items()));
        }
        DataTreeData simplified = new DataTreeData(branches);
        outputValues.put(OUTPUT_TREE_ID, simplified);
        outputValues.put(OUTPUT_REMOVED_PREFIX_ID, prefix);
        outputValues.put(OUTPUT_BRANCH_COUNT_ID, simplified.getBranchCount());
    }

    private List<Integer> commonPrefix(DataTreeData tree) {
        if (tree.getBranches().isEmpty()) {
            return List.of();
        }
        List<Integer> prefix = new ArrayList<>(tree.getBranches().getFirst().path());
        for (DataTreeData.Branch branch : tree.getBranches()) {
            List<Integer> path = branch.path();
            int count = 0;
            int max = Math.min(prefix.size(), path.size());
            while (count < max && prefix.get(count).equals(path.get(count))) {
                count++;
            }
            prefix = new ArrayList<>(prefix.subList(0, count));
            if (prefix.isEmpty()) {
                break;
            }
        }
        return List.copyOf(prefix);
    }
}
