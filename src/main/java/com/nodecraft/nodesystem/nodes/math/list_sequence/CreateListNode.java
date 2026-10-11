package com.nodecraft.nodesystem.nodes.math.list_sequence;

import com.nodecraft.gui.editor.GraphStructureEditSupport;
import com.nodecraft.gui.editor.StructureEditRollbackException;
import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.gui.editor.impl.ImGuiNodeEditor;
import com.nodecraft.gui.editor.impl.ZoomHelper;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Generic heterogeneous list builder. Typed lists come from typed producers.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.list.create_list",
    displayName = "Create List",
    description = "Generic: packs multiple ANY items into a heterogeneous LIST (not a typed LIST<T> constructor).",
    category = "math.list",
    order = 80
)
public class CreateListNode extends BaseCustomUINode {

    private static final Logger LOGGER = LoggerFactory.getLogger(CreateListNode.class);
    private static final String OUTPUT_LIST_ID = "output_list";
    private static final int MIN_INPUT_COUNT = 1;
    private static final int MAX_INPUT_COUNT = 20;

    @NodeProperty(
        displayName = "Input Count",
        category = "Settings",
        order = 1,
        description = "Number of dynamic list input ports."
    )
    private volatile int inputCount = 3;

    public CreateListNode() {
        super(UUID.randomUUID(), "math.list.create_list");
        rebuildInputPorts();
        addOutputPort(new BasePort(
            OUTPUT_LIST_ID,
            "List",
            "The resulting generic list containing all input items",
            NodeDataType.LIST,
            this
        ));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        List<Object> resultList = new ArrayList<>();
        for (int i = 0; i < inputCount; i++) {
            String portId = inputPortId(i);
            if (!isDriven(portId)) {
                continue;
            }
            resultList.add(inputValues.get(portId));
        }
        outputValues.put(OUTPUT_LIST_ID, resultList);
    }

    private boolean isDriven(String portId) {
        return OptionalPortDrive.isConnected(this, portId) || inputValues.containsKey(portId);
    }

    @Override
    protected float calculateUIHeight() {
        return getMediumPadding() + ImGui.getFrameHeight() + getMediumPadding();
    }

    @Override
    protected float calculateMinUIWidth() {
        return 132f + getContentMargin();
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, layout -> {
            boolean changed = false;
            try {
                layout.addVerticalSpacing(getMediumPadding());

                float buttonWidth = ZoomHelper.applyZoom(40f, zoom);
                boolean canRemove = inputCount > MIN_INPUT_COUNT;
                boolean canAdd = inputCount < MAX_INPUT_COUNT;

                pushDisabledButtonStyle(!canRemove);
                if (ImGui.button("-##remove", buttonWidth, 0) && canRemove) {
                    changed = changeInputCountWithHistory(inputCount - 1) || changed;
                }
                popDisabledButtonStyle(!canRemove);

                ImGui.sameLine();

                pushDisabledButtonStyle(!canAdd);
                if (ImGui.button("+##add", buttonWidth, 0) && canAdd) {
                    changed = changeInputCountWithHistory(inputCount + 1) || changed;
                }
                popDisabledButtonStyle(!canAdd);

                layout.addVerticalSpacing(getMediumPadding());
            } catch (Exception e) {
                LOGGER.error("Failed to render CreateListNode UI", e);
            }
            return changed;
        });
    }

    private void rebuildInputPorts() {
        inputPorts.clear();
        for (int i = 0; i < inputCount; i++) {
            IPort inputPort = new BasePort(
                inputPortId(i),
                "Item " + (i + 1),
                "Item to add to the output list",
                NodeDataType.ANY,
                this
            );
            addInputPort(inputPort);
        }
        invalidateCache();
    }

    private String inputPortId(int index) {
        return "input_" + index;
    }

    private void pushDisabledButtonStyle(boolean disabled) {
        if (!disabled) {
            return;
        }
        ImGui.pushStyleColor(ImGuiCol.Button, 0.3f, 0.3f, 0.3f, 0.5f);
        ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 0.5f);
    }

    private void popDisabledButtonStyle(boolean disabled) {
        if (disabled) {
            ImGui.popStyleColor(2);
        }
    }

    public int getInputCount() {
        return inputCount;
    }

    public void setInputCount(int count) {
        int clamped = Math.max(MIN_INPUT_COUNT, Math.min(MAX_INPUT_COUNT, count));
        if (clamped != inputCount) {
            inputCount = clamped;
            rebuildInputPorts();
            markDirty();
        }
    }

    /**
     * Custom-UI +/- path: same full-document transaction as the property-panel {@code inputCount}
     * setter, so Undo restores ports and dangling edges together.
     *
     * @return true when the input count actually changed
     */
    public boolean changeInputCountWithHistory(int newCount) {
        int clamped = Math.max(MIN_INPUT_COUNT, Math.min(MAX_INPUT_COUNT, newCount));
        if (clamped == inputCount) {
            return false;
        }
        final int target = clamped;
        try {
            ImGuiNodeEditor editor = ImGuiNodeEditor.getInstance();
            // Only use the structure transaction when this node is in the active editor graph.
            if (editor.getCurrentGraph() == null || editor.getCurrentGraph().getNode(getId()) == null) {
                setInputCount(target);
                return getInputCount() == target;
            }
            GraphStructureEditSupport.Outcome outcome = GraphStructureEditSupport.run(
                    editor.structureEditHost(),
                    "Change inputCount",
                    () -> {
                        setInputCount(target);
                        editor.cleanupDanglingConnections();
                        return true;
                    }
            );
            return GraphStructureEditSupport.succeeded(outcome) && getInputCount() == target;
        } catch (StructureEditRollbackException e) {
            LOGGER.error("CreateList inputCount structure edit rollback failed: {}", e.getMessage(), e);
            return false;
        } catch (Exception e) {
            // Editor unavailable (tests / headless): mutate without history.
            setInputCount(target);
            return getInputCount() == target;
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("inputCount", getInputCount());
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        // Legacy allowDifferentTypes is ignored — inputs are always ANY.
        Object count = map.get("inputCount");
        if (count instanceof Number value) {
            setInputCount(value.intValue());
        }
    }
}
