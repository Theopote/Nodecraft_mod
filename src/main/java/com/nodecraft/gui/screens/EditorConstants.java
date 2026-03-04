package com.nodecraft.gui.screens;

/**
 * NodeCraft编辑器常量定义
 */
public final class EditorConstants {
    // 私有构造函数，防止实例化
    private EditorConstants() {}
    
    // 窗口常量
    public static final float MIN_WINDOW_WIDTH = 480.0f;
    public static final float MIN_WINDOW_HEIGHT = 320.0f;

    // 布局比例常量 (确保和为 1.0)
    public static final float NODE_PANEL_WIDTH_RATIO = 0.20f; // 左侧节点库比例
    public static final float CANVAS_WIDTH_RATIO = 0.55f;     // 中间画布比例
    public static final float PROPERTY_PANEL_WIDTH_RATIO = 0.25f; // 右侧属性面板比例

    // 最小面板尺寸常量
    public static final float MIN_PANEL_WIDTH = 150f; // 增大最小面板宽度
    public static final float MIN_CANVAS_WIDTH = 200f; // 增大最小画布宽度
    public static final float WINDOW_EDGE_MARGIN = 20f; // 减小边缘边距

    // 窗口尺寸常量 (保持简化版)
    public static final float SCREEN_WIDTH_RATIO = 0.5f;
    public static final float SCREEN_HEIGHT_RATIO = 0.5f;
    public static final float MAX_WINDOW_WIDTH = 1800f;
    public static final float MAX_WINDOW_HEIGHT = 1200f;

    // 新增样式常量
    public static final float DEFAULT_WINDOW_PADDING = 5.0f;
    public static final float DEFAULT_ITEM_SPACING = 6.0f;
    public static final float DEFAULT_LAYOUT_SPACING = 5.0f;

    // 颜色常量
    public static final int COLOR_TEXT_ERROR = 0xFF0000; // 红色
    public static final int COLOR_TEXT_HINT = 0xFFFFFF; // 白色
} 