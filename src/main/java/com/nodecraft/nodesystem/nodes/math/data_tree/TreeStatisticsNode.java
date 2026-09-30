package com.nodecraft.nodesystem.nodes.math.data_tree;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
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
    effect = NodeEffect.PURE,
    id = "math.data_tree.statistics",
    displayName = "Tree Statistics",
    description = "Reports branch count, item count, depth, and branch sizes for a data tree.",
    category = "math.data_tree",
    order = 6
)
public class TreeStatisticsNode extends BaseNode {
    private static final String INPUT_TREE_ID = "input_tree";
    private static final String OUTPUT_BRANCH_COUNT_ID = "output_branch_count";
    private static final String OUTPUT_ITEM_COUNT_ID = "output_item_count";
    private static final String OUTPUT_MAX_DEPTH_ID = "output_max_depth";
    private static final String OUTPUT_BRANCH_SIZES_ID = "output_branch_sizes";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public TreeStatisticsNode() {
        super(UUID.randomUUID(), "math.data_tree.statistics");
        addInputPort(new BasePort(INPUT_TREE_ID, "Tree", "Data tree to inspect", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_BRANCH_COUNT_ID, "Branch Count", "Number of branches",
                NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_ITEM_COUNT_ID, "Item Count", "Total number of items",
                NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_MAX_DEPTH_ID, "Max Depth", "Deepest path length",
                NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_BRANCH_SIZES_ID, "Branch Sizes", "Item count per branch",
                NodeDataType.INTEGER_LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether statistics succeeded",
                NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false",
                NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object treeValue = inputValues.get(INPUT_TREE_ID);
        DataTreeNodeUtils.ParseResult<DataTreeData> treeResult = DataTreeNodeUtils.parseTree(treeValue);
        if (!treeResult.valid()) {
            writeInvalid(treeResult.error());
            return;
        }
        DataTreeData tree = treeResult.value();
        List<Integer> sizes = null;
        if (tree != null) {
            sizes = new ArrayList<>(tree.getBranchCount());
        }
        if (tree != null) {
            for (DataTreeData.Branch branch : tree.getBranches()) {
                sizes.add(branch.items().size());
            }
        }
        if (tree != null) {
            outputValues.put(OUTPUT_BRANCH_COUNT_ID, tree.getBranchCount());
        }
        if (tree != null) {
            outputValues.put(OUTPUT_ITEM_COUNT_ID, tree.getItemCount());
        }
        if (tree != null) {
            outputValues.put(OUTPUT_MAX_DEPTH_ID, tree.getMaxDepth());
        }
        if (sizes != null) {
            outputValues.put(OUTPUT_BRANCH_SIZES_ID, List.copyOf(sizes));
        }
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_BRANCH_COUNT_ID, 0);
        outputValues.put(OUTPUT_ITEM_COUNT_ID, 0);
        outputValues.put(OUTPUT_MAX_DEPTH_ID, 0);
        outputValues.put(OUTPUT_BRANCH_SIZES_ID, List.of());
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}
