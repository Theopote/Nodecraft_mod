package com.nodecraft.nodesystem.nodes.output.debug;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.gui.editor.impl.ZoomHelper;
import com.nodecraft.gui.layout.ImGuiChildScope;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.DebugValueFormatter;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.UUID;

/**
 * Value Monitor: identity passthrough with a live bounded display of the wire value.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "output.debug.value_monitor",
    displayName = "Value Monitor",
    description = "Passthrough identity with a live panel showing the connected value and type.",
    category = "output.debug",
    order = 0
)
public class ValueMonitorNode extends BaseCustomUINode {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValueMonitorNode.class);

    private static final String INPUT_VALUE_ID = "input_value";
    private static final String OUTPUT_VALUE_ID = "output_value";

    private volatile String displayContent = "";
    private volatile String typeLabel = "—";

    public ValueMonitorNode() {
        super(UUID.randomUUID(), "output.debug.value_monitor");
        BasePort input = new BasePort(INPUT_VALUE_ID, "Input", "Connect any output to inspect it here", NodeDataType.ANY, this);
        input.bindPassthroughType("T");
        addInputPort(input);
        BasePort output = new BasePort(OUTPUT_VALUE_ID, "Output", "Passthrough of the input value", NodeDataType.ANY, this);
        output.bindPassthroughType("T");
        addOutputPort(output);
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object value = inputValues.get(INPUT_VALUE_ID);
        DebugValueFormatter.FormatResult result = DebugValueFormatter.format(value, DebugValueFormatter.MONITOR_OPTIONS);
        typeLabel = result.typeLabel();
        displayContent = result.text();
        outputValues.put(OUTPUT_VALUE_ID, value);
    }

    @Override
    protected float calculateUIHeight() {
        float h = getMediumPadding();
        h += ImGui.getTextLineHeight() * 4;
        h += getMediumPadding();
        return h;
    }

    @Override
    protected float calculateMinUIWidth() {
        return 188f + getContentMargin();
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, l -> {
            try {
                float edgeMargin = ZoomHelper.applyZoom(getMediumPadding(), zoom);
                float aw = Math.max(0.0f, l.toPixelsExact(width) - edgeMargin * 2.0f);
                float totalHeightPixels = l.toPixelsExact(height);
                float topBottomPadding = ZoomHelper.applyZoom(getSmallPadding(), zoom);
                float screenH = Math.max(ImGui.getTextLineHeight() * 2.0f, totalHeightPixels - topBottomPadding * 2.0f);
                float baseCursorX = ImGui.getCursorPosX();
                ImGui.setCursorPosX(baseCursorX + edgeMargin);

                ImGui.pushStyleColor(ImGuiCol.ChildBg, 0.08f, 0.08f, 0.10f, 0.98f);
                ImGui.pushStyleVar(ImGuiStyleVar.ChildBorderSize, ZoomHelper.applyZoom(1.2f, zoom));
                ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, ZoomHelper.applyZoom(4f, zoom));
                ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, ZoomHelper.applyZoom(4f, zoom), ZoomHelper.applyZoom(4f, zoom));
                int childFlags = ImGuiWindowFlags.AlwaysUseWindowPadding
                        | ImGuiWindowFlags.NoScrollbar
                        | ImGuiWindowFlags.NoScrollWithMouse
                        | ImGuiWindowFlags.NoMove
                        | ImGuiWindowFlags.NoNav
                        | ImGuiWindowFlags.NoNavFocus;
                try (ImGuiChildScope scope = new ImGuiChildScope(
                        "##value_monitor_screen", aw, screenH, true, childFlags)) {
                    if (scope.isOpen()) {
                        float contentW = ImGui.getContentRegionAvailX();
                        ImGui.pushStyleColor(ImGuiCol.Text, 0xFF666666);
                        ImGui.text("[" + typeLabel + "]");
                        ImGui.popStyleColor();
                        ImGui.sameLine(0, ZoomHelper.applyZoom(6, zoom));
                        ImGui.setCursorPosY(ImGui.getCursorPosY() - ZoomHelper.applyZoom(2, zoom));

                        ImGui.pushStyleColor(ImGuiCol.Text, 0xFFCCDDEE);
                        String text = displayContent.isEmpty() ? "(unconnected or empty)" : displayContent;
                        ImGui.setNextItemWidth(contentW);
                        ImGui.textWrapped(text);
                        ImGui.popStyleColor();
                    }
                }
                ImGui.popStyleVar(3);
                ImGui.popStyleColor();
            } catch (Exception e) {
                LOGGER.error("ValueMonitorNode UI render failed", e);
            }
            return false;
        });
    }

    @Override
    public @Nullable Object getNodeState() {
        return new HashMap<String, Object>();
    }

    @Override
    public void setNodeState(@Nullable Object state) {
        // No persisted options.
    }
}
