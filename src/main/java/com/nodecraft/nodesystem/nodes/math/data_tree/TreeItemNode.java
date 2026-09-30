package com.nodecraft.nodesystem.nodes.math.data_tree;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.datatypes.TreePathData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.StrictIntegerUtils;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.data_tree.item",
    displayName = "Tree Item",
    description = "Gets one item from a data tree branch by TREE_PATH and index (negatives from end; OOR → Found=false).",
    category = "math.data_tree",
    order = 5
)
public class TreeItemNode extends BaseNode {

    public static final String ERROR_INVALID_INDEX = "invalid_index";

    private static final String LIST_T = "T";
    private static final String INPUT_TREE_ID = "input_tree";
    private static final String INPUT_PATH_ID = "input_path";
    private static final String INPUT_INDEX_ID = "input_index";
    private static final String OUTPUT_ITEM_ID = "output_item";
    private static final String OUTPUT_FOUND_ID = "output_found";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public TreeItemNode() {
        super(UUID.randomUUID(), "math.data_tree.item");
        addInputPort(new BasePort(INPUT_TREE_ID, "Tree", "Data tree to query",
                NodeDataType.DATA_TREE, this).bindListType(LIST_T));
        addInputPort(new BasePort(INPUT_PATH_ID, "Path", "Branch path", NodeDataType.TREE_PATH, this));
        addInputPort(new BasePort(INPUT_INDEX_ID, "Index", "Item index inside the branch (negatives from end)",
                NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_ITEM_ID, "Item", "Selected item", NodeDataType.ANY, this)
                .bindListElementType(LIST_T));
        addOutputPort(new BasePort(OUTPUT_FOUND_ID, "Found", "Whether the item was found",
                NodeDataType.BOOLEAN, this));
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

        Integer indexValue = StrictIntegerUtils.requireExactInteger(inputValues.get(INPUT_INDEX_ID));
        if (indexValue == null) {
            writeInvalid(ERROR_INVALID_INDEX);
            return;
        }

        DataTreeData.Branch branch = tree.getBranch(path);
        if (branch == null) {
            writeNotFound();
            return;
        }
        int index = DataTreeNodeUtils.resolveIndex(indexValue, branch.items().size());
        if (index < 0) {
            writeNotFound();
            return;
        }
        outputValues.put(OUTPUT_ITEM_ID, branch.items().get(index));
        outputValues.put(OUTPUT_FOUND_ID, true);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    @Override
    public Object getNodeState() {
        return Map.of();
    }

    @Override
    public void setNodeState(Object state) {
        // Legacy allowNegativeIndex / wrapIndex ignored.
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_ITEM_ID, null);
        outputValues.put(OUTPUT_FOUND_ID, false);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    private void writeNotFound() {
        outputValues.put(OUTPUT_ITEM_ID, null);
        outputValues.put(OUTPUT_FOUND_ID, false);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }
}
