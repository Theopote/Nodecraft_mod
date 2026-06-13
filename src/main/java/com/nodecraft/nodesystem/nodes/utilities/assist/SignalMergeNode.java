package com.nodecraft.nodesystem.nodes.utilities.assist;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
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
 * 汇线节点：将两路输入按优先级汇聚为一路输出，用于简化连线。
 */
@NodeInfo(
    id = "utilities.assist.signal_merge",
    displayName = "Signal Merge",
    description = "将两路输入按优先级汇聚为一路输出",
    category = "utilities.assist"
)
public class SignalMergeNode extends BaseCustomUINode {

    private static final int MIN_INPUT_BRANCHES = 2;
    private static final int DEFAULT_INPUT_BRANCHES = 2;
    private static final int MAX_INPUT_BRANCHES = 8;

    private static final String INPUT_PRIMARY_ID = "input_primary";
    private static final String INPUT_SECONDARY_ID = "input_secondary";
    private static final String INPUT_PREFER_PRIMARY_ID = "input_prefer_primary";

    private static final String OUTPUT_SIGNAL_ID = "output_signal";
    private static final String OUTPUT_SOURCE_ID = "output_source";

    private volatile int inputBranchCount = DEFAULT_INPUT_BRANCHES;
    @NodeProperty(displayName = "Prefer Primary", category = "Merge", order = 1)
    private boolean preferPrimary = true;

    public SignalMergeNode() {
        super(UUID.randomUUID(), "utilities.assist.signal_merge");

        rebuildInputPorts();

        addInputPort(new BasePort(
            INPUT_PREFER_PRIMARY_ID,
            "主输入优先",
            "是否优先使用主输入（布尔）",
            NodeDataType.BOOLEAN,
            this
        ));

        addOutputPort(new BasePort(
            OUTPUT_SIGNAL_ID,
            "输出",
            "汇聚后的输出",
            NodeDataType.ANY,
            this
        ));

        addOutputPort(new BasePort(
            OUTPUT_SOURCE_ID,
            "来源",
            "输出来源：primary / secondary / branch_n / none",
            NodeDataType.STRING,
            this
        ));
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

            boolean canRemove = canDecreaseInputBranch();
            if (!canRemove) {
                ImGui.pushStyleColor(ImGuiCol.Button, 0.3f, 0.3f, 0.3f, 0.5f);
                ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 0.5f);
            }
            if (ImGui.button(" - ##merge_remove", buttonWidth, 0) && canRemove) {
                removeLastInputBranch();
                changed = true;
            }
            if (!canRemove) {
                ImGui.popStyleColor(2);
            }

            ImGui.sameLine();

            boolean canAdd = canIncreaseInputBranch();
            if (!canAdd) {
                ImGui.pushStyleColor(ImGuiCol.Button, 0.3f, 0.3f, 0.3f, 0.5f);
                ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 0.5f);
            }
            if (ImGui.button(" + ##merge_add", buttonWidth, 0) && canAdd) {
                addInputBranch();
                changed = true;
            }
            if (!canAdd) {
                ImGui.popStyleColor(2);
            }

            ImGui.sameLine();
            ImGui.textDisabled("Inputs: " + inputBranchCount);

            if (availableWidth > 150f * zoom) {
                ImGui.sameLine();
                ImGui.textDisabled("(2-8)");
            }

            l.addVerticalSpacing(getMediumPadding());
            return changed;
        });
    }

    private static String getInputBranchPortId(int index) {
        return switch (index) {
            case 1 -> INPUT_PRIMARY_ID;
            case 2 -> INPUT_SECONDARY_ID;
            default -> "input_branch_" + index;
        };
    }

    private static String getInputBranchDisplayName(int index) {
        return switch (index) {
            case 1 -> "主输入";
            case 2 -> "次输入";
            default -> "输入 " + index;
        };
    }

    private static String getInputBranchDescription(int index) {
        return switch (index) {
            case 1 -> "主优先级输入";
            case 2 -> "次优先级输入";
            default -> "可参与汇聚的输入分支 " + index;
        };
    }

    private static String getSourceNameForBranch(int index) {
        return switch (index) {
            case 1 -> "primary";
            case 2 -> "secondary";
            default -> "branch_" + index;
        };
    }

    private void rebuildInputPorts() {
        inputPorts.clear();
        for (int i = 1; i <= inputBranchCount; i++) {
            addInputPort(new BasePort(
                getInputBranchPortId(i),
                getInputBranchDisplayName(i),
                getInputBranchDescription(i),
                NodeDataType.ANY,
                this
            ));
        }
    }

    public int getInputBranchCount() {
        return inputBranchCount;
    }

    public boolean canIncreaseInputBranch() {
        return inputBranchCount < MAX_INPUT_BRANCHES;
    }

    public boolean canDecreaseInputBranch() {
        return inputBranchCount > MIN_INPUT_BRANCHES;
    }

    public boolean addInputBranch() {
        if (!canIncreaseInputBranch()) {
            return false;
        }

        inputBranchCount++;
        rebuildInputPorts();
        markDirty();
        return true;
    }

    public @Nullable String removeLastInputBranch() {
        if (!canDecreaseInputBranch()) {
            return null;
        }

        String removedPortId = getInputBranchPortId(inputBranchCount);
        inputBranchCount--;
        rebuildInputPorts();
        markDirty();
        return removedPortId;
    }

    public void setInputBranchCount(int count) {
        int clamped = Math.max(MIN_INPUT_BRANCHES, Math.min(MAX_INPUT_BRANCHES, count));
        if (inputBranchCount != clamped) {
            inputBranchCount = clamped;
            rebuildInputPorts();
            markDirty();
        }
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean usePrimaryFirst = preferPrimary;
        Object preferPrimaryInput = inputValues.get(INPUT_PREFER_PRIMARY_ID);
        if (preferPrimaryInput instanceof Boolean value) {
            usePrimaryFirst = value;
        }

        Object result = null;
        String source = "none";

        if (usePrimaryFirst) {
            for (int i = 1; i <= inputBranchCount; i++) {
                Object value = inputValues.get(getInputBranchPortId(i));
                if (value != null) {
                    result = value;
                    source = getSourceNameForBranch(i);
                    break;
                }
            }
        } else {
            for (int i = inputBranchCount; i >= 1; i--) {
                Object value = inputValues.get(getInputBranchPortId(i));
                if (value != null) {
                    result = value;
                    source = getSourceNameForBranch(i);
                    break;
                }
            }
        }

        outputValues.put(OUTPUT_SIGNAL_ID, result);
        outputValues.put(OUTPUT_SOURCE_ID, source);
    }

    public boolean isPreferPrimary() {
        return preferPrimary;
    }

    public void setPreferPrimary(boolean preferPrimary) {
        if (this.preferPrimary != preferPrimary) {
            this.preferPrimary = preferPrimary;
            markDirty();
        }
    }

    @Override
    public @Nullable Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("preferPrimary", preferPrimary);
        state.put("inputBranchCount", inputBranchCount);
        return state;
    }

    @Override
    public void setNodeState(@Nullable Object state) {
        if (state instanceof Boolean value) {
            setPreferPrimary(value);
            return;
        }

        if (state instanceof Map<?, ?> map) {
            Object prefer = map.get("preferPrimary");
            if (prefer instanceof Boolean value) {
                setPreferPrimary(value);
            }

            Object count = map.get("inputBranchCount");
            if (count instanceof Number number) {
                setInputBranchCount(number.intValue());
            }
        }
    }
}
