package com.nodecraft.nodesystem.nodes.deferred.math;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Deferred arithmetic series generator kept for compatibility until its
 * semantics are reconciled with range/sequence nodes.
 */
@NodeInfo(
    id = "deferred.math.math_series",
    displayName = "Series",
    description = "Generates an arithmetic series with Start, Step, and Count.",
    category = "deferred.math"
)
public class MathSeriesNode extends BaseNode {

    private static final String INPUT_START_ID = "input_start";
    private static final String INPUT_STEP_ID = "input_step";
    private static final String INPUT_COUNT_ID = "input_count";
    private static final String OUTPUT_SERIES_ID = "output_series";

    public MathSeriesNode() {
        super(UUID.randomUUID(), "deferred.math.math_series");
        addInputPort(new BasePort(INPUT_START_ID, "Start", "The first number in the series", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_STEP_ID, "Step", "The increment between numbers", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_COUNT_ID, "Count", "The total number of items in the series", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SERIES_ID, "Series", "The generated list of numbers", NodeDataType.ANY, this));
    }

    @Override
    public String getDescription() {
        return "Generates an arithmetic series with Start, Step, and Count.";
    }

    @Override
    public String getDisplayName() {
        return "Series";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        double start = getValueAsDouble(inputValues.get(INPUT_START_ID), 0.0);
        double step = getValueAsDouble(inputValues.get(INPUT_STEP_ID), 1.0);
        int count = getValueAsInt(inputValues.get(INPUT_COUNT_ID), 10);

        if (count <= 0) {
            outputValues.put(OUTPUT_SERIES_ID, Collections.emptyList());
            return;
        }

        List<Double> series = new ArrayList<>(count);
        double current = start;
        for (int i = 0; i < count; i++) {
            series.add(current);
            current += step;
        }

        outputValues.put(OUTPUT_SERIES_ID, Collections.unmodifiableList(series));
    }

    private double getValueAsDouble(Object value, double defaultValue) {
        return value instanceof Number ? ((Number) value).doubleValue() : defaultValue;
    }

    private int getValueAsInt(Object value, int defaultValue) {
        if (value instanceof Number) {
            double doubleVal = ((Number) value).doubleValue();
            if (doubleVal >= Integer.MIN_VALUE && doubleVal <= Integer.MAX_VALUE) {
                return (int) Math.round(doubleVal);
            }
        }
        return defaultValue;
    }
}
