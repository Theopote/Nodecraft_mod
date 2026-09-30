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
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

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
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether merging succeeded",
                NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false",
                NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        ListElementKind resolvedKind = ListElementKind.UNCONSTRAINED;
        List<DataTreeData> presentTrees = new ArrayList<>(2);
        List<ListElementKind> presentKinds = new ArrayList<>(2);

        for (String portId : List.of(INPUT_A_ID, INPUT_B_ID)) {
            DataTreeNodeUtils.ConnectedTreeResult input = DataTreeNodeUtils.resolveConnectedTree(this, portId);
            if (input.state() == DataTreeNodeUtils.TreeInputState.INVALID) {
                writeInvalid(resolvedKind, input.error());
                return;
            }
            if (input.state() == DataTreeNodeUtils.TreeInputState.VALID) {
                presentTrees.add(input.tree());
                presentKinds.add(DataTreeNodeUtils.resolveElementKindFromTreePort(this, portId, input.tree()));
            }
        }

        ListElementKind kind = ListElementKind.UNCONSTRAINED;
        for (ListElementKind nextKind : presentKinds) {
            DataTreeNodeUtils.ParseResult<ListElementKind> folded = DataTreeNodeUtils.foldKindsStrict(kind, nextKind);
            if (!folded.valid()) {
                writeInvalid(resolvedKind, folded.error());
                return;
            }
            kind = folded.value();
        }

        if (isConstrained(kind)) {
            for (DataTreeData tree : presentTrees) {
                DataTreeNodeUtils.ParseResult<Void> kindCheck = DataTreeNodeUtils.validateTreeItemsMatchKind(tree, kind);
                if (!kindCheck.valid()) {
                    writeInvalid(kind, kindCheck.error());
                    return;
                }
            }
        }

        DataTreeNodeUtils.ParseResult<Void> budgetCheck =
                DataTreeNodeUtils.preflightCombinedTreeBudget(presentTrees);
        if (!budgetCheck.valid()) {
            writeInvalid(kind, budgetCheck.error());
            return;
        }

        List<DataTreeData.Branch> branches = new ArrayList<>();
        for (DataTreeData tree : presentTrees) {
            branches.addAll(tree.getBranches());
        }

        DataTreeData merged = new DataTreeData(branches, kind);
        outputValues.put(OUTPUT_TREE_ID, merged);
        outputValues.put(OUTPUT_BRANCH_COUNT_ID, merged.getBranchCount());
        outputValues.put(OUTPUT_ITEM_COUNT_ID, merged.getItemCount());
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    @Override
    public Object getNodeState() {
        return Map.of();
    }

    @Override
    public void setNodeState(Object state) {
        // Legacy preserveSourceIndex ignored — use Entwine for source-indexed isolation.
    }

    private void writeInvalid(ListElementKind kind, String error) {
        outputValues.put(OUTPUT_TREE_ID, DataTreeData.empty(kind));
        outputValues.put(OUTPUT_BRANCH_COUNT_ID, 0);
        outputValues.put(OUTPUT_ITEM_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    private static boolean isConstrained(ListElementKind kind) {
        return kind != null
                && kind != ListElementKind.UNCONSTRAINED
                && kind != ListElementKind.NONE;
    }
}
