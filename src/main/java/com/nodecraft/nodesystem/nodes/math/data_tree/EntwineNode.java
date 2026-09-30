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
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.data_tree.entwine",
    displayName = "Entwine",
    description = "Combines up to four data trees under source-index path prefixes (preserves T).",
    category = "math.data_tree",
    order = 13
)
public class EntwineNode extends BaseNode {
    private static final String LIST_T = "T";
    private static final String INPUT_A_ID = "input_a";
    private static final String INPUT_B_ID = "input_b";
    private static final String INPUT_C_ID = "input_c";
    private static final String INPUT_D_ID = "input_d";
    private static final String OUTPUT_TREE_ID = "output_tree";
    private static final String OUTPUT_BRANCH_COUNT_ID = "output_branch_count";
    private static final String OUTPUT_ITEM_COUNT_ID = "output_item_count";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    private static final List<String> INPUT_IDS = List.of(INPUT_A_ID, INPUT_B_ID, INPUT_C_ID, INPUT_D_ID);

    public EntwineNode() {
        super(UUID.randomUUID(), "math.data_tree.entwine");
        addInputPort(new BasePort(INPUT_A_ID, "A", "First data tree", NodeDataType.DATA_TREE, this)
                .bindListType(LIST_T));
        addInputPort(new BasePort(INPUT_B_ID, "B", "Second data tree", NodeDataType.DATA_TREE, this)
                .bindListType(LIST_T));
        addInputPort(new BasePort(INPUT_C_ID, "C", "Third data tree", NodeDataType.DATA_TREE, this)
                .bindListType(LIST_T));
        addInputPort(new BasePort(INPUT_D_ID, "D", "Fourth data tree", NodeDataType.DATA_TREE, this)
                .bindListType(LIST_T));
        addOutputPort(new BasePort(OUTPUT_TREE_ID, "Tree", "Entwined data tree", NodeDataType.DATA_TREE, this)
                .bindListType(LIST_T));
        addOutputPort(new BasePort(OUTPUT_BRANCH_COUNT_ID, "Branch Count", "Number of output branches",
                NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_ITEM_COUNT_ID, "Item Count", "Total item count",
                NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether entwining succeeded",
                NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false",
                NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        ListElementKind kind = ListElementKind.UNCONSTRAINED;
        List<DataTreeData> presentTrees = new ArrayList<>(4);
        List<DataTreeData.Branch> branches = new ArrayList<>();

        for (int sourceIndex = 0; sourceIndex < INPUT_IDS.size(); sourceIndex++) {
            String portId = INPUT_IDS.get(sourceIndex);
            DataTreeNodeUtils.ConnectedTreeResult input = DataTreeNodeUtils.resolveConnectedTree(this, portId);
            if (input.state() == DataTreeNodeUtils.TreeInputState.INVALID) {
                writeInvalid(kind, input.error());
                return;
            }
            if (input.state() == DataTreeNodeUtils.TreeInputState.SKIP) {
                continue;
            }

            DataTreeData tree = input.tree();
            ListElementKind nextKind = DataTreeNodeUtils.resolveElementKindFromTreePort(this, portId, tree);
            DataTreeNodeUtils.ParseResult<ListElementKind> folded = DataTreeNodeUtils.foldKindsStrict(kind, nextKind);
            if (!folded.valid()) {
                writeInvalid(kind, folded.error());
                return;
            }
            kind = folded.value();

            if (tree.getBranchCount() == 0) {
                continue;
            }

            DataTreeNodeUtils.ParseResult<Void> depthCheck =
                    DataTreeNodeUtils.preflightEntwinePaths(tree, sourceIndex);
            if (!depthCheck.valid()) {
                writeInvalid(kind, depthCheck.error());
                return;
            }

            presentTrees.add(tree);
            for (DataTreeData.Branch branch : tree.getBranches()) {
                List<Integer> path = new ArrayList<>(1 + branch.path().size());
                path.add(sourceIndex);
                path.addAll(branch.path());
                branches.add(new DataTreeData.Branch(path, branch.items()));
            }
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

        DataTreeData result = new DataTreeData(branches, kind);
        outputValues.put(OUTPUT_TREE_ID, result);
        outputValues.put(OUTPUT_BRANCH_COUNT_ID, result.getBranchCount());
        outputValues.put(OUTPUT_ITEM_COUNT_ID, result.getItemCount());
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
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
