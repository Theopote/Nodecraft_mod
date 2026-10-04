package com.nodecraft.nodesystem.nodes.output.debug;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.gui.editor.impl.ZoomHelper;
import com.nodecraft.gui.layout.ImGuiChildScope;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.DebugValueFormatter;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Panel: live text inspector for the connected input value.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "output.debug.data_inspector",
    displayName = "Panel",
    description = "Displays the connected input value as bounded text for inspection.",
    category = "output.debug",
    order = 3
)
public class PanelNode extends BaseCustomUINode {

    private static final Logger LOGGER = LoggerFactory.getLogger(PanelNode.class);

    @NodeProperty(displayName = "Use Formatting", category = "Display", order = 1)
    private boolean useFormatting = true;

    @NodeProperty(displayName = "Wrap Text", category = "Display", order = 2)
    private boolean wrapText = true;

    @NodeProperty(displayName = "Max Length", category = "Display", order = 3)
    private int maxDisplayLength = 2000;

    private volatile String panelContent = "";
    private volatile String panelDataType = "null";

    private static final String INPUT_DATA_ID = "input_data";
    private static final String INPUT_FORMAT_ID = "input_format";
    private static final String INPUT_MAX_LENGTH_ID = "input_max_length";
    private static final String OUTPUT_TEXT_ID = "output_text";
    private static final String OUTPUT_TEXT_LENGTH_ID = "output_text_length";
    private static final String OUTPUT_DATA_TYPE_ID = "output_data_type";

    public PanelNode() {
        super(UUID.randomUUID(), "output.debug.data_inspector");
        addInputPort(new BasePort(INPUT_DATA_ID, "Data", "Value to display (any type)", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_FORMAT_ID, "Use Formatting", "Pretty structured formatting when true", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_MAX_LENGTH_ID, "Max Length", "Maximum display characters", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_TEXT_ID, "Text", "Formatted display text", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_TEXT_LENGTH_ID, "Text Length", "Length of display text", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_DATA_TYPE_ID, "Data Type", "Type label of the input value", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Boolean fmtResolved = OptionalPortDrive.resolveOptionalBoolean(this, INPUT_FORMAT_ID, this.useFormatting);
        Integer maxLenResolved = OptionalPortDrive.resolveOptionalInteger(this, INPUT_MAX_LENGTH_ID, this.maxDisplayLength);

        if (fmtResolved == null || maxLenResolved == null) {
            panelContent = "";
            panelDataType = "invalid";
            outputValues.put(OUTPUT_TEXT_ID, panelContent);
            outputValues.put(OUTPUT_TEXT_LENGTH_ID, 0);
            outputValues.put(OUTPUT_DATA_TYPE_ID, panelDataType);
            return;
        }

        boolean pretty = fmtResolved;
        int maxLen = Math.clamp(maxLenResolved, 10, GenerationLimits.MAX_DEBUG_TEXT_CHARS);
        Object dataObj = inputValues.get(INPUT_DATA_ID);

        DebugValueFormatter.FormatResult result = DebugValueFormatter.format(
            dataObj,
            new DebugValueFormatter.FormatOptions(
                maxLen,
                GenerationLimits.MAX_DEBUG_ITEMS,
                GenerationLimits.MAX_DEBUG_DEPTH,
                pretty
            )
        );
        panelContent = result.text();
        panelDataType = result.typeLabel();

        outputValues.put(OUTPUT_TEXT_ID, panelContent);
        outputValues.put(OUTPUT_TEXT_LENGTH_ID, panelContent.length());
        outputValues.put(OUTPUT_DATA_TYPE_ID, panelDataType);
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
        return 176f + getContentMargin();
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, l -> {
            boolean changed = false;
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
                        "##panel_preview_screen", aw, screenH, true, childFlags)) {
                    if (scope.isOpen()) {
                        ImGui.pushStyleColor(ImGuiCol.Text, 0xFFCCCCCC);
                        String preview = panelContent.isEmpty() ? "(no data)" : panelContent;
                        if (wrapText) {
                            ImGui.textWrapped(preview);
                        } else {
                            String[] lines = preview.split("\n", 5);
                            for (int i = 0; i < 4; i++) {
                                if (i < lines.length) {
                                    String line = lines[i];
                                    if (line.length() > 50) {
                                        line = line.substring(0, 47) + "...";
                                    }
                                    ImGui.text(line);
                                } else {
                                    ImGui.text("");
                                }
                            }
                        }
                        ImGui.popStyleColor();
                    }
                }
                ImGui.popStyleVar(3);
                ImGui.popStyleColor();
            } catch (Exception e) {
                LOGGER.error("PanelNode UI render failed", e);
            }
            return changed;
        });
    }

    public String getPanelContent() {
        return panelContent;
    }

    public boolean isUseFormatting() {
        return useFormatting;
    }

    public void setUseFormatting(boolean v) {
        if (this.useFormatting != v) {
            this.useFormatting = v;
            markDirty();
        }
    }

    public boolean isWrapText() {
        return wrapText;
    }

    public void setWrapText(boolean v) {
        if (this.wrapText != v) {
            this.wrapText = v;
            markDirty();
        }
    }

    public int getMaxDisplayLength() {
        return maxDisplayLength;
    }

    public void setMaxDisplayLength(int v) {
        v = Math.clamp(v, 10, GenerationLimits.MAX_DEBUG_TEXT_CHARS);
        if (this.maxDisplayLength != v) {
            this.maxDisplayLength = v;
            markDirty();
        }
    }

    @Override
    public @Nullable Object getNodeState() {
        Map<String, Object> s = new HashMap<>();
        s.put("useFormatting", useFormatting);
        s.put("maxDisplayLength", maxDisplayLength);
        s.put("wrapText", wrapText);
        return s;
    }

    @Override
    public void setNodeState(@Nullable Object state) {
        if (state instanceof Map<?, ?> m) {
            if (m.get("useFormatting") instanceof Boolean b) {
                setUseFormatting(b);
            }
            if (m.get("maxDisplayLength") instanceof Number n) {
                setMaxDisplayLength(n.intValue());
            }
            if (m.get("wrapText") instanceof Boolean b) {
                setWrapText(b);
            }
            // Legacy autoRefresh key ignored intentionally.
        }
    }
}
