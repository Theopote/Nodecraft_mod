package com.nodecraft.nodesystem.nodes.math.list_sequence;

import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.math.SequenceOps;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.RandomInputResolver;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.sequence.series",
    displayName = "Number Series",
    description = "Generates a DOUBLE_LIST with Start, Step, and Count (no Sum — use Sum Numbers).",
    category = "math.sequence"
)
public class DataSeriesNode extends SequenceGenerationNode {

    private int defaultCount = 10;
    private double defaultStart = 0;
    private double defaultStep = 1;

    private static final String INPUT_START_ID = "input_start";
    private static final String INPUT_STEP_ID = "input_step";
    private static final String INPUT_COUNT_ID = "input_count";
    private static final String OUTPUT_SERIES_ID = "output_series";

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
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Double start = resolveStrictDouble(INPUT_START_ID, defaultStart);
        Double step = resolveStrictDouble(INPUT_STEP_ID, defaultStep);
        RandomInputResolver.IntegerResolveResult countResult = RandomInputResolver.resolveCount(
                resolveValue(INPUT_COUNT_ID),
                defaultCount,
                isDriven(INPUT_COUNT_ID)
        );

        if (start == null || step == null || !countResult.valid()) {
            emitListFailure(OUTPUT_SERIES_ID, "invalid_input");
            return;
        }

        emitSequenceResult(OUTPUT_SERIES_ID, SequenceOps.series(start, step, countResult.value()));
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
        if (!Double.isFinite(start)) {
            return;
        }
        if (Double.compare(this.defaultStart, start) != 0) {
            this.defaultStart = start;
            markDirty();
        }
    }

    public double getDefaultStep() {
        return defaultStep;
    }

    public void setDefaultStep(double step) {
        if (!Double.isFinite(step)) {
            return;
        }
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

        Object count = stateMap.get("defaultCount");
        if (count instanceof Integer integer) {
            setDefaultCount(integer);
        }
        Object start = stateMap.get("defaultStart");
        if (start instanceof Double value) {
            setDefaultStart(value);
        }
        Object step = stateMap.get("defaultStep");
        if (step instanceof Double value) {
            setDefaultStep(value);
        }
    }
}
