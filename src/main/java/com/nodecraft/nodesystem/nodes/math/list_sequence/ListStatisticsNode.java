package com.nodecraft.nodesystem.nodes.math.list_sequence;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.list.statistics",
    displayName = "List Statistics",
    description = "Computes min, max, sum, average, and median for a DOUBLE_LIST.",
    category = "math.list",
    order = 30
)
public class ListStatisticsNode extends BaseNode {

    private static final String INPUT_LIST_ID = "input_list";

    private static final String OUTPUT_MIN_ID = "output_min";
    private static final String OUTPUT_MAX_ID = "output_max";
    private static final String OUTPUT_SUM_ID = "output_sum";
    private static final String OUTPUT_AVERAGE_ID = "output_average";
    private static final String OUTPUT_MEDIAN_ID = "output_median";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ListStatisticsNode() {
        super(UUID.randomUUID(), "math.list.statistics");

        addInputPort(new BasePort(INPUT_LIST_ID, "Numbers", "Input double list", NodeDataType.DOUBLE_LIST, this));

        addOutputPort(new BasePort(OUTPUT_MIN_ID, "Min", "Minimum value", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_MAX_ID, "Max", "Maximum value", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_SUM_ID, "Sum", "Sum of values", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_AVERAGE_ID, "Average", "Average value", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_MEDIAN_ID, "Median", "Median value", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Count", "Count of finite numeric values", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether computation succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object listObj = inputValues.get(INPUT_LIST_ID);
        if (!(listObj instanceof List<?> inputList)) {
            setInvalidOutputs();
            return;
        }

        List<Double> values = new ArrayList<>(inputList.size());
        for (Object item : inputList) {
            if (!(item instanceof Number number)) {
                setInvalidOutputs();
                return;
            }
            double value = number.doubleValue();
            if (!Double.isFinite(value)) {
                setInvalidOutputs();
                return;
            }
            values.add(value);
        }

        if (values.isEmpty()) {
            setInvalidOutputs();
            return;
        }

        double min = values.getFirst();
        double max = values.getFirst();
        double sum = 0.0d;
        for (double v : values) {
            min = Math.min(min, v);
            max = Math.max(max, v);
            sum += v;
        }
        double average = sum / values.size();

        Collections.sort(values);
        double median;
        int size = values.size();
        int mid = size / 2;
        if ((size & 1) == 1) {
            median = values.get(mid);
        } else {
            median = (values.get(mid - 1) + values.get(mid)) * 0.5d;
        }

        outputValues.put(OUTPUT_MIN_ID, min);
        outputValues.put(OUTPUT_MAX_ID, max);
        outputValues.put(OUTPUT_SUM_ID, sum);
        outputValues.put(OUTPUT_AVERAGE_ID, average);
        outputValues.put(OUTPUT_MEDIAN_ID, median);
        outputValues.put(OUTPUT_COUNT_ID, values.size());
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private void setInvalidOutputs() {
        outputValues.put(OUTPUT_MIN_ID, Double.NaN);
        outputValues.put(OUTPUT_MAX_ID, Double.NaN);
        outputValues.put(OUTPUT_SUM_ID, Double.NaN);
        outputValues.put(OUTPUT_AVERAGE_ID, Double.NaN);
        outputValues.put(OUTPUT_MEDIAN_ID, Double.NaN);
        outputValues.put(OUTPUT_COUNT_ID, 0);
        outputValues.put(OUTPUT_VALID_ID, false);
    }
}
