package com.nodecraft.nodesystem.nodes.flow.control;

import com.nodecraft.nodesystem.api.ExecRoutingNode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.ExecutionRunGuard;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * Once-per-execution-run exec gate. Gate state lives on {@link ExecutionRunGuard}, never SavedGraph.
 */
@NodeInfo(
    effect = NodeEffect.CONTEXT_WRITE,
    id = "flow.control.do_once",
    displayName = "Do Once",
    description = "Passes exec once per execution run unless reset. Signal is optional passthrough T and never gates exec.",
    category = "flow.control",
    order = 2
)
public class DoOnceNode extends BaseNode implements ExecRoutingNode {

    private static final String INPUT_EXEC_ID = "exec_in";
    private static final String INPUT_SIGNAL_ID = "input_signal";
    private static final String INPUT_RESET_ID = "input_reset";

    private static final String OUTPUT_EXEC_OUT_ID = "exec_out";
    private static final String OUTPUT_EXEC_BLOCKED_ID = "exec_blocked";
    private static final String OUTPUT_FIRST_PASS_ID = "output_first_pass";
    private static final String OUTPUT_BLOCKED_ID = "output_blocked";
    private static final String OUTPUT_DID_EXECUTE_ID = "output_did_execute";
    private static final String OUTPUT_HAS_EXECUTED_ID = "output_has_executed";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    private transient Set<String> activeExecOutputs = Set.of();

    public DoOnceNode() {
        super(UUID.randomUUID(), "flow.control.do_once");

        addInputPort(new BasePort(INPUT_EXEC_ID, "Exec In", "Incoming execution pulse", NodeDataType.EXEC, this, true, false));

        BasePort signalIn = new BasePort(INPUT_SIGNAL_ID, "Signal", "Optional payload (passthrough T)", NodeDataType.ANY, this);
        signalIn.bindPassthroughType("T");
        addInputPort(signalIn);

        addInputPort(new BasePort(INPUT_RESET_ID, "Reset", "Resets the run-local gate when true", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_EXEC_OUT_ID, "Exec Out", "Fires on first pass", NodeDataType.EXEC, this));
        addOutputPort(new BasePort(OUTPUT_EXEC_BLOCKED_ID, "Exec Blocked", "Fires when gate is already consumed", NodeDataType.EXEC, this));

        BasePort firstPass = new BasePort(OUTPUT_FIRST_PASS_ID, "First Pass", "Signal when first execution passes", NodeDataType.ANY, this);
        firstPass.bindPassthroughType("T");
        addOutputPort(firstPass);

        BasePort blocked = new BasePort(OUTPUT_BLOCKED_ID, "Blocked", "Signal when execution is blocked", NodeDataType.ANY, this);
        blocked.bindPassthroughType("T");
        addOutputPort(blocked);

        addOutputPort(new BasePort(OUTPUT_DID_EXECUTE_ID, "Did Execute", "Whether this pulse passed the gate", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_HAS_EXECUTED_ID, "Has Executed", "Whether the gate is already consumed", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether Reset preflight succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why Do Once did not run", NodeDataType.STRING, this));
    }

    @Override
    public Set<String> getActiveExecOutputPortIds() {
        return activeExecOutputs;
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        activeExecOutputs = Set.of();
        outputValues.put(OUTPUT_EXEC_OUT_ID, null);
        outputValues.put(OUTPUT_EXEC_BLOCKED_ID, null);
        outputValues.put(OUTPUT_FIRST_PASS_ID, null);
        outputValues.put(OUTPUT_BLOCKED_ID, null);
        outputValues.put(OUTPUT_DID_EXECUTE_ID, false);
        outputValues.put(OUTPUT_HAS_EXECUTED_ID, false);

        Boolean reset = resolveReset();
        if (reset == null) {
            publishValid(false, "Reset is null or invalid.");
            return;
        }

        Object signal = inputValues.get(INPUT_SIGNAL_ID);
        String gateKey = gateKey();

        if (reset) {
            clearExecuted(context, gateKey);
        }

        boolean alreadyExecuted = readExecuted(context, gateKey);
        if (!alreadyExecuted) {
            writeExecuted(context, gateKey, true);
            activeExecOutputs = Set.of(OUTPUT_EXEC_OUT_ID);
            outputValues.put(OUTPUT_EXEC_OUT_ID, Boolean.TRUE);
            outputValues.put(OUTPUT_FIRST_PASS_ID, signal);
            outputValues.put(OUTPUT_BLOCKED_ID, null);
            outputValues.put(OUTPUT_DID_EXECUTE_ID, true);
            outputValues.put(OUTPUT_HAS_EXECUTED_ID, true);
            publishValid(true, "");
            return;
        }

        activeExecOutputs = Set.of(OUTPUT_EXEC_BLOCKED_ID);
        outputValues.put(OUTPUT_EXEC_BLOCKED_ID, Boolean.TRUE);
        outputValues.put(OUTPUT_FIRST_PASS_ID, null);
        outputValues.put(OUTPUT_BLOCKED_ID, signal);
        outputValues.put(OUTPUT_DID_EXECUTE_ID, false);
        outputValues.put(OUTPUT_HAS_EXECUTED_ID, true);
        publishValid(true, "");
    }

    private @Nullable Boolean resolveReset() {
        if (OptionalPortDrive.isConnected(this, INPUT_RESET_ID)) {
            Object value = inputValues.get(INPUT_RESET_ID);
            return value instanceof Boolean bool ? bool : null;
        }
        Object raw = inputValues.get(INPUT_RESET_ID);
        if (raw == null) {
            return false;
        }
        return raw instanceof Boolean bool ? bool : null;
    }

    private String gateKey() {
        return "flow.do_once.executed." + getId();
    }

    private static boolean readExecuted(@Nullable ExecutionContext context, String key) {
        ExecutionRunGuard guard = resolveGuard(context);
        if (guard == null) {
            // No run-local store: treat as not-yet-executed for this call only.
            return false;
        }
        return guard.getRunLocalFlag(key);
    }

    private static void writeExecuted(@Nullable ExecutionContext context, String key, boolean executed) {
        ExecutionRunGuard guard = resolveGuard(context);
        if (guard == null) {
            return;
        }
        guard.setRunLocalFlag(key, executed);
    }

    private static void clearExecuted(@Nullable ExecutionContext context, String key) {
        ExecutionRunGuard guard = resolveGuard(context);
        if (guard == null) {
            return;
        }
        guard.clearRunLocalFlag(key);
    }

    private static @Nullable ExecutionRunGuard resolveGuard(@Nullable ExecutionContext context) {
        if (context != null && context.getSharedExecutionRunGuard() != null) {
            return context.getSharedExecutionRunGuard();
        }
        return ExecutionRunGuard.current();
    }

    private void publishValid(boolean valid, String error) {
        outputValues.put(OUTPUT_VALID_ID, valid);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}
