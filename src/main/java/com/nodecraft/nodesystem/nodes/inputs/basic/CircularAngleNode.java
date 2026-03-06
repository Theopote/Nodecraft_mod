package com.nodecraft.nodesystem.nodes.inputs.basic;

// 导入必要的 NodeCraft 基类或接口
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

// 导入ImGui相关类用于自定义UI渲染
import imgui.ImGui;
import imgui.ImDrawList;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.type.ImFloat;
import imgui.type.ImInt;
import imgui.flag.ImGuiStyleVar;

// 导入属性注解
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.api.NodeInfo;

// 导入自定义UI基类
import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.gui.editor.impl.ZoomHelper;

import java.util.UUID;

/**
 * 圆形角度选择器节点 - 高级自定义UI示例
 * 
 * 继承BaseCustomUINode，自动获得缩放感知和布局管理功能。
 * 
 * 展示如何实现具有复杂交互的自定义UI组件：
 * 1. 圆形旋转盘界面
 * 2. 鼠标交互处理
 * 3. 自定义绘制和动画
 * 4. 直接绘制模式支持
 * 
 * 特性：
 * - 可视化的圆形角度选择器
 * - 实时角度指示器
 * - 鼠标拖拽交互
 * - 键盘输入支持
 * - 自动缩放友好的渲染
 */
@NodeInfo(
    id = "inputs.basic.circular_angle",
    displayName = "圆形角度选择器",
    description = "通过圆形界面选择角度值，支持拖拽旋转操作。支持度数和弧度输出。",
    category = "inputs.basic"
)
public class CircularAngleNode extends BaseCustomUINode {

    // === UI 常量定义 ===
    
    // --- 圆形UI参数 ---
    /** 旋转盘半径（未缩放） */
    private static final float DIAL_RADIUS = 40.0f;
    /** 旋转盘边框宽度 */
    private static final float DIAL_BORDER_WIDTH = 2.0f;
    /** 角度指示器长度 */
    private static final float INDICATOR_LENGTH = 15.0f;
    /** 角度指示器宽度 */
    private static final float INDICATOR_WIDTH = 3.0f;
    /** 刻度标记长度 */
    private static final float TICK_LENGTH = 6.0f;
    /** 主刻度间隔（度） */
    private static final float MAJOR_TICK_INTERVAL = 45.0f;
    /** 次刻度间隔（度） */
    private static final float MINOR_TICK_INTERVAL = 15.0f;
    
    // --- 颜色常量 ---
    /** 旋转盘背景色 */
    private static final int DIAL_BG_COLOR = 0xFF2A2A2A;
    /** 旋转盘边框色 */
    private static final int DIAL_BORDER_COLOR = 0xFF4A4A4A;
    /** 角度指示器颜色 */
    private static final int INDICATOR_COLOR = 0xFF66B3FF;
    /** 主刻度颜色 */
    private static final int MAJOR_TICK_COLOR = 0xFF888888;
    /** 次刻度颜色 */
    private static final int MINOR_TICK_COLOR = 0xFF555555;
    /** 中心点颜色 */
    private static final int CENTER_DOT_COLOR = 0xFFFFFFFF;

    // --- 节点属性 ---
    @NodeProperty(displayName = "角度", category = "设置", order = 1,
                  description = "当前角度值（度）")
    private double angle = 0.0;

    @NodeProperty(displayName = "精度", category = "设置", order = 2,
                  description = "角度值的小数位数")
    private int precision = 1;

    @NodeProperty(displayName = "显示刻度", category = "UI", order = 10,
                  description = "是否显示角度刻度标记")
    private boolean showTicks = true;

    @NodeProperty(displayName = "显示数值", category = "UI", order = 11,
                  description = "是否显示角度数值输入框")
    private boolean showValueInput = true;

    @NodeProperty(displayName = "启用直接绘制", category = "性能", order = 20,
                  description = "启用直接绘制模式以提升渲染性能")
    private boolean allowDirectDrawing = false;

