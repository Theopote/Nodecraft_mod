package com.nodecraft.nodesystem.nodes.inputs.basic;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.gui.editor.impl.ZoomHelper;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import imgui.ImGui;
import imgui.ImDrawList;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiMouseButton;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 布尔值开关节点，提供一个可视化的开关/切换按钮。
 * 用户可以点击开关来切换真/假状态。
 */
@NodeInfo(
    id = "inputs.basic.boolean_toggle",
    displayName = "布尔开关",
    description = "提供一个可以切换的布尔值开关控制",
    category = "inputs.basic"
)
public class BooleanToggleNode extends BaseCustomUINode {
    
    // --- 节点属性 ---
    @NodeProperty(displayName = "当前值", category = "数值", order = 1,
                  description = "当前的布尔值")
    private boolean value = false;

    @NodeProperty(displayName = "True标签", category = "标签", order = 2,
                  description = "值为True时显示的标签文本")
    private String trueLabel = "ON";

    @NodeProperty(displayName = "False标签", category = "标签", order = 3,
                  description = "值为False时显示的标签文本")
    private String falseLabel = "OFF";
    
    // --- 输出端口 ---
    private static final String OUTPUT_VALUE_ID = "output_value";
    
    /**
     * 构造一个新的布尔值开关节点
     */
    public BooleanToggleNode() {
        super(UUID.randomUUID(), "inputs.basic.boolean_toggle");
        IPort valueOutput = new BasePort(OUTPUT_VALUE_ID, "Value", "The boolean value", NodeDataType.BOOLEAN, this);
        addOutputPort(valueOutput);
        updateOutput();
    }
    
