package com.nodecraft.nodesystem.nodes.inputs.basic;

import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.Color;
import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.gui.editor.impl.ZoomHelper;
import org.jetbrains.annotations.Nullable;

// ImGUI相关导入
import imgui.ImGui;
import imgui.flag.ImGuiColorEditFlags;
import imgui.type.ImBoolean;

import java.util.UUID;

/**
 * 颜色选择器节点，允许用户选择颜色。
 * 支持ImGUI自定义UI渲染，继承BaseCustomUINode获得缩放感知和布局管理功能。
 */
@NodeInfo(
    id = "inputs.basic.color_picker",
    displayName = "颜色选择器",
    description = "允许用户选择颜色值，支持RGB和透明度",
    category = "inputs.basic"
)
public class ColorPickerNode extends BaseCustomUINode {
    
    // --- 节点属性 ---
    @NodeProperty(displayName = "颜色", category = "设置", order = 1,
                  description = "当前选择的颜色")
    private Color color = new Color(1.0f, 1.0f, 1.0f, 1.0f); // 默认为白色
    
    @NodeProperty(displayName = "包含透明度", category = "设置", order = 2,
                  description = "是否包含透明度选择")
    private boolean includeAlpha = true;
    
    @NodeProperty(displayName = "显示预览", category = "UI设置", order = 10,
                  description = "显示颜色预览按钮")
    private boolean showPreview = true;
    
    @NodeProperty(displayName = "显示十六进制", category = "UI设置", order = 11,
                  description = "显示十六进制颜色值")
    private boolean showHexValue = true;
    
    @NodeProperty(displayName = "显示RGB数值", category = "UI设置", order = 12,
                  description = "显示RGB数值信息")
    private boolean showRGBValues = true;
    
    private String description = "Allows selection of a color value."; // 节点描述
    
    // --- UI状态 ---
    private float[] colorArray = {1.0f, 1.0f, 1.0f, 1.0f}; // ImGUI颜色数组
    private boolean needsUIUpdate = false; // 标记是否需要更新UI
    
    // --- UI布局常量 ---
    private static final float COLOR_PREVIEW_HEIGHT = 30.0f;
    private static final float COLOR_EDIT_HEIGHT = 120.0f;
    private static final float TEXT_LINE_HEIGHT = 18.0f;
    private static final float CHECKBOX_HEIGHT = 20.0f;
    
    // --- 输出端口 ---
    private static final String OUTPUT_COLOR_ID = "output_color";
    private static final String OUTPUT_RED_ID = "output_red";
    private static final String OUTPUT_GREEN_ID = "output_green";
    private static final String OUTPUT_BLUE_ID = "output_blue";
    private static final String OUTPUT_ALPHA_ID = "output_alpha";
    
    /**
     * 构造一个新的颜色选择器节点
     */
    public ColorPickerNode() {
        // 使用新的分类命名 - inputs.basic.color_picker
        super(UUID.randomUUID(), "inputs.basic.color_picker");
        
        // 创建并添加输出端口
        IPort colorOutput = new BasePort(OUTPUT_COLOR_ID, "Color", "The selected color", NodeDataType.COLOR, this);
        addOutputPort(colorOutput);
        
        IPort redOutput = new BasePort(OUTPUT_RED_ID, "Red", "Red component (0-1)", NodeDataType.FLOAT, this);
        addOutputPort(redOutput);
        
        IPort greenOutput = new BasePort(OUTPUT_GREEN_ID, "Green", "Green component (0-1)", NodeDataType.FLOAT, this);
        addOutputPort(greenOutput);
        
        IPort blueOutput = new BasePort(OUTPUT_BLUE_ID, "Blue", "Blue component (0-1)", NodeDataType.FLOAT, this);
        addOutputPort(blueOutput);
        
        IPort alphaOutput = new BasePort(OUTPUT_ALPHA_ID, "Alpha", "Alpha component (0-1)", NodeDataType.FLOAT, this);
        addOutputPort(alphaOutput);
        
        // 初始化UI颜色数组
        updateColorArray();
        
        // 初始化输出值
        updateOutput();
    }
    
    // === BaseCustomUINode 抽象方法实现 ===
    
