package com.nodecraft.nodesystem.nodes.flow.loop;

import com.nodecraft.nodesystem.api.ExecLoopNode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Collection-driven exec loop: fires Exec Body once per list item, then Exec Complete.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "flow.loop.for_each",
    displayName = "For Each Loop",
    description = "Iterates a list with exec_body per item. List element type T binds to Item. "
        + "Enabled false or empty list: body 0 times, complete once.",
    category = "flow.loop",
    order = 0
)
public class ForEachLoopNode extends BaseNode implements ExecLoopNode {

    private static final String LIST_T = "T";

    private static final String INPUT_EXEC_ID = "exec_in";
    private static final String INPUT_LIST_ID = "input_list";
    private static final String INPUT_ENABLED_ID = "input_enabled";

    private static final String OUTPUT_EXEC_BODY_ID = "exec_body";
    private static final String OUTPUT_EXEC_COMPLETE_ID = "exec_complete";
    private static final String OUTPUT_ITEM_ID = "output_item";
    private static final String OUTPUT_INDEX_ID = "output_index";
    private static final String OUTPUT_COUNT_ID = "output_count";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    @NodeProperty(displayName = "Enabled", category = "Loop", order = 0)
    private boolean enabled = true;

    private transient List<Object> resolvedItems = List.of();
    private transient boolean fireComplete = true;
    private transient Set<String> activeExecOutputs = Set.of();

    public ForEachLoopNode() {
        super(UUID.randomUUID(), "flow.loop.for_each");

        addInputPort(new BasePort(INPUT_EXEC_ID, "Exec In", "Incoming execution pulse", NodeDataType.EXEC, this, true, false));
        addInputPort(new BasePort(INPUT_LIST_ID, "List", "List to iterate (element type T)", NodeDataType.LIST, this)
            .bindListType(LIST_T));
        addInputPort(new BasePort(INPUT_ENABLED_ID, "Enabled", "Whether iteration is enabled", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_EXEC_BODY_ID, "Exec Body", "Fires once per list item", NodeDataType.EXEC, this));
        addOutputPort(new BasePort(OUTPUT_EXEC_COMPLETE_ID, "Exec Complete", "Fires after all items (or empty/disabled)", NodeDataType.EXEC, this));
        addOutputPort(new BasePort(OUTPUT_ITEM_ID, "Item", "Current iterated item (T)", NodeDataType.ANY, this)
            .bindListElementType(LIST_T));
        addOutputPort(new BasePort(OUTPUT_INDEX_ID, "Index", "Current item index (0-based)", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_COUNT_ID, "Iteration Count",
            "Resolved number of iterations for this loop invocation (planned body pulses, not necessarily completed)",
            NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether preflight succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why the loop did not run", NodeDataType.STRING, this));
    }

    @Override
    public Set<String> getActiveExecOutputPortIds() {
        return activeExecOutputs;
    }

    @Override
    public int execLoopIterationCount() {
        return resolvedItems.size();
    }

    @Override
    public boolean shouldFireExecComplete() {
        return fireComplete;
    }

    @Override
    public void prepareExecLoopIteration(int iterationIndex) {
        outputValues.put(OUTPUT_EXEC_BODY_ID, null);
        outputValues.put(OUTPUT_EXEC_COMPLETE_ID, null);
        activeExecOutputs = Set.of();

        if (iterationIndex < 0 || iterationIndex >= resolvedItems.size()) {
            outputValues.put(OUTPUT_ITEM_ID, null);
            outputValues.put(OUTPUT_INDEX_ID, null);
            syncOutputPorts();
            return;
        }

        outputValues.put(OUTPUT_ITEM_ID, resolvedItems.get(iterationIndex));
        outputValues.put(OUTPUT_INDEX_ID, iterationIndex);
        outputValues.put(OUTPUT_EXEC_BODY_ID, Boolean.TRUE);
        activeExecOutputs = Set.of(OUTPUT_EXEC_BODY_ID);
        syncOutputPorts();
    }

    @Override
    public String execBodyPortId() {
        return OUTPUT_EXEC_BODY_ID;
    }

    @Override
    public String execCompletePortId() {
        return OUTPUT_EXEC_COMPLETE_ID;
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        activeExecOutputs = Set.of();
        outputValues.put(OUTPUT_EXEC_BODY_ID, null);
        outputValues.put(OUTPUT_EXEC_COMPLETE_ID, null);
        outputValues.put(OUTPUT_ITEM_ID, null);
        outputValues.put(OUTPUT_INDEX_ID, null);
        resolvedItems = List.of();
        fireComplete = true;

        Boolean enabledResolved = resolveEnabled();
        if (enabledResolved == null) {
            publish(0, false, "Enabled is null or invalid.");
            fireComplete = false;
            return;
        }

        if (!enabledResolved) {
            publish(0, true, "");
            return;
        }

        Object listObj = inputValues.get(INPUT_LIST_ID);
        if (!(listObj instanceof List<?> inputList)) {
            publish(0, false, "List is null or invalid.");
            fireComplete = false;
            return;
        }

        if (inputList.size() > GenerationLimits.MAX_LOOP_ITERATIONS) {
            publish(0, false, "List size exceeds MAX_LOOP_ITERATIONS ("
                + GenerationLimits.MAX_LOOP_ITERATIONS + ").");
            fireComplete = false;
            return;
        }

        resolvedItems = new ArrayList<>(inputList);
        publish(resolvedItems.size(), true, "");
    }

    private @Nullable Boolean resolveEnabled() {
        if (OptionalPortDrive.isConnected(this, INPUT_ENABLED_ID)) {
            Object value = inputValues.get(INPUT_ENABLED_ID);
            return value instanceof Boolean bool ? bool : null;
        }
        Object raw = inputValues.get(INPUT_ENABLED_ID);
        if (raw == null) {
            return enabled;
        }
        return raw instanceof Boolean bool ? bool : null;
    }

    private void publish(int count, boolean valid, String error) {
        outputValues.put(OUTPUT_COUNT_ID, count);
        outputValues.put(OUTPUT_VALID_ID, valid);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        markDirty();
    }
}
