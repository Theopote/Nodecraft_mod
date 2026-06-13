package com.nodecraft.nodesystem.nodes.math.data_tree;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
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
    id = "math.data_tree.merge",
    displayName = "Merge Trees",
    description = "Merges two data trees or lists into one data tree",
    category = "math.data_tree",
    order = 8
)
public class MergeTreesNode extends BaseNode {
    @NodeProperty(displayName = "Preserve Source Index", category = "Merge", order = 1)
    private boolean preserveSourceIndex = true;

    private static final String INPUT_A_ID = "input_a";
    private static final String INPUT_B_ID = "input_b";
    private static final String OUTPUT_TREE_ID = "output_tree";
    private static final String OUTPUT_BRANCH_COUNT_ID = "output_branch_count";
    private static final String OUTPUT_ITEM_COUNT_ID = "output_item_count";

    public MergeTreesNode() {
        super(UUID.randomUUID(), "math.data_tree.merge");
        addInputPort(new BasePort(INPUT_A_ID, "A", "First tree or list", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_B_ID, "B", "Second tree or list", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_TREE_ID, "Tree", "Merged data tree", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_BRANCH_COUNT_ID, "Branch Count", "Number of merged branches", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_ITEM_COUNT_ID, "Item Count", "Total item count", NodeDataType.INTEGER, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        DataTreeData treeA = DataTreeNodeUtils.resolveTree(inputValues.get(INPUT_A_ID));
        DataTreeData treeB = DataTreeNodeUtils.resolveTree(inputValues.get(INPUT_B_ID));
        List<DataTreeData.Branch> branches = new ArrayList<>();
        appendBranches(branches, treeA, 0);
        appendBranches(branches, treeB, 1);

        DataTreeData merged = new DataTreeData(branches);
        outputValues.put(OUTPUT_TREE_ID, merged);
        outputValues.put(OUTPUT_BRANCH_COUNT_ID, merged.getBranchCount());
        outputValues.put(OUTPUT_ITEM_COUNT_ID, merged.getItemCount());
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("preserveSourceIndex", preserveSourceIndex);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> map && map.get("preserveSourceIndex") instanceof Boolean value) {
            preserveSourceIndex = value;
            markDirty();
        }
    }

    public boolean isPreserveSourceIndex() {
        return preserveSourceIndex;
    }

    public void setPreserveSourceIndex(boolean preserveSourceIndex) {
        this.preserveSourceIndex = preserveSourceIndex;
        markDirty();
    }

    private void appendBranches(List<DataTreeData.Branch> output, DataTreeData tree, int sourceIndex) {
        for (DataTreeData.Branch branch : tree.getBranches()) {
            List<Integer> path = new ArrayList<>();
            if (preserveSourceIndex) {
                path.add(sourceIndex);
                path.addAll(branch.path());
            } else {
                path.add(output.size());
            }
            output.add(new DataTreeData.Branch(path, branch.items()));
        }
    }
}