    @Override
    protected float calculateUIHeight() {
        float height = getMediumPadding(); // 顶部边距
        
        // 颜色预览区域
        if (showPreview) {
            height += COLOR_PREVIEW_HEIGHT + getSmallPadding();
        }
        
        // 颜色编辑器区域 - 使用动态高度计算
        height += COLOR_EDIT_HEIGHT + getMediumPadding();
        
        // 信息显示区域
        if (showRGBValues) {
            // RGB行 + Alpha行（如果启用）
            int textLines = includeAlpha ? 2 : 1;
            height += TEXT_LINE_HEIGHT * textLines + getSmallPadding();
        }
        
        if (showHexValue) {
            height += TEXT_LINE_HEIGHT + getSmallPadding();
        }
        
        // Alpha开关
        height += CHECKBOX_HEIGHT + getMediumPadding();
        
        return height;
    }
    
    @Override
    protected float calculateMinUIWidth() {
        // 颜色选择器需要足够的宽度来显示颜色编辑器
        float minWidth = 220.0f; // 增加基础宽度以适应颜色选择器
        
        if (showHexValue) {
            // 考虑十六进制文本的宽度
            String sampleHex = includeAlpha ? "Hex: #AARRGGBB" : "Hex: #RRGGBB";
            // 使用估算的文本宽度，因为ImGui.calcTextSize在这里可能不可用
            float estimatedHexWidth = sampleHex.length() * 8.0f + 20.0f; // 估算字符宽度
            minWidth = Math.max(minWidth, estimatedHexWidth);
        }
        
        if (showRGBValues) {
            // 考虑RGB文本的宽度
            String sampleRGB = "RGB: 1.000, 1.000, 1.000";
            float estimatedRGBWidth = sampleRGB.length() * 8.0f + 20.0f;
            minWidth = Math.max(minWidth, estimatedRGBWidth);
        }
        
        return minWidth + getContentMargin();
    }
    
    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        // 使用新的 LayoutHelper API
        return layout(zoom, l -> {
            boolean valueChanged = false;
            
            // 处理需要的UI更新
            if (needsUIUpdate) {
                updateColorArray();
                needsUIUpdate = false;
            }
            
            // 计算可用宽度
            float availableWidth = l.getAvailableContentWidth(width);
            
            // 颜色预览按钮
            if (showPreview) {
                valueChanged |= renderColorPreview(availableWidth, l);
                l.addVerticalSpacing(getSmallPadding());
            }
            
            // 颜色编辑器
            valueChanged |= renderColorEditor(availableWidth, l);
            l.addVerticalSpacing(getMediumPadding());
            
            // 显示数值信息
            if (showRGBValues) {
                renderRGBValues(l);
                l.addVerticalSpacing(getSmallPadding());
            }
            
            // 十六进制显示
            if (showHexValue) {
                renderHexValue(l);
                l.addVerticalSpacing(getSmallPadding());
            }
            
            // Alpha开关
            valueChanged |= renderAlphaToggle(l);
            
            return valueChanged;
        });
    }
    
    /**
     * 渲染颜色预览按钮
     */
    private boolean renderColorPreview(float availableWidth, LayoutHelper l) {
        float buttonHeight = l.toPixels(COLOR_PREVIEW_HEIGHT);
        
        // 使用缩放后的宽度
        ImGui.pushItemWidth(availableWidth);
        boolean clicked = ImGui.colorButton("##color_preview_" + getId(), 
                                          colorArray,
                                          ImGuiColorEditFlags.AlphaPreview, availableWidth, buttonHeight);
        ImGui.popItemWidth();
        
        return false; // 预览按钮不改变值
    }
    
    /**
     * 渲染颜色编辑器
     */
    private boolean renderColorEditor(float availableWidth, LayoutHelper l) {
        // 设置颜色编辑器标志
        int colorEditFlags = ImGuiColorEditFlags.DisplayRGB | 
                           ImGuiColorEditFlags.InputRGB |
                           ImGuiColorEditFlags.Float |
                           ImGuiColorEditFlags.PickerHueWheel;
        
        if (includeAlpha) {
            colorEditFlags |= ImGuiColorEditFlags.AlphaBar | ImGuiColorEditFlags.AlphaPreview;
        } else {
            colorEditFlags |= ImGuiColorEditFlags.NoAlpha;
        }
        
        // 应用缩放的样式
        l.pushFramePadding(4.0f, 4.0f);
        l.pushFrameRounding(3.0f);
        
        // 颜色拾取器小部件 - 使用缩放后的宽度
        ImGui.pushItemWidth(availableWidth);
        boolean colorChanged = false;
        
        if (includeAlpha) {
            colorChanged = ImGui.colorEdit4("##color_edit_" + getId(), colorArray, colorEditFlags);
        } else {
            colorChanged = ImGui.colorEdit3("##color_edit_" + getId(), colorArray, colorEditFlags);
        }
        
        ImGui.popItemWidth();
        l.popStyleVar(2); // 弹出样式变量
        
        if (colorChanged) {
            onColorChangedFromUI();
        }
        
        return colorChanged;
    }
    
    /**
     * 渲染RGB数值信息
     */
    private void renderRGBValues(LayoutHelper l) {
        // 使用缩放感知的文本渲染
        ImGui.text(String.format("RGB: %.3f, %.3f, %.3f", colorArray[0], colorArray[1], colorArray[2]));
        if (includeAlpha) {
            ImGui.text(String.format("Alpha: %.3f", colorArray[3]));
        }
    }
    
    /**
     * 渲染十六进制值
     */
    private void renderHexValue(LayoutHelper l) {
        String hexColor = colorToHex();
        ImGui.text("Hex: " + hexColor);
    }
    
    /**
     * 渲染Alpha开关
     */
    private boolean renderAlphaToggle(LayoutHelper l) {
        // 应用缩放的样式
        l.pushFramePadding(2.0f, 2.0f);
        
        ImBoolean alphaToggle = new ImBoolean(includeAlpha);
        boolean changed = ImGui.checkbox("Include Alpha##" + getId(), alphaToggle);
        
        l.popStyleVar(); // 弹出样式变量
        
        if (changed) {
            setIncludeAlpha(alphaToggle.get());
        }
        
        return changed;
    }
    
    /**
     * 当颜色从UI改变时调用
     */
    private void onColorChangedFromUI() {
        // 更新内部颜色对象
        float alpha = includeAlpha ? colorArray[3] : color.getAlpha();
        Color newColor = new Color(colorArray[0], colorArray[1], colorArray[2], alpha);
        
        if (!this.color.equals(newColor)) {
            this.color = newColor;
            updateOutput();
            markDirty();
        }
    }
    
    /**
     * 将颜色对象同步到UI数组
     */
    private void updateColorArray() {
        colorArray[0] = color.getRed();
        colorArray[1] = color.getGreen();
        colorArray[2] = color.getBlue();
        colorArray[3] = color.getAlpha();
    }
    
    /**
     * 将当前颜色转换为十六进制字符串
     */
    private String colorToHex() {
        int r = (int)(colorArray[0] * 255);
        int g = (int)(colorArray[1] * 255);
        int b = (int)(colorArray[2] * 255);
        
        if (includeAlpha) {
            int a = (int)(colorArray[3] * 255);
            return String.format("#%02X%02X%02X%02X", a, r, g, b);
        } else {
            return String.format("#%02X%02X%02X", r, g, b);
        }
    }
    
    /**
     * 实现INode接口的getDescription方法
     * @return 节点描述
     */
    @Override
    public String getDescription() {
        return this.description;
    }
    
    /**
     * 节点的计算逻辑
     * @param context 执行上下文
     */
    @Override
    public void processNode(@Nullable ExecutionContext context) {
        updateOutput();
    }
    
    /**
     * 设置颜色
     * @param color 新的颜色
     */
    public void setColor(Color color) {
        if (color == null) {
            color = new Color(1.0f, 1.0f, 1.0f, 1.0f); // 防止空引用
        }
        
        // 如果不包含透明度，则保留原透明度
        if (!includeAlpha) {
            color = new Color(color.getRed(), color.getGreen(), color.getBlue(), this.color.getAlpha());
        }
        
        // 如果值变化了，更新节点状态
        if (!this.color.equals(color)) {
            this.color = color;
            needsUIUpdate = true; // 标记需要更新UI
            updateOutput();
            
            // 通知系统此节点的输出已更新
            markDirty();
        }
    }
    
    /**
     * 设置颜色的RGBA分量
     * @param red 红色分量 (0-1)
     * @param green 绿色分量 (0-1)
     * @param blue 蓝色分量 (0-1)
     * @param alpha 透明度 (0-1)
     */
    public void setColor(float red, float green, float blue, float alpha) {
        // 如果不包含透明度，则保留原透明度
        if (!includeAlpha) {
            alpha = this.color.getAlpha();
        }
        
        setColor(new Color(red, green, blue, alpha));
    }
    
    /**
     * 从十六进制字符串设置颜色
     * @param hexColor 十六进制颜色字符串 (#RRGGBB 或 #AARRGGBB)
     */
    public void setColorFromHex(String hexColor) {
        Color newColor = Color.fromHex(hexColor);
        
        // 如果不包含透明度，则保留原透明度
        if (!includeAlpha) {
            newColor = new Color(newColor.getRed(), newColor.getGreen(), newColor.getBlue(), this.color.getAlpha());
        }
        
        setColor(newColor);
    }
    
    /**
     * 更新输出端口的值
     */
    private void updateOutput() {
        outputValues.put(OUTPUT_COLOR_ID, this.color);
        outputValues.put(OUTPUT_RED_ID, this.color.getRed());
        outputValues.put(OUTPUT_GREEN_ID, this.color.getGreen());
        outputValues.put(OUTPUT_BLUE_ID, this.color.getBlue());
        outputValues.put(OUTPUT_ALPHA_ID, this.color.getAlpha());
    }
    
    // --- Getters/Setters for Properties ---
    
    public Color getColor() {
        return color;
    }
    
    public boolean isIncludeAlpha() {
        return includeAlpha;
    }
    
    public void setIncludeAlpha(boolean includeAlpha) {
        if (this.includeAlpha != includeAlpha) {
            this.includeAlpha = includeAlpha;
            needsUIUpdate = true; // 标记需要更新UI
            invalidateCache(); // 使缓存无效，重新计算UI尺寸
        }
    }
    
    public boolean isShowPreview() {
        return showPreview;
    }
    
    public void setShowPreview(boolean showPreview) {
        if (this.showPreview != showPreview) {
            this.showPreview = showPreview;
            invalidateCache(); // 使缓存无效，重新计算UI尺寸
        }
    }
    
    public boolean isShowHexValue() {
        return showHexValue;
    }
    
    public void setShowHexValue(boolean showHexValue) {
        if (this.showHexValue != showHexValue) {
            this.showHexValue = showHexValue;
            invalidateCache(); // 使缓存无效，重新计算UI尺寸
        }
    }
    
    public boolean isShowRGBValues() {
        return showRGBValues;
    }
    
    public void setShowRGBValues(boolean showRGBValues) {
        if (this.showRGBValues != showRGBValues) {
            this.showRGBValues = showRGBValues;
            invalidateCache(); // 使缓存无效，重新计算UI尺寸
        }
    }
    
    // === 添加缺失的助手方法 ===
    
    /**
     * 计算可用的内容宽度（减去边距）
     * @param totalWidth 总宽度
     * @param zoom 缩放因子
     * @return 可用宽度
     */
    protected final float getAvailableWidth(float totalWidth, float zoom) {
        return getAvailableContentWidth(totalWidth, zoom);
    }
    
    /**
     * 计算居中偏移量
     * @param availableWidth 可用宽度
     * @param elementWidth 元素宽度
     * @return 居中偏移量
     */
    protected final float getCenterOffset(float availableWidth, float elementWidth) {
        return ZoomHelper.getCenterOffset(availableWidth, elementWidth);
    }
    
    // --- 节点状态序列化 ---
    
    @Override
    public Object getNodeState() {
        // 返回节点所有可序列化的状态
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        
        // 保存颜色为ARGB整数
        state.put("color", this.color.toArgb());
        state.put("includeAlpha", this.includeAlpha);
        state.put("showPreview", this.showPreview);
        state.put("showHexValue", this.showHexValue);
        state.put("showRGBValues", this.showRGBValues);
        
        return state;
    }
    
    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> stateMap = (java.util.Map<?, ?>) state;
            
            // 设置是否包含透明度
            if (stateMap.containsKey("includeAlpha")) {
                Object includeAlpha = stateMap.get("includeAlpha");
                if (includeAlpha instanceof Boolean) {
                    setIncludeAlpha((Boolean) includeAlpha);
                }
            }
            
            // 设置UI显示选项
            if (stateMap.containsKey("showPreview")) {
                Object showPreview = stateMap.get("showPreview");
                if (showPreview instanceof Boolean) {
                    setShowPreview((Boolean) showPreview);
                }
            }
            
            if (stateMap.containsKey("showHexValue")) {
                Object showHexValue = stateMap.get("showHexValue");
                if (showHexValue instanceof Boolean) {
                    setShowHexValue((Boolean) showHexValue);
                }
            }
            
            if (stateMap.containsKey("showRGBValues")) {
                Object showRGBValues = stateMap.get("showRGBValues");
                if (showRGBValues instanceof Boolean) {
                    setShowRGBValues((Boolean) showRGBValues);
                }
            }
            
            // 设置颜色
            if (stateMap.containsKey("color")) {
                Object colorValue = stateMap.get("color");
                if (colorValue instanceof Integer) {
                    setColor(new Color((Integer) colorValue));
                } else if (colorValue instanceof String) {
                    // 尝试作为十六进制字符串解析
                    setColorFromHex((String) colorValue);
                }
            }
        }
    }
} 