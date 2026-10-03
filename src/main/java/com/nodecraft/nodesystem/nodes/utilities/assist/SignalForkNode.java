package com.nodecraft.nodesystem.nodes.utilities.assist;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Forks one input to multiple outputs by identity (same runtime reference).
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "utilities.assist.signal_fork",
    displayName = "Signal Fork",
    description = "Passes one input through to multiple outputs by identity for layout/forking.",
    category = "utilities.assist",
    order = 4
)
public class SignalForkNode extends BaseCustomUINode {

    private static final String INPUT_SIGNAL_ID = "input_signal";
    private static final int MIN_OUTPUT_BRANCHES = 1;
    private static final int DEFAULT_OUTPUT_BRANCHES = 2;
    private static final int MAX_OUTPUT_BRANCHES = 8;

    private volatile int outputBranchCount = DEFAULT_OUTPUT_BRANCHES;

    public SignalForkNode() {
        super(UUID.randomUUID(), "utilities.assist.signal_fork");

        BasePort input = new BasePort(
            INPUT_SIGNAL_ID,
            "Input",
            "Signal to fork",
            NodeDataType.ANY,
            this
        );
        input.bindPassthroughType("T");
        addInputPort(input);

        syncOutputBranchPorts(DEFAULT_OUTPUT_BRANCHES);
    }

    @Override
    public String getDescription() {
        return "Passes one input through to multiple outputs by identity for layout/forking.";
    }

    @Override
    protected float calculateUIHeight() {
        float height = getMediumPadding();
        height += ImGui.getFrameHeight();
        height += getMediumPadding();
        return height;
    }

    @Override
    protected float calculateMinUIWidth() {
        return 132f + getContentMargin();
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, l -> {
            boolean changed = false;
            float buttonWidth = 40f * zoom;
            float availableWidth = l.getAvailableContentWidth(width);

            l.addVerticalSpacing(getMediumPadding());

            boolean canRemove = canDecreaseOutputBranch();
            if (!canRemove) {
                ImGui.pushStyleColor(ImGuiCol.Button, 0.3f, 0.3f, 0.3f, 0.5f);
                ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 0.5f);
            }
            if (ImGui.button(" - ##fork_remove", buttonWidth, 0) && canRemove) {
                removeLastOutputBranch();
                changed = true;
            }
            if (!canRemove) {
                ImGui.popStyleColor(2);
            }

            ImGui.sameLine();

            boolean canAdd = canIncreaseOutputBranch();
            if (!canAdd) {
                ImGui.pushStyleColor(ImGuiCol.Button, 0.3f, 0.3f, 0.3f, 0.5f);
                ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 0.5f);
            }
            if (ImGui.button(" + ##fork_add", buttonWidth, 0) && canAdd) {
                addOutputBranch();
                changed = true;
            }
            if (!canAdd) {
                ImGui.popStyleColor(2);
            }

            ImGui.sameLine();
            ImGui.textDisabled("Outputs: " + outputBranchCount);

            if (availableWidth > 150f * zoom) {
                ImGui.sameLine();
                ImGui.textDisabled("(1-8)");
            }

            l.addVerticalSpacing(getMediumPadding());
            return changed;
        });
    }

    private static String getOutputPortId(int index) {
        return switch (index) {
            case 1 -> "output_a";
            case 2 -> "output_b";
            default -> "output_" + index;
        };
    }

    private static String getOutputDisplayName(int index) {
        if (index <= 0) {
            return "Output";
        }
        char suffix = (char) ('A' + (index - 1));
        return "Output " + suffix;
    }

    private static String getOutputDescription(int index) {
        if (index <= 0) {
            return "Fork output";
        }
        char suffix = (char) ('A' + (index - 1));
        return "Fork output " + suffix;
    }

    private void ensureOutputPortExists(int index) {
        String portId = getOutputPortId(index);
        if (findPortById(portId, false) != null) {
            return;
        }
        BasePort output = new BasePort(
            portId,
            getOutputDisplayName(index),
            getOutputDescription(index),
            NodeDataType.ANY,
            this
        );
        output.bindPassthroughType("T");
        addOutputPort(output);
    }

    private void syncOutputBranchPorts(int targetCount) {
        for (int i = 1; i <= targetCount; i++) {
            ensureOutputPortExists(i);
        }
        for (int i = outputBranchCount; i > targetCount; i--) {
            removePortById(getOutputPortId(i), false);
        }
        outputBranchCount = targetCount;
        markDirty();
    }

    public int getOutputBranchCount() {
        return outputBranchCount;
    }

    public boolean canIncreaseOutputBranch() {
        return outputBranchCount < MAX_OUTPUT_BRANCHES;
    }

    public boolean canDecreaseOutputBranch() {
        return outputBranchCount > MIN_OUTPUT_BRANCHES;
    }

    public boolean addOutputBranch() {
        if (!canIncreaseOutputBranch()) {
            return false;
        }
        ensureOutputPortExists(outputBranchCount + 1);
        outputBranchCount++;
        markDirty();
        return true;
    }

    public @Nullable String removeLastOutputBranch() {
        if (!canDecreaseOutputBranch()) {
            return null;
        }

        String removedPortId = getOutputPortId(outputBranchCount);
        removePortById(removedPortId, false);
        outputBranchCount--;
        markDirty();
        return removedPortId;
    }

    public void setOutputBranchCount(int count) {
        int clamped = Math.max(MIN_OUTPUT_BRANCHES, Math.min(MAX_OUTPUT_BRANCHES, count));
        if (outputBranchCount != clamped) {
            syncOutputBranchPorts(clamped);
        }
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object value = inputValues.get(INPUT_SIGNAL_ID);
        outputValues.clear();
        for (int i = 1; i <= outputBranchCount; i++) {
            outputValues.put(getOutputPortId(i), value);
        }
    }

    @Override
    public @Nullable Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("outputBranchCount", outputBranchCount);
        return state;
    }

    @Override
    public void setNodeState(@Nullable Object state) {
        if (state instanceof Integer integer) {
            setOutputBranchCount(integer);
            return;
        }

        if (state instanceof Map<?, ?> map) {
            Object count = map.get("outputBranchCount");
            if (count instanceof Integer integer) {
                setOutputBranchCount(integer);
            }
        }
    }
}
