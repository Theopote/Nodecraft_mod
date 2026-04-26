package com.nodecraft.nodesystem.nodes.flow.control;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;

import java.util.UUID;

@NodeInfo(
    id = "flow.control.branch",
    displayName = "Branch",
    description = "Routes an input signal to True or False output based on condition.",
    category = "flow.control",
    order = 0
)
public class BranchNode extends BaseNode {

    private static final String INPUT_CONDITION_ID = "input_condition";
    private static final String INPUT_SIGNAL_ID = "input_signal";

    private static final String OUTPUT_TRUE_ID = "output_true";
    private static final String OUTPUT_FALSE_ID = "output_false";
    private static final String OUTPUT_WAS_TRUE_ID = "output_was_true";
    private static final String OUTPUT_WAS_FALSE_ID = "output_was_false";

    public BranchNode() {
        super(UUID.randomUUID(), "flow.control.branch");

        addInputPort(new BasePort(INPUT_CONDITION_ID, "Condition", "Branch condition", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_SIGNAL_ID, "Signal", "Value to route", NodeDataType.ANY, this));

        addOutputPort(new BasePort(OUTPUT_TRUE_ID, "True", "Signal routed to true branch", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_FALSE_ID, "False", "Signal routed to false branch", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_WAS_TRUE_ID, "Was True", "Whether true branch was taken", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_WAS_FALSE_ID, "Was False", "Whether false branch was taken", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Branch";
    }

    @Override
    public String getDescription() {
        return "Routes an input signal to True or False output based on condition.";
    }

    @Override
    public void processNode(ExecutionContext context) {
        boolean condition = coerceToBoolean(inputValues.get(INPUT_CONDITION_ID));
        Object signal = inputValues.get(INPUT_SIGNAL_ID);

        if (condition) {
            outputValues.put(OUTPUT_TRUE_ID, signal);
            outputValues.put(OUTPUT_FALSE_ID, null);
            outputValues.put(OUTPUT_WAS_TRUE_ID, true);
            outputValues.put(OUTPUT_WAS_FALSE_ID, false);
            return;
        }

        outputValues.put(OUTPUT_TRUE_ID, null);
        outputValues.put(OUTPUT_FALSE_ID, signal);
        outputValues.put(OUTPUT_WAS_TRUE_ID, false);
        outputValues.put(OUTPUT_WAS_FALSE_ID, true);
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
            return Boolean.parseBoolean(normalized);
        }
        return value != null;
    }
}

