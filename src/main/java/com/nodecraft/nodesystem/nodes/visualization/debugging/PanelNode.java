package com.nodecraft.nodesystem.nodes.visualization.debugging;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.gui.editor.impl.ZoomHelper;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Panel 节点: 显示连接到其输入端口的原始数据（文本形式）。
 * 提供格式化、自动刷新、换行控制选项和数据预览区域。
 */
@NodeInfo(
    id = "visualization.debugging.panel",
    displayName = "面板",
    description = "显示连接到其输入端口的原始数据（文本形式）",
    category = "visualization.debugging"
)
public class PanelNode extends BaseCustomUINode {

    @NodeProperty(displayName = "格式化", category = "显示", order = 1)
    private boolean useFormatting = true;

    @NodeProperty(displayName = "自动刷新", category = "显示", order = 2)
    private boolean autoRefresh = true;

    @NodeProperty(displayName = "自动换行", category = "显示", order = 3)
    private boolean wrapText = true;

    @NodeProperty(displayName = "最大长度", category = "显示", order = 4)
    private int maxDisplayLength = 2000;

    private volatile String panelContent = "";

    private static final String INPUT_DATA_ID = "input_data";
    private static final String INPUT_FORMAT_ID = "input_format";
    private static final String INPUT_MAX_LENGTH_ID = "input_max_length";
    private static final String INPUT_REFRESH_ID = "input_refresh";
    private static final String OUTPUT_TEXT_ID = "output_text";
    private static final String OUTPUT_TEXT_LENGTH_ID = "output_text_length";
    private static final String OUTPUT_DATA_TYPE_ID = "output_data_type";

    public PanelNode() {
        super(UUID.randomUUID(), "visualization.debugging.panel");
        addInputPort(new BasePort(INPUT_DATA_ID, "Data", "要显示的数据（任意类型）", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_FORMAT_ID, "Use Formatting", "是否使用格式化显示", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_MAX_LENGTH_ID, "Max Length", "最大显示字符数", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_REFRESH_ID, "Refresh", "刷新触发信号", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_TEXT_ID, "Text", "显示的文本内容", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_TEXT_LENGTH_ID, "Text Length", "显示文本的长度", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_DATA_TYPE_ID, "Data Type", "输入数据的类型", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() { return "显示连接到其输入端口的原始数据（文本形式）"; }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object dataObj = inputValues.get(INPUT_DATA_ID);
        Object formatObj = inputValues.get(INPUT_FORMAT_ID);
        Object maxLengthObj = inputValues.get(INPUT_MAX_LENGTH_ID);

        boolean fmt = this.useFormatting;
        if (formatObj instanceof Boolean) fmt = (Boolean) formatObj;
        int maxLen = this.maxDisplayLength;
        if (maxLengthObj instanceof Number) maxLen = Math.max(10, ((Number) maxLengthObj).intValue());

        String displayText = "";
        String dataType = "null";
        if (dataObj != null) {
            dataType = getDataTypeName(dataObj);
            displayText = formatDataToString(dataObj, fmt, maxLen);
        }
        panelContent = displayText;
        outputValues.put(OUTPUT_TEXT_ID, displayText);
        outputValues.put(OUTPUT_TEXT_LENGTH_ID, displayText.length());
        outputValues.put(OUTPUT_DATA_TYPE_ID, dataType);
    }

    @Override
    protected float calculateUIHeight() {
        float h = getMediumPadding();
        h += ImGui.getTextLineHeight() * 4; // 数据预览区（4行）
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
                boolean childOpen = ImGui.beginChild(
                    "##panel_preview_screen",
                    aw,
                    screenH,
                    true,
                    ImGuiWindowFlags.AlwaysUseWindowPadding
                        | ImGuiWindowFlags.NoScrollbar
                        | ImGuiWindowFlags.NoScrollWithMouse
                        | ImGuiWindowFlags.NoMove
                        | ImGuiWindowFlags.NoNav
                        | ImGuiWindowFlags.NoNavFocus
                );

                if (childOpen) {
                    ImGui.pushStyleColor(ImGuiCol.Text, 0xFFCCCCCC);
                    String preview = panelContent.isEmpty() ? "(无数据)" : panelContent;
                    String[] lines = preview.split("\n", 5);
                    for (int i = 0; i < 4; i++) {
                        if (i < lines.length) {
                            String line = lines[i];
                            if (line.length() > 50) line = line.substring(0, 47) + "...";
                            ImGui.text(line);
                        } else {
                            ImGui.text("");
                        }
                    }
                    ImGui.popStyleColor();
                }

                ImGui.endChild();
                ImGui.popStyleVar(3);
                ImGui.popStyleColor();
            } catch (Exception e) {
                System.err.println("PanelNode UI渲染失败: " + e.getMessage());
            }
            return changed;
        });
    }

    private String getDataTypeName(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            return "List (size=" + list.size() + ")";
        }
        if (obj instanceof String) return "String";
        if (obj instanceof Integer) return "Integer";
        if (obj instanceof Float) return "Float";
        if (obj instanceof Double) return "Double";
        if (obj instanceof Boolean) return "Boolean";
        if (obj instanceof Number) return "Number";
        return obj.getClass().getSimpleName();
    }

    private String formatDataToString(Object data, boolean fmt, int maxLen) {
        if (data == null) return "null";
        String result = data.toString();
        if (result.length() > maxLen) result = result.substring(0, maxLen) + "...";
        return result;
    }

    public String getPanelContent() { return panelContent; }
    public boolean isUseFormatting() { return useFormatting; }
    public void setUseFormatting(boolean v) { if (this.useFormatting != v) { this.useFormatting = v; markDirty(); } }
    public boolean isAutoRefresh() { return autoRefresh; }
    public void setAutoRefresh(boolean v) { if (this.autoRefresh != v) { this.autoRefresh = v; markDirty(); } }
    public boolean isWrapText() { return wrapText; }
    public void setWrapText(boolean v) { if (this.wrapText != v) { this.wrapText = v; markDirty(); } }
    public int getMaxDisplayLength() { return maxDisplayLength; }
    public void setMaxDisplayLength(int v) { v = Math.max(10, v); if (this.maxDisplayLength != v) { this.maxDisplayLength = v; markDirty(); } }

    @Override
    public @Nullable Object getNodeState() {
        java.util.Map<String, Object> s = new java.util.HashMap<>();
        s.put("useFormatting", useFormatting); s.put("autoRefresh", autoRefresh);
        s.put("maxDisplayLength", maxDisplayLength); s.put("wrapText", wrapText);
        return s;
    }

    @Override
    public void setNodeState(@Nullable Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> m = (java.util.Map<?, ?>) state;
            if (m.get("useFormatting") instanceof Boolean) setUseFormatting((Boolean) m.get("useFormatting"));
            if (m.get("autoRefresh") instanceof Boolean) setAutoRefresh((Boolean) m.get("autoRefresh"));
            if (m.get("maxDisplayLength") instanceof Number) setMaxDisplayLength(((Number) m.get("maxDisplayLength")).intValue());
            if (m.get("wrapText") instanceof Boolean) setWrapText((Boolean) m.get("wrapText"));
        }
    }
}
