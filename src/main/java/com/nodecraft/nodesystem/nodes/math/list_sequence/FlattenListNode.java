package com.nodecraft.nodesystem.nodes.math.list_sequence;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.math.ListFlattenOps;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.list.flatten_list",
    displayName = "Flatten List",
    description = "Recursively flattens nested List elements only; bounded by depth and element limits.",
    category = "math.list"
)
public class FlattenListNode extends BaseNode {

    public static final String ERROR_INVALID_INPUT = "invalid_input";
    public static final String ERROR_INVALID_DEPTH = "invalid_depth";

    private int maxDepth = -1;

    private static final String INPUT_LIST_ID = "input_list";
    private static final String INPUT_DEPTH_ID = "input_depth";
    private static final String OUTPUT_LIST_ID = "output_list";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public FlattenListNode() {
        super(UUID.randomUUID(), "math.list.flatten_list");

        addInputPort(new BasePort(INPUT_LIST_ID, "List",
                "The nested list to flatten", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_DEPTH_ID, "Depth",
                "Maximum flattening depth (-1 = safe full flatten)", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_LIST_ID, "Flattened List",
                "The resulting flattened list", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether flattening succeeded",
                NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when flattening failed",
                NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object listObj = inputValues.get(INPUT_LIST_ID);
        if (!(listObj instanceof List<?> inputList)) {
            writeFailure(ERROR_INVALID_INPUT);
            return;
        }

        Integer depth = OptionalPortDrive.resolveOptionalInteger(this, INPUT_DEPTH_ID, maxDepth);
        if (depth == null || !isValidMaxDepth(depth)) {
            writeFailure(ERROR_INVALID_DEPTH);
            return;
        }

        int effectiveDepth = depth == -1 ? GenerationLimits.MAX_FORMAT_DEPTH : depth;
        ListFlattenOps.FlattenDepthMode mode = depth == -1
                ? ListFlattenOps.FlattenDepthMode.FULL_FLATTEN
                : ListFlattenOps.FlattenDepthMode.PARTIAL_DEPTH;

        ListFlattenOps.FlattenResult result =
                ListFlattenOps.flatten(inputList, effectiveDepth, mode);
        if (!result.valid()) {
            writeFailure(result.error());
            return;
        }

        outputValues.put(OUTPUT_LIST_ID, result.items());
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeFailure(String error) {
        outputValues.put(OUTPUT_LIST_ID, List.of());
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    /**
     * Allowed depths: {@code -1} (safe full flatten) or any non-negative integer.
     * Illegal values are rejected (property unchanged).
     */
    public void setMaxDepth(int depth) {
        if (!isValidMaxDepth(depth) || this.maxDepth == depth) {
            return;
        }
        this.maxDepth = depth;
        markDirty();
    }

    static boolean isValidMaxDepth(int depth) {
        return depth == -1 || depth >= 0;
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("maxDepth", getMaxDepth());
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> stateMap)) {
            return;
        }
        Object depth = stateMap.get("maxDepth");
        if (depth instanceof Number number) {
            setMaxDepth(number.intValue());
        }
        // Legacy preserveTypes is ignored at runtime but accepted for old saved graphs.
    }
}
