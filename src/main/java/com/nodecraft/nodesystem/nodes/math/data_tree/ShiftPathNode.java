package com.nodecraft.nodesystem.nodes.math.data_tree;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "math.data_tree.shift_path",
    displayName = "Shift Path",
    description = "Moves data tree paths up by removing leading levels or down by adding zero levels",
    category = "math.data_tree",
    order = 10
)
public class ShiftPathNode extends BaseNode {
    @NodeProperty(displayName = "Shift", category = "Path", order = 1)
    private int shift = 1;

    private static final String INPUT_TREE_ID = "input_tree";
    private static final String INPUT_SHIFT_ID = "input_shift";
    private static final String OUTPUT_TREE_ID = "output_tree";
    private static final String OUTPUT_BRANCH_COUNT_ID = "output_branch_count";

    public ShiftPathNode() {
        super(UUID.randomUUID(), "math.data_tree.shift_path");
        addInputPort(new BasePort(INPUT_TREE_ID, "Tree", "Data tree to shift", NodeDataType.DATA_TREE, this));
        addInputPort(new BasePort(INPUT_SHIFT_ID, "Shift", "Positive removes leading path levels; negative adds zero levels", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_TREE_ID, "Tree", "Shifted data tree", NodeDataType.DATA_TREE, this));
        addOutputPort(new BasePort(OUTPUT_BRANCH_COUNT_ID, "Branch Count", "Number of output branches", NodeDataType.INTEGER, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        DataTreeData tree = DataTreeNodeUtils.resolveTree(inputValues.get(INPUT_TREE_ID));
        Object shiftObj = inputValues.get(INPUT_SHIFT_ID);
        int resolvedShift = shiftObj instanceof Number number ? number.intValue() : shift;
        List<DataTreeData.Branch> branches = new ArrayList<>(tree.getBranchCount());
        for (DataTreeData.Branch branch : tree.getBranches()) {
            branches.add(new DataTreeData.Branch(shiftPath(branch.path(), resolvedShift), branch.items()));
        }
        DataTreeData shifted = new DataTreeData(branches);
        outputValues.put(OUTPUT_TREE_ID, shifted);
        outputValues.put(OUTPUT_BRANCH_COUNT_ID, shifted.getBranchCount());
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("shift", shift);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> map && map.get("shift") instanceof Number value) {
            shift = value.intValue();
            markDirty();
        }
    }

    public int getShift() {
        return shift;
    }

    public void setShift(int shift) {
        this.shift = shift;
        markDirty();
    }

    private List<Integer> shiftPath(List<Integer> path, int amount) {
        if (amount == 0) {
            return path;
        }
        if (amount > 0) {
            return amount >= path.size() ? List.of() : List.copyOf(path.subList(amount, path.size()));
        }
        List<Integer> shifted = new ArrayList<>(path.size() + Math.abs(amount));
        for (int i = 0; i < Math.abs(amount); i++) {
            shifted.add(0);
        }
        shifted.addAll(path);
        return List.copyOf(shifted);
    }
}
