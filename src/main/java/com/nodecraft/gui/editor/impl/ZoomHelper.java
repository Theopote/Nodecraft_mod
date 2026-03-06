package com.nodecraft.gui.editor.impl;

/**
 * 提供缩放和布局计算的助手方法
 * <p>
 * ### 单位转换说明
 * 为了解决接口单位不一致的问题，本类提供了完整的单位转换工具：
 * <p>
 * **术语定义**：
 * - **逻辑单位**：未缩放的基础单位，通常用于设计和计算
 * - **像素单位**：已缩放的实际屏幕像素，用于渲染和显示
 * <p>
 * **转换规则**：
 * - 逻辑单位 × 缩放因子 = 像素单位
 * - 像素单位 ÷ 缩放因子 = 逻辑单位
 * <p>
 * **使用指导**：
 * - 计算方法（如 calculateUIHeight）应返回逻辑单位
 * - 渲染方法（如 renderCustomUI）接收像素单位
 * - 使用本类的转换方法确保单位正确性
 */
public class ZoomHelper {
    
    // ### 基础缩放方法
    
    /**
     * 将逻辑单位转换为像素单位（应用缩放）。
     * <p>
     * 这是最基础的转换方法，将设计时的逻辑尺寸转换为实际渲染的像素尺寸。
     * 
     * @param logicalSize 逻辑单位尺寸
     * @param zoom 缩放因子
     * @return 像素单位尺寸
     */
    public static float applyZoom(float logicalSize, float zoom) {
        return logicalSize * zoom;
    }
    
    /**
     * 将逻辑单位转换为像素单位的别名方法。
     * <p>
     * 提供更明确的方法名，避免混淆。
     * 
     * @param logicalSize 逻辑单位尺寸
     * @param zoom 缩放因子
     * @return 像素单位尺寸
     */
    public static float toScaledPixels(float logicalSize, float zoom) {
        return applyZoom(logicalSize, zoom);
    }
    
    /**
     * 将像素单位转换为逻辑单位（移除缩放）。
     * <p>
     * 当需要将已缩放的像素值转换回逻辑单位时使用。
     * 
     * @param pixelSize 像素单位尺寸
     * @param zoom 缩放因子
     * @return 逻辑单位尺寸
     */
    public static float toLogicalUnits(float pixelSize, float zoom) {
        if (zoom == 0) {
            return 0; // 避免除零错误
        }
        return pixelSize / zoom;
    }
    
    /**
     * 移除缩放的别名方法。
     * 
     * @param pixelSize 像素单位尺寸
     * @param zoom 缩放因子
     * @return 逻辑单位尺寸
     */
    public static float removeZoom(float pixelSize, float zoom) {
        return toLogicalUnits(pixelSize, zoom);
    }
    
    // ### 批量转换方法
    
    /**
     * 批量将逻辑单位转换为像素单位。
     * 
     * 用于同时转换多个尺寸值，提高代码简洁性。
     * 
     * @param logicalSizes 逻辑单位尺寸数组
     * @param zoom 缩放因子
     * @return 像素单位尺寸数组
     */
    public static float[] toScaledPixels(float[] logicalSizes, float zoom) {
        float[] result = new float[logicalSizes.length];
        for (int i = 0; i < logicalSizes.length; i++) {
            result[i] = toScaledPixels(logicalSizes[i], zoom);
        }
        return result;
    }
    
    /**
     * 批量将像素单位转换为逻辑单位。
     * 
     * @param pixelSizes 像素单位尺寸数组
     * @param zoom 缩放因子
     * @return 逻辑单位尺寸数组
     */
    public static float[] toLogicalUnits(float[] pixelSizes, float zoom) {
        float[] result = new float[pixelSizes.length];
        for (int i = 0; i < pixelSizes.length; i++) {
            result[i] = toLogicalUnits(pixelSizes[i], zoom);
        }
        return result;
    }
    
    // ### 尺寸结构转换
    
    /**
     * 尺寸信息结构体（逻辑单位）
     */
    public static class LogicalSize {
        public final float width;
        public final float height;
        
        public LogicalSize(float width, float height) {
            this.width = width;
            this.height = height;
        }
        
        /**
         * 转换为像素单位
         */
        public PixelSize toPixels(float zoom) {
            return new PixelSize(toScaledPixels(width, zoom), toScaledPixels(height, zoom));
        }
        
        @Override
        public String toString() {
            return String.format("LogicalSize{width=%.1f, height=%.1f}", width, height);
        }
    }
    
    /**
     * 尺寸信息结构体（像素单位）
     */
    public static class PixelSize {
        public final float width;
        public final float height;
        
        public PixelSize(float width, float height) {
            this.width = width;
            this.height = height;
        }
        
        /**
         * 转换为逻辑单位
         */
        public LogicalSize toLogical(float zoom) {
            return new LogicalSize(toLogicalUnits(width, zoom), toLogicalUnits(height, zoom));
        }
        