    // --- 交互状态 ---
    private transient boolean isDragging = false;
    private transient float lastMouseAngle = 0.0f;

    // --- 输出端口 ---
    private static final String OUTPUT_ANGLE_ID = "output_angle";
    private static final String OUTPUT_RADIANS_ID = "output_radians";

    /**
     * 构造函数
     */
    public CircularAngleNode() {
        super(UUID.randomUUID(), "inputs.basic.circular_angle");

        // 创建输出端口
        IPort angleOutput = new BasePort(OUTPUT_ANGLE_ID, "Degrees", "Angle in degrees", NodeDataType.DOUBLE, this);
        IPort radiansOutput = new BasePort(OUTPUT_RADIANS_ID, "Radians", "Angle in radians", NodeDataType.DOUBLE, this);
        
        addOutputPort(angleOutput);
        addOutputPort(radiansOutput);

        // 初始化输出值
        updateOutput();
    }

    @Override
    public String getDescription() {
        return "使用圆形旋转盘界面选择角度值。支持鼠标拖拽和键盘输入。";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        updateOutput();
    }

    // === BaseCustomUINode 抽象方法实现 ===
    
    @Override
    protected float calculateUIHeight() {
        // 与 renderCustomUIScaled 的真实堆叠顺序保持一致，避免固定高度导致底部留白。
        float height = getMediumPadding(); // 顶部间距
        height += DIAL_RADIUS * 2.0f;      // 圆盘按钮实际高度
        height += getMediumPadding();      // 圆盘与下方控件之间间距
        
        if (showValueInput) {
            height += ImGui.getFrameHeight(); // 使用ImGui的框架高度
            height += getMediumPadding();     // 底部间距
        }
        
        return height;
    }
    
    @Override
    protected float calculateMinUIWidth() {
        // 计算所需的最小宽度，考虑圆形UI和文本输入
        float minWidth = (DIAL_RADIUS * 2) + getContentMargin();
        
        if (showValueInput) {
            // 考虑输入框的宽度需求
            String sampleText = "360.0°"; // 最长的可能文本
            float inputWidth = Math.max(120.0f, ImGui.calcTextSize(sampleText).x + 40.0f);
            minWidth = Math.max(minWidth, inputWidth + getContentMargin());
        }
        
        return minWidth;
    }
    
    @Override
    public boolean supportsDirectDrawing() {
        return allowDirectDrawing;
    }
    
