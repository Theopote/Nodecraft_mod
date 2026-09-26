package com.nodecraft.nodesystem.nodes.utilities.assist;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Coalesce: first non-null connected branch wins (prefer-primary ordering).
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "utilities.assist.coalesce",
    displayName = "Coalesce",
    description = "Returns the first non-null connected branch input by priority.",
    category = "utilities.assist",
    order = 2
)
public class CoalesceNode extends BaseCustomUINode {

    private static final int MIN_INPUT_BRANCHES = 2;
    private static final int DEFAULT_INPUT_BRANCHES = 2;
    private static final int MAX_INPUT_BRANCHES = 8;

    private static final String INPUT_PRIMARY_ID = "input_primary";
    private static final String INPUT_SECONDARY_ID = "input_secondary";
    private static final String INPUT_PREFER_PRIMARY_ID = "input_prefer_primary";

    private static final String OUTPUT_SIGNAL_ID = "output_signal";
    private static final String OUTPUT_SOURCE_ID = "output_source";
    private static final String OUTPUT_VALID_ID = "output_valid";

    private volatile int inputBranchCount = DEFAULT_INPUT_BRANCHES;
    @NodeProperty(displayName = "Prefer Primary", category = "Coalesce", order = 1)
    private boolean preferPrimary = true;

    public CoalesceNode() {
        super(UUID.randomUUID(), "utilities.assist.coalesce");

        rebuildInputPorts();

        BasePort signalOut = new BasePort(
            OUTPUT_SIGNAL_ID,
            "Output",
            "First non-null connected branch",
            NodeDataType.ANY,
            this
        );
        signalOut.bindPassthroughType("T");
        addOutputPort(signalOut);

        addOutputPort(new BasePort(
            OUTPUT_SOURCE_ID,
            "Source",
            "Winning branch: primary / secondary / branch_n / none",
            NodeDataType.STRING,
            this
        ));

        addOutputPort(new BasePort(
            OUTPUT_VALID_ID,
            "Valid",
            "Always true for coalesce",
            NodeDataType.BOOLEAN,
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
            if (ImGui.button(" - ##coalesce_remove", buttonWidth, 0) && canRemove) {
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
            if (ImGui.button(" + ##coalesce_add", buttonWidth, 0) && canAdd) {
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
            case 1 -> "Primary";
            case 2 -> "Secondary";
            default -> "Branch " + index;
        };
    }

    private static String getInputBranchDescription(int index) {
        return switch (index) {
            case 1 -> "Primary priority input";
            case 2 -> "Secondary priority input";
            default -> "Coalesce input branch " + index;
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
            BasePort branch = new BasePort(
                getInputBranchPortId(i),
                getInputBranchDisplayName(i),
                getInputBranchDescription(i),
                NodeDataType.ANY,
                this
            );
            branch.bindPassthroughType("T");
            addInputPort(branch);
        }
        addInputPort(new BasePort(
            INPUT_PREFER_PRIMARY_ID,
            "Prefer Primary",
            "Whether to scan primary-first (boolean)",
            NodeDataType.BOOLEAN,
            this
        ));
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
        Boolean preferOverride = OptionalPortDrive.resolveOptionalBoolean(
            this, INPUT_PREFER_PRIMARY_ID, preferPrimary);
        // Connected Prefer Primary with null/invalid → fail closed (not property fallback).
        if (preferOverride == null) {
            outputValues.put(OUTPUT_SIGNAL_ID, null);
            outputValues.put(OUTPUT_SOURCE_ID, "none");
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }
        boolean usePrimaryFirst = preferOverride;

        Object result = null;
        String source = "none";

        if (usePrimaryFirst) {
            for (int i = 1; i <= inputBranchCount; i++) {
                String portId = getInputBranchPortId(i);
                if (!OptionalPortDrive.isConnected(this, portId)) {
                    continue;
                }
                Object value = inputValues.get(portId);
                if (value != null) {
                    result = value;
                    source = getSourceNameForBranch(i);
                    break;
                }
            }
        } else {
            for (int i = inputBranchCount; i >= 1; i--) {
                String portId = getInputBranchPortId(i);
                if (!OptionalPortDrive.isConnected(this, portId)) {
                    continue;
                }
                Object value = inputValues.get(portId);
                if (value != null) {
                    result = value;
                    source = getSourceNameForBranch(i);
                    break;
                }
            }
        }

        outputValues.put(OUTPUT_SIGNAL_ID, result);
        outputValues.put(OUTPUT_SOURCE_ID, source);
        outputValues.put(OUTPUT_VALID_ID, true);
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
