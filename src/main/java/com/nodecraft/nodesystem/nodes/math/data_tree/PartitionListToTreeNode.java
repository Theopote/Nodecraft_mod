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
    id = "math.data_tree.partition_list",
    displayName = "Partition List To Tree",
    description = "Splits a list into fixed-size data tree branches",
    category = "math.data_tree",
    order = 3
)
public class PartitionListToTreeNode extends BaseNode {
    @NodeProperty(displayName = "Drop Remainder", category = "Partition", order = 1)
    private boolean dropRemainder = false;

    private static final String INPUT_LIST_ID = "input_list";
    private static final String INPUT_SIZE_ID = "input_size";
    private static final String OUTPUT_TREE_ID = "output_tree";
    private static final String OUTPUT_BRANCH_COUNT_ID = "output_branch_count";
    private static final String OUTPUT_REMAINDER_ID = "output_remainder";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public PartitionListToTreeNode() {
        super(UUID.randomUUID(), "math.data_tree.partition_list");
        addInputPort(new BasePort(INPUT_LIST_ID, "List", "Input list", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_SIZE_ID, "Size", "Branch size", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_TREE_ID, "Tree", "Partitioned data tree", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_BRANCH_COUNT_ID, "Branch Count", "Number of created branches", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_REMAINDER_ID, "Remainder", "Trailing items omitted when Drop Remainder is enabled", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether partitioning succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Object> list = DataTreeNodeUtils.resolveList(inputValues.get(INPUT_LIST_ID));
        Object sizeObj = inputValues.get(INPUT_SIZE_ID);
        if (!(sizeObj instanceof Number number) || number.intValue() <= 0) {
            writeInvalid();
            return;
        }

        int size = number.intValue();
        List<DataTreeData.Branch> branches = new ArrayList<>();
        List<Object> remainder = new ArrayList<>();
        int branchIndex = 0;
        for (int i = 0; i < list.size(); i += size) {
            int end = Math.min(i + size, list.size());
            List<Object> branchItems = new ArrayList<>(list.subList(i, end));
            if (branchItems.size() < size && dropRemainder) {
                remainder.addAll(branchItems);
            } else {
                branches.add(new DataTreeData.Branch(List.of(branchIndex), branchItems));
                branchIndex++;
            }
        }

        DataTreeData tree = new DataTreeData(branches);
        outputValues.put(OUTPUT_TREE_ID, tree);
        outputValues.put(OUTPUT_BRANCH_COUNT_ID, tree.getBranchCount());
        outputValues.put(OUTPUT_REMAINDER_ID, List.copyOf(remainder));
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("dropRemainder", dropRemainder);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> map && map.get("dropRemainder") instanceof Boolean value) {
            dropRemainder = value;
            markDirty();
        }
    }

    public boolean isDropRemainder() {
        return dropRemainder;
    }

    public void setDropRemainder(boolean dropRemainder) {
        this.dropRemainder = dropRemainder;
        markDirty();
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_TREE_ID, DataTreeData.empty());
        outputValues.put(OUTPUT_BRANCH_COUNT_ID, 0);
        outputValues.put(OUTPUT_REMAINDER_ID, List.of());
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}