    @Override
    public boolean renderCustomUIDirect(ImDrawList drawList, float screenX, float screenY, 
                                       float width, float height, float zoom) {
        if (!supportsDirectDrawing()) {
            return false;
        }
        
        try {
            float scaledRadius = ZoomHelper.applyZoom(DIAL_RADIUS, zoom);
            // width/height are already scaled screen-space pixels in direct drawing mode.
            float centerX = screenX + width / 2;
            float centerY = screenY + scaledRadius + ZoomHelper.applyZoom(getMediumPadding(), zoom);
            
            // 绘制旋转盘背景
            drawList.addCircleFilled(centerX, centerY, scaledRadius, DIAL_BG_COLOR);
            drawList.addCircle(centerX, centerY, scaledRadius, DIAL_BORDER_COLOR, 0, ZoomHelper.applyZoom(DIAL_BORDER_WIDTH, zoom));
            
            // 绘制刻度（如果启用）
            if (showTicks) {
                drawTicks(drawList, centerX, centerY, scaledRadius, zoom);
            }
            
            // 绘制角度指示器
            drawAngleIndicator(drawList, centerX, centerY, scaledRadius, zoom);
            
            // 绘制中心点
            drawList.addCircleFilled(centerX, centerY, ZoomHelper.applyZoom(3.0f, zoom), CENTER_DOT_COLOR);
            
            // 绘制角度文本（如果显示数值输入）
            if (showValueInput) {
                String angleText = String.format("%." + precision + "f°", angle);
                float textWidth = ImGui.calcTextSize(angleText).x * zoom;
                float textX = centerX - textWidth / 2;
                float textY = centerY + scaledRadius + ZoomHelper.applyZoom(getMediumPadding(), zoom) * 2;
                
                drawList.addText(ImGui.getFont(), ImGui.getFontSize() * zoom, textX, textY, 
                               ImGui.getColorU32(ImGuiCol.Text), angleText);
            }
            
        } catch (Exception e) {
            System.err.println("CircularAngleNode直接绘制失败: " + e.getMessage());
        }
        
        return false; // 直接绘制模式不处理交互
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        boolean valueChanged = false;
        boolean hasUIInteraction = false; // 跟踪是否有UI交互

        try {
            // 使用助手方法计算可用宽度
            float availableWidth = getAvailableWidth(width, zoom);
            float scaledDialRadius = ZoomHelper.applyZoom(DIAL_RADIUS, zoom);
            
            // 添加顶部间距
            addVerticalSpacing(getMediumPadding(), zoom);
            
            // 创建不可见按钮来处理交互（应用缩放）
            float buttonSize = scaledDialRadius * 2;
            setCenterX(availableWidth, buttonSize);
            
            ImGui.invisibleButton("dial_interaction", buttonSize, buttonSize);

            // Anchor all geometry/interaction to the real item rect to avoid drift.
            ImVec2 dialRectMin = ImGui.getItemRectMin();
            ImVec2 dialRectMax = ImGui.getItemRectMax();
            float absoluteCenterX = (dialRectMin.x + dialRectMax.x) * 0.5f;
            float absoluteCenterY = (dialRectMin.y + dialRectMax.y) * 0.5f;
            
            boolean isHovered = ImGui.isItemHovered();
            boolean isClicked = ImGui.isItemClicked();
            boolean isActive = ImGui.isItemActive();
            
            // 处理鼠标交互
            if (isClicked) {
                isDragging = true;
            }
            
            if (isDragging && ImGui.isMouseDown(0)) {
                ImVec2 mousePos = ImGui.getMousePos();
                
                float deltaX = mousePos.x - absoluteCenterX;
                float deltaY = mousePos.y - absoluteCenterY;
                
                // 计算鼠标相对于中心的角度（屏幕坐标系：Y 向下为正）。
                // 结果语义与 drawAngleIndicator 保持一致：0° 在上方，顺时针递增。
                float mouseAngle = (float) Math.toDegrees(Math.atan2(deltaY, deltaX)) + 90.0f;
                if (mouseAngle < 0) mouseAngle += 360;
                if (mouseAngle >= 360.0f) mouseAngle -= 360.0f;
                
                // 根据精度调整角度
                float stepSize = (float) Math.pow(10, -precision);
                mouseAngle = Math.round(mouseAngle / stepSize) * stepSize;
                
                if (Math.abs(mouseAngle - angle) > 1e-6) {
                    setAngle(mouseAngle);
                    valueChanged = true;
                }
            } else {
                isDragging = false;
            }
            
            // 修复：确保在有鼠标交互时返回true以阻止节点拖动
            // 当鼠标悬停、活动或正在拖动时都应该返回交互状态
            hasUIInteraction |= isHovered || isActive || isDragging;
            
            // 获取绘制列表进行自定义绘制
            ImDrawList drawList = ImGui.getWindowDrawList();
            
            // 绘制旋转盘（所有尺寸都应用缩放）
            int bgColor = isHovered ? brightenColor(DIAL_BG_COLOR, 0.1f) : DIAL_BG_COLOR;
            drawList.addCircleFilled(absoluteCenterX, absoluteCenterY, scaledDialRadius, bgColor);
            drawList.addCircle(absoluteCenterX, absoluteCenterY, scaledDialRadius, DIAL_BORDER_COLOR, 
                             0, ZoomHelper.applyZoom(DIAL_BORDER_WIDTH, zoom));
            
            // 绘制刻度
            if (showTicks) {
                drawTicks(drawList, absoluteCenterX, absoluteCenterY, scaledDialRadius, zoom);
            }
            
            // 绘制角度指示器
            drawAngleIndicator(drawList, absoluteCenterX, absoluteCenterY, scaledDialRadius, zoom);
            
            // 绘制中心点
            drawList.addCircleFilled(absoluteCenterX, absoluteCenterY, ZoomHelper.applyZoom(3.0f, zoom), CENTER_DOT_COLOR);
            
            // invisibleButton already consumed buttonSize vertical space; only add extra gap.
            addVerticalSpacing(getMediumPadding(), zoom);
            
            // 数值输入框（如果启用）
            if (showValueInput) {
                float inputWidth = 120.0f;
                setCenterX(availableWidth, ZoomHelper.applyZoom(inputWidth, zoom));
                
                setItemWidth(inputWidth, zoom);
                
                ImFloat angleValue = new ImFloat((float) angle);
                String format = "%." + precision + "f";
                
                if (ImGui.inputFloat("##angle_input", angleValue, 1.0f, 10.0f, format)) {
                    setAngle(angleValue.get());
                    valueChanged = true;
                }
                hasUIInteraction |= ImGui.isItemHovered() || ImGui.isItemActive();
                
                ImGui.popItemWidth();
                
                // 显示单位标签
                ImGui.sameLine();
                ImGui.text("°");
                
                // 底部间距
                addVerticalSpacing(getMediumPadding(), zoom);
            }

        } catch (Exception e) {
            System.err.println("CircularAngleNode UI渲染失败: " + e.getMessage());
        }

        // 如果有UI交互，阻止事件传播到底层画布和游戏世界
        if (hasUIInteraction) {
            return true;
        }

        return valueChanged;
    }

