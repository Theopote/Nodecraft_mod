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
    id = "math.data_tree.entwine",
    displayName = "Entwine",
    description = "Combines up to four trees or lists into source-indexed data tree branches",
    category = "math.data_tree",
    order = 13
)
public class EntwineNode extends BaseNode {
    private static final String INPUT_A_ID = "input_a";
    private static final String INPUT_B_ID = "input_b";
    private static final String INPUT_C_ID = "input_c";
    private static final String INPUT_D_ID = "input_d";
    private static final String OUTPUT_TREE_ID = "output_tree";
    private static final String OUTPUT_BRANCH_COUNT_ID = "output_branch_count";
    private static final String OUTPUT_ITEM_COUNT_ID = "output_item_count";

    public EntwineNode() {
        super(UUID.randomUUID(), "math.data_tree.entwine");
        addInputPort(new BasePort(INPUT_A_ID, "A", "First tree, list, or item", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_B_ID, "B", "Second tree, list, or item", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_C_ID, "C", "Third tree, list, or item", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_D_ID, "D", "Fourth tree, list, or item", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_TREE_ID, "Tree", "Entwined data tree", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_BRANCH_COUNT_ID, "Branch Count", "Number of output branches", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_ITEM_COUNT_ID, "Item Count", "Total item count", NodeDataType.INTEGER, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<DataTreeData.Branch> branches = new ArrayList<>();
        appendSource(branches, inputValues.get(INPUT_A_ID), 0);
        appendSource(branches, inputValues.get(INPUT_B_ID), 1);
        appendSource(branches, inputValues.get(INPUT_C_ID), 2);
        appendSource(branches, inputValues.get(INPUT_D_ID), 3);
        DataTreeData tree = new DataTreeData(branches);
        outputValues.put(OUTPUT_TREE_ID, tree);
        outputValues.put(OUTPUT_BRANCH_COUNT_ID, tree.getBranchCount());
        outputValues.put(OUTPUT_ITEM_COUNT_ID, tree.getItemCount());
    }

    private void appendSource(List<DataTreeData.Branch> output, Object value, int sourceIndex) {
        if (value == null) {
            return;
        }
        DataTreeData tree = DataTreeNodeUtils.resolveTree(value);
        for (DataTreeData.Branch branch : tree.getBranches()) {
            List<Integer> path = new ArrayList<>();
            path.add(sourceIndex);
            path.addAll(branch.path());
            output.add(new DataTreeData.Branch(path, branch.items()));
        }
    }
}
