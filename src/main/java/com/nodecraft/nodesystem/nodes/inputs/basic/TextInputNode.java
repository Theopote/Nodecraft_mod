package com.nodecraft.nodesystem.nodes.inputs.basic;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.gui.editor.impl.ZoomHelper;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import imgui.ImGui;
import imgui.type.ImString;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiInputTextFlags;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 文本输入节点，提供单行或多行文本输入框。
 */
@NodeInfo(
    id = "inputs.basic.text_input",
    displayName = "文本输入",
    description = "允许用户输入单行或多行文本",
    category = "inputs.basic"
)
public class TextInputNode extends BaseCustomUINode {
    
    // --- 节点属性 ---
    @NodeProperty(displayName = "文本内容", category = "内容", order = 1,
                  description = "当前的文本内容")
    private String text = "";

    @NodeProperty(displayName = "多行模式", category = "UI设置", order = 10,
                  description = "是否启用多行输入")
    private boolean multiline = false;

    @NodeProperty(displayName = "最大长度", category = "限制", order = 11,
                  description = "允许的最大文本长度")
    private int maxLength = 32767;

    @NodeProperty(displayName = "占位文本", category = "UI设置", order = 12,
                  description = "输入框为空时显示的提示文本")
    private String placeholder = "输入文本...";
    
    // --- 输出端口 ---
    private static final String OUTPUT_TEXT_ID = "output_text";
    private static final String OUTPUT_LENGTH_ID = "output_length";

    // --- UI状态 ---
    private transient ImString inputBuffer = null;
    private transient boolean bufferNeedsSync = true;

    // --- 常量 ---
    private static final int SINGLE_LINE_BUF_SIZE = 1024;
    private static final int MULTI_LINE_BUF_SIZE = 32768;
    
    public TextInputNode() {
        super(UUID.randomUUID(), "inputs.basic.text_input");
        IPort textOutput = new BasePort(OUTPUT_TEXT_ID, "Text", "The entered text", NodeDataType.STRING, this);
        addOutputPort(textOutput);
        IPort lengthOutput = new BasePort(OUTPUT_LENGTH_ID, "Length", "Text length", NodeDataType.INTEGER, this);
        addOutputPort(lengthOutput);
        updateOutput();
    }
    
    @Override
    public String getDescription() {
        return "允许用户输入单行或多行文本。";
    }
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        updateOutput();
    }
    
    // === BaseCustomUINode 抽象方法实现 ===

    @Override
    protected float calculateUIHeight() {
        float height = getMediumPadding(); // 顶部

        if (multiline) {
            height += ImGui.getTextLineHeight(); // "文本:" 标签
            height += getSmallPadding();
            height += ImGui.getFrameHeight() * 4; // 多行输入框 (约4行高度)
        } else {
            height += ImGui.getFrameHeight(); // 单行输入框
        }
        
        height += getSmallPadding();
        height += ImGui.getTextLineHeight(); // 字符计数行
        height += getMediumPadding(); // 底部
        return height;
    }

    @Override
    protected float calculateMinUIWidth() {
        return multiline ? 200f : 160f;
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, l -> {
            boolean valueChanged = false;
            
            try {
                float availableWidth = getAvailableWidth(width, zoom);
                
                // 确保缓冲区存在并同步
                ensureBuffer();
                
                l.addVerticalSpacing(getMediumPadding());
                
                if (multiline) {
                    // === 多行模式 ===
                    ImGui.pushStyleColor(ImGuiCol.Text, 0.7f, 0.7f, 0.7f, 1.0f);
                    ImGui.text("文本:");
                    ImGui.popStyleColor();
                    l.addVerticalSpacing(getSmallPadding());
                    
                    float inputWidth = availableWidth;
                    float inputHeight = ImGui.getFrameHeight() * 4;
                    
                    l.pushFramePadding(4.0f, 3.0f);
                    
                    if (ImGui.inputTextMultiline("##text_input", inputBuffer, inputWidth / zoom, inputHeight / zoom, 
                            ImGuiInputTextFlags.AllowTabInput)) {
                        String newText = inputBuffer.get();
                        if (newText.length() > maxLength) {
                            newText = newText.substring(0, maxLength);
                        }
                        if (!this.text.equals(newText)) {
                            this.text = newText;
                            updateOutput();
                            markDirty();
                            valueChanged = true;
                        }
                    }
                    
                    l.popStyleVar(); // framePadding
                    
                } else {
                    // === 单行模式 ===
                    float inputWidth = availableWidth;
                    
                    l.pushFramePadding(4.0f, 3.0f);
                    l.setItemWidth(inputWidth / zoom);
                    
                    if (ImGui.inputTextWithHint("##text_input", placeholder, inputBuffer)) {
                        String newText = inputBuffer.get();
                        // 单行模式移除换行
                        newText = newText.replace("\n", " ").replace("\r", "");
                        if (newText.length() > maxLength) {
                            newText = newText.substring(0, maxLength);
                        }
                        if (!this.text.equals(newText)) {
                            this.text = newText;
                            updateOutput();
                            markDirty();
                            valueChanged = true;
                        }
                    }
                    
                    l.popItemWidth();
                    l.popStyleVar(); // framePadding
                }
                
                l.addVerticalSpacing(getSmallPadding());
                
                // === 字符计数 ===
                String countText = text.length() + " / " + maxLength + " 字符";
                float countW = ImGui.calcTextSize(countText).x;
                // 右对齐字符计数
                float offsetX = availableWidth - countW;
                if (offsetX > 0) {
                    ImGui.setCursorPosX(ImGui.getCursorPosX() + offsetX);
                }
                float ratio = (float) text.length() / maxLength;
                if (ratio > 0.9f) {
                    ImGui.pushStyleColor(ImGuiCol.Text, 0.9f, 0.3f, 0.3f, 1.0f); // 红色警告
                } else {
                    ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 1.0f);
                }
                ImGui.text(countText);
                ImGui.popStyleColor();
                
                l.addVerticalSpacing(getMediumPadding());
                
            } catch (Exception e) {
                System.err.println("TextInputNode UI渲染失败: " + e.getMessage());
            }
            
            return valueChanged;
        });
    }
    
    // === 缓冲区管理 ===
    
    private void ensureBuffer() {
        int bufSize = multiline ? MULTI_LINE_BUF_SIZE : SINGLE_LINE_BUF_SIZE;
        if (inputBuffer == null || bufferNeedsSync) {
            inputBuffer = new ImString(bufSize);
            inputBuffer.set(text);
            bufferNeedsSync = false;
        }
    }
    
    // === 业务逻辑 ===
    
    public void setText(String text) {
        String newText = text != null ? text : "";
        if (newText.length() > maxLength) newText = newText.substring(0, maxLength);
        if (!multiline) newText = newText.replace("\n", " ").replace("\r", "");
        
        if (!this.text.equals(newText)) {
            this.text = newText;
            bufferNeedsSync = true;
            updateOutput();
            markDirty();
        }
    }
    
    private void updateOutput() {
        outputValues.put(OUTPUT_TEXT_ID, this.text);
        outputValues.put(OUTPUT_LENGTH_ID, this.text.length());
    }
    
    // === Getters / Setters ===
    
    public String getText() { return text; }
    public boolean isMultiline() { return multiline; }
    
    public void setMultiline(boolean multiline) {
        if (this.multiline != multiline) {
            this.multiline = multiline;
            bufferNeedsSync = true;
            if (!multiline) setText(this.text);
            invalidateCache();
        }
    }
    
    public int getMaxLength() { return maxLength; }
    
    public void setMaxLength(int maxLength) {
        if (maxLength <= 0) maxLength = 32767;
        this.maxLength = maxLength;
        if (text.length() > maxLength) setText(text);
    }
    
    public String getPlaceholder() { return placeholder; }
    
    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder != null ? placeholder : "输入文本...";
    }
    
    // === 序列化 ===
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("text", getText());
        state.put("multiline", isMultiline());
        state.put("maxLength", getMaxLength());
        state.put("placeholder", getPlaceholder());
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> stateMap = (java.util.Map<?, ?>) state;
            
            if (stateMap.containsKey("multiline")) {
                Object ml = stateMap.get("multiline");
                if (ml instanceof Boolean) setMultiline((Boolean) ml);
            }
            if (stateMap.containsKey("maxLength")) {
                Object ml = stateMap.get("maxLength");
                if (ml instanceof Number) setMaxLength(((Number) ml).intValue());
            }
            if (stateMap.containsKey("placeholder")) {
                Object ph = stateMap.get("placeholder");
                if (ph instanceof String) setPlaceholder((String) ph);
            }
            if (stateMap.containsKey("text")) {
                Object t = stateMap.get("text");
                if (t instanceof String) setText((String) t);
                else setText(String.valueOf(t));
            }
        } else if (state instanceof String) {
            setText((String) state);
        } else if (state != null) {
            setText(String.valueOf(state));
        }
    }

    protected final float getAvailableWidth(float totalWidth, float zoom) {
        return totalWidth - ZoomHelper.applyZoom(getContentMargin() * 2, zoom);
    }
}