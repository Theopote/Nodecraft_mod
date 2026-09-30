package com.nodecraft.nodesystem.nodes.math.data_tree;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.data_tree.viewer",
    displayName = "Tree Viewer",
    description = "Outputs a readable summary of a data tree for debugging",
    category = "math.data_tree",
    order = 7
)
public class TreeViewerNode extends BaseNode {
    private static final String INPUT_TREE_ID = "input_tree";
    private static final String OUTPUT_SUMMARY_ID = "output_summary";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";
    private static final String OUTPUT_TRUNCATED_ID = "output_truncated";

    public TreeViewerNode() {
        super(UUID.randomUUID(), "math.data_tree.viewer");
        addInputPort(new BasePort(INPUT_TREE_ID, "Tree", "Data tree to inspect", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_SUMMARY_ID, "Summary", "Readable branch summary", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the summary was generated",
                NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false",
                NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_TRUNCATED_ID, "Truncated", "Whether the preview was truncated",
                NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object treeValue = inputValues.get(INPUT_TREE_ID);
        DataTreeNodeUtils.ParseResult<DataTreeData> treeResult = DataTreeNodeUtils.parseTree(treeValue);
        if (!treeResult.valid()) {
            writeInvalid(treeResult.error());
            return;
        }
        DataTreeData.TreePreview preview = treeResult.value().describePreview(
                GenerationLimits.MAX_TREE_VIEWER_PREVIEW_BRANCHES,
                GenerationLimits.MAX_TREE_VIEWER_OUTPUT_CHARS);
        outputValues.put(OUTPUT_SUMMARY_ID, preview.summary());
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
        outputValues.put(OUTPUT_TRUNCATED_ID, preview.truncated());
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_SUMMARY_ID, "");
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
        outputValues.put(OUTPUT_TRUNCATED_ID, false);
    }
}