        @Override
        public String toString() {
            return String.format("PixelSize{width=%.1f, height=%.1f}", width, height);
        }
    }
    
    /**
     * 边距信息结构体（逻辑单位）
     */
    public static class LogicalMargin {
        public final float left, top, right, bottom;
        
        public LogicalMargin(float all) {
            this(all, all, all, all);
        }
        
        public LogicalMargin(float horizontal, float vertical) {
            this(horizontal, vertical, horizontal, vertical);
        }
        
        public LogicalMargin(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
        
        /**
         * 转换为像素单位
         */
        public PixelMargin toPixels(float zoom) {
            return new PixelMargin(
                toScaledPixels(left, zoom),
                toScaledPixels(top, zoom),
                toScaledPixels(right, zoom),
                toScaledPixels(bottom, zoom)
            );
        }
        
        /**
         * 获取水平边距总和（逻辑单位）
         */
        public float getHorizontalTotal() {
            return left + right;
        }
        
        /**
         * 获取垂直边距总和（逻辑单位）
         */
        public float getVerticalTotal() {
            return top + bottom;
        }
        
        @Override
        public String toString() {
            return String.format("LogicalMargin{left=%.1f, top=%.1f, right=%.1f, bottom=%.1f}", 
                               left, top, right, bottom);
        }
    }
    
    /**
     * 边距信息结构体（像素单位）
     */
    public static class PixelMargin {
        public final float left, top, right, bottom;
        
        public PixelMargin(float all) {
            this(all, all, all, all);
        }
        
        public PixelMargin(float horizontal, float vertical) {
            this(horizontal, vertical, horizontal, vertical);
        }
        
        public PixelMargin(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
        
        /**
         * 转换为逻辑单位
         */
        public LogicalMargin toLogical(float zoom) {
            return new LogicalMargin(
                toLogicalUnits(left, zoom),
                toLogicalUnits(top, zoom),
                toLogicalUnits(right, zoom),
                toLogicalUnits(bottom, zoom)
            );
        }
        
        /**
         * 获取水平边距总和（像素单位）
         */
        public float getHorizontalTotal() {
            return left + right;
        }
        
        /**
         * 获取垂直边距总和（像素单位）
         */
        public float getVerticalTotal() {
            return top + bottom;
        }
        
        @Override
        public String toString() {
            return String.format("PixelMargin{left=%.1f, top=%.1f, right=%.1f, bottom=%.1f}", 
                               left, top, right, bottom);
        }
    }
    
    // ### 布局计算方法
    
    /**
     * 计算可用的内容宽度（减去边距）。
     * 
     * ### 参数单位说明
     * - `totalWidth` - 像素单位（已缩放）
     * - `contentMargin` - 逻辑单位（未缩放）
     * - 返回值 - 像素单位（已缩放）
     * 
     * @param totalWidth 总宽度（像素单位）
     * @param contentMargin 内容边距（逻辑单位）
     * @param zoom 缩放因子
     * @return 可用宽度（像素单位）
     */
    public static float getAvailableWidth(float totalWidth, float contentMargin, float zoom) {
        return totalWidth - toScaledPixels(contentMargin, zoom);
    }
    
    /**
     * 计算可用的内容宽度（减去边距）- 使用边距结构体。
     * 
     * @param totalWidth 总宽度（像素单位）
     * @param margin 边距信息（逻辑单位）
     * @param zoom 缩放因子
     * @return 可用宽度（像素单位）
     */
    public static float getAvailableWidth(float totalWidth, LogicalMargin margin, float zoom) {
        return totalWidth - toScaledPixels(margin.getHorizontalTotal(), zoom);
    }
    
    /**
     * 计算可用的内容高度（减去边距）。
     * 
     * ### 参数单位说明
     * - `totalHeight` - 像素单位（已缩放）
     * - `contentMargin` - 逻辑单位（未缩放）
     * - 返回值 - 像素单位（已缩放）
     * 
     * @param totalHeight 总高度（像素单位）
     * @param contentMargin 内容边距（逻辑单位）
     * @param zoom 缩放因子
     * @return 可用高度（像素单位）
     */
    public static float getAvailableHeight(float totalHeight, float contentMargin, float zoom) {
        return totalHeight - toScaledPixels(contentMargin, zoom);
    }
    
    /**
     * 计算可用的内容高度（减去边距）- 使用边距结构体。
     * 
     * @param totalHeight 总高度（像素单位）
     * @param margin 边距信息（逻辑单位）
     * @param zoom 缩放因子
     * @return 可用高度（像素单位）
     */
    public static float getAvailableHeight(float totalHeight, LogicalMargin margin, float zoom) {
        return totalHeight - toScaledPixels(margin.getVerticalTotal(), zoom);
    }
    
    /**
     * 计算居中偏移量。
     * 
     * ### 参数单位说明
     * - `availableSize` 和 `elementSize` 必须使用相同单位（都是像素或都是逻辑单位）
     * - 返回值与输入参数使用相同单位
     * 
     * @param availableSize 可用尺寸
     * @param elementSize 元素尺寸
     * @return 居中偏移量（非负值）
     */
    public static float getCenterOffset(float availableSize, float elementSize) {
        return Math.max(0, (availableSize - elementSize) / 2);
    }
    
    // ### 单位验证方法
    
    /**
     * 验证两个值是否使用相同的单位（调试辅助）。
     * 
     * 通过比较数值的合理性来推断单位是否匹配。
     * 这是一个启发式检查，不能100%准确，但能发现大多数单位不匹配问题。
     * 
     * @param value1 第一个值
     * @param value2 第二个值
     * @param context 上下文描述（用于日志）
     * @return 如果可能存在单位不匹配返回false
     */
    public static boolean validateUnitConsistency(float value1, float value2, String context) {
        // 如果其中一个值为0或负数，跳过检查
        if (value1 <= 0 || value2 <= 0) {
            return true;
        }
        
        // 检查数值比例是否合理
        float ratio = Math.max(value1, value2) / Math.min(value1, value2);
        
        // 如果比例过大（超过10倍），可能存在单位不匹配
        if (ratio > 10.0f) {
            System.err.printf(
                    "[Unit Warning] %s: Possible unit mismatch - value1=%.2f, value2=%.2f, ratio=%.2f%n",
                context, value1, value2, ratio);
            return false;
        }
        
        return true;
    }
    
    /**
     * 验证尺寸值是否在合理范围内。
     * 
     * @param size 尺寸值
     * @param isPixelUnit 是否为像素单位
     * @param context 上下文描述
     * @return 如果尺寸合理返回true
     */
    public static boolean validateSizeRange(float size, boolean isPixelUnit, String context) {
        if (size < 0) {
            System.err.printf(
                    "[Size Warning] %s: Negative size value: %.2f%n", context, size);
            return false;
        }
        
        if (isPixelUnit) {
            // 像素单位的合理范围：0-10000像素
            if (size > 10000) {
                System.err.printf(
                        "[Size Warning] %s: Unusually large pixel size: %.2f%n", context, size);
                return false;
            }
        } else {
            // 逻辑单位的合理范围：0-2000逻辑单位
            if (size > 2000) {
                System.err.printf(
                        "[Size Warning] %s: Unusually large logical size: %.2f%n", context, size);
                return false;
            }
        }
        
        return true;
    }
    
    // ### 便利方法
    
    /**
     * 创建逻辑尺寸对象。
     * 
     * @param width 宽度（逻辑单位）
     * @param height 高度（逻辑单位）
     * @return 逻辑尺寸对象
     */
    public static LogicalSize logicalSize(float width, float height) {
        return new LogicalSize(width, height);
    }
    
    /**
     * 创建像素尺寸对象。
     * 
     * @param width 宽度（像素单位）
     * @param height 高度（像素单位）
     * @return 像素尺寸对象
     */
    public static PixelSize pixelSize(float width, float height) {
        return new PixelSize(width, height);
    }
    
    /**
     * 创建逻辑边距对象。
     * 
     * @param margin 边距值（逻辑单位）
     * @return 逻辑边距对象
     */
    public static LogicalMargin logicalMargin(float margin) {
        return new LogicalMargin(margin);
    }
    
    /**
     * 创建逻辑边距对象。
     * 
     * @param horizontal 水平边距（逻辑单位）
     * @param vertical 垂直边距（逻辑单位）
     * @return 逻辑边距对象
     */
    public static LogicalMargin logicalMargin(float horizontal, float vertical) {
        return new LogicalMargin(horizontal, vertical);
    }
    
    /**
     * 创建逻辑边距对象。
     * 
     * @param left 左边距（逻辑单位）
     * @param top 上边距（逻辑单位）
     * @param right 右边距（逻辑单位）
     * @param bottom 下边距（逻辑单位）
     * @return 逻辑边距对象
     */
    public static LogicalMargin logicalMargin(float left, float top, float right, float bottom) {
        return new LogicalMargin(left, top, right, bottom);
    }
    
    /**
     * 创建像素边距对象。
     * 
     * @param margin 边距值（像素单位）
     * @return 像素边距对象
     */
    public static PixelMargin pixelMargin(float margin) {
        return new PixelMargin(margin);
    }
    
    /**
     * 创建像素边距对象。
     * 
     * @param horizontal 水平边距（像素单位）
     * @param vertical 垂直边距（像素单位）
     * @return 像素边距对象
     */
    public static PixelMargin pixelMargin(float horizontal, float vertical) {
        return new PixelMargin(horizontal, vertical);
    }
    
    /**
     * 创建像素边距对象。
     * 
     * @param left 左边距（像素单位）
     * @param top 上边距（像素单位）
     * @param right 右边距（像素单位）
     * @param bottom 下边距（像素单位）
     * @return 像素边距对象
     */
    public static PixelMargin pixelMargin(float left, float top, float right, float bottom) {
        return new PixelMargin(left, top, right, bottom);
    }
}