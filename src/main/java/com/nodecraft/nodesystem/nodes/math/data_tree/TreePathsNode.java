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
    id = "math.data_tree.paths",
    displayName = "Tree Paths",
    description = "Outputs data tree branch paths as strings and path index lists",
    category = "math.data_tree",
    order = 11
)
public class TreePathsNode extends BaseNode {
    private static final String INPUT_TREE_ID = "input_tree";
    private static final String OUTPUT_PATH_STRINGS_ID = "output_path_strings";
    private static final String OUTPUT_PATHS_ID = "output_paths";
    private static final String OUTPUT_BRANCH_COUNT_ID = "output_branch_count";

    public TreePathsNode() {
        super(UUID.randomUUID(), "math.data_tree.paths");
        addInputPort(new BasePort(INPUT_TREE_ID, "Tree", "Data tree to inspect", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_PATH_STRINGS_ID, "Path Strings", "Branch paths formatted as {0;1}", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_PATHS_ID, "Paths", "Branch paths as index lists", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_BRANCH_COUNT_ID, "Branch Count", "Number of paths", NodeDataType.INTEGER, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        DataTreeData tree = DataTreeNodeUtils.resolveTree(inputValues.get(INPUT_TREE_ID));
        List<String> pathStrings = new ArrayList<>(tree.getBranchCount());
        for (DataTreeData.Branch branch : tree.getBranches()) {
            pathStrings.add(DataTreeData.formatPath(branch.path()));
        }
        outputValues.put(OUTPUT_PATH_STRINGS_ID, List.copyOf(pathStrings));
        outputValues.put(OUTPUT_PATHS_ID, tree.getPaths());
        outputValues.put(OUTPUT_BRANCH_COUNT_ID, tree.getBranchCount());
    }
}
