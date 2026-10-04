package com.nodecraft.nodesystem.nodes.flow.loop;

import com.nodecraft.nodesystem.api.ExecRoutingNode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.ExecutionRunGuard;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.StrictIntegerUtils;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Condition-driven exec loop. Body loop-back re-evaluates Condition each pulse.
 * Iteration count is run-local on {@link ExecutionRunGuard}.
 * Completing a session clears the counter so a later pulse in the same run starts a new session.
 */
@NodeInfo(
    effect = NodeEffect.CONTEXT_WRITE,
    id = "flow.loop.while",
    displayName = "While Loop",
    description = "Routes exec_body while Condition is true under Max Iterations. "
        + "Loop exec_body back to exec_in; exec_complete fires on false or hit limit. "
        + "Condition is required BOOLEAN.",
    category = "flow.loop",
    order = 1
)
public class WhileLoopNode extends BaseNode implements ExecRoutingNode {

    private static final int DEFAULT_MAX_ITERATIONS = 256;

    private static final String INPUT_EXEC_ID = "exec_in";
    private static final String INPUT_CONDITION_ID = "input_condition";
    private static final String INPUT_MAX_ITERATIONS_ID = "input_max_iterations";

    private static final String OUTPUT_EXEC_BODY_ID = "exec_body";
    private static final String OUTPUT_EXEC_COMPLETE_ID = "exec_complete";
    private static final String OUTPUT_ITERATIONS_ID = "output_iterations";
    private static final String OUTPUT_TERMINATED_BY_CONDITION_ID = "output_terminated_by_condition";
    private static final String OUTPUT_HIT_LIMIT_ID = "output_hit_limit";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    @NodeProperty(displayName = "Max Iterations", category = "Loop", order = 1)
    private int maxIterations = DEFAULT_MAX_ITERATIONS;

    private transient Set<String> activeExecOutputs = Set.of();

    public WhileLoopNode() {
        super(UUID.randomUUID(), "flow.loop.while");

        addInputPort(new BasePort(INPUT_EXEC_ID, "Exec In", "Incoming execution pulse", NodeDataType.EXEC, this, true, false));
        addInputPort(new BasePort(INPUT_CONDITION_ID, "Condition",
            "Required BOOLEAN; unconnected is invalid", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_MAX_ITERATIONS_ID, "Max Iterations", "Exact INTEGER cap 1.."
            + GenerationLimits.MAX_LOOP_ITERATIONS, NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_EXEC_BODY_ID, "Exec Body", "Fires while condition is true", NodeDataType.EXEC, this));
        addOutputPort(new BasePort(OUTPUT_EXEC_COMPLETE_ID, "Exec Complete", "Fires when condition is false or hit limit", NodeDataType.EXEC, this));
        addOutputPort(new BasePort(OUTPUT_ITERATIONS_ID, "Iterations", "Body pulses fired in this loop session so far", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_TERMINATED_BY_CONDITION_ID, "Terminated By Condition", "Stopped because condition became false", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_HIT_LIMIT_ID, "Hit Limit", "Stopped because max iterations was reached", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether preflight succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why the loop did not run", NodeDataType.STRING, this));
    }

    @Override
    public Set<String> getActiveExecOutputPortIds() {
        return activeExecOutputs;
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        activeExecOutputs = Set.of();
        outputValues.put(OUTPUT_EXEC_BODY_ID, null);
        outputValues.put(OUTPUT_EXEC_COMPLETE_ID, null);
        outputValues.put(OUTPUT_ITERATIONS_ID, 0);
        outputValues.put(OUTPUT_TERMINATED_BY_CONDITION_ID, false);
        outputValues.put(OUTPUT_HIT_LIMIT_ID, false);

        Boolean condition = resolveCondition();
        if (condition == null) {
            publishValid(false, "Condition is required, null, or invalid.");
            return;
        }

        Integer limit = resolveMaxIterations();
        if (limit == null) {
            publishValid(false, "Max Iterations must be an exact INTEGER between 1 and "
                + GenerationLimits.MAX_LOOP_ITERATIONS + ".");
            return;
        }

        String counterKey = iterationKey();
        int bodyCount = readIterations(context, counterKey);

        if (condition && bodyCount < limit) {
            bodyCount++;
            writeIterations(context, counterKey, bodyCount);
            activeExecOutputs = Set.of(OUTPUT_EXEC_BODY_ID);
            outputValues.put(OUTPUT_EXEC_BODY_ID, Boolean.TRUE);
            outputValues.put(OUTPUT_ITERATIONS_ID, bodyCount);
            outputValues.put(OUTPUT_TERMINATED_BY_CONDITION_ID, false);
            outputValues.put(OUTPUT_HIT_LIMIT_ID, false);
            publishValid(true, "");
            return;
        }

        boolean hitLimit = condition && bodyCount >= limit;
        boolean terminatedByCondition = !condition;

        outputValues.put(OUTPUT_ITERATIONS_ID, bodyCount);
        outputValues.put(OUTPUT_TERMINATED_BY_CONDITION_ID, terminatedByCondition);
        outputValues.put(OUTPUT_HIT_LIMIT_ID, hitLimit);
        clearIterations(context, counterKey);

        activeExecOutputs = Set.of(OUTPUT_EXEC_COMPLETE_ID);
        outputValues.put(OUTPUT_EXEC_COMPLETE_ID, Boolean.TRUE);
        publishValid(true, "");
    }

    private @Nullable Boolean resolveCondition() {
        if (!OptionalPortDrive.isConnected(this, INPUT_CONDITION_ID)) {
            Object raw = inputValues.get(INPUT_CONDITION_ID);
            // Unconnected with no value → required Condition missing.
            if (raw == null) {
                return null;
            }
            return raw instanceof Boolean bool ? bool : null;
        }
        Object value = inputValues.get(INPUT_CONDITION_ID);
        return value instanceof Boolean bool ? bool : null;
    }

    private @Nullable Integer resolveMaxIterations() {
        Integer resolved;
        if (OptionalPortDrive.isConnected(this, INPUT_MAX_ITERATIONS_ID)) {
            resolved = StrictIntegerUtils.requireExactInteger(inputValues.get(INPUT_MAX_ITERATIONS_ID));
        } else {
            Object raw = inputValues.get(INPUT_MAX_ITERATIONS_ID);
            if (raw == null) {
                resolved = maxIterations;
            } else {
                resolved = StrictIntegerUtils.requireExactInteger(raw);
            }
        }
        if (resolved == null || resolved < 1 || resolved > GenerationLimits.MAX_LOOP_ITERATIONS) {
            return null;
        }
        return resolved;
    }

    private String iterationKey() {
        return "flow.while.iterations." + getId();
    }

    private static int readIterations(@Nullable ExecutionContext context, String key) {
        ExecutionRunGuard guard = resolveGuard(context);
        return guard == null ? 0 : guard.getRunLocalInt(key);
    }

    private static void writeIterations(@Nullable ExecutionContext context, String key, int value) {
        ExecutionRunGuard guard = resolveGuard(context);
        if (guard != null) {
            guard.setRunLocalInt(key, value);
        }
    }

    private static void clearIterations(@Nullable ExecutionContext context, String key) {
        ExecutionRunGuard guard = resolveGuard(context);
        if (guard != null) {
            guard.clearRunLocalInt(key);
        }
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

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("maxIterations", maxIterations);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        Object maxIterationsObj = map.get("maxIterations");
        if (maxIterationsObj instanceof Integer integer
            && integer >= 1
            && integer <= GenerationLimits.MAX_LOOP_ITERATIONS) {
            maxIterations = integer;
        }
        // Ignore legacy defaultCondition and out-of-range maxIterations (keep current/default).
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(int maxIterations) {
        if (maxIterations >= 1 && maxIterations <= GenerationLimits.MAX_LOOP_ITERATIONS) {
            this.maxIterations = maxIterations;
            markDirty();
        }
    }
}
