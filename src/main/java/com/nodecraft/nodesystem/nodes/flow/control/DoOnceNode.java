package com.nodecraft.nodesystem.nodes.flow.control;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "flow.control.do_once",
    displayName = "Do Once",
    description = "Allows a signal to pass once per execution context, unless reset.",
    category = "flow.control",
    order = 2
)
public class DoOnceNode extends BaseNode {

    private static final String INPUT_SIGNAL_ID = "input_signal";
    private static final String INPUT_RESET_ID = "input_reset";

    private static final String OUTPUT_FIRST_PASS_ID = "output_first_pass";
    private static final String OUTPUT_BLOCKED_ID = "output_blocked";
    private static final String OUTPUT_DID_EXECUTE_ID = "output_did_execute";
    private static final String OUTPUT_HAS_EXECUTED_ID = "output_has_executed";

    private static final String STATE_KEY_FALLBACK_EXECUTED = "fallbackExecuted";

    private boolean fallbackExecuted;

    public DoOnceNode() {
        super(UUID.randomUUID(), "flow.control.do_once");

        addInputPort(new BasePort(INPUT_SIGNAL_ID, "Signal", "Signal that should pass only once", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_RESET_ID, "Reset", "Resets execution gate when true", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_FIRST_PASS_ID, "First Pass", "Signal when first execution passes", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_BLOCKED_ID, "Blocked", "Signal when execution is blocked", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_DID_EXECUTE_ID, "Did Execute", "Whether this tick passed the signal", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_HAS_EXECUTED_ID, "Has Executed", "Whether the gate is already consumed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Do Once";
    }

    @Override
    public String getDescription() {
        return "Allows a signal to pass once per execution context, unless reset.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean reset = Boolean.TRUE.equals(inputValues.get(INPUT_RESET_ID));
        Object signal = inputValues.get(INPUT_SIGNAL_ID);
        boolean hasSignal = signal != null;

        if (context != null && reset) {
            context.setVariable(contextStateKey(), false);
        }
        if (context == null && reset) {
            fallbackExecuted = false;
        }

        boolean alreadyExecuted = readExecutedState(context);
        if (hasSignal && !alreadyExecuted) {
            writeExecutedState(context, true);
            outputValues.put(OUTPUT_FIRST_PASS_ID, signal);
            outputValues.put(OUTPUT_BLOCKED_ID, null);
            outputValues.put(OUTPUT_DID_EXECUTE_ID, true);
            outputValues.put(OUTPUT_HAS_EXECUTED_ID, true);
            return;
        }

        outputValues.put(OUTPUT_FIRST_PASS_ID, null);
        outputValues.put(OUTPUT_BLOCKED_ID, signal);
        outputValues.put(OUTPUT_DID_EXECUTE_ID, false);
        outputValues.put(OUTPUT_HAS_EXECUTED_ID, alreadyExecuted);
    }

    private boolean readExecutedState(@Nullable ExecutionContext context) {
        if (context != null) {
            Object value = context.getVariable(contextStateKey());
            return Boolean.TRUE.equals(value);
        }
        return fallbackExecuted;
    }

    private void writeExecutedState(@Nullable ExecutionContext context, boolean executed) {
        if (context != null) {
            context.setVariable(contextStateKey(), executed);
            return;
        }
        fallbackExecuted = executed;
    }

    private String contextStateKey() {
        return "flow.do_once.executed." + getId();
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put(STATE_KEY_FALLBACK_EXECUTED, fallbackExecuted);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        Object executed = map.get(STATE_KEY_FALLBACK_EXECUTED);
        if (executed instanceof Boolean value) {
            fallbackExecuted = value;
        }
    }
}

