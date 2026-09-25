package com.nodecraft.nodesystem.nodes.math.data_tree;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

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

    public TreePathsNode() {
        super(UUID.randomUUID(), "math.data_tree.paths");
        addInputPort(new BasePort(INPUT_TREE_ID, "Tree", "Data tree to inspect", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_PATHS_ID, "Paths", "Branch paths", NodeDataType.TREE_PATH_LIST, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        DataTreeData tree = DataTreeNodeUtils.requireTree(inputValues.get(INPUT_TREE_ID));
        outputValues.put(OUTPUT_PATHS_ID, tree.getTreePaths());
    }
}
