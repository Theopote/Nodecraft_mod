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
    id = "math.data_tree.statistics",
    displayName = "Tree Statistics",
    description = "Reports branch count, item count, depth, paths, and branch sizes for a data tree",
    category = "math.data_tree",
    order = 6
)
public class TreeStatisticsNode extends BaseNode {
    private static final String INPUT_TREE_ID = "input_tree";
    private static final String OUTPUT_BRANCH_COUNT_ID = "output_branch_count";
    private static final String OUTPUT_ITEM_COUNT_ID = "output_item_count";
    private static final String OUTPUT_MAX_DEPTH_ID = "output_max_depth";
    private static final String OUTPUT_PATHS_ID = "output_paths";
    private static final String OUTPUT_BRANCH_SIZES_ID = "output_branch_sizes";

    public TreeStatisticsNode() {
        super(UUID.randomUUID(), "math.data_tree.statistics");
        addInputPort(new BasePort(INPUT_TREE_ID, "Tree", "Data tree to inspect", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_BRANCH_COUNT_ID, "Branch Count", "Number of branches", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_ITEM_COUNT_ID, "Item Count", "Total number of items", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_MAX_DEPTH_ID, "Max Depth", "Deepest path length", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_PATHS_ID, "Paths", "Branch paths as strings", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_BRANCH_SIZES_ID, "Branch Sizes", "Item count per branch", NodeDataType.LIST, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        DataTreeData tree = DataTreeNodeUtils.resolveTree(inputValues.get(INPUT_TREE_ID));
        List<String> paths = new ArrayList<>(tree.getBranchCount());
        List<Integer> sizes = new ArrayList<>(tree.getBranchCount());
        for (DataTreeData.Branch branch : tree.getBranches()) {
            paths.add(DataTreeData.formatPath(branch.path()));
            sizes.add(branch.items().size());
        }
        outputValues.put(OUTPUT_BRANCH_COUNT_ID, tree.getBranchCount());
        outputValues.put(OUTPUT_ITEM_COUNT_ID, tree.getItemCount());
        outputValues.put(OUTPUT_MAX_DEPTH_ID, tree.getMaxDepth());
        outputValues.put(OUTPUT_PATHS_ID, List.copyOf(paths));
        outputValues.put(OUTPUT_BRANCH_SIZES_ID, List.copyOf(sizes));
    }
}
