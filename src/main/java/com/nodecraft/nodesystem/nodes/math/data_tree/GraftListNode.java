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
    id = "math.data_tree.graft_list",
    displayName = "Graft List",
    description = "Converts each list item into its own data tree branch (preserves element type T).",
    category = "math.data_tree",
    order = 1
)
public class GraftListNode extends BaseNode {
    private static final String LIST_T = "T";
    private static final String INPUT_LIST_ID = "input_list";
    private static final String OUTPUT_TREE_ID = "output_tree";
    private static final String OUTPUT_BRANCH_COUNT_ID = "output_branch_count";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public GraftListNode() {
        super(UUID.randomUUID(), "math.data_tree.graft_list");
        addInputPort(new BasePort(INPUT_LIST_ID, "List", "List to graft into one branch per item",
                NodeDataType.LIST, this).bindListType(LIST_T));
        addOutputPort(new BasePort(OUTPUT_TREE_ID, "Tree", "Grafted data tree",
                NodeDataType.DATA_TREE, this).bindListType(LIST_T));
        addOutputPort(new BasePort(OUTPUT_BRANCH_COUNT_ID, "Branch Count", "Number of created branches",
                NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether grafting succeeded",
                NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false",
                NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        ListElementKind kind = DataTreeNodeUtils.resolveElementKindFromListPort(this, INPUT_LIST_ID);

        DataTreeNodeUtils.ParseResult<List<?>> listResult =
                DataTreeNodeUtils.parseList(inputValues.get(INPUT_LIST_ID));
        if (!listResult.valid()) {
            writeInvalid(kind, listResult.error());
            return;
        }
        List<?> list = listResult.value();

        DataTreeNodeUtils.ParseResult<Void> nullCheck = null;
        if (list != null) {
            nullCheck = DataTreeNodeUtils.validateNonNullItems(list);
        }
        if (nullCheck != null && !nullCheck.valid()) {
            writeInvalid(kind, nullCheck.error());
            return;
        }

        DataTreeNodeUtils.ParseResult<Void> kindCheck = DataTreeNodeUtils.validateItemsMatchKind(list, kind);
        if (!kindCheck.valid()) {
            writeInvalid(kind, kindCheck.error());
            return;
        }

        List<DataTreeData.Branch> branches = null;
        if (list != null) {
            branches = new ArrayList<>(list.size());
        }
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                branches.add(new DataTreeData.Branch(List.of(i), new ArrayList<>(List.of(list.get(i)))));
            }
        }
        DataTreeData tree = new DataTreeData(branches, kind);
        outputValues.put(OUTPUT_TREE_ID, tree);
        outputValues.put(OUTPUT_BRANCH_COUNT_ID, tree.getBranchCount());
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(ListElementKind kind, String error) {
        outputValues.put(OUTPUT_TREE_ID, DataTreeData.empty(kind));
        outputValues.put(OUTPUT_BRANCH_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}
