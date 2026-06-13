package com.nodecraft.nodesystem.nodes.math.data_tree;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    id = "math.data_tree.viewer",
    displayName = "Tree Viewer",
    description = "Outputs a readable summary of a data tree for debugging",
    category = "math.data_tree",
    order = 7
)
public class TreeViewerNode extends BaseNode {
    private static final String INPUT_TREE_ID = "input_tree";
    private static final String OUTPUT_SUMMARY_ID = "output_summary";

    public TreeViewerNode() {
        super(UUID.randomUUID(), "math.data_tree.viewer");
        addInputPort(new BasePort(INPUT_TREE_ID, "Tree", "Data tree to inspect", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_SUMMARY_ID, "Summary", "Readable branch summary", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        DataTreeData tree = DataTreeNodeUtils.resolveTree(inputValues.get(INPUT_TREE_ID));
        outputValues.put(OUTPUT_SUMMARY_ID, tree.describe());
    }
}
