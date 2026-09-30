package com.nodecraft.nodesystem.nodes.math.data_tree;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.datatypes.TreePathData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.data_tree.branch",
    displayName = "Tree Branch",
    description = "Gets one branch from a data tree by TREE_PATH (preserves T). Missing path → Found=false.",
    category = "math.data_tree",
    order = 4
)
public class TreeBranchNode extends BaseNode {
    private static final String LIST_T = "T";
    private static final String INPUT_TREE_ID = "input_tree";
    private static final String INPUT_PATH_ID = "input_path";
    private static final String OUTPUT_BRANCH_ID = "output_branch";
    private static final String OUTPUT_FOUND_ID = "output_found";
    private static final String OUTPUT_ITEM_COUNT_ID = "output_item_count";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public TreeBranchNode() {
        super(UUID.randomUUID(), "math.data_tree.branch");
        addInputPort(new BasePort(INPUT_TREE_ID, "Tree", "Data tree to query",
                NodeDataType.DATA_TREE, this).bindListType(LIST_T));
        addInputPort(new BasePort(INPUT_PATH_ID, "Path", "Branch path", NodeDataType.TREE_PATH, this));
        addOutputPort(new BasePort(OUTPUT_BRANCH_ID, "Branch", "Items in the selected branch",
                NodeDataType.LIST, this).bindListType(LIST_T));
        addOutputPort(new BasePort(OUTPUT_FOUND_ID, "Found", "Whether the branch was found",
                NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ITEM_COUNT_ID, "Item Count", "Number of items in the branch",
                NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether inputs were valid",
                NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false",
                NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        DataTreeNodeUtils.ParseResult<DataTreeData> treeResult =
                DataTreeNodeUtils.parseTree(inputValues.get(INPUT_TREE_ID));
        if (!treeResult.valid()) {
            writeInvalid(treeResult.error());
            return;
        }
        DataTreeData tree = treeResult.value();

        DataTreeNodeUtils.ParseResult<TreePathData> pathResult =
                DataTreeNodeUtils.parsePath(inputValues.get(INPUT_PATH_ID));
        if (!pathResult.valid()) {
            writeInvalid(pathResult.error());
            return;
        }
        TreePathData path = pathResult.value();

        DataTreeData.Branch branch = tree.getBranch(path);
        if (branch == null) {
            writeNotFound();
            return;
        }
        outputValues.put(OUTPUT_BRANCH_ID, branch.items());
        outputValues.put(OUTPUT_FOUND_ID, true);
        outputValues.put(OUTPUT_ITEM_COUNT_ID, branch.items().size());
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_BRANCH_ID, List.of());
        outputValues.put(OUTPUT_FOUND_ID, false);
        outputValues.put(OUTPUT_ITEM_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    private void writeNotFound() {
        outputValues.put(OUTPUT_BRANCH_ID, List.of());
        outputValues.put(OUTPUT_FOUND_ID, false);
        outputValues.put(OUTPUT_ITEM_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }
}
