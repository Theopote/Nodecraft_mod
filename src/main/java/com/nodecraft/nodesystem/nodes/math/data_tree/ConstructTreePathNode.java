package com.nodecraft.nodesystem.nodes.math.data_tree;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.TreePathData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.StrictIntegerUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.data_tree.tree_path",
    displayName = "Construct Tree Path",
    description = "Builds a TREE_PATH from an ordered list of non-negative integer indices.",
    category = "math.data_tree",
    order = 0
)
public class ConstructTreePathNode extends BaseNode {
    private static final String INPUT_INDICES_ID = "input_indices";
    private static final String OUTPUT_PATH_ID = "output_path";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public ConstructTreePathNode() {
        super(UUID.randomUUID(), "math.data_tree.tree_path");
        addInputPort(new BasePort(INPUT_INDICES_ID, "Indices", "Ordered non-negative path indices",
                NodeDataType.INTEGER_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PATH_ID, "Path", "Constructed tree path",
                NodeDataType.TREE_PATH, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether all indices were exact non-negative Integers",
                NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false",
                NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Integer> indices = StrictIntegerUtils.resolveStrictIntegerList(inputValues.get(INPUT_INDICES_ID));
        if (indices == null) {
            writeInvalid(DataTreeNodeUtils.ERROR_INVALID_PATH);
            return;
        }
        if (indices.size() > GenerationLimits.MAX_TREE_PATH_DEPTH) {
            writeInvalid(DataTreeNodeUtils.ERROR_PATH_DEPTH_EXCEEDED);
            return;
        }
        for (Integer index : indices) {
            if (index == null || index < 0) {
                writeInvalid(DataTreeNodeUtils.ERROR_INVALID_PATH);
                return;
            }
        }
        outputValues.put(OUTPUT_PATH_ID, new TreePathData(indices));
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_PATH_ID, TreePathData.empty());
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}
