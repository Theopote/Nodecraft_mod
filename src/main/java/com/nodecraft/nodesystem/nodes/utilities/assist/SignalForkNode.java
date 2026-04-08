package com.nodecraft.nodesystem.nodes.utilities.assist;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 分线节点：将一路输入透传到两路输出，用于整理连线布局。
 */
@NodeInfo(
    id = "utilities.assist.signal_fork",
    displayName = "分线节点",
    description = "将一路输入透传到两路输出，便于连线分流",
    category = "utilities.assist"
)
public class SignalForkNode extends BaseCustomUINode {

    private static final String INPUT_SIGNAL_ID = "input_signal";
    private static final int MIN_OUTPUT_BRANCHES = 1;
    private static final int DEFAULT_OUTPUT_BRANCHES = 2;
    private static final int MAX_OUTPUT_BRANCHES = 8;

    private volatile int outputBranchCount = DEFAULT_OUTPUT_BRANCHES;

    public SignalForkNode() {
        super(UUID.randomUUID(), "utilities.assist.signal_fork");

        addInputPort(new BasePort(
            INPUT_SIGNAL_ID,
            "输入",
            "需要分流的输入信号",
            NodeDataType.ANY,
            this
        ));

        rebuildOutputPorts();
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
            return "输出";
        }
        char suffix = (char) ('A' + (index - 1));
        return "输出 " + suffix;
    }

    private static String getOutputDescription(int index) {
        if (index <= 0) {
            return "分流输出";
        }
        char suffix = (char) ('A' + (index - 1));
        return "分流输出 " + suffix;
    }

    private void rebuildOutputPorts() {
        outputPorts.clear();
        for (int i = 1; i <= outputBranchCount; i++) {
            addOutputPort(new BasePort(
                getOutputPortId(i),
                getOutputDisplayName(i),
                getOutputDescription(i),
                NodeDataType.ANY,
                this
            ));
        }
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
        outputBranchCount++;
        rebuildOutputPorts();
        return true;
    }

    public @Nullable String removeLastOutputBranch() {
        if (!canDecreaseOutputBranch()) {
            return null;
        }

        String removedPortId = getOutputPortId(outputBranchCount);
        outputBranchCount--;
        rebuildOutputPorts();
        return removedPortId;
    }

    public void setOutputBranchCount(int count) {
        int clamped = Math.max(MIN_OUTPUT_BRANCHES, Math.min(MAX_OUTPUT_BRANCHES, count));
        if (outputBranchCount != clamped) {
            outputBranchCount = clamped;
            rebuildOutputPorts();
        }
    }

    @Override
    public String getDescription() {
        return "将一路输入透传到两路输出，便于连线分流";
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
        if (state instanceof Number number) {
            setOutputBranchCount(number.intValue());
            return;
        }

        if (state instanceof Map<?, ?> map) {
            Object count = map.get("outputBranchCount");
            if (count instanceof Number number) {
                setOutputBranchCount(number.intValue());
            }
        }
    }
}
