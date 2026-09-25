package com.nodecraft.nodesystem.nodes.math.list_sequence;

import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.sequence.series",
    displayName = "Number Series",
    description = "Generates a DOUBLE_LIST with Start, Step, and Count.",
    category = "math.sequence"
)
public class DataSeriesNode extends BaseNode {

    private int defaultCount = 10;
    private double defaultStart = 0;
    private double defaultStep = 1;

    private static final String INPUT_START_ID = "input_start";
    private static final String INPUT_STEP_ID = "input_step";
    private static final String INPUT_COUNT_ID = "input_count";
    private static final String OUTPUT_SERIES_ID = "output_series";
    private static final String OUTPUT_SUM_ID = "output_sum";

    public DataSeriesNode() {
        super(UUID.randomUUID(), "math.sequence.series");

        IPort startInput = new BasePort(INPUT_START_ID, "Start",
                "Starting value of the series", NodeDataType.DOUBLE, this);
        addInputPort(startInput);

        IPort stepInput = new BasePort(INPUT_STEP_ID, "Step",
                "Increment between consecutive elements", NodeDataType.DOUBLE, this);
        addInputPort(stepInput);

        IPort countInput = new BasePort(INPUT_COUNT_ID, "Count",
                "Number of elements to generate", NodeDataType.INTEGER, this);
        addInputPort(countInput);

        IPort seriesOutput = new BasePort(OUTPUT_SERIES_ID, "Series",
                "The generated double list", NodeDataType.DOUBLE_LIST, this);
        addOutputPort(seriesOutput);

        IPort sumOutput = new BasePort(OUTPUT_SUM_ID, "Sum",
                "Sum of all values in the series", NodeDataType.DOUBLE, this);
        addOutputPort(sumOutput);
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object startObj = inputValues.get(INPUT_START_ID);
        Object stepObj = inputValues.get(INPUT_STEP_ID);
        Object countObj = inputValues.get(INPUT_COUNT_ID);

        double start = defaultStart;
        if (startObj instanceof Number) {
            start = ((Number) startObj).doubleValue();
        }

        double step = defaultStep;
        if (stepObj instanceof Number) {
            step = ((Number) stepObj).doubleValue();
        }

        int count = defaultCount;
        if (countObj instanceof Number) {
            count = ((Number) countObj).intValue();
        }
        count = GenerationLimits.clampNonNegativeCount(count);

        if (!Double.isFinite(start) || !Double.isFinite(step)) {
            outputValues.put(OUTPUT_SERIES_ID, new ArrayList<Double>());
            outputValues.put(OUTPUT_SUM_ID, 0.0);
            return;
        }

        List<Double> series = new ArrayList<>(count);
        double sum = 0.0;

        for (int i = 0; i < count; i++) {
            double value = start + i * step;
            series.add(value);
            sum += value;
        }

        outputValues.put(OUTPUT_SERIES_ID, series);
        outputValues.put(OUTPUT_SUM_ID, sum);
    }

    public int getDefaultCount() {
        return defaultCount;
    }

    public void setDefaultCount(int count) {
        int resolved = GenerationLimits.clampNonNegativeCount(count);
        if (this.defaultCount != resolved) {
            this.defaultCount = resolved;
            markDirty();
        }
    }

    public double getDefaultStart() {
        return defaultStart;
    }

    public void setDefaultStart(double start) {
        if (Double.compare(this.defaultStart, start) != 0) {
            this.defaultStart = start;
            markDirty();
        }
    }

    public double getDefaultStep() {
        return defaultStep;
    }

    public void setDefaultStep(double step) {
        if (Double.compare(this.defaultStep, step) != 0) {
            this.defaultStep = step;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("defaultCount", getDefaultCount());
        state.put("defaultStart", getDefaultStart());
        state.put("defaultStep", getDefaultStep());
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> stateMap)) {
            return;
        }
        // Legacy useIntegerType is ignored — series is always DOUBLE_LIST.

        Object count = stateMap.get("defaultCount");
        if (count instanceof Number number) {
            setDefaultCount(number.intValue());
        }
        Object start = stateMap.get("defaultStart");
        if (start instanceof Number number) {
            setDefaultStart(number.doubleValue());
        }
        Object step = stateMap.get("defaultStep");
        if (step instanceof Number number) {
            setDefaultStep(number.doubleValue());
        }
    }
}
