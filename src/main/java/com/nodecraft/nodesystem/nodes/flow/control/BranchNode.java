package com.nodecraft.nodesystem.nodes.flow.control;

import com.nodecraft.nodesystem.api.ExecRoutingNode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@NodeInfo(
    id = "flow.control.branch",
    displayName = "Branch",
    description = "Routes data and exec flow by condition. Wire exec_true/exec_false for branch skipping; legacy data outputs still work in dataflow graphs.",
    category = "flow.control",
    order = 0
)
public class BranchNode extends BaseNode implements ExecRoutingNode {

    private static final String INPUT_EXEC_ID = "exec_in";
    private static final String INPUT_CONDITION_ID = "input_condition";
    private static final String INPUT_SIGNAL_ID = "input_signal";

    private static final String OUTPUT_EXEC_TRUE_ID = "exec_true";
    private static final String OUTPUT_EXEC_FALSE_ID = "exec_false";
    private static final String OUTPUT_TRUE_ID = "output_true";
    private static final String OUTPUT_FALSE_ID = "output_false";

    private transient Set<String> activeExecOutputs = Set.of();

    public BranchNode() {
        super(UUID.randomUUID(), "flow.control.branch");

        addInputPort(new BasePort(INPUT_EXEC_ID, "Exec In", "Incoming execution pulse", NodeDataType.EXEC, this, true, false));
        addInputPort(new BasePort(INPUT_CONDITION_ID, "Condition", "Branch condition", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_SIGNAL_ID, "Signal", "Value to route", NodeDataType.ANY, this));

        addOutputPort(new BasePort(OUTPUT_EXEC_TRUE_ID, "Exec True", "Fires when condition is true", NodeDataType.EXEC, this));
        addOutputPort(new BasePort(OUTPUT_EXEC_FALSE_ID, "Exec False", "Fires when condition is false", NodeDataType.EXEC, this));
        addOutputPort(new BasePort(OUTPUT_TRUE_ID, "True", "Signal routed to true branch", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_FALSE_ID, "False", "Signal routed to false branch", NodeDataType.ANY, this));
    }

    @Override
    public Set<String> getActiveExecOutputPortIds() {
        return activeExecOutputs;
    }

    @Override
    public void processNode(ExecutionContext context) {
        boolean condition = coerceToBoolean(inputValues.get(INPUT_CONDITION_ID));
        Object signal = inputValues.get(INPUT_SIGNAL_ID);

        activeExecOutputs = Set.of();
        outputValues.put(OUTPUT_EXEC_TRUE_ID, null);
        outputValues.put(OUTPUT_EXEC_FALSE_ID, null);

        if (signal == null) {
            outputValues.put(OUTPUT_TRUE_ID, null);
            outputValues.put(OUTPUT_FALSE_ID, null);
            return;
        }

        if (condition) {
            activeExecOutputs = Set.of(OUTPUT_EXEC_TRUE_ID);
            outputValues.put(OUTPUT_EXEC_TRUE_ID, Boolean.TRUE);
            outputValues.put(OUTPUT_TRUE_ID, signal);
            outputValues.put(OUTPUT_FALSE_ID, null);
            return;
        }

        activeExecOutputs = Set.of(OUTPUT_EXEC_FALSE_ID);
        outputValues.put(OUTPUT_EXEC_FALSE_ID, Boolean.TRUE);
        outputValues.put(OUTPUT_TRUE_ID, null);
        outputValues.put(OUTPUT_FALSE_ID, signal);
    }

    private boolean coerceToBoolean(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0d;
        }
        if (value instanceof String stringValue) {
            String normalized = stringValue.trim();
            if (normalized.isEmpty()) {
                return false;
            }
            return switch (normalized.toLowerCase(Locale.ROOT)) {
                case "true", "yes", "1", "on" -> true;
                default -> false;
            };
        }
        return value != null;
    }
}

