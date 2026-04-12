package com.nodecraft.nodesystem.nodes.output.debug;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.gui.editor.impl.ZoomHelper;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import imgui.flag.ImGuiCol;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/**
 * Print to Chat 节点: 将输入数据显示到游戏聊天框。
 * 提供前缀文本输入、复选框控制输出格式。
 */
@NodeInfo(
    id = "output.debug.print_to_chat",
    displayName = "打印到聊天",
    description = "将输入数据显示到游戏聊天框",
    category = "output.debug",
    order = 1
)
public class PrintToChatNode extends BaseCustomUINode {

    @NodeProperty(displayName = "前缀", category = "格式", order = 1)
    private String prefix = "[Debug] ";

    @NodeProperty(displayName = "包含节点名", category = "格式", order = 2)
    private boolean includeNodeName = true;

    @NodeProperty(displayName = "包含数据类型", category = "格式", order = 3)
    private boolean includeDataType = true;

    @NodeProperty(displayName = "自动格式化", category = "格式", order = 4)
    private boolean autoFormat = true;

    @NodeProperty(displayName = "文本颜色", category = "格式", order = 5)
    private String textColor = "gold";

    private static final String INPUT_DATA_ID = "input_data";
    private static final String INPUT_PREFIX_ID = "input_prefix";
    private static final String INPUT_COLOR_ID = "input_color";
    private static final String INPUT_TRIGGER_ID = "input_trigger";
    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_MESSAGE_ID = "output_message";

    private transient ImString prefixBuffer = new ImString(256);
    private transient boolean prefixNeedsSync = true;

    public PrintToChatNode() {
        super(UUID.randomUUID(), "output.debug.print_to_chat");
        addInputPort(new BasePort(INPUT_DATA_ID, "Data", "要打印的数据", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_PREFIX_ID, "Prefix", "消息前缀", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_COLOR_ID, "Color", "文本颜色", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger", "触发打印信号", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "是否成功打印", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_MESSAGE_ID, "Message", "打印的消息内容", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() { return "将输入数据显示到游戏聊天框"; }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean success = false;
        String message = "";
        Object dataObj = inputValues.get(INPUT_DATA_ID);
        Object prefixObj = inputValues.get(INPUT_PREFIX_ID);
        Object colorObj = inputValues.get(INPUT_COLOR_ID);

        if (context != null && context.getPlayerAccessor() != null) {
            try {
                String pfx = (prefixObj instanceof String) ? (String) prefixObj : this.prefix;
                String color = (colorObj instanceof String) ? (String) colorObj : this.textColor;
                StringBuilder sb = new StringBuilder();
                sb.append(pfx);
                if (includeNodeName) sb.append(getDisplayName()).append(": ");
                String dataStr = formatData(dataObj);
                if (includeDataType && dataObj != null) sb.append("(").append(getDataTypeName(dataObj)).append(") ");
                sb.append(dataStr);
                message = sb.toString();
                success = true;
            } catch (Exception e) {
                System.err.println("Error printing to chat: " + e.getMessage());
            }
        }
        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_MESSAGE_ID, message);
    }

    @Override
    protected float calculateUIHeight() {
        return 0f;
    }

    @Override
    protected float calculateMinUIWidth() {
        return 0f;
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return false;
    }

    private void ensurePrefixBuffer() {
        if (prefixBuffer == null) prefixBuffer = new ImString(256);
        if (prefixNeedsSync) { prefixBuffer.set(prefix != null ? prefix : ""); prefixNeedsSync = false; }
    }

    private String getDataTypeName(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String) return "String";
        if (obj instanceof Integer) return "Int";
        if (obj instanceof Float) return "Float";
        if (obj instanceof Boolean) return "Bool";
        if (obj instanceof Number) return "Number";
        return obj.getClass().getSimpleName();
    }

    private String formatData(Object data) {
        if (data == null) return "null";
        if (data instanceof String) {
            String str = (String) data;
            return autoFormat && str.length() > 100 ? "\"" + str.substring(0, 97) + "...\"" : "\"" + str + "\"";
        }
        return data.toString();
    }

    public String getPrefix() { return prefix; }
    public void setPrefix(String v) { if (v != null && !v.equals(this.prefix)) { this.prefix = v; prefixNeedsSync = true; markDirty(); } }
    public boolean isIncludeNodeName() { return includeNodeName; }
    public void setIncludeNodeName(boolean v) { if (this.includeNodeName != v) { this.includeNodeName = v; markDirty(); } }
    public boolean isIncludeDataType() { return includeDataType; }
    public void setIncludeDataType(boolean v) { if (this.includeDataType != v) { this.includeDataType = v; markDirty(); } }
    public boolean isAutoFormat() { return autoFormat; }
    public void setAutoFormat(boolean v) { if (this.autoFormat != v) { this.autoFormat = v; markDirty(); } }
    public String getTextColor() { return textColor; }
    public void setTextColor(String v) { if (v != null) { this.textColor = v; markDirty(); } }

    @Override
    public @Nullable Object getNodeState() {
        java.util.Map<String, Object> s = new java.util.HashMap<>();
        s.put("prefix", prefix); s.put("includeNodeName", includeNodeName);
        s.put("includeDataType", includeDataType); s.put("autoFormat", autoFormat);
        s.put("textColor", textColor);
        return s;
    }

    @Override
    public void setNodeState(@Nullable Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> m = (java.util.Map<?, ?>) state;
            if (m.get("prefix") instanceof String) setPrefix((String) m.get("prefix"));
            if (m.get("includeNodeName") instanceof Boolean) setIncludeNodeName((Boolean) m.get("includeNodeName"));
            if (m.get("includeDataType") instanceof Boolean) setIncludeDataType((Boolean) m.get("includeDataType"));
            if (m.get("autoFormat") instanceof Boolean) setAutoFormat((Boolean) m.get("autoFormat"));
            if (m.get("textColor") instanceof String) setTextColor((String) m.get("textColor"));
        }
    }
}
