package com.nodecraft.nodesystem.nodes.math.data_tree;

import com.nodecraft.nodesystem.api.ListElementKind;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.StrictIntegerUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.data_tree.shift_path",
    displayName = "Shift Path",
    description = "Moves data tree paths up by removing leading levels or down by adding zero levels. Colliding paths merge items.",
    category = "math.data_tree",
    order = 10
)
public class ShiftPathNode extends BaseNode {
    private static final String LIST_T = "T";

    @NodeProperty(displayName = "Shift", category = "Path", order = 1)
    private int shift = 1;

    private static final String INPUT_TREE_ID = "input_tree";
    private static final String INPUT_SHIFT_ID = "input_shift";
    private static final String OUTPUT_TREE_ID = "output_tree";
    private static final String OUTPUT_BRANCH_COUNT_ID = "output_branch_count";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public ShiftPathNode() {
        super(UUID.randomUUID(), "math.data_tree.shift_path");
        addInputPort(new BasePort(INPUT_TREE_ID, "Tree", "Data tree to shift",
                NodeDataType.DATA_TREE, this).bindListType(LIST_T));
        addInputPort(new BasePort(INPUT_SHIFT_ID, "Shift",
                "Positive removes leading path levels; negative adds zero levels", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_TREE_ID, "Tree", "Shifted data tree",
                NodeDataType.DATA_TREE, this).bindListType(LIST_T));
        addOutputPort(new BasePort(OUTPUT_BRANCH_COUNT_ID, "Branch Count", "Number of output branches",
                NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether shifting succeeded",
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

        Integer resolvedShift = resolveShift();
        if (resolvedShift == null) {
            writeInvalid(kind, DataTreeNodeUtils.ERROR_INVALID_SHIFT);
            return;
        }

        DataTreeNodeUtils.ParseResult<Void> depthCheck = null;
        if (tree != null) {
            depthCheck = DataTreeNodeUtils.preflightShiftPaths(tree, resolvedShift);
        }
        if (depthCheck != null && !depthCheck.valid()) {
            writeInvalid(kind, depthCheck.error());
            return;
        }

        List<DataTreeData.Branch> branches = null;
        if (tree != null) {
            branches = new ArrayList<>(tree.getBranchCount());
        }
        if (tree != null) {
            for (DataTreeData.Branch branch : tree.getBranches()) {
                branches.add(new DataTreeData.Branch(
                        DataTreeNodeUtils.shiftPath(branch.path(), resolvedShift),
                        branch.items()));
            }
        }
        DataTreeData shifted = new DataTreeData(branches, kind);
        outputValues.put(OUTPUT_TREE_ID, shifted);
        outputValues.put(OUTPUT_BRANCH_COUNT_ID, shifted.getBranchCount());
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
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

    private @Nullable Integer resolveShift() {
        if (OptionalPortDrive.isConnected(this, INPUT_SHIFT_ID) || isInputPresent(INPUT_SHIFT_ID)) {
            return StrictIntegerUtils.requireExactInteger(getInput(INPUT_SHIFT_ID));
        }
        return shift;
    }

    private void writeInvalid(ListElementKind kind, String error) {
        outputValues.put(OUTPUT_TREE_ID, DataTreeData.empty(kind));
        outputValues.put(OUTPUT_BRANCH_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}
