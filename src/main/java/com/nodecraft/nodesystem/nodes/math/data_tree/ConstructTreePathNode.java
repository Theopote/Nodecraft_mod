package com.nodecraft.nodesystem.nodes.math.data_tree;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.TreePathData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.data_tree.tree_path",
    displayName = "Construct Tree Path",
    description = "Builds a TREE_PATH from an ordered list of integer indices.",
    category = "math.data_tree",
    order = 0
)
public class ConstructTreePathNode extends BaseNode {
    private static final String INPUT_INDICES_ID = "input_indices";
    private static final String OUTPUT_PATH_ID = "output_path";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ConstructTreePathNode() {
        super(UUID.randomUUID(), "math.data_tree.tree_path");
        addInputPort(new BasePort(INPUT_INDICES_ID, "Indices", "Ordered path indices",
                NodeDataType.INTEGER_LIST, this));
        addOutputPort(new BasePort(OUTPUT_PATH_ID, "Path", "Constructed tree path",
                NodeDataType.TREE_PATH, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether all indices were integers",
                NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object indicesObj = inputValues.get(INPUT_INDICES_ID);
        if (!(indicesObj instanceof List<?> list)) {
            outputValues.put(OUTPUT_PATH_ID, TreePathData.empty());
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }
        List<Integer> indices = new ArrayList<>(list.size());
        for (Object item : list) {
            if (!(item instanceof Number number)) {
                outputValues.put(OUTPUT_PATH_ID, TreePathData.empty());
                outputValues.put(OUTPUT_VALID_ID, false);
                return;
            }
            indices.add(number.intValue());
        }
        outputValues.put(OUTPUT_PATH_ID, new TreePathData(indices));
        outputValues.put(OUTPUT_VALID_ID, true);
    }
}
