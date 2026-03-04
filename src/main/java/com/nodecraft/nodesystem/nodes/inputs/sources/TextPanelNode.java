package com.nodecraft.nodesystem.nodes.inputs.sources;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.gui.editor.impl.ZoomHelper;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import imgui.ImGui;
import imgui.type.ImString;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiInputTextFlags;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 文本面板节点，提供多行文本编辑区域。
 */
@NodeInfo(
    id = "inputs.sources.text_panel",
    displayName = "文本面板",
    description = "用于手动输入文本列表或显示数据",
    category = "inputs.sources"
)
public class TextPanelNode extends BaseCustomUINode {
    
    @NodeProperty(displayName = "文本内容", category = "文本", order = 1,
                  description = "文本内容")
    private String text = "";

    @NodeProperty(displayName = "多行模式", category = "设置", order = 10,
                  description = "是否为多行文本")
    private boolean isMultiline = true;

    @NodeProperty(displayName = "分行输出", category = "设置", order = 11,
                  description = "是否将文本按行分割为列表输出")
    private boolean splitLines = true;

    @NodeProperty(displayName = "只读", category = "设置", order = 12,
                  description = "是否为只读模式")
    private boolean readOnly = false;

    @NodeProperty(displayName = "分隔符", category = "设置", order = 13,
                  description = "行分隔符")
    private String delimiter = "\n";
    
    // --- 端口 ---
    private static final String INPUT_TEXT_ID = "input_text";
    private static final String OUTPUT_TEXT_ID = "output_text";
    private static final String OUTPUT_LINES_ID = "output_lines";
    private static final String OUTPUT_LINE_COUNT_ID = "output_line_count";
    
    // --- UI状态 ---
    private transient ImString textBuffer = new ImString(32768);
    private transient boolean bufferNeedsSync = true;
    
    public TextPanelNode() {
        super(UUID.randomUUID(), "inputs.sources.text_panel");
        
        addInputPort(new BasePort(INPUT_TEXT_ID, "Text Input", "Optional text input to display", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_TEXT_ID, "Text", "The text content as a single string", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_LINES_ID, "Lines", "The text content as a list of lines", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_LINE_COUNT_ID, "Line Count", "The number of lines", NodeDataType.INTEGER, this));
    }
    
    @Override
    public String getDescription() { return "手动输入文本或显示数据"; }
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        String textValue = text;
        Object inputText = inputValues.get(INPUT_TEXT_ID);
        if (inputText != null) textValue = inputText.toString();
        
        List<String> lines;
        if (splitLines) {
            lines = new ArrayList<>(Arrays.asList(textValue.split(delimiter)));
        } else {
            lines = new ArrayList<>();
            if (!textValue.isEmpty()) lines.add(textValue);
        }
        
        outputValues.put(OUTPUT_TEXT_ID, textValue);
        outputValues.put(OUTPUT_LINES_ID, lines);
        outputValues.put(OUTPUT_LINE_COUNT_ID, lines.size());
    }
    
    @Override
    protected float calculateUIHeight() {
        float height = getMediumPadding();
        if (isMultiline) {
            height += ImGui.getTextLineHeightWithSpacing() * 6; // 6行高度的文本区
        } else {
            height += ImGui.getFrameHeight(); // 单行输入
        }
        height += getSmallPadding();
        height += ImGui.getTextLineHeight(); // 信息行
        height += getMediumPadding();
        return height;
    }

    @Override
    protected float calculateMinUIWidth() {
        return 200f + getContentMargin();
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, l -> {
            boolean changed = false;
            try {
                float availableWidth = width - ZoomHelper.applyZoom(getContentMargin() * 2, zoom);
                l.addVerticalSpacing(getMediumPadding());
                
                ensureBuffer();
                
                int flags = 0;
                if (readOnly) flags |= ImGuiInputTextFlags.ReadOnly;
                
                if (isMultiline) {
                    // === 多行文本区 ===
                    float textAreaHeight = ImGui.getTextLineHeightWithSpacing() * 6;
                    if (ImGui.inputTextMultiline("##text_panel", textBuffer, 
                            availableWidth / zoom, textAreaHeight / zoom, flags)) {
                        String newText = textBuffer.get();
                        if (!newText.equals(text)) {
                            text = newText;
                            markDirty();
                            changed = true;
                        }
                    }
                } else {
                    // === 单行输入 ===
                    l.pushFramePadding(4.0f, 3.0f);
                    l.setItemWidth(availableWidth / zoom);
                    if (ImGui.inputTextWithHint("##text_panel", "输入文本...", textBuffer, flags)) {
                        String newText = textBuffer.get();
                        if (!newText.equals(text)) {
                            text = newText;
                            markDirty();
                            changed = true;
                        }
                    }
                    l.popItemWidth();
                    l.popStyleVar();
                }
                
                l.addVerticalSpacing(getSmallPadding());
                
                // === 信息行 ===
                int charCount = text.length();
                int lineCount = text.isEmpty() ? 0 : text.split(delimiter, -1).length;
                ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 1.0f);
                ImGui.text(lineCount + " 行 | " + charCount + " 字符");
                ImGui.popStyleColor();
                
                l.addVerticalSpacing(getMediumPadding());
            } catch (Exception e) {
                System.err.println("TextPanelNode UI渲染失败: " + e.getMessage());
            }
            return changed;
        });
    }
    
    private void ensureBuffer() {
        if (textBuffer == null) textBuffer = new ImString(32768);
        if (bufferNeedsSync) {
            textBuffer.set(text != null ? text : "");
            bufferNeedsSync = false;
        }
    }
    
    public void setText(String text) {
        if (text == null) text = "";
        if (!this.text.equals(text)) {
            this.text = text;
            bufferNeedsSync = true;
            markDirty();
        }
    }
    
    public String getText() { return text; }
    public boolean isMultiline() { return isMultiline; }
    public void setMultiline(boolean multiline) { this.isMultiline = multiline; invalidateCache(); }
    public boolean isSplitLines() { return splitLines; }
    public void setSplitLines(boolean splitLines) { if (this.splitLines != splitLines) { this.splitLines = splitLines; markDirty(); } }
    public boolean isReadOnly() { return readOnly; }
    public void setReadOnly(boolean readOnly) { this.readOnly = readOnly; }
    public String getDelimiter() { return delimiter; }
    public void setDelimiter(String delimiter) { if (delimiter == null || delimiter.isEmpty()) delimiter = "\n"; if (!this.delimiter.equals(delimiter)) { this.delimiter = delimiter; markDirty(); } }
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("text", getText());
        state.put("isMultiline", isMultiline());
        state.put("splitLines", isSplitLines());
        state.put("readOnly", isReadOnly());
        state.put("delimiter", getDelimiter());
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> m = (java.util.Map<?, ?>) state;
            if (m.containsKey("isMultiline")) { Object v = m.get("isMultiline"); if (v instanceof Boolean) setMultiline((Boolean) v); }
            if (m.containsKey("splitLines")) { Object v = m.get("splitLines"); if (v instanceof Boolean) setSplitLines((Boolean) v); }
            if (m.containsKey("readOnly")) { Object v = m.get("readOnly"); if (v instanceof Boolean) setReadOnly((Boolean) v); }
            if (m.containsKey("delimiter")) { Object v = m.get("delimiter"); if (v instanceof String) setDelimiter((String) v); }
            if (m.containsKey("text")) { Object v = m.get("text"); if (v instanceof String) setText((String) v); }
        }
    }
}