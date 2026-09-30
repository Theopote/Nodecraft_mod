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
    id = "math.data_tree.paths",
    displayName = "Tree Paths",
    description = "Outputs all branch paths as a TREE_PATH_LIST.",
    category = "math.data_tree",
    order = 11
)
public class TreePathsNode extends BaseNode {
    private static final String INPUT_TREE_ID = "input_tree";
    private static final String OUTPUT_PATHS_ID = "output_paths";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public TreePathsNode() {
        super(UUID.randomUUID(), "math.data_tree.paths");
        addInputPort(new BasePort(INPUT_TREE_ID, "Tree", "Data tree to inspect", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_PATHS_ID, "Paths", "Branch paths", NodeDataType.TREE_PATH_LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether path extraction succeeded",
                NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false",
                NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object treeValue = inputValues.get(INPUT_TREE_ID);
        DataTreeNodeUtils.ParseResult<DataTreeData> treeResult = DataTreeNodeUtils.parseTree(treeValue);
        if (!treeResult.valid()) {
            writeInvalid(treeResult.error());
            return;
        }
        outputValues.put(OUTPUT_PATHS_ID, treeResult.value().getTreePaths());
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_PATHS_ID, List.<TreePathData>of());
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}
