package com.nodecraft.nodesystem.nodes.flow.control;

import com.nodecraft.nodesystem.api.ExecRoutingNode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.StrictIntegerUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Replicates an optional signal across a fixed number of ordered exec steps.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "flow.control.sequence",
    displayName = "Sequence",
    description = "Fires Exec Step 1..N in order. Signal is optional passthrough T; Step Count is exact INTEGER 1..8.",
    category = "flow.control",
    order = 1
)
public class SequenceNode extends BaseNode implements ExecRoutingNode {

    private static final int MAX_STEPS = 8;
    private static final int DEFAULT_STEP_COUNT = 2;

    private static final String INPUT_EXEC_ID = "exec_in";
    private static final String INPUT_SIGNAL_ID = "input_signal";
    private static final String INPUT_STEP_COUNT_ID = "input_step_count";

    private static final String OUTPUT_ACTIVE_STEP_COUNT_ID = "output_active_step_count";
    private static final String OUTPUT_ACTIVE_STEPS_ID = "output_active_steps";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    @NodeProperty(displayName = "Step Count", category = "Sequence", order = 0)
    private int stepCount = DEFAULT_STEP_COUNT;

    private transient LinkedHashSet<String> activeExecOutputs = new LinkedHashSet<>();

    public SequenceNode() {
        super(UUID.randomUUID(), "flow.control.sequence");

        addInputPort(new BasePort(INPUT_EXEC_ID, "Exec In", "Incoming execution pulse", NodeDataType.EXEC, this, true, false));

        BasePort signalIn = new BasePort(INPUT_SIGNAL_ID, "Signal", "Optional signal to replicate (passthrough T)", NodeDataType.ANY, this);
        signalIn.bindPassthroughType("T");
        addInputPort(signalIn);

        addInputPort(new BasePort(INPUT_STEP_COUNT_ID, "Step Count", "Exact INTEGER how many outputs are active (1..8)", NodeDataType.INTEGER, this));

        for (int i = 1; i <= MAX_STEPS; i++) {
            addOutputPort(new BasePort(execStepPortId(i), "Exec Step " + i, "Execution pulse for step " + i, NodeDataType.EXEC, this));
            BasePort stepOut = new BasePort(stepOutputId(i), "Step " + i, "Sequence output step " + i, NodeDataType.ANY, this);
            stepOut.bindPassthroughType("T");
            addOutputPort(stepOut);
        }
        addOutputPort(new BasePort(OUTPUT_ACTIVE_STEP_COUNT_ID, "Active Step Count", "Resolved active step count", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_ACTIVE_STEPS_ID, "Active Step Indexes", "1-based indexes of active steps", NodeDataType.INTEGER_LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether Step Count preflight succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why sequence did not run", NodeDataType.STRING, this));
    }

    @Override
    public Set<String> getActiveExecOutputPortIds() {
        return activeExecOutputs;
    }

    @Override
    public boolean drainExecPortsSequentially() {
        return true;
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        activeExecOutputs = new LinkedHashSet<>();
        for (int i = 1; i <= MAX_STEPS; i++) {
            outputValues.put(stepOutputId(i), null);
            outputValues.put(execStepPortId(i), null);
        }
        outputValues.put(OUTPUT_ACTIVE_STEP_COUNT_ID, 0);
        outputValues.put(OUTPUT_ACTIVE_STEPS_ID, List.of());

        Integer activeCount = resolveStepCount();
        if (activeCount == null) {
            publishValid(false, "Step Count must be an exact INTEGER between 1 and " + MAX_STEPS + ".");
            return;
        }

        Object signal = inputValues.get(INPUT_SIGNAL_ID);
        LinkedHashSet<String> firedExecSteps = new LinkedHashSet<>();
        List<Integer> activeSteps = new ArrayList<>(activeCount);
        for (int i = 1; i <= MAX_STEPS; i++) {
            String execPortId = execStepPortId(i);
            if (i <= activeCount) {
                outputValues.put(stepOutputId(i), signal);
                outputValues.put(execPortId, Boolean.TRUE);
                firedExecSteps.add(execPortId);
                activeSteps.add(i);
            } else {
                outputValues.put(stepOutputId(i), null);
                outputValues.put(execPortId, null);
            }
        }

        activeExecOutputs = firedExecSteps;
        outputValues.put(OUTPUT_ACTIVE_STEP_COUNT_ID, activeCount);
        outputValues.put(OUTPUT_ACTIVE_STEPS_ID, activeSteps);
        publishValid(true, "");
    }

    private @Nullable Integer resolveStepCount() {
        Integer resolved;
        if (OptionalPortDrive.isConnected(this, INPUT_STEP_COUNT_ID)) {
            resolved = StrictIntegerUtils.requireExactInteger(inputValues.get(INPUT_STEP_COUNT_ID));
        } else {
            Object raw = inputValues.get(INPUT_STEP_COUNT_ID);
            if (raw == null) {
                resolved = stepCount;
            } else {
                resolved = StrictIntegerUtils.requireExactInteger(raw);
            }
        }
        if (resolved == null || resolved < 1 || resolved > MAX_STEPS) {
            return null;
        }
        return resolved;
    }

    private void publishValid(boolean valid, String error) {
        outputValues.put(OUTPUT_VALID_ID, valid);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    private static String stepOutputId(int index) {
        return "output_step_" + index;
    }

    public static String execStepPortId(int index) {
        return "exec_step_" + index;
    }

    public int getStepCount() {
        return stepCount;
    }

    public void setStepCount(int stepCount) {
        if (stepCount >= 1 && stepCount <= MAX_STEPS) {
            this.stepCount = stepCount;
            markDirty();
        }
    }
}
