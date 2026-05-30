package com.nodecraft.nodesystem.nodes.flow.control;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "flow.control.sequence",
    displayName = "Sequence",
    description = "Replicates one signal to ordered sequence outputs.",
    category = "flow.control",
    order = 1
)
public class SequenceNode extends BaseNode {

    private static final int MAX_STEPS = 8;
    private static final int DEFAULT_STEP_COUNT = 2;

    private static final String INPUT_SIGNAL_ID = "input_signal";
    private static final String INPUT_STEP_COUNT_ID = "input_step_count";

    private static final String OUTPUT_ACTIVE_STEP_COUNT_ID = "output_active_step_count";
    private static final String OUTPUT_ACTIVE_STEPS_ID = "output_active_steps";

    public SequenceNode() {
        super(UUID.randomUUID(), "flow.control.sequence");

        addInputPort(new BasePort(INPUT_SIGNAL_ID, "Signal", "Signal to replicate across sequence steps", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_STEP_COUNT_ID, "Step Count", "How many outputs are active (1..8)", NodeDataType.INTEGER, this));

        for (int i = 1; i <= MAX_STEPS; i++) {
            addOutputPort(new BasePort(stepOutputId(i), "Step " + i, "Sequence output step " + i, NodeDataType.ANY, this));
        }
        addOutputPort(new BasePort(OUTPUT_ACTIVE_STEP_COUNT_ID, "Active Step Count", "Resolved active step count", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_ACTIVE_STEPS_ID, "Active Step Indexes", "1-based indexes of active steps", NodeDataType.LIST, this));
    }

    @Override
    public void processNode(ExecutionContext context) {
        Object signal = inputValues.get(INPUT_SIGNAL_ID);
        int activeCount = resolveStepCount(inputValues.get(INPUT_STEP_COUNT_ID));

        List<Integer> activeSteps = new ArrayList<>(activeCount);
        for (int i = 1; i <= MAX_STEPS; i++) {
            if (i <= activeCount) {
                outputValues.put(stepOutputId(i), signal);
                activeSteps.add(i);
            } else {
                outputValues.put(stepOutputId(i), null);
            }
        }

        outputValues.put(OUTPUT_ACTIVE_STEP_COUNT_ID, activeCount);
        outputValues.put(OUTPUT_ACTIVE_STEPS_ID, activeSteps);
    }

    private int resolveStepCount(Object value) {
        int stepCount = DEFAULT_STEP_COUNT;
        if (value instanceof Number number) {
            stepCount = number.intValue();
        }
        if (stepCount < 1) {
            return 1;
        }
        return Math.min(stepCount, MAX_STEPS);
    }

    private static String stepOutputId(int index) {
        return "output_step_" + index;
    }
}

