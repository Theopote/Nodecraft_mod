package com.nodecraft.nodesystem.nodes.math.data_tree;

import com.nodecraft.nodesystem.api.ListElementKind;
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
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.data_tree.merge",
    displayName = "Merge Trees",
    description = "Merges two data trees by concatenating items on matching paths (preserves T).",
    category = "math.data_tree",
    order = 8
)
public class MergeTreesNode extends BaseNode {
    private static final String LIST_T = "T";
    private static final String INPUT_A_ID = "input_a";
    private static final String INPUT_B_ID = "input_b";
    private static final String OUTPUT_TREE_ID = "output_tree";
    private static final String OUTPUT_BRANCH_COUNT_ID = "output_branch_count";
    private static final String OUTPUT_ITEM_COUNT_ID = "output_item_count";

    public MergeTreesNode() {
        super(UUID.randomUUID(), "math.data_tree.merge");
        addInputPort(new BasePort(INPUT_A_ID, "A", "First data tree", NodeDataType.DATA_TREE, this)
                .bindListType(LIST_T));
        addInputPort(new BasePort(INPUT_B_ID, "B", "Second data tree", NodeDataType.DATA_TREE, this)
                .bindListType(LIST_T));
        addOutputPort(new BasePort(OUTPUT_TREE_ID, "Tree", "Merged data tree", NodeDataType.DATA_TREE, this)
                .bindListType(LIST_T));
        addOutputPort(new BasePort(OUTPUT_BRANCH_COUNT_ID, "Branch Count", "Number of merged branches",
                NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_ITEM_COUNT_ID, "Item Count", "Total item count",
                NodeDataType.INTEGER, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object valueA = inputValues.get(INPUT_A_ID);
        Object valueB = inputValues.get(INPUT_B_ID);
        DataTreeData treeA = DataTreeNodeUtils.requireTree(valueA);
        DataTreeData treeB = DataTreeNodeUtils.requireTree(valueB);
        ListElementKind kind = DataTreeNodeUtils.mergeKinds(
                DataTreeNodeUtils.resolveElementKindFromTreePort(this, INPUT_A_ID, valueA),
                DataTreeNodeUtils.resolveElementKindFromTreePort(this, INPUT_B_ID, valueB));

        List<DataTreeData.Branch> branches = new ArrayList<>();
        branches.addAll(treeA.getBranches());
        branches.addAll(treeB.getBranches());

        DataTreeData merged = new DataTreeData(branches, kind);
        outputValues.put(OUTPUT_TREE_ID, merged);
        outputValues.put(OUTPUT_BRANCH_COUNT_ID, merged.getBranchCount());
        outputValues.put(OUTPUT_ITEM_COUNT_ID, merged.getItemCount());
    }

    @Override
    public Object getNodeState() {
        return Map.of();
    }

    @Override
    public void setNodeState(Object state) {
        // Legacy preserveSourceIndex ignored — use Entwine for source-indexed isolation.
    }
}