    /**
     * 绘制刻度标记
     */
    private void drawTicks(ImDrawList drawList, float centerX, float centerY, float radius, float zoom) {
        // 绘制主刻度
        for (float tickAngle = 0; tickAngle < 360; tickAngle += MAJOR_TICK_INTERVAL) {
            drawTick(drawList, centerX, centerY, radius, tickAngle, TICK_LENGTH * zoom, 
                    MAJOR_TICK_COLOR, 2.0f * zoom);
        }
        
        // 绘制次刻度
        for (float tickAngle = 0; tickAngle < 360; tickAngle += MINOR_TICK_INTERVAL) {
            if (tickAngle % MAJOR_TICK_INTERVAL != 0) {
                drawTick(drawList, centerX, centerY, radius, tickAngle, TICK_LENGTH * 0.6f * zoom, 
                        MINOR_TICK_COLOR, 1.0f * zoom);
            }
        }
    }

    /**
     * 绘制单个刻度
     */
    private void drawTick(ImDrawList drawList, float centerX, float centerY, float radius, 
                         float angleDeg, float length, int color, float thickness) {
        double angleRad = Math.toRadians(angleDeg - 90); // -90 使0度指向上方
        
        float outerX = centerX + (float) (Math.cos(angleRad) * radius);
        float outerY = centerY + (float) (Math.sin(angleRad) * radius);
        float innerX = centerX + (float) (Math.cos(angleRad) * (radius - length));
        float innerY = centerY + (float) (Math.sin(angleRad) * (radius - length));
        
        drawList.addLine(innerX, innerY, outerX, outerY, color, thickness);
    }

    /**
     * 绘制角度指示器
     */
    private void drawAngleIndicator(ImDrawList drawList, float centerX, float centerY, 
                                   float radius, float zoom) {
        double angleRad = Math.toRadians(angle - 90); // -90 使0度指向上方
        
        float indicatorLength = INDICATOR_LENGTH * zoom;
        float indicatorEndX = centerX + (float) (Math.cos(angleRad) * (radius - indicatorLength));
        float indicatorEndY = centerY + (float) (Math.sin(angleRad) * (radius - indicatorLength));
        
        drawList.addLine(centerX, centerY, indicatorEndX, indicatorEndY, 
                        INDICATOR_COLOR, INDICATOR_WIDTH * zoom);
    }

    /**
     * 增加颜色亮度
     */
    private int brightenColor(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        
        r = Math.min(255, (int)(r * (1 + factor)));
        g = Math.min(255, (int)(g * (1 + factor)));
        b = Math.min(255, (int)(b * (1 + factor)));
        
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * 设置角度值
     */
    public void setAngle(double newAngle) {
        // 规范化角度到 0-360 范围
        newAngle = newAngle % 360.0;
        if (newAngle < 0) newAngle += 360.0;
        
        // 根据精度四舍五入
        double stepSize = Math.pow(10, -precision);
        newAngle = Math.round(newAngle / stepSize) * stepSize;
        
        if (Math.abs(this.angle - newAngle) > 1e-9) {
            this.angle = newAngle;
            updateOutput();
            markDirty();
        }
    }

    /**
     * 更新输出端口的值
     */
    private void updateOutput() {
        outputValues.put(OUTPUT_ANGLE_ID, angle);
        outputValues.put(OUTPUT_RADIANS_ID, Math.toRadians(angle));
    }

    // --- Getters/Setters ---

    public double getAngle() { return angle; }
    public int getPrecision() { return precision; }
    public void setPrecision(int precision) {
        int clamped = Math.max(0, Math.min(5, precision));
        if (this.precision != clamped) {
            this.precision = clamped;
            invalidateCache();
            markDirty();
        }
    }
    public boolean isShowTicks() { return showTicks; }
    public void setShowTicks(boolean showTicks) {
        if (this.showTicks != showTicks) {
            this.showTicks = showTicks;
            invalidateCache();
            markDirty();
        }
    }
    public boolean isShowValueInput() { return showValueInput; }
    public void setShowValueInput(boolean showValueInput) {
        if (this.showValueInput != showValueInput) {
            this.showValueInput = showValueInput;
            invalidateCache();
            markDirty();
        }
    }
    public boolean isAllowDirectDrawing() { return allowDirectDrawing; }
    public void setAllowDirectDrawing(boolean allowDirectDrawing) {
        if (this.allowDirectDrawing != allowDirectDrawing) {
            this.allowDirectDrawing = allowDirectDrawing;
            markDirty();
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

    // --- 状态序列化 ---

    @Override
    public Object getNodeState() {
        java.util.Map<String, Object> state = new java.util.HashMap<>();
        state.put("angle", angle);
        state.put("precision", precision);
        state.put("showTicks", showTicks);
        state.put("showValueInput", showValueInput);
        state.put("allowDirectDrawing", allowDirectDrawing);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (state instanceof java.util.Map) {
            java.util.Map<?, ?> stateMap = (java.util.Map<?, ?>) state;

            if (stateMap.containsKey("angle")) {
                Object angleObj = stateMap.get("angle");
                if (angleObj instanceof Number) {
                    setAngle(((Number) angleObj).doubleValue());
                }
            }

            if (stateMap.containsKey("precision")) {
                Object precisionObj = stateMap.get("precision");
                if (precisionObj instanceof Number) {
                    setPrecision(((Number) precisionObj).intValue());
                }
            }

            if (stateMap.containsKey("showTicks")) {
                Object showTicksObj = stateMap.get("showTicks");
                if (showTicksObj instanceof Boolean) {
                    setShowTicks((Boolean) showTicksObj);
                }
            }

            if (stateMap.containsKey("showValueInput")) {
                Object showInputObj = stateMap.get("showValueInput");
                if (showInputObj instanceof Boolean) {
                    setShowValueInput((Boolean) showInputObj);
                }
            }

            if (stateMap.containsKey("allowDirectDrawing")) {
                Object allowDirectObj = stateMap.get("allowDirectDrawing");
                if (allowDirectObj instanceof Boolean) {
                    setAllowDirectDrawing((Boolean) allowDirectObj);
                }
            }
        }
        markDirty();
    }
} 