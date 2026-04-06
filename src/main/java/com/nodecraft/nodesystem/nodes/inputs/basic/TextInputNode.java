package com.nodecraft.nodesystem.nodes.inputs.basic;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImString;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "inputs.basic.text_input",
    displayName = "文本输入",
    description = "允许用户输入单行或多行文本",
    category = "inputs.basic"
)
public class TextInputNode extends BaseCustomUINode {

    private static final String OUTPUT_TEXT_ID = "output_text";
    private static final String OUTPUT_LENGTH_ID = "output_length";
    private static final int SINGLE_LINE_BUF_SIZE = 1024;
    private static final int MULTI_LINE_BUF_SIZE = 32768;

    @NodeProperty(displayName = "文本内容", category = "内容", order = 1,
        description = "当前文本内容")
    private String text = "";

    @NodeProperty(displayName = "多行模式", category = "UI设置", order = 10,
        description = "是否启用多行输入")
    private boolean multiline = true;

    @NodeProperty(displayName = "最大长度", category = "限制", order = 11,
        description = "允许输入的最大字符数")
    private int maxLength = 32767;

    @NodeProperty(displayName = "占位文本", category = "UI设置", order = 12,
        description = "输入框为空时显示的提示文本")
    private String placeholder = "输入文本...";

    @NodeProperty(displayName = "显示字数统计", category = "UI设置", order = 13,
        description = "是否在底部显示当前字数和最大长度")
    private boolean showLengthCounter = true;

    private transient ImString inputBuffer;
    private transient boolean bufferNeedsSync = true;

    public TextInputNode() {
        super(UUID.randomUUID(), "inputs.basic.text_input");
        IPort textOutput = new BasePort(OUTPUT_TEXT_ID, "Text", "输入的文本内容", NodeDataType.STRING, this);
        addOutputPort(textOutput);
        IPort lengthOutput = new BasePort(OUTPUT_LENGTH_ID, "Length", "文本长度", NodeDataType.INTEGER, this);
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

    @Override
    protected float calculateUIHeight() {
        float height = getMediumPadding();
        height += ImGui.getFrameHeight() * getVisibleLineCount();
        if (showLengthCounter) {
            height += getSmallPadding();
            height += ImGui.getTextLineHeight();
        }
        height += getSmallPadding();
        return height;
    }

    @Override
    protected float calculateMinUIWidth() {
        return 220f;
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, l -> {
            boolean changed = false;
            float edgeMargin = l.toPixels(getSmallPadding());
            float availableWidth = Math.max(0.0f, l.toPixelsExact(width) - edgeMargin * 2.0f);
            float baseCursorX = ImGui.getCursorPosX();

            ensureBuffer();
            l.addVerticalSpacing(getMediumPadding());

            float inputHeight = ImGui.getFrameHeight() * getVisibleLineCount();
            ImGui.setCursorPosX(baseCursorX + edgeMargin);
            l.pushFramePadding(4.0f, 3.0f);
            if (ImGui.inputTextMultiline("##text_input", inputBuffer, availableWidth, inputHeight, ImGuiInputTextFlags.AllowTabInput)) {
                changed = applyBufferText(inputBuffer.get());
            }
            l.popStyleVar();

            if (showLengthCounter) {
                l.addVerticalSpacing(getSmallPadding());
                String countText = text.length() + " / " + maxLength + " 字符";
                float countWidth = ImGui.calcTextSize(countText).x;
                float offsetX = availableWidth - countWidth;
                ImGui.setCursorPosX(baseCursorX + edgeMargin + Math.max(0.0f, offsetX));

                float ratio = maxLength <= 0 ? 0.0f : (float) text.length() / maxLength;
                if (ratio > 0.9f) {
                    ImGui.pushStyleColor(ImGuiCol.Text, 0.9f, 0.3f, 0.3f, 1.0f);
                } else {
                    ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 1.0f);
                }
                ImGui.text(countText);
                ImGui.popStyleColor();
            }

            l.addVerticalSpacing(getSmallPadding());
            return changed;
        });
    }

    private int getVisibleLineCount() {
        String current = "";
        if (inputBuffer != null) {
            current = inputBuffer.get();
        }
        if (current == null || current.isEmpty()) {
            current = text != null ? text : "";
        }
        int lineCount = 1;
        for (int i = 0; i < current.length(); i++) {
            if (current.charAt(i) == '\n') {
                lineCount++;
            }
        }
        return Math.max(1, Math.min(6, lineCount));
    }

    private void ensureBuffer() {
        int bufferSize = MULTI_LINE_BUF_SIZE;
        if (inputBuffer == null || bufferNeedsSync) {
            inputBuffer = new ImString(bufferSize);
            inputBuffer.set(text);
            bufferNeedsSync = false;
        }
    }

    private boolean applyBufferText(String rawText) {
        String newText = rawText != null ? rawText : "";
        if (newText.length() > maxLength) {
            newText = newText.substring(0, maxLength);
        }
        if (!text.equals(newText)) {
            text = newText;
            updateOutput();
            markDirty();
            return true;
        }
        return false;
    }

    public void setText(String text) {
        String newText = text != null ? text : "";
        if (newText.length() > maxLength) {
            newText = newText.substring(0, maxLength);
        }
        if (!this.text.equals(newText)) {
            this.text = newText;
            bufferNeedsSync = true;
            updateOutput();
            markDirty();
        }
    }

    private void updateOutput() {
        outputValues.put(OUTPUT_TEXT_ID, text);
        outputValues.put(OUTPUT_LENGTH_ID, text.length());
        syncOutputPorts();
    }

    public String getText() {
        return text;
    }

    public boolean isMultiline() {
        return multiline;
    }

    public void setMultiline(boolean multiline) {
        if (this.multiline != multiline) {
            this.multiline = multiline;
            bufferNeedsSync = true;
            if (!multiline) {
                setText(this.text);
            }
            invalidateCache();
            markDirty();
        }
    }

    public int getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(int maxLength) {
        int normalized = maxLength <= 0 ? 32767 : maxLength;
        if (this.maxLength != normalized) {
            this.maxLength = normalized;
            if (text.length() > normalized) {
                setText(text);
            }
            invalidateCache();
            markDirty();
        }
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public void setPlaceholder(String placeholder) {
        String normalized = placeholder != null ? placeholder : "输入文本...";
        if (!this.placeholder.equals(normalized)) {
            this.placeholder = normalized;
            markDirty();
        }
    }

    public boolean isShowLengthCounter() {
        return showLengthCounter;
    }

    public void setShowLengthCounter(boolean showLengthCounter) {
        if (this.showLengthCounter != showLengthCounter) {
            this.showLengthCounter = showLengthCounter;
            invalidateCache();
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("text", getText());
        state.put("multiline", isMultiline());
        state.put("maxLength", getMaxLength());
        state.put("placeholder", getPlaceholder());
        state.put("showLengthCounter", isShowLengthCounter());
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof Map<?, ?> stateMap) {
            if (stateMap.get("multiline") instanceof Boolean ml) {
                setMultiline(ml);
            }
            if (stateMap.get("maxLength") instanceof Number max) {
                setMaxLength(max.intValue());
            }
            if (stateMap.get("placeholder") instanceof String ph) {
                setPlaceholder(ph);
            }
            if (stateMap.get("showLengthCounter") instanceof Boolean showCounter) {
                setShowLengthCounter(showCounter);
            }
            if (stateMap.containsKey("text")) {
                Object t = stateMap.get("text");
                if (t instanceof String str) {
                    setText(str);
                } else {
                    setText(String.valueOf(t));
                }
            }
        } else if (state instanceof String str) {
            setText(str);
        } else if (state != null) {
            setText(String.valueOf(state));
        }
    }
}
