package com.nodecraft.nodesystem.nodes.math.data_tree;

import com.nodecraft.nodesystem.api.ListElementKind;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.data_tree.flatten",
    displayName = "Flatten Tree",
    description = "Flattens all data tree branches into a single list (preserves element type T).",
    category = "math.data_tree",
    order = 2
)
public class FlattenTreeNode extends BaseNode {
    private static final String LIST_T = "T";
    private static final String INPUT_TREE_ID = "input_tree";
    private static final String OUTPUT_LIST_ID = "output_list";
    private static final String OUTPUT_ITEM_COUNT_ID = "output_item_count";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    /** Package-visible for budget contract tests; production uses {@link GenerationLimits#MAX_LIST_ELEMENTS}. */
    int flattenElementLimit = GenerationLimits.MAX_LIST_ELEMENTS;

    public FlattenTreeNode() {
        super(UUID.randomUUID(), "math.data_tree.flatten");
        addInputPort(new BasePort(INPUT_TREE_ID, "Tree", "Data tree to flatten",
                NodeDataType.DATA_TREE, this).bindListType(LIST_T));
        addOutputPort(new BasePort(OUTPUT_LIST_ID, "List", "Flattened list",
                NodeDataType.LIST, this).bindListType(LIST_T));
        addOutputPort(new BasePort(OUTPUT_ITEM_COUNT_ID, "Item Count", "Total item count",
                NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether flattening succeeded",
                NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false",
                NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object treeValue = inputValues.get(INPUT_TREE_ID);
        ListElementKind kind = DataTreeNodeUtils.resolveElementKindFromTreePort(this, INPUT_TREE_ID, treeValue);

        DataTreeNodeUtils.ParseResult<DataTreeData> treeResult = DataTreeNodeUtils.parseTree(treeValue);
        if (!treeResult.valid()) {
            writeInvalid(kind, treeResult.error());
            return;
        }
        DataTreeData tree = treeResult.value();

        DataTreeNodeUtils.ParseResult<Void> budgetCheck =
                DataTreeNodeUtils.preflightFlattenItemCount(tree, flattenElementLimit);
        if (!budgetCheck.valid()) {
            writeInvalid(kind, budgetCheck.error());
            return;
        }

        outputValues.put(OUTPUT_LIST_ID, tree.flatten());
        outputValues.put(OUTPUT_ITEM_COUNT_ID, tree.getItemCount());
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(ListElementKind kind, String error) {
        outputValues.put(OUTPUT_LIST_ID, List.of());
        outputValues.put(OUTPUT_ITEM_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}
