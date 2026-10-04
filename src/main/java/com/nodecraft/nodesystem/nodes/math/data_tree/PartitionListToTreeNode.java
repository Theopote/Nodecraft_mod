package com.nodecraft.nodesystem.nodes.math.data_tree;

import com.nodecraft.nodesystem.api.ListElementKind;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.StrictIntegerUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.data_tree.partition_list",
    displayName = "Partition List To Tree",
    description = "Splits a list into fixed-size data tree branches (keeps incomplete last branch; preserves T).",
    category = "math.data_tree",
    order = 3
)
public class PartitionListToTreeNode extends BaseNode {

    public static final String ERROR_INVALID_SIZE = "invalid_size";

    private static final String LIST_T = "T";
    private static final String INPUT_LIST_ID = "input_list";
    private static final String INPUT_SIZE_ID = "input_size";
    private static final String OUTPUT_TREE_ID = "output_tree";
    private static final String OUTPUT_BRANCH_COUNT_ID = "output_branch_count";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public PartitionListToTreeNode() {
        super(UUID.randomUUID(), "math.data_tree.partition_list");
        addInputPort(new BasePort(INPUT_LIST_ID, "List", "Input list", NodeDataType.LIST, this)
                .bindListType(LIST_T));
        addInputPort(new BasePort(INPUT_SIZE_ID, "Size", "Branch size", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_TREE_ID, "Tree", "Partitioned data tree", NodeDataType.DATA_TREE, this)
                .bindListType(LIST_T));
        addOutputPort(new BasePort(OUTPUT_BRANCH_COUNT_ID, "Branch Count", "Number of created branches",
                NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether partitioning succeeded",
                NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false",
                NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        ListElementKind kind = DataTreeNodeUtils.resolveElementKindFromListPort(this, INPUT_LIST_ID);

        Integer size = StrictIntegerUtils.requireExactInteger(inputValues.get(INPUT_SIZE_ID));
        if (size == null || size < 1) {
            writeInvalid(kind, ERROR_INVALID_SIZE);
            return;
        }

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

        long itemCount = list.size();
        long branchCount = itemCount == 0L ? 0L : (itemCount + size - 1L) / size;
        DataTreeNodeUtils.ParseResult<Void> budget =
                DataTreeNodeUtils.preflightTreeConstruction(branchCount, itemCount, 1);
        if (!budget.valid()) {
            writeInvalid(kind, budget.error());
            return;
        }

        List<DataTreeData.Branch> branches = new ArrayList<>((int) branchCount);
        int branchIndex = 0;
        for (int i = 0; i < list.size(); i += size) {
            int end = Math.min(i + size, list.size());
            List<Object> branchItems = new ArrayList<>(list.subList(i, end));
            branches.add(new DataTreeData.Branch(List.of(branchIndex), branchItems));
            branchIndex++;
        }

        DataTreeData tree = new DataTreeData(branches, kind);
        outputValues.put(OUTPUT_TREE_ID, tree);
        outputValues.put(OUTPUT_BRANCH_COUNT_ID, tree.getBranchCount());
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    @Override
    public Object getNodeState() {
        return Map.of();
    }

    @Override
    public void setNodeState(Object state) {
        // Legacy dropRemainder ignored.
    }

    private void writeInvalid(ListElementKind kind, String error) {
        outputValues.put(OUTPUT_TREE_ID, DataTreeData.empty(kind));
        outputValues.put(OUTPUT_BRANCH_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}
