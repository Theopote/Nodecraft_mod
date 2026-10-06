package com.nodecraft.nodesystem.nodes.flow.control;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One-shot EXEC source. Does not write the world; pair with Apply Changes / other WORLD_WRITE sinks.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "flow.control.manual_trigger",
    displayName = "Manual Trigger",
    description = "Fires a one-shot EXEC pulse when Trigger is pressed. Does not write the world.",
    category = "flow.control",
    order = 3
)
public class ManualTriggerNode extends BaseNode {

    private static final String OUTPUT_EXEC_ID = "output_exec";

    private final AtomicBoolean pulseRequested = new AtomicBoolean(false);

    public ManualTriggerNode() {
        super(UUID.randomUUID(), "flow.control.manual_trigger");
        addOutputPort(new BasePort(
            OUTPUT_EXEC_ID,
            "Exec",
            "One-shot EXEC pulse after Trigger is pressed",
            NodeDataType.EXEC,
            this
        ));
    }

    @Override
    public String getDescription() {
        return "Fires a one-shot EXEC pulse when Trigger is pressed. Does not write the world.";
    }

    public void requestPulse() {
        pulseRequested.set(true);
        markDirty();
    }

    public boolean isPulsePending() {
        return pulseRequested.get();
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean fire = pulseRequested.getAndSet(false);
        outputValues.put(OUTPUT_EXEC_ID, fire ? Boolean.TRUE : Boolean.FALSE);
    }
}
