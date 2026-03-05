package com.nodecraft.nodesystem.preview;

import org.joml.Vector3f;

/**
 * 预览选项配置类
 * 包含所有预览元素的可配置属性
 */
public class PreviewOptions {
    
    // ================= 基础属性 =================
    public Float opacity; // 透明度 (0.0-1.0)
    public Float minOpacity; // 最小透明度 (0.0-1.0)，防止完全透明
    public Boolean visible; // 是否可见
    public Integer renderPriority; // 渲染优先级
    public Integer duration; // 持续时间（秒）
    
    // ================= 视觉属性 =================
    public Vector3f color; // 颜色
    public Vector3f tintColor; // 着色
    public Float lineWidth; // 线条宽度
    public Float pointSize; // 点大小
    public Boolean showOutline; // 是否显示轮廓
    public Boolean enableGlow; // 是否启用发光效果
    
    // ================= 动画属性 =================
    public Boolean enableAnimation; // 是否启用动画
    public Boolean pulseAnimation; // 脉冲动画
    public Float animationSpeed; // 动画速度
    public String animationType; // 动画类型
    
    // ================= 方块特定属性 =================
    public String textureMode; // 纹理模式 ("original", "solid_color", "wireframe")
    public Boolean useOriginalTexture; // 使用原始纹理
    
    // ================= 几何体特定属性 =================
    public Boolean showFill; // 是否显示填充
    public String shapeType; // 形状类型 ("cube", "sphere", "cylinder")
    public Float radius; // 半径（用于球体等）
    public Vector3f scale; // 缩放
    
    // ================= 向量/箭头特定属性 =================
    public Float arrowSize; // 箭头大小
    public Boolean showArrows; // 是否显示箭头
    public Float lengthScale; // 长度缩放
    
    // ================= 线条特定属性 =================
    public String lineStyle; // 线条样式 ("solid", "dashed", "dotted")
    public Float dashLength; // 虚线长度
    public Boolean smoothCurves; // 平滑曲线
    
    // ================= 域可视化特定属性 =================
    public String gradientType; // 渐变类型
    public Vector3f[] colorRamp; // 颜色渐变
    public Integer particleDensity; // 粒子密度
    public String flowDirection; // 流动方向
    
    // ================= 文本特定属性 =================
    public Float fontSize; // 字体大小
    public Boolean showBackground; // 显示背景
    public Vector3f backgroundColor; // 背景颜色
    public String fontFamily; // 字体
    
    // ================= Gizmo 特定属性 =================
    public Float gizmoSize; // Gizmo 大小
    public Boolean alwaysOnTop; // 始终在最上层
    public Float interactionRadius; // 交互半径
    public String gizmoType; // Gizmo 类型 ("move", "rotate", "scale", "all")
    
    // ================= 性能属性 =================
    public Integer maxRenderDistance; // 最大渲染距离
    public Boolean enableLOD; // 启用LOD
    public Boolean enableOcclusionCulling; // 启用遮挡剔除
    public Boolean enableDistanceFading; // 启用距离衰减
    
    // ================= 构造函数 =================
    
    public PreviewOptions() {
        // 默认构造函数，所有属性都是 null，将使用默认值
    }
    
    // ================= 便捷方法 =================
    
    /**
     * 设置基础颜色
     */
    public PreviewOptions setColor(float r, float g, float b) {
        this.color = new Vector3f(r, g, b);
        return this;
    }
    
    /**
     * 设置透明度
     */
    public PreviewOptions setOpacity(float opacity) {
        this.opacity = Math.max(0.0f, Math.min(1.0f, opacity));
        return this;
    }
    
    /**
     * 设置最小透明度
     * 确保即使在动画或距离衰减情况下，透明度也不会低于此值
     */
    public PreviewOptions setMinOpacity(float minOpacity) {
        this.minOpacity = Math.max(0.0f, Math.min(1.0f, minOpacity));
        return this;
    }
    
    /**
     * 设置线条宽度
     */
    public PreviewOptions setLineWidth(float width) {
        this.lineWidth = Math.max(0.1f, width);
        return this;
    }