    @Override
    public String getDescription() {
        return "一个可视化的布尔开关，点击切换真/假状态。";
    }
    
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        updateOutput();
    }
    
    // === BaseCustomUINode 抽象方法实现 ===

    @Override
    protected float calculateUIHeight() {
        float height = getMediumPadding(); // 顶部间距
        height += 24f;                     // 开关控件高度
        height += getSmallPadding();       // 间距
        height += ImGui.getTextLineHeight(); // 状态文字行
        height += getMediumPadding();      // 底部间距
        return height;
    }

    @Override
    protected float calculateMinUIWidth() {
        float switchWidth = 50f;
        String longerLabel = trueLabel.length() > falseLabel.length() ? trueLabel : falseLabel;
        float labelWidth = ImGui.calcTextSize(longerLabel).x;
        return Math.max(120f, switchWidth + labelWidth + 20f) + getContentMargin();
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return layout(zoom, l -> {
            boolean changed = false;
            
            try {
                float availableWidth = getAvailableWidth(width, zoom);
                
                l.addVerticalSpacing(getMediumPadding());
                
                // === 绘制开关控件 ===
                float switchWidth = 44f * zoom;
                float switchHeight = 22f * zoom;
                float knobRadius = 9f * zoom;
                float knobPadding = 2f * zoom;
                
                // 居中开关 + 标签
                String currentLabel = value ? trueLabel : falseLabel;
                float labelW = ImGui.calcTextSize(currentLabel).x;
                float totalWidth = switchWidth + 8f * zoom + labelW;
                setCenterX(availableWidth, totalWidth);
                
                // 获取开关绘制起始位置
                ImVec2 cursorScreenPos = ImGui.getCursorScreenPos();
                float switchX = cursorScreenPos.x;
                float switchY = cursorScreenPos.y;
                
                ImDrawList drawList = ImGui.getWindowDrawList();
                float rounding = switchHeight * 0.5f;
                
                // 绘制背景胶囊
                int bgColor = value ? 
                    ImGui.colorConvertFloat4ToU32(0.15f, 0.68f, 0.38f, 1.0f) :  // 绿色
                    ImGui.colorConvertFloat4ToU32(0.45f, 0.45f, 0.50f, 1.0f);   // 灰色
                drawList.addRectFilled(switchX, switchY, 
                    switchX + switchWidth, switchY + switchHeight, 
                    bgColor, rounding);
                
                // 绘制圆形滑块
                float knobX = value
                    ? switchX + switchWidth - knobRadius - knobPadding
                    : switchX + knobRadius + knobPadding;
                float knobY = switchY + switchHeight * 0.5f;
                drawList.addCircleFilled(knobX, knobY, knobRadius,
                    ImGui.colorConvertFloat4ToU32(1.0f, 1.0f, 1.0f, 1.0f));
                
                // 不可见按钮检测点击
                ImGui.invisibleButton("##toggle_switch", switchWidth, switchHeight);
                boolean hovered = ImGui.isItemHovered();
                
                if (hovered) {
                    drawList.addRectFilled(switchX, switchY, 
                        switchX + switchWidth, switchY + switchHeight, 
                        ImGui.colorConvertFloat4ToU32(1.0f, 1.0f, 1.0f, 0.15f), rounding);
                }
                
                if (ImGui.isItemClicked(ImGuiMouseButton.Left)) {
                    toggle();
                    changed = true;
                }
                
                // 开关右侧显示标签
                ImGui.sameLine();
                ImGui.dummy(4f * zoom, 0);
                ImGui.sameLine();
                
                float textY = (switchHeight - ImGui.getTextLineHeight()) * 0.5f;
                ImGui.setCursorPosY(ImGui.getCursorPosY() + textY);
                
                if (value) {
                    ImGui.pushStyleColor(ImGuiCol.Text, 0.15f, 0.85f, 0.45f, 1.0f);
                } else {
                    ImGui.pushStyleColor(ImGuiCol.Text, 0.65f, 0.65f, 0.65f, 1.0f);
                }
                ImGui.text(currentLabel);
                ImGui.popStyleColor();
                
                l.addVerticalSpacing(getSmallPadding());
                
                // === 状态指示文字 ===
                String stateDesc = value ? "● 已启用" : "○ 已禁用";
                float stateW = ImGui.calcTextSize(stateDesc).x;
                setCenterX(availableWidth, stateW);
                ImGui.pushStyleColor(ImGuiCol.Text, 0.55f, 0.55f, 0.55f, 1.0f);
                ImGui.text(stateDesc);
                ImGui.popStyleColor();
                
                l.addVerticalSpacing(getMediumPadding());
                
            } catch (Exception e) {
                System.err.println("BooleanToggleNode UI渲染失败: " + e.getMessage());
            }
            
            return changed;
        });
    }
    
    // === 业务逻辑 ===
    
    public void toggle() {
        setValue(!value);
    }
    
    public void setValue(boolean value) {
        if (this.value != value) {
            this.value = value;
            updateOutput();
            markDirty();
        }
    }
    
    private void updateOutput() {
        outputValues.put(OUTPUT_VALUE_ID, this.value);
    }
    
    public boolean getValue() { return value; }
    
    public String getTrueLabel() { return trueLabel; }
    
    public void setTrueLabel(String trueLabel) {
        this.trueLabel = trueLabel != null ? trueLabel : "ON";
        invalidateCache();
    }
    
    public String getFalseLabel() { return falseLabel; }
    
    public void setFalseLabel(String falseLabel) {
        this.falseLabel = falseLabel != null ? falseLabel : "OFF";
        invalidateCache();
    }
    
    // === 序列化 ===
    
    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("value", getValue());
        state.put("trueLabel", getTrueLabel());
        state.put("falseLabel", getFalseLabel());
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> stateMap = (java.util.Map<?, ?>) state;
            
            if (stateMap.containsKey("trueLabel")) {
                Object label = stateMap.get("trueLabel");
                if (label instanceof String) setTrueLabel((String) label);
            }
            if (stateMap.containsKey("falseLabel")) {
                Object label = stateMap.get("falseLabel");
                if (label instanceof String) setFalseLabel((String) label);
            }
            if (stateMap.containsKey("value")) {
                Object value = stateMap.get("value");
                if (value instanceof Boolean) setValue((Boolean) value);
                else if (value instanceof String) setValue(Boolean.parseBoolean((String) value));
                else if (value instanceof Number) setValue(((Number) value).intValue() != 0);
            }
        } else if (state instanceof Boolean) {
            setValue((Boolean) state);
        } else if (state instanceof String) {
            setValue(Boolean.parseBoolean((String) state));
        } else if (state instanceof Number) {
            setValue(((Number) state).intValue() != 0);
        }
    }

    protected final float getAvailableWidth(float totalWidth, float zoom) {
        return totalWidth - ZoomHelper.applyZoom(getContentMargin() * 2, zoom);
    }
}