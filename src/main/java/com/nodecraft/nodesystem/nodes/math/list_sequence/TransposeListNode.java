package com.nodecraft.nodesystem.nodes.math.list_sequence;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "math.list.transpose",
    displayName = "Transpose List of Lists",
    description = "Transposes a list of lists by swapping rows and columns.",
    category = "math.list",
    order = 34
)
public class TransposeListNode extends BaseNode {

    @NodeProperty(displayName = "Use Longest Row", category = "Transpose", order = 1)
    private boolean useLongestRow = false;

    @NodeProperty(displayName = "Fill Missing With Null", category = "Transpose", order = 2)
    private boolean fillMissingWithNull = true;

    @NodeProperty(displayName = "Ignore Non-List Rows", category = "Transpose", order = 3)
    private boolean ignoreNonListRows = true;

    private static final String INPUT_LIST_ID = "input_list";

    private static final String OUTPUT_TRANSPOSED_ID = "output_transposed";
    private static final String OUTPUT_ROW_COUNT_ID = "output_row_count";
    private static final String OUTPUT_COLUMN_COUNT_ID = "output_column_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public TransposeListNode() {
        super(UUID.randomUUID(), "math.list.transpose");

        addInputPort(new BasePort(INPUT_LIST_ID, "List of Lists", "Input matrix-style list", NodeDataType.LIST, this));

        addOutputPort(new BasePort(OUTPUT_TRANSPOSED_ID, "Transposed", "Transposed list of lists", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_ROW_COUNT_ID, "Rows", "Input row count", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_COLUMN_COUNT_ID, "Columns", "Resolved column count", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether transpose succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Transpose List of Lists";
    }

    @Override
    public String getDescription() {
        return "Transposes a list of lists by swapping rows and columns.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object matrixObj = inputValues.get(INPUT_LIST_ID);
        if (!(matrixObj instanceof List<?> outer)) {
            outputValues.put(OUTPUT_TRANSPOSED_ID, List.of());
            outputValues.put(OUTPUT_ROW_COUNT_ID, 0);
            outputValues.put(OUTPUT_COLUMN_COUNT_ID, 0);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        List<List<?>> rows = new ArrayList<>();
        for (Object rowObj : outer) {
            if (rowObj instanceof List<?> row) {
                rows.add(row);
            } else if (!ignoreNonListRows) {
                outputValues.put(OUTPUT_TRANSPOSED_ID, List.of());
                outputValues.put(OUTPUT_ROW_COUNT_ID, outer.size());
                outputValues.put(OUTPUT_COLUMN_COUNT_ID, 0);
                outputValues.put(OUTPUT_VALID_ID, false);
                return;
            }
        }

        if (rows.isEmpty()) {
            outputValues.put(OUTPUT_TRANSPOSED_ID, List.of());
            outputValues.put(OUTPUT_ROW_COUNT_ID, 0);
            outputValues.put(OUTPUT_COLUMN_COUNT_ID, 0);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        int columns = useLongestRow ? getMaxRowSize(rows) : getMinRowSize(rows);
        List<Object> transposed = new ArrayList<>();
        for (int c = 0; c < columns; c++) {
            List<Object> column = new ArrayList<>();
            for (List<?> row : rows) {
                if (c < row.size()) {
                    column.add(row.get(c));
                } else if (fillMissingWithNull && useLongestRow) {
                    column.add(null);
                }
            }
            transposed.add(column);
        }

        outputValues.put(OUTPUT_TRANSPOSED_ID, transposed);
        outputValues.put(OUTPUT_ROW_COUNT_ID, rows.size());
        outputValues.put(OUTPUT_COLUMN_COUNT_ID, columns);
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private int getMaxRowSize(List<List<?>> rows) {
        int max = 0;
        for (List<?> row : rows) {
            max = Math.max(max, row.size());
        }
        return max;
    }

    private int getMinRowSize(List<List<?>> rows) {
        int min = Integer.MAX_VALUE;
        for (List<?> row : rows) {
            min = Math.min(min, row.size());
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("useLongestRow", useLongestRow);
        state.put("fillMissingWithNull", fillMissingWithNull);
        state.put("ignoreNonListRows", ignoreNonListRows);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        Object useLongestRowValue = map.get("useLongestRow");
        if (useLongestRowValue instanceof Boolean value) {
            useLongestRow = value;
        }
        Object fillMissingValue = map.get("fillMissingWithNull");
        if (fillMissingValue instanceof Boolean value) {
            fillMissingWithNull = value;
        }
        Object ignoreNonListRowsValue = map.get("ignoreNonListRows");
        if (ignoreNonListRowsValue instanceof Boolean value) {
            ignoreNonListRows = value;
        }
    }
}