    /**
     * 设置是否显示填充
     */
    public PreviewOptions setShowFill(boolean showFill) {
        this.showFill = showFill;
        return this;
    }

    /**
     * 设置是否显示轮廓
     */
    public PreviewOptions setShowOutline(boolean showOutline) {
        this.showOutline = showOutline;
        return this;
    }
    
    /**
     * 设置持续时间
     */
    public PreviewOptions setDuration(int seconds) {
        this.duration = Math.max(0, seconds);
        return this;
    }
    
    /**
     * 启用脉冲动画
     */
    public PreviewOptions enablePulse() {
        this.pulseAnimation = true;
        this.enableAnimation = true;
        return this;
    }
    
    /**
     * 设置为线框模式
     */
    public PreviewOptions wireframeMode() {
        this.textureMode = "wireframe";
        this.showOutline = true;
        this.showFill = false;
        return this;
    }
    
    /**
     * 设置为幽灵方块模式
     */
    public PreviewOptions ghostBlockMode() {
        this.textureMode = "original";
        this.useOriginalTexture = true;
        this.showOutline = false;
        return this;
    }
    
    /**
     * 创建默认的方块高亮选项 - 模仿 Minecraft 原版
     */
    public static PreviewOptions createBlockHighlight() {
        return new PreviewOptions()
                .setColor(0.0f, 0.0f, 0.0f) // 黑色轮廓，类似 Minecraft 原版
                .setOpacity(1.0f) // 完全不透明，确保可见
                .setMinOpacity(0.0f) // 不设置最小透明度，完全遵循透明度设置
                .setLineWidth(2.0f)
                .wireframeMode();
    }
    
    /**
     * 创建默认的区域框选项
     */
    public static PreviewOptions createRegionBox() {
        return new PreviewOptions()
                .setColor(0.2f, 0.7f, 1.0f) // 天蓝色
                .setOpacity(0.3f)
                .setLineWidth(1.5f)
                .enablePulse();
    }
    
    /**
     * 创建默认的点显示选项
     */
    public static PreviewOptions createPoints() {
        PreviewOptions options = new PreviewOptions();
        options.setColor(1.0f, 0.0f, 0.0f) // 红色
                .setOpacity(0.9f);
        options.pointSize = 0.1f;
        options.shapeType = "sphere";
        return options;
    }
    
    /**
     * 创建默认的向量箭头选项
     */
    public static PreviewOptions createVectorArrows() {
        PreviewOptions options = new PreviewOptions();
        options.setColor(0.0f, 1.0f, 0.0f) // 绿色
                .setOpacity(0.8f)
                .setLineWidth(1.5f);
        options.showArrows = true;
        options.arrowSize = 0.2f;
        options.lengthScale = 1.0f;
        return options;
    }
    
    /**
     * 创建默认的变换 Gizmo 选项
     */
    public static PreviewOptions createTransformGizmo() {
        PreviewOptions options = new PreviewOptions();
        options.gizmoSize = 1.0f;
        options.alwaysOnTop = true;
        options.interactionRadius = 0.5f;
        options.gizmoType = "all";
        options.renderPriority = -100; // 高优先级
        return options;
    }
    
    /**
     * 复制选项
     */
    public PreviewOptions copy() {
        PreviewOptions copy = new PreviewOptions();
        
        // 基础属性
        copy.opacity = this.opacity;
        copy.minOpacity = this.minOpacity;
        copy.visible = this.visible;
        copy.renderPriority = this.renderPriority;
        copy.duration = this.duration;
        
        // 视觉属性
        copy.color = this.color != null ? new Vector3f(this.color) : null;
        copy.tintColor = this.tintColor != null ? new Vector3f(this.tintColor) : null;
        copy.lineWidth = this.lineWidth;
        copy.pointSize = this.pointSize;
        copy.showOutline = this.showOutline;
        copy.enableGlow = this.enableGlow;
        
        // 动画属性
        copy.enableAnimation = this.enableAnimation;
        copy.pulseAnimation = this.pulseAnimation;
        copy.animationSpeed = this.animationSpeed;
        copy.animationType = this.animationType;
        
        // 其他属性... (根据需要添加)
        
        return copy;
    }
} 