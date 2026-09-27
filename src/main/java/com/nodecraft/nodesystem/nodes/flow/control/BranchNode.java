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
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * Routes exec and optional signal by a strict BOOLEAN condition.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "flow.control.branch",
    displayName = "Branch",
    description = "Routes exec by Condition. Signal is optional passthrough T and never gates exec routing.",
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
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    @NodeProperty(displayName = "Condition", category = "Branch", order = 0)
    private boolean condition = false;

    private transient Set<String> activeExecOutputs = Set.of();

    public BranchNode() {
        super(UUID.randomUUID(), "flow.control.branch");

        addInputPort(new BasePort(INPUT_EXEC_ID, "Exec In", "Incoming execution pulse", NodeDataType.EXEC, this, true, false));
        addInputPort(new BasePort(INPUT_CONDITION_ID, "Condition", "Branch condition (BOOLEAN only)", NodeDataType.BOOLEAN, this));

        BasePort signalIn = new BasePort(INPUT_SIGNAL_ID, "Signal", "Optional value to route (passthrough T)", NodeDataType.ANY, this);
        signalIn.bindPassthroughType("T");
        addInputPort(signalIn);

        addOutputPort(new BasePort(OUTPUT_EXEC_TRUE_ID, "Exec True", "Fires when condition is true", NodeDataType.EXEC, this));
        addOutputPort(new BasePort(OUTPUT_EXEC_FALSE_ID, "Exec False", "Fires when condition is false", NodeDataType.EXEC, this));

        BasePort trueOut = new BasePort(OUTPUT_TRUE_ID, "True", "Signal routed to true branch", NodeDataType.ANY, this);
        trueOut.bindPassthroughType("T");
        addOutputPort(trueOut);

        BasePort falseOut = new BasePort(OUTPUT_FALSE_ID, "False", "Signal routed to false branch", NodeDataType.ANY, this);
        falseOut.bindPassthroughType("T");
        addOutputPort(falseOut);

        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether Condition preflight succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why branching did not run", NodeDataType.STRING, this));
    }

    @Override
    public Set<String> getActiveExecOutputPortIds() {
        return activeExecOutputs;
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        activeExecOutputs = Set.of();
        outputValues.put(OUTPUT_EXEC_TRUE_ID, null);
        outputValues.put(OUTPUT_EXEC_FALSE_ID, null);
        outputValues.put(OUTPUT_TRUE_ID, null);
        outputValues.put(OUTPUT_FALSE_ID, null);

        Boolean resolved = resolveCondition();
        if (resolved == null) {
            publishValid(false, "Condition is null or invalid.");
            return;
        }

        Object signal = inputValues.get(INPUT_SIGNAL_ID);
        if (resolved) {
            activeExecOutputs = Set.of(OUTPUT_EXEC_TRUE_ID);
            outputValues.put(OUTPUT_EXEC_TRUE_ID, Boolean.TRUE);
            outputValues.put(OUTPUT_TRUE_ID, signal);
            outputValues.put(OUTPUT_FALSE_ID, null);
        } else {
            activeExecOutputs = Set.of(OUTPUT_EXEC_FALSE_ID);
            outputValues.put(OUTPUT_EXEC_FALSE_ID, Boolean.TRUE);
            outputValues.put(OUTPUT_TRUE_ID, null);
            outputValues.put(OUTPUT_FALSE_ID, signal);
        }
        publishValid(true, "");
    }

    /**
     * Connected: Boolean only (null/wrong type → fail closed).
     * Unconnected: local input if present, else property default.
     */
    private @Nullable Boolean resolveCondition() {
        if (OptionalPortDrive.isConnected(this, INPUT_CONDITION_ID)) {
            Object value = inputValues.get(INPUT_CONDITION_ID);
            return value instanceof Boolean bool ? bool : null;
        }
        Object raw = inputValues.get(INPUT_CONDITION_ID);
        if (raw == null) {
            return condition;
        }
        return raw instanceof Boolean bool ? bool : null;
    }

    private void publishValid(boolean valid, String error) {
        outputValues.put(OUTPUT_VALID_ID, valid);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    public boolean isCondition() {
        return condition;
    }

    public void setCondition(boolean condition) {
        this.condition = condition;
        markDirty();
    }
}
