package com.nodecraft.gui.editor.impl;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.core.NodeCraft;
import imgui.ImGui;
import imgui.ImDrawList;

import java.util.UUID;

/**
 * 自定义UI节点的抽象基类
 * 
 * <h2>缓存机制和 markDirty() 使用指南</h2>
 * <p>
 * 本类使用缓存机制优化UI尺寸计算性能。子类在修改任何可能影响UI尺寸的属性后，
 * <strong>必须</strong>调用 {@link #markDirty()} 方法以确保缓存失效。
 * </p>
 * 
 * <h3>何时需要调用 markDirty()</h3>
 * <ul>
 *   <li><strong>文本内容变化</strong>：修改显示的文本内容</li>
 *   <li><strong>UI元素增减</strong>：添加或删除UI组件</li>
 *   <li><strong>布局参数变化</strong>：修改间距、边距、对齐方式等</li>
 *   <li><strong>样式属性变化</strong>：修改字体大小、颜色、边框等影响尺寸的样式</li>
 *   <li><strong>可见性变化</strong>：显示或隐藏UI元素</li>
 * </ul>
 * 
 * <h3>正确使用示例</h3>
 * <pre>{@code
 * // 示例1：修改文本内容
 * public void setText(String newText) {
 *     this.text = newText;
 *     markDirty(); // 确保缓存失效
 * }
 * 
 * // 示例2：添加UI元素
 * public void addElement(UIElement element) {
 *     elements.add(element);
 *     markDirty(); // 确保缓存失效
 * }
 * 
 * // 示例3：修改布局参数
 * public void setPadding(float padding) {
 *     this.padding = padding;
 *     markDirty(); // 确保缓存失效
 * }
 * 
 * // 示例4：批量修改时的优化
 * public void updateMultipleProperties(String text, float padding, boolean visible) {
 *     this.text = text;
 *     this.padding = padding;
 *     this.visible = visible;
 *     markDirty(); // 只需在最后调用一次
 * }
 * }</pre>
 * 
 * <h3>调试支持</h3>
 * <p>
 * 启用缓存调试模式（设置系统属性 {@code debug.ui.cache=true}）可以：
 * </p>
 * <ul>
 *   <li>监控缓存操作和版本变化</li>
 *   <li>检测缓存一致性问题</li>
 *   <li>识别缺失的 markDirty() 调用</li>
 * </ul>
 * 
 * <h3>性能注意事项</h3>
 * <ul>
 *   <li>避免在渲染循环中频繁调用 markDirty()</li>
 *   <li>批量修改时只在最后调用一次 markDirty()</li>
 *   <li>不要在 calculateUIHeight() 或 calculateMinUIWidth() 中调用 markDirty()</li>
 * </ul>
 * 
 * <h3>字体缩放优化</h3>
 * <p>
 * 本类实现了字体缩放的性能优化机制：
 * </p>
 * <ul>
 *   <li><strong>缓存机制</strong>：字体缩放值会被缓存，避免重复计算</li>
 *   <li><strong>阈值控制</strong>：只有当缩放差异超过阈值时才更新，减少不必要的ImGui调用</li>
 *   <li><strong>可配置范围</strong>：子类可以覆写 {@link #getMinFontScale()} 和 {@link #getMaxFontScale()} 自定义缩放范围</li>
 *   <li><strong>自定义逻辑</strong>：子类可以覆写 {@link #calculateImGuiFontScale(float)} 实现特定的字体缩放策略</li>
 * </ul>
 * 
 * <h3>布局助手方法和单位一致性</h3>
 * <p>
 * 为了避免单位混淆和布局错误，本类提供了两套布局API：
 * </p>
 * <ul>
 *   <li><strong>通用方法</strong>：如 {@link #setCenterX(float, float)}，需要确保参数使用相同单位</li>
 *   <li><strong>像素专用方法</strong>：如 {@link #setCenterXInPixels(float, float)}，明确要求像素单位</li>
 *   <li><strong>单位验证</strong>：启用布局调试模式可以自动检测单位不匹配问题</li>
 *   <li><strong>LayoutHelper增强</strong>：提供 {@code getUnitSuggestion()} 方法帮助识别合适的单位类型</li>
 * </ul>
 * 
 * <h4>单位使用建议</h4>
 * <pre>{@code
 * // 推荐：使用明确的像素方法
 * setCenterXInPixels(availableWidthPixels, elementWidthPixels);
 * 
 * // 或者：在LayoutHelper中进行转换
 * layout(zoom, helper -> {
 *     float elementWidthPixels = helper.logicalToPixels(elementWidthLogical);
 *     helper.setCenterXInPixels(availableWidthPixels, elementWidthPixels);
 *     return true;
 * });
 * }</pre>
 */
public abstract class BaseCustomUINode extends BaseNode implements ICustomUINode {

    // ### 通用常量

    /** 默认节点内容边距（未缩放逻辑单位） */
    protected static final float DEFAULT_CONTENT_MARGIN = 16.0f;
    /** 默认小间距（未缩放逻辑单位） */
    protected static final float DEFAULT_PADDING_SMALL = 2.0f;
    /** 默认中等间距（未缩放逻辑单位） */
    protected static final float DEFAULT_PADDING_MEDIUM = 3.0f;
    /** 默认大间距（未缩放逻辑单位） */
    protected static final float DEFAULT_PADDING_LARGE = 5.0f;
    
    // ### 缓存一致性检查阈值
    
    /** 缓存一致性检查的浮点数比较阈值 */
    private static final float CACHE_CONSISTENCY_THRESHOLD = 0.001f;
    /** 缓存不一致时是否抛出异常（调试模式下） */
    private static final boolean STRICT_CACHE_VALIDATION = Boolean.getBoolean("debug.ui.cache.strict");
    
    // ### 字体缩放相关常量
    
    /** 字体缩放变化的最小阈值，避免频繁更新 */
    private static final float FONT_SCALE_CHANGE_THRESHOLD = 0.05f;
    /** 默认最小字体缩放比例 */
    private static final float DEFAULT_MIN_FONT_SCALE = 0.7f;
    /** 默认最大字体缩放比例 */
    private static final float DEFAULT_MAX_FONT_SCALE = 2.8f;
    /** 字体缩放缓存的最大年龄（毫秒），超过此时间强制重新计算 */
    private static final long FONT_SCALE_CACHE_MAX_AGE = 100; // 100ms

    // ### 缓存字段

    /** 缓存的UI高度（未缩放的逻辑单位） */
    private transient float cachedUIHeight = -1;
    /** 缓存的最小宽度（未缩放的逻辑单位） */
    private transient float cachedMinWidth = -1;
    /** 缓存失效标记 */
    private transient boolean cacheInvalid = true;
    /** 缓存版本号，用于调试和一致性检查 */
    private transient long cacheVersion = 0;
    /** 缓存的ImGui ID，用于避免重复计算 */
    private transient String cachedImGuiId = null;
    /** 上次缓存更新的时间戳，用于调试 */
    private transient long lastCacheUpdateTime = 0;
    
    // ### 字体缩放缓存字段
    
    /** 缓存的字体缩放值 */
    private transient float cachedFontScale = -1;
    /** 缓存字体缩放时对应的zoom值 */
    private transient float cachedFontScaleZoom = -1;
    /** 字体缩放缓存的更新时间 */
    private transient long fontScaleCacheTime = 0;

    /**
     * 构造函数
     * @param id 节点ID
     * @param nodeType 节点类型
     */
    public BaseCustomUINode(UUID id, String nodeType) {
        super(id, nodeType);
        lastCacheUpdateTime = System.currentTimeMillis();
    }

    // ### ICustomUINode 默认实现

    @Override
    public final boolean hasCustomUI() {
        return true;
    }

    @Override
    public final float getCustomUIHeight() {
        if (cacheInvalid || cachedUIHeight < 0) {
                    if (isCacheDebugEnabled()) {
                NodeCraft.LOGGER.debug("[Cache Debug] Node {}: Recalculating UI height (cache invalid: {}, cached value: {})", 
                                     getId(), cacheInvalid, cachedUIHeight);
        }
            
            float newHeight = calculateUIHeight();
            
            // 确保返回值为非负
            if (newHeight < 0) {
                NodeCraft.LOGGER.warn("calculateUIHeight() returned a negative value ({}) for node {}. Clamping to 0.", 
                                    newHeight, getId());
                newHeight = 0;
            }
            
            // 增强的缓存一致性检查
            performCacheConsistencyCheck("UI height", cachedUIHeight, newHeight, "calculateUIHeight()");
            
            cachedUIHeight = newHeight;
            cacheInvalid = false;
            cacheVersion++;
            lastCacheUpdateTime = System.currentTimeMillis();
            
                        if (isCacheDebugEnabled()) {
                NodeCraft.LOGGER.debug("[Cache Debug] Node {}: Cached UI height: {} (version: {}, timestamp: {})", 
                                     getId(), cachedUIHeight, cacheVersion, lastCacheUpdateTime);
        }
        } else if (isCacheDebugEnabled()) {
            NodeCraft.LOGGER.debug("[Cache Debug] Node {}: Using cached UI height: {} (version: {}, age: {}ms)", 
                                 getId(), cachedUIHeight, cacheVersion, 
                                 System.currentTimeMillis() - lastCacheUpdateTime);
        }
        
        return cachedUIHeight;
    }

    @Override
    public final float getCustomUIHeight(float zoom) {
        // 返回未缩放的高度乘以缩放因子
        return getCustomUIHeight() * zoom;
    }

    @Override
    public final float getMinRequiredUIWidth() {
        if (cacheInvalid || cachedMinWidth < 0) {
                        if (isCacheDebugEnabled()) {
                NodeCraft.LOGGER.debug("[Cache Debug] Node {}: Recalculating min UI width (cache invalid: {}, cached value: {})", 
                                     getId(), cacheInvalid, cachedMinWidth);
            }
            
            float newWidth = calculateMinUIWidth();
            
            // 确保返回值为非负
            if (newWidth < 0) {
                NodeCraft.LOGGER.warn("calculateMinUIWidth() returned a negative value ({}) for node {}. Clamping to 0.", 
                                    newWidth, getId());
                newWidth = 0;
            }
            
            // 增强的缓存一致性检查
            performCacheConsistencyCheck("min UI width", cachedMinWidth, newWidth, "calculateMinUIWidth()");
            
            cachedMinWidth = newWidth;
            cacheInvalid = false;
            cacheVersion++;
            lastCacheUpdateTime = System.currentTimeMillis();
            
            if (isCacheDebugEnabled()) {
                NodeCraft.LOGGER.debug("[Cache Debug] Node {}: Cached min UI width: {} (version: {}, timestamp: {})", 
                                     getId(), cachedMinWidth, cacheVersion, lastCacheUpdateTime);
        }
        } else if (isCacheDebugEnabled()) {
            NodeCraft.LOGGER.debug("[Cache Debug] Node {}: Using cached min UI width: {} (version: {}, age: {}ms)", 
                                 getId(), cachedMinWidth, cacheVersion, 
                                 System.currentTimeMillis() - lastCacheUpdateTime);
        }
        
        return cachedMinWidth;
    }

    @Override
    public final float getMinRequiredUIWidth(float zoom) {
        // 返回未缩放的最小宽度乘以缩放因子
        return getMinRequiredUIWidth() * zoom;
    }

    @Override
    public boolean supportsDirectDrawing() {
        // 默认不支持直接绘制，子类可以覆写
        // 只有在确实需要直接绘制功能时才返回true
        return false;
    }

    @Override
    public boolean renderCustomUIDirect(ImDrawList drawList, float screenX, float screenY,
                                        float width, float height, float zoom) {
        // 默认实现返回false，回退到标准渲染
        // 子类应该覆写此方法以实现具体的直接绘制逻辑
        
        if (isDirectDrawDebugEnabled()) {
            NodeCraft.LOGGER.debug("[Direct Draw Debug] Node {}: Default implementation called - falling back to standard rendering", getId());
            NodeCraft.LOGGER.debug("[Direct Draw Debug] Parameters: screenX={}, screenY={}, width={}, height={}, zoom={}", 
                                 screenX, screenY, width, height, zoom);
        }
        
        return false;  // 回退到标准渲染
    }

    @Override
    public final boolean renderCustomUI(float width, float height, float zoom) {
        // 推入唯一的ImGui ID，确保节点内部控件的ID隔离
        // 使用更可靠的ID生成策略，避免hashCode()的碰撞风险
        pushUniqueImGuiId();

        try {
            // 调用子类实现的缩放感知渲染方法
            return renderCustomUIScaled(width, height, zoom);
        } finally {
            // 确保在任何情况下都能正确弹出ID
            ImGui.popID();
        }
    }

    @Override
    public ICustomUINode.ContentBounds getContentBounds(float zoom) {
        // 获取基础尺寸
        float scaledMinWidth = getMinRequiredUIWidth(zoom);
        float scaledMinHeight = getCustomUIHeight(zoom);
        
        // 默认实现：提供基础的安全边距以减少裁剪风险
        // 这是一个保守的默认值，适用于大多数简单UI
        float safetyMargin = getSafetyMarginForContentBounds(zoom);
        
        if (isBoundsDebugEnabled()) {
            NodeCraft.LOGGER.debug("[Bounds Debug] Node {}: Default content bounds - width={}, height={}, safetyMargin={}", 
                                 getId(), scaledMinWidth, scaledMinHeight, safetyMargin);
        }
        
        // 如果子类需要特殊的边界处理，应该覆写此方法
        // 这里提供一个小的安全边距，减少意外裁剪的风险
        return new ICustomUINode.ContentBounds(
            scaledMinWidth + safetyMargin * 2,  // 左右各增加安全边距
            scaledMinHeight + safetyMargin * 2, // 上下各增加安全边距
            safetyMargin, safetyMargin, safetyMargin, safetyMargin  // 四周安全边距
        );
    }

    protected float getSafetyMarginForContentBounds(float zoom) {
        // 默认提供2个逻辑像素的安全边距
        // 这个值足够小，不会显著影响布局，但能减少轻微溢出的裁剪风险
        return ZoomHelper.applyZoom(2.0f, zoom);
    }

    @Override
    public ViewPortRenderInfo getViewPortRenderInfo(float zoom, float screenX, float screenY) {
        // 默认实现，子类可以覆写以提供更多信息
        return new ViewPortRenderInfo(zoom, screenX, screenY);
    }

    @Override
    public boolean renderWithViewPortAwareness(ViewPortRenderInfo renderInfo, float availableScaledWidth, float availableScaledHeight) {
        // 现在所有渲染方法都使用统一的缩放后像素值，无需进行单位转换
        // 直接调用标准的 renderCustomUI 方法，这会自动处理ImGui缩放同步
        return renderCustomUI(availableScaledWidth, availableScaledHeight, renderInfo.canvasZoom);
    }


    protected abstract float calculateUIHeight();

    protected abstract float calculateMinUIWidth();

    protected abstract boolean renderCustomUIScaled(float width, float height, float zoom);

    /**
     * 计算ImGui字体缩放因子（带缓存优化）
     * 
     * @param zoom 当前缩放级别
     * @return 计算得出的字体缩放因子
     */
    private float calculateImGuiFontScaleWithCache(float zoom) {
        long currentTime = System.currentTimeMillis();
        
        // 检查缓存是否有效
        if (cachedFontScale > 0 && 
            Math.abs(cachedFontScaleZoom - zoom) < 0.001f && 
            (currentTime - fontScaleCacheTime) < FONT_SCALE_CACHE_MAX_AGE) {
            
            if (isLayoutDebugEnabled()) {
                NodeCraft.LOGGER.debug("[Font Scale Debug] Node {}: Using cached font scale {:.3f} for zoom {:.3f} (age: {}ms)", 
                                     getId(), cachedFontScale, zoom, currentTime - fontScaleCacheTime);
            }
            return cachedFontScale;
        }
        
        // 重新计算字体缩放
        float newFontScale = calculateImGuiFontScale(zoom);
        
        // 更新缓存
        cachedFontScale = newFontScale;
        cachedFontScaleZoom = zoom;
        fontScaleCacheTime = currentTime;
        
        if (isLayoutDebugEnabled()) {
            NodeCraft.LOGGER.debug("[Font Scale Debug] Node {}: Calculated new font scale {:.3f} for zoom {:.3f}", 
                                 getId(), newFontScale, zoom);
        }
        
        return newFontScale;
    }

    /**
     * 计算ImGui字体缩放因子
     * 
     * <p>子类可以覆写此方法以提供自定义的字体缩放逻辑。
     * 默认实现提供了适合大多数UI场景的缩放策略。</p>
     * 
     * @param zoom 当前缩放级别
     * @return 计算得出的字体缩放因子，会被限制在 {@link #getMinFontScale()} 和 {@link #getMaxFontScale()} 之间
     */
    protected float calculateImGuiFontScale(float zoom) {
        // 确保缩放因子在合理范围内
        float clampedZoom = Math.max(0.1f, Math.min(zoom, 10.0f));
        
        float fontScale;
        if (clampedZoom <= 0.5f) {
            // 极小缩放：保证最小可读性
            fontScale = getMinFontScale();
        } else if (clampedZoom <= 2.0f) {
            // 正常缩放范围：线性缩放
            fontScale = clampedZoom;
        } else if (clampedZoom <= 4.0f) {
            // 大缩放：平滑过渡，避免字体过大
            // 使用平方根函数进行平滑过渡：y = 2 * sqrt(zoom/2)
            fontScale = 2.0f * (float) Math.sqrt(clampedZoom / 2.0f);
        } else {
            // 极大缩放：限制最大字体缩放
            fontScale = getMaxFontScale();
        }
        
        // 确保结果在配置的范围内
        return Math.max(getMinFontScale(), Math.min(fontScale, getMaxFontScale()));
    }

    /**
     * 获取字体缩放变化的最小阈值
     * 
     * <p>只有当新的字体缩放值与当前值的差异超过此阈值时，才会实际更新ImGui的字体缩放。
     * 这有助于减少不必要的字体缩放更改，提升渲染性能。</p>
     * 
     * @return 字体缩放变化阈值，默认为 0.05f
     */
    protected float getFontScaleChangeThreshold() {
        return FONT_SCALE_CHANGE_THRESHOLD;
    }

    /**
     * 获取最小字体缩放比例
     * 
     * <p>子类可以覆写此方法以提供自定义的最小字体缩放限制。</p>
     * 
     * @return 最小字体缩放比例，默认为 0.7f
     */
    protected float getMinFontScale() {
        return DEFAULT_MIN_FONT_SCALE;
    }

    /**
     * 获取最大字体缩放比例
     * 
     * <p>子类可以覆写此方法以提供自定义的最大字体缩放限制。</p>
     * 
     * @return 最大字体缩放比例，默认为 2.8f
     */
    protected float getMaxFontScale() {
        return DEFAULT_MAX_FONT_SCALE;
    }

    /**
     * 检查字体缩放是否需要更新
     * 
     * <p>此方法可以被子类覆写以提供自定义的字体缩放更新逻辑。
     * 例如，某些UI可能需要更频繁或更少频繁的字体缩放更新。</p>
     * 
     * @param currentScale 当前字体缩放值
     * @param targetScale 目标字体缩放值
     * @param zoom 当前缩放级别
     * @return 如果需要更新字体缩放返回true，否则返回false
     */
    protected boolean shouldUpdateFontScale(float currentScale, float targetScale, float zoom) {
        return Math.abs(targetScale - currentScale) > getFontScaleChangeThreshold();
    }

    /**
     * 获取当前缓存的字体缩放值
     * 
     * @return 当前缓存的字体缩放值，如果缓存无效返回-1
     */
    protected final float getCachedFontScale() {
        return cachedFontScale;
    }

    /**
     * 获取字体缩放缓存的状态信息
     * 
     * @return 字体缩放缓存的详细状态
     */
    protected final String getFontScaleCacheInfo() {
        if (cachedFontScale <= 0) {
            return "FontScaleCache[invalid]";
        }
        
        long age = System.currentTimeMillis() - fontScaleCacheTime;
        return String.format("FontScaleCache[scale=%.3f, zoom=%.3f, age=%dms, valid=%s]",
                           cachedFontScale, cachedFontScaleZoom, age, 
                           age < FONT_SCALE_CACHE_MAX_AGE);
    }

    /**
     * 强制重新计算字体缩放
     * 
     * <p>此方法会清除字体缩放缓存并强制在下次需要时重新计算。
     * 通常在字体相关的UI属性发生变化时调用。</p>
     */
    protected final void forceRecalculateFontScale() {
        if (isLayoutDebugEnabled()) {
            NodeCraft.LOGGER.debug("[Font Scale Debug] Node {}: Force recalculate font scale requested. {}", 
                                 getId(), getFontScaleCacheInfo());
        }
        
        invalidateFontScaleCache();
    }

    protected final boolean layout(float zoom, java.util.function.Function<LayoutHelper, Boolean> layoutConsumer) {
        LayoutHelper helper = new LayoutHelper(zoom);
        return layoutConsumer.apply(helper);
    }

    public final class LayoutHelper {
        private final float zoom;

        public LayoutHelper(float zoom) {
            this.zoom = zoom;
        }

        public float toPixels(float logicalSize) {
            return BaseCustomUINode.this.toPixels(logicalSize, zoom);
        }

        public float toPixels(float logicalSize, ResponsiveElementType elementType) {
            return BaseCustomUINode.this.toPixels(logicalSize, zoom, elementType);
        }

        public float toPixelsExact(float logicalSize) {
            return BaseCustomUINode.this.toPixelsExact(logicalSize, zoom);
        }

        public float toLogical(float pixelSize) {
            return BaseCustomUINode.this.toLogical(pixelSize, zoom);
        }

        public void setCenterX(float availableWidth, float elementWidth) {
            BaseCustomUINode.this.setCenterX(availableWidth, elementWidth);
        }

        public void setCenterY(float availableHeight, float elementHeight) {
            BaseCustomUINode.this.setCenterY(availableHeight, elementHeight);
        }

        public void addVerticalSpacing(float spacing) {
            BaseCustomUINode.this.addVerticalSpacing(spacing, zoom);
        }

        public void addHorizontalSpacing(float spacing) {
            BaseCustomUINode.this.addHorizontalSpacing(spacing, zoom);
        }

        public void setItemWidth(float width) {
            BaseCustomUINode.this.setItemWidth(width, zoom);
        }

        public float getAvailableContentWidth(float totalWidth) {
            return BaseCustomUINode.this.getAvailableContentWidth(totalWidth, zoom);
        }

        public float getAvailableContentHeight(float totalHeight) {
            return BaseCustomUINode.this.getAvailableContentHeight(totalHeight, zoom);
        }

        public void pushFramePadding(float paddingX, float paddingY) {
            BaseCustomUINode.this.pushFramePadding(paddingX, paddingY, zoom);
        }

        public void pushFrameRounding(float rounding) {
            BaseCustomUINode.this.pushFrameRounding(rounding, zoom);
        }

        public void pushFramePaddingExact(float paddingX, float paddingY) {
            BaseCustomUINode.this.pushFramePaddingExact(paddingX, paddingY, zoom);
        }

        public void pushFrameRoundingExact(float rounding) {
            BaseCustomUINode.this.pushFrameRoundingExact(rounding, zoom);
        }

        public void popStyleVar(int count) {
            ImGui.popStyleVar(count);
        }

        public void popStyleVar() {
            ImGui.popStyleVar();
        }

        public void popItemWidth() {
            ImGui.popItemWidth();
        }

        public float getSmallPaddingPixels() {
            return toPixels(BaseCustomUINode.this.getSmallPadding(), ResponsiveElementType.SPACING);
        }

        public float getMediumPaddingPixels() {
            return toPixels(BaseCustomUINode.this.getMediumPadding(), ResponsiveElementType.SPACING);
        }

        public float getLargePaddingPixels() {
            return toPixels(BaseCustomUINode.this.getLargePadding(), ResponsiveElementType.SPACING);
        }

        public float getZoom() {
            return zoom;
        }

        // ### 像素单位方法（明确单位语义）

        /**
         * 在水平方向居中元素（像素单位）
         * 
         * @param availableWidthPixels 可用宽度（像素单位）
         * @param elementWidthPixels 元素宽度（像素单位）
         */
        public void setCenterXInPixels(float availableWidthPixels, float elementWidthPixels) {
            BaseCustomUINode.this.setCenterXInPixels(availableWidthPixels, elementWidthPixels);
        }

        /**
         * 在垂直方向居中元素（像素单位）
         * 
         * @param availableHeightPixels 可用高度（像素单位）
         * @param elementHeightPixels 元素高度（像素单位）
         */
        public void setCenterYInPixels(float availableHeightPixels, float elementHeightPixels) {
            BaseCustomUINode.this.setCenterYInPixels(availableHeightPixels, elementHeightPixels);
        }

        /**
         * 添加垂直间距（像素单位）
         * 
         * @param spacingPixels 间距大小（像素单位）
         */
        public void addVerticalSpacingInPixels(float spacingPixels) {
            BaseCustomUINode.this.addVerticalSpacingInPixels(spacingPixels);
        }

        /**
         * 添加水平间距（像素单位）
         * 
         * @param spacingPixels 间距大小（像素单位）
         */
        public void addHorizontalSpacingInPixels(float spacingPixels) {
            BaseCustomUINode.this.addHorizontalSpacingInPixels(spacingPixels);
        }

        /**
         * 设置项目宽度（像素单位）
         * 
         * @param widthPixels 宽度（像素单位）
         */
        public void setItemWidthInPixels(float widthPixels) {
            BaseCustomUINode.this.setItemWidthInPixels(widthPixels);
        }

        // ### 单位转换便利方法

        /**
         * 将当前缩放级别下的逻辑尺寸转换为像素，用于像素方法
         * 
         * @param logicalSize 逻辑尺寸
         * @return 对应的像素尺寸
         */
        public float logicalToPixels(float logicalSize) {
            return toPixels(logicalSize);
        }

        /**
         * 将像素尺寸转换为当前缩放级别下的逻辑尺寸
         * 
         * @param pixelSize 像素尺寸
         * @return 对应的逻辑尺寸
         */
        public float pixelsToLogical(float pixelSize) {
            return toLogical(pixelSize);
        }

        /**
         * 获取单位使用建议
         * 
         * @param size 要检查的尺寸值
         * @return 单位使用建议字符串
         */
        public String getUnitSuggestion(float size) {
            if (size < 0) {
                return "Invalid (negative value)";
            } else if (size < 5) {
                return "Likely logical units - use regular methods";
            } else if (size > 1000) {
                return "Likely pixels - use InPixels methods";
            } else if (size > 100) {
                return "Possibly pixels - consider InPixels methods";
            } else {
                return "Could be either - verify unit type";
            }
        }
    }

    protected final void setCenterX(float availableWidth, float elementWidth) {
        // 验证参数合理性（调试模式下）
        if (isLayoutDebugEnabled()) {
            validateLayoutParameters("setCenterX", availableWidth, elementWidth);
        }
        
        float offset = ZoomHelper.getCenterOffset(availableWidth, elementWidth);
        ImGui.setCursorPosX(ImGui.getCursorPosX() + offset);
        
        if (isLayoutDebugEnabled()) {
            NodeCraft.LOGGER.debug("[Layout Debug] Node {}: setCenterX - availableWidth={}, elementWidth={}, offset={}", 
                                 getId(), availableWidth, elementWidth, offset);
        }
    }

    protected final void setCenterY(float availableHeight, float elementHeight) {
        // 验证参数合理性（调试模式下）
        if (isLayoutDebugEnabled()) {
            validateLayoutParameters("setCenterY", availableHeight, elementHeight);
        }
        
        float offset = ZoomHelper.getCenterOffset(availableHeight, elementHeight);
        ImGui.setCursorPosY(ImGui.getCursorPosY() + offset);
        
        if (isLayoutDebugEnabled()) {
            NodeCraft.LOGGER.debug("[Layout Debug] Node {}: setCenterY - availableHeight={}, elementHeight={}, offset={}", 
                                 getId(), availableHeight, elementHeight, offset);
        }
    }

    // ### 简化的像素单位API方法
    
    /**
     * 在水平方向居中元素（仅接受像素单位）
     * 
     * <p>此方法明确要求两个参数都使用像素单位，减少单位混淆的可能性。
     * 相比 {@link #setCenterX(float, float)}，此方法提供更明确的单位语义。</p>
     * 
     * @param availableWidthPixels 可用宽度（像素单位）
     * @param elementWidthPixels 元素宽度（像素单位）
     */
    protected final void setCenterXInPixels(float availableWidthPixels, float elementWidthPixels) {
        // 使用专门的像素单位验证
        if (isLayoutDebugEnabled()) {
            validatePixelParameters("setCenterXInPixels", availableWidthPixels, elementWidthPixels);
        }
        
        float offset = ZoomHelper.getCenterOffset(availableWidthPixels, elementWidthPixels);
        ImGui.setCursorPosX(ImGui.getCursorPosX() + offset);
        
        if (isLayoutDebugEnabled()) {
            NodeCraft.LOGGER.debug("[Layout Debug] Node {}: setCenterXInPixels - availableWidth={:.1f}px, elementWidth={:.1f}px, offset={:.1f}px", 
                                 getId(), availableWidthPixels, elementWidthPixels, offset);
        }
    }

    /**
     * 在垂直方向居中元素（仅接受像素单位）
     * 
     * <p>此方法明确要求两个参数都使用像素单位，减少单位混淆的可能性。
     * 相比 {@link #setCenterY(float, float)}，此方法提供更明确的单位语义。</p>
     * 
     * @param availableHeightPixels 可用高度（像素单位）
     * @param elementHeightPixels 元素高度（像素单位）
     */
    protected final void setCenterYInPixels(float availableHeightPixels, float elementHeightPixels) {
        // 使用专门的像素单位验证
        if (isLayoutDebugEnabled()) {
            validatePixelParameters("setCenterYInPixels", availableHeightPixels, elementHeightPixels);
        }
        
        float offset = ZoomHelper.getCenterOffset(availableHeightPixels, elementHeightPixels);
        ImGui.setCursorPosY(ImGui.getCursorPosY() + offset);
        
        if (isLayoutDebugEnabled()) {
            NodeCraft.LOGGER.debug("[Layout Debug] Node {}: setCenterYInPixels - availableHeight={:.1f}px, elementHeight={:.1f}px, offset={:.1f}px", 
                                 getId(), availableHeightPixels, elementHeightPixels, offset);
        }
    }

    /**
     * 添加垂直间距（仅接受像素单位）
     * 
     * @param spacingPixels 间距大小（像素单位）
     */
    protected final void addVerticalSpacingInPixels(float spacingPixels) {
        ImGui.dummy(0, spacingPixels);
        
        if (isLayoutDebugEnabled()) {
            NodeCraft.LOGGER.debug("[Layout Debug] Node {}: addVerticalSpacingInPixels - spacing={:.1f}px", 
                                 getId(), spacingPixels);
        }
    }

    /**
     * 添加水平间距（仅接受像素单位）
     * 
     * @param spacingPixels 间距大小（像素单位）
     */
    protected final void addHorizontalSpacingInPixels(float spacingPixels) {
        ImGui.dummy(spacingPixels, 0);
        
        if (isLayoutDebugEnabled()) {
            NodeCraft.LOGGER.debug("[Layout Debug] Node {}: addHorizontalSpacingInPixels - spacing={:.1f}px", 
                                 getId(), spacingPixels);
        }
    }

    /**
     * 设置项目宽度（仅接受像素单位）
     * 
     * @param widthPixels 宽度（像素单位）
     */
    protected final void setItemWidthInPixels(float widthPixels) {
        ImGui.pushItemWidth(widthPixels);
        
        if (isLayoutDebugEnabled()) {
            NodeCraft.LOGGER.debug("[Layout Debug] Node {}: setItemWidthInPixels - width={:.1f}px", 
                                 getId(), widthPixels);
        }
    }

    /**
     * 验证像素单位参数的合理性
     * 
     * @param methodName 方法名
     * @param size1 第一个尺寸参数
     * @param size2 第二个尺寸参数
     */
    private void validatePixelParameters(String methodName, float size1, float size2) {
        String context = String.format("Node %s: %s", getId(), methodName);
        
        // 基础负值检查
        if (size1 < 0) {
            NodeCraft.LOGGER.warn("[Layout Warning] {} - First parameter is negative: {:.1f}px", 
                                context, size1);
        }
        
        if (size2 < 0) {
            NodeCraft.LOGGER.warn("[Layout Warning] {} - Second parameter is negative: {:.1f}px", 
                                context, size2);
        }
        
        // 检查是否看起来像逻辑单位（对于像素方法来说这是错误的）
        if ((size1 > 0 && size1 < 5) || (size2 > 0 && size2 < 5)) {
            NodeCraft.LOGGER.warn("[Layout Warning] {} - Very small values detected: {:.1f}px, {:.1f}px. " +
                                "Are you sure these are pixel values? Consider using the non-pixel methods for logical units.", 
                                context, size1, size2);
        }
        
        // 检查是否过大（可能的错误）
        if (size1 > 5000 || size2 > 5000) {
            NodeCraft.LOGGER.warn("[Layout Warning] {} - Very large values detected: {:.1f}px, {:.1f}px. " +
                                "Please verify these pixel values are correct.", 
                                context, size1, size2);
        }
        
        // 布局合理性检查
        if (size2 > size1 * 2 && size1 > 0) {
            NodeCraft.LOGGER.warn("[Layout Warning] {} - Element size ({:.1f}px) is much larger than available size ({:.1f}px). " +
                                "This may cause layout issues.", 
                                context, size2, size1);
        }
    }

    protected final void addVerticalSpacing(float spacing, float zoom) {
        float responsiveSpacing = toPixels(spacing, zoom, ResponsiveElementType.SPACING);
        ImGui.dummy(0, responsiveSpacing);
        
        if (isLayoutDebugEnabled()) {
            NodeCraft.LOGGER.debug("[Layout Debug] Node {}: addVerticalSpacing - spacing={}, zoom={}, responsiveSpacing={}", 
                                 getId(), spacing, zoom, responsiveSpacing);
        }
    }

    protected final void addHorizontalSpacing(float spacing, float zoom) {
        float responsiveSpacing = toPixels(spacing, zoom, ResponsiveElementType.SPACING);
        ImGui.dummy(responsiveSpacing, 0);
        
        if (isLayoutDebugEnabled()) {
            NodeCraft.LOGGER.debug("[Layout Debug] Node {}: addHorizontalSpacing - spacing={}, zoom={}, responsiveSpacing={}", 
                                 getId(), spacing, zoom, responsiveSpacing);
        }
    }

    protected final void setItemWidth(float width, float zoom) {
        float responsiveWidth = toPixels(width, zoom, ResponsiveElementType.GENERIC);
        ImGui.pushItemWidth(responsiveWidth);
        
        if (isLayoutDebugEnabled()) {
            NodeCraft.LOGGER.debug("[Layout Debug] Node {}: setItemWidth - width={}, zoom={}, responsiveWidth={}", 
                                 getId(), width, zoom, responsiveWidth);
        }
    }

    protected final float getAvailableContentWidth(float totalWidth, float zoom) {
        // totalWidth 是逻辑单位，必须先转换为像素单位，再减去像素单位的边距
        float totalWidthPixels = toPixelsExact(totalWidth, zoom);
        float responsiveMargin = toPixels(getContentMargin() * 2, zoom, ResponsiveElementType.SPACING); // 左右两侧边距
        float availableWidth = Math.max(0, totalWidthPixels - responsiveMargin);
        
        if (isLayoutDebugEnabled()) {
            NodeCraft.LOGGER.debug("[Layout Debug] Node {}: getAvailableContentWidth - totalWidth={}, totalWidthPixels={}, margin={}, responsiveMargin={}, availableWidth={}", 
                                 getId(), totalWidth, totalWidthPixels, getContentMargin(), responsiveMargin, availableWidth);
        }
        
        return availableWidth;
    }

    protected final float getAvailableContentHeight(float totalHeight, float zoom) {
        // totalHeight 是逻辑单位，必须先转换为像素单位，再减去像素单位的边距
        float totalHeightPixels = toPixelsExact(totalHeight, zoom);
        float responsiveMargin = toPixels(getContentMargin() * 2, zoom, ResponsiveElementType.SPACING); // 上下两侧边距
        float availableHeight = Math.max(0, totalHeightPixels - responsiveMargin);
        
        if (isLayoutDebugEnabled()) {
            NodeCraft.LOGGER.debug("[Layout Debug] Node {}: getAvailableContentHeight - totalHeight={}, totalHeightPixels={}, margin={}, responsiveMargin={}, availableHeight={}", 
                                 getId(), totalHeight, totalHeightPixels, getContentMargin(), responsiveMargin, availableHeight);
        }
        
        return availableHeight;
    }

    /**
     * 验证布局参数的合理性和单位一致性
     * 
     * @param methodName 调用的方法名
     * @param availableSize 可用尺寸
     * @param elementSize 元素尺寸
     */
    private void validateLayoutParameters(String methodName, float availableSize, float elementSize) {
        String context = String.format("Node %s: %s", getId(), methodName);
        
        // 基础负值检查
        if (availableSize < 0) {
            NodeCraft.LOGGER.warn("[Layout Warning] {} - availableSize is negative: {}", 
                                context, availableSize);
        }
        
        if (elementSize < 0) {
            NodeCraft.LOGGER.warn("[Layout Warning] {} - elementSize is negative: {}", 
                                context, elementSize);
        }
        
        // 增强的单位一致性检查
        validateUnitConsistency(context, availableSize, elementSize);
        
        // 尺寸合理性检查（假设这些是像素单位，因为在渲染方法中调用）
        ZoomHelper.validateSizeRange(availableSize, true, context + " availableSize");
        ZoomHelper.validateSizeRange(elementSize, true, context + " elementSize");
        
        // 额外的布局合理性检查
        if (elementSize > availableSize * 2) {
            NodeCraft.LOGGER.warn("[Layout Warning] {} - elementSize ({}) is much larger than availableSize ({}). " +
                                "This may cause layout issues.", 
                                context, elementSize, availableSize);
        }
    }

    /**
     * 增强的单位一致性验证
     * 
     * @param context 上下文信息
     * @param availableSize 可用尺寸
     * @param elementSize 元素尺寸
     */
    private void validateUnitConsistency(String context, float availableSize, float elementSize) {
        // 使用 ZoomHelper 的基础单位一致性验证
        if (!ZoomHelper.validateUnitConsistency(availableSize, elementSize, context)) {
            NodeCraft.LOGGER.warn("[Layout Warning] {} - Basic unit mismatch detected. " +
                                "Ensure both availableSize and elementSize use the same unit (both pixels or both logical).", 
                                context);
        }
        
        // 增强的启发式单位不匹配检测
        // 检查是否一个值明显像像素（较大），另一个像逻辑单位（较小）
        if (availableSize > 100 && elementSize < 10 && elementSize > 0) {
            NodeCraft.LOGGER.warn("[Layout Warning] {} - Possible unit mismatch: availableSize={:.1f} (pixels?) vs elementSize={:.1f} (logical units?). " +
                                "Consider using setCenterXInPixels() or ensure both parameters use the same unit system.", 
                                context, availableSize, elementSize);
        } else if (elementSize > 100 && availableSize < 10 && availableSize > 0) {
            NodeCraft.LOGGER.warn("[Layout Warning] {} - Possible unit mismatch: elementSize={:.1f} (pixels?) vs availableSize={:.1f} (logical units?). " +
                                "Consider using setCenterXInPixels() or ensure both parameters use the same unit system.", 
                                context, elementSize, availableSize);
        }
        
        // 检查异常的比例关系
        if (availableSize > 0 && elementSize > 0) {
            float ratio = Math.max(availableSize, elementSize) / Math.min(availableSize, elementSize);
            if (ratio > 50) {
                NodeCraft.LOGGER.warn("[Layout Warning] {} - Extreme size ratio detected: {:.1f} vs {:.1f} (ratio: {:.1f}). " +
                                    "This strongly suggests a unit mismatch between pixels and logical units.", 
                                    context, availableSize, elementSize, ratio);
            }
        }
        
        // 检查是否使用了常见的错误模式
        if (isCommonUnitMismatchPattern(availableSize, elementSize)) {
            NodeCraft.LOGGER.warn("[Layout Warning] {} - Detected common unit mismatch pattern. " +
                                "Available size: {:.1f}, Element size: {:.1f}. " +
                                "Tip: Use explicit pixel methods like setCenterXInPixels() to avoid confusion.", 
                                context, availableSize, elementSize);
        }
    }

    /**
     * 检查是否匹配常见的单位不匹配模式
     * 
     * @param size1 第一个尺寸
     * @param size2 第二个尺寸
     * @return 如果匹配常见的错误模式返回true
     */
    private boolean isCommonUnitMismatchPattern(float size1, float size2) {
        // 常见错误：UI宽度（像素，如200-800）vs 逻辑元素宽度（如10-50）
        return (size1 >= 200 && size1 <= 2000 && size2 >= 5 && size2 <= 100) ||
               (size2 >= 200 && size2 <= 2000 && size1 >= 5 && size1 <= 100);
    }

    /**
     * 主动检查两个尺寸值的单位一致性
     * 
     * <p>此方法可以被子类调用以主动验证尺寸参数的单位一致性。
     * 相比自动验证，此方法提供更详细的分析结果。</p>
     * 
     * @param size1 第一个尺寸值
     * @param size2 第二个尺寸值
     * @param context 上下文描述（用于日志）
     * @return 单位一致性检查结果
     */
    protected final UnitConsistencyResult checkUnitConsistency(float size1, float size2, String context) {
        boolean hasNegative = size1 < 0 || size2 < 0;
        boolean hasZero = size1 == 0 || size2 == 0;
        boolean likelyMismatch = false;
        String suggestion = "";
        
        if (hasNegative) {
            suggestion = "检查负值参数";
        } else if (hasZero) {
            suggestion = "注意零值参数可能影响布局";
        } else {
            // 启发式单位检测
            if (size1 > 100 && size2 < 10) {
                likelyMismatch = true;
                suggestion = String.format("size1(%.1f)可能是像素，size2(%.1f)可能是逻辑单位", size1, size2);
            } else if (size2 > 100 && size1 < 10) {
                likelyMismatch = true;
                suggestion = String.format("size2(%.1f)可能是像素，size1(%.1f)可能是逻辑单位", size2, size1);
            } else if (isCommonUnitMismatchPattern(size1, size2)) {
                likelyMismatch = true;
                suggestion = "检测到常见的单位不匹配模式";
            } else {
                float ratio = Math.max(size1, size2) / Math.min(size1, size2);
                if (ratio > 50) {
                    likelyMismatch = true;
                    suggestion = String.format("极端尺寸比例(%.1f)强烈暗示单位不匹配", ratio);
                } else {
                    suggestion = "单位看起来一致";
                }
            }
        }
        
        return new UnitConsistencyResult(likelyMismatch, suggestion, context);
    }

    /**
     * 单位一致性检查结果
     */
    protected static final class UnitConsistencyResult {
        public final boolean likelyMismatch;
        public final String suggestion;
        public final String context;
        
        public UnitConsistencyResult(boolean likelyMismatch, String suggestion, String context) {
            this.likelyMismatch = likelyMismatch;
            this.suggestion = suggestion;
            this.context = context;
        }
        
        @Override
        public String toString() {
            return String.format("UnitCheck[%s: %s, mismatch=%s]", 
                               context, suggestion, likelyMismatch);
        }
        
        /**
         * 如果检测到问题，记录警告日志
         */
        public void logIfProblematic() {
            if (likelyMismatch) {
                NodeCraft.LOGGER.warn("[Unit Consistency] {}: {}", context, suggestion);
            }
        }
    }

    protected final void pushFramePadding(float paddingX, float paddingY, float zoom) {
        float responsivePaddingX = toPixels(paddingX, zoom, ResponsiveElementType.SPACING);
        float responsivePaddingY = toPixels(paddingY, zoom, ResponsiveElementType.SPACING);
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.FramePadding, responsivePaddingX, responsivePaddingY);
        
        if (isLayoutDebugEnabled()) {
            NodeCraft.LOGGER.debug("[Layout Debug] Node {}: pushFramePadding - original=({}, {}), responsive=({}, {})", 
                                 getId(), paddingX, paddingY, responsivePaddingX, responsivePaddingY);
        }
    }

    protected final void pushFrameRounding(float rounding, float zoom) {
        float responsiveRounding = toPixels(rounding, zoom, ResponsiveElementType.GENERIC);
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.FrameRounding, responsiveRounding);
        
        if (isLayoutDebugEnabled()) {
            NodeCraft.LOGGER.debug("[Layout Debug] Node {}: pushFrameRounding - original={}, responsive={}", 
                                 getId(), rounding, responsiveRounding);
        }
    }

    protected final void pushFramePaddingExact(float paddingX, float paddingY, float zoom) {
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.FramePadding,
                toPixelsExact(paddingX, zoom), toPixelsExact(paddingY, zoom));
    }

    protected final void pushFrameRoundingExact(float rounding, float zoom) {
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.FrameRounding, toPixelsExact(rounding, zoom));
    }

    protected final float toPixels(float logicalSize, float zoom) {
        // 直接使用精确的线性缩放，不再应用响应式最小值。
        // 响应式最小值（如 4.0f 固定像素）在小 zoom 时会使元素比例失调，
        // 导致内容总高度超出 calculateUIHeight() 的预期值，引起裁剪。
        // CustomUIRenderer 已经统一缩放了所有样式属性，各元素应严格按 zoom 等比缩放。
        float result = ZoomHelper.toScaledPixels(logicalSize, zoom);
        
        if (isLayoutDebugEnabled()) {
            NodeCraft.LOGGER.debug("[Layout Debug] Node {}: toPixels - logicalSize={}, zoom={}, result={}", 
                                 getId(), logicalSize, zoom, result);
        }
        
        return result;
    }

    protected final float toPixels(float logicalSize, float zoom, ResponsiveElementType elementType) {
        // 直接使用精确的线性缩放，与 toPixelsExact 行为一致。
        // 之前的响应式最小值在 zoom < 1.0 时会使 padding/spacing 膨胀，
        // 破坏与 calculateUIHeight()（线性预期）的一致性，导致UI元素被裁剪或位移。
        float result = ZoomHelper.toScaledPixels(logicalSize, zoom);
        
        if (isLayoutDebugEnabled()) {
            NodeCraft.LOGGER.debug("[Layout Debug] Node {}: toPixels (typed) - logicalSize={}, zoom={}, type={}, result={}", 
                                 getId(), logicalSize, zoom, elementType, result);
        }
        
        return result;
    }

    protected final float toPixelsExact(float logicalSize, float zoom) {
        float result = ZoomHelper.toScaledPixels(logicalSize, zoom);
        
        if (isLayoutDebugEnabled()) {
            NodeCraft.LOGGER.debug("[Layout Debug] Node {}: toPixelsExact - logicalSize={}, zoom={}, result={}", 
                                 getId(), logicalSize, zoom, result);
        }
        
        return result;
    }

    protected final float toLogical(float pixelSize, float zoom) {
        return ZoomHelper.toLogicalUnits(pixelSize, zoom);
    }

    protected enum ResponsiveElementType {
        BUTTON,   // 按钮类控件 - 注重可点击性
        TEXT,     // 文本类元素 - 注重可读性
        ICON,     // 图标类元素 - 注重可识别性
        SPACING,  // 间距类元素 - 注重视觉分离
        GENERIC   // 通用元素 - 使用标准策略
    }

    protected final ZoomHelper.LogicalSize logicalSize(float width, float height) {
        return ZoomHelper.logicalSize(width, height);
    }

    protected final ZoomHelper.PixelSize pixelSize(float width, float height) {
        return ZoomHelper.pixelSize(width, height);
    }

    protected final ZoomHelper.LogicalMargin logicalMargin(float margin) {
        return ZoomHelper.logicalMargin(margin);
    }

    protected final ZoomHelper.LogicalSize getLogicalSize() {
        return logicalSize(getMinRequiredUIWidth(), getCustomUIHeight());
    }

    protected final ZoomHelper.PixelSize getPixelSize(float zoom) {
        return pixelSize(toPixels(getMinRequiredUIWidth(), zoom), toPixels(getCustomUIHeight(), zoom));
    }

    protected final ZoomHelper.PixelSize getPixelSizeExact(float zoom) {
        return getLogicalSize().toPixels(zoom);
    }

    protected final ZoomHelper.LogicalMargin getContentMarginAsLogical() {
        return logicalMargin(getContentMargin());
    }

    protected final ZoomHelper.PixelMargin getContentMarginAsPixels(float zoom) {
        float responsiveMargin = toPixels(getContentMargin(), zoom, ResponsiveElementType.SPACING);
        return ZoomHelper.pixelMargin(responsiveMargin);
    }

    protected float getContentMargin() {
        return DEFAULT_CONTENT_MARGIN;
    }

    protected float getSmallPadding() {
        return DEFAULT_PADDING_SMALL;
    }

    protected float getMediumPadding() {
        return DEFAULT_PADDING_MEDIUM;
    }

    protected float getLargePadding() {
        return DEFAULT_PADDING_LARGE;
    }

    protected float getMinResponsiveSize() {
        return 4.0f; // 4像素是大多数UI元素的最小可交互尺寸
    }

    protected float getMinResponsiveWidth() {
        return 8.0f; // 稍大一些确保文本可读性
    }

    protected float getMinResponsiveHeight() {
        return 6.0f; // 确保可点击性
    }

    protected float getMinResponsiveFontSize() {
        return 8.0f; // 8像素是大多数情况下可读的最小字体
    }

    protected float getMinResponsiveSpacing() {
        return 2.0f; // 2像素提供基本的视觉分离
    }

    /**
     * 执行缓存一致性检查
     * 
     * @param valueName 值的名称（用于日志）
     * @param cachedValue 缓存的值
     * @param newValue 新计算的值
     * @param calculationMethod 计算方法名称
     */
    private void performCacheConsistencyCheck(String valueName, float cachedValue, float newValue, String calculationMethod) {
        // 只在调试模式下进行检查，且缓存有效且已初始化
        if (!isCacheDebugEnabled() || cacheInvalid || cachedValue < 0) {
            return;
        }
        
        float difference = Math.abs(cachedValue - newValue);
        if (difference > CACHE_CONSISTENCY_THRESHOLD) {
            String errorMessage = String.format(
                "[Cache Inconsistency] Node %s: %s changed from %.3f to %.3f (difference: %.3f) without cache invalidation. " +
                "This indicates a missing markDirty() call after modifying properties that affect UI size. " +
                "Calculation method: %s, Cache version: %d, Cache age: %dms",
                getId(), valueName, cachedValue, newValue, difference, calculationMethod, 
                cacheVersion, System.currentTimeMillis() - lastCacheUpdateTime
            );
            
            if (STRICT_CACHE_VALIDATION) {
                // 严格模式：抛出异常
                throw new IllegalStateException(errorMessage + 
                    "\n\nTo fix this issue:" +
                    "\n1. Find where you modify properties that affect UI size" +
                    "\n2. Add markDirty() call after the modification" +
                    "\n3. Or disable strict validation with -Ddebug.ui.cache.strict=false");
            } else {
                // 宽松模式：记录警告
                NodeCraft.LOGGER.warn(errorMessage);
                
                // 提供修复建议
                NodeCraft.LOGGER.warn("[Cache Fix Suggestion] Node {}: Check recent property modifications in your {} implementation. " +
                                    "Common causes: setText(), addElement(), setPadding(), setVisible(), etc. " +
                                    "Add markDirty() after such modifications.", getId(), getClass().getSimpleName());
            }
        }
    }

    protected final void invalidateCache() {
        if (isCacheDebugEnabled()) {
            NodeCraft.LOGGER.debug("[Cache Debug] Node {}: Cache invalidated (version was: {})", getId(), cacheVersion);
        }
        
        cacheInvalid = true;
        cachedUIHeight = -1;
        cachedMinWidth = -1;
        
        // 清除字体缩放缓存
        invalidateFontScaleCache();
        
        // 注意：不清除ImGui ID缓存，因为它通常不需要重新生成
        // 除非节点的UUID发生变化（这种情况很少见）
    }

    /**
     * 使字体缩放缓存失效
     * 
     * <p>当UI属性发生变化可能影响字体缩放计算时，应调用此方法。
     * 通常在 {@link #invalidateCache()} 中自动调用。</p>
     */
    protected final void invalidateFontScaleCache() {
        if (isLayoutDebugEnabled() && cachedFontScale > 0) {
            NodeCraft.LOGGER.debug("[Font Scale Debug] Node {}: Font scale cache invalidated (was: {:.3f} for zoom {:.3f})", 
                                 getId(), cachedFontScale, cachedFontScaleZoom);
        }
        
        cachedFontScale = -1;
        cachedFontScaleZoom = -1;
        fontScaleCacheTime = 0;
    }

    /**
     * 标记节点为脏状态，使缓存失效
     * 
     * <p><strong>重要：</strong>子类在修改任何可能影响UI尺寸的属性后必须调用此方法。
     * 这包括但不限于：</p>
     * <ul>
     *   <li>文本内容变化</li>
     *   <li>UI元素的添加或删除</li>
     *   <li>布局参数修改（间距、边距等）</li>
     *   <li>样式属性变化</li>
     *   <li>可见性状态变化</li>
     * </ul>
     * 
     * <p>如果忘记调用此方法，UI可能会显示不正确的尺寸，导致布局问题。
     * 启用缓存调试模式（{@code debug.ui.cache=true}）可以帮助检测这类问题。</p>
     */
    @Override
    public void markDirty() {
        super.markDirty();
        invalidateCache();
        
        if (isCacheDebugEnabled()) {
            NodeCraft.LOGGER.debug("[Cache Debug] Node {}: markDirty() called - cache invalidated", getId());
        }
    }

    /**
     * 强制刷新缓存
     * 
     * <p>此方法会立即失效当前缓存并重新计算UI尺寸。
     * 通常在批量修改属性后调用，以确保缓存是最新的。</p>
     * 
     * <p><strong>注意：</strong>这个方法会立即触发尺寸计算，可能会有性能开销。
     * 在性能敏感的场景中应谨慎使用。</p>
     */
    protected final void refreshCache() {
        if (isCacheDebugEnabled()) {
            NodeCraft.LOGGER.debug("[Cache Debug] Node {}: Force refreshing cache (old status: {})", 
                                 getId(), getCacheStatusInfo());
        }
        
        invalidateCache();
        
        // 立即重新计算以更新缓存
        float newHeight = getCustomUIHeight();
        float newWidth = getMinRequiredUIWidth();
        
        if (isCacheDebugEnabled()) {
            NodeCraft.LOGGER.debug("[Cache Debug] Node {}: Cache refreshed - height: {}, width: {}, new status: {}", 
                                 getId(), newHeight, newWidth, getCacheStatusInfo());
        }
    }

    protected final long getCacheVersion() {
        return cacheVersion;
    }

    protected final boolean isCacheValid() {
        return !cacheInvalid && cachedUIHeight >= 0 && cachedMinWidth >= 0;
    }

    /**
     * 获取缓存状态的详细信息，用于调试
     * 
     * @return 缓存状态描述
     */
    protected final String getCacheStatusInfo() {
        return String.format(
            "Cache[valid=%s, version=%d, height=%.2f, width=%.2f, age=%dms] %s",
            isCacheValid(), cacheVersion, cachedUIHeight, cachedMinWidth,
            System.currentTimeMillis() - lastCacheUpdateTime,
            getFontScaleCacheInfo()
        );
    }

    /**
     * 验证缓存状态并在检测到问题时记录警告
     * 这个方法可以在子类的关键操作前调用，确保缓存状态正确
     * 
     * @param operationName 操作名称，用于日志记录
     * @return 如果缓存状态正常返回true，否则返回false
     */
    protected final boolean validateCacheState(String operationName) {
        if (!isCacheDebugEnabled()) {
            return true; // 非调试模式下跳过验证
        }

        boolean isValid = isCacheValid();
        if (!isValid) {
            NodeCraft.LOGGER.debug("[Cache Validation] Node {}: Cache invalid during '{}' operation. {}", 
                                 getId(), operationName, getCacheStatusInfo());
        } else {
            // 检查缓存是否过旧（超过5秒）
            long cacheAge = System.currentTimeMillis() - lastCacheUpdateTime;
            if (cacheAge > 5000) {
                NodeCraft.LOGGER.debug("[Cache Validation] Node {}: Cache is old ({} ms) during '{}' operation. " +
                                     "Consider if this is expected.", 
                                     getId(), cacheAge, operationName);
            }
        }
        
        return isValid;
    }

    /**
     * 强制验证缓存一致性
     * 这个方法会重新计算UI尺寸并与缓存值比较，用于调试缓存问题
     * 
     * @return 如果缓存一致返回true，否则返回false
     */
    protected final boolean forceCacheConsistencyValidation() {
        if (!isCacheDebugEnabled() || !isCacheValid()) {
            return true; // 缓存无效时无需验证
        }

        // 临时保存当前缓存值
        float savedHeight = cachedUIHeight;
        float savedWidth = cachedMinWidth;
        boolean savedInvalid = cacheInvalid;
        long savedVersion = cacheVersion;

        try {
            // 强制重新计算
            cacheInvalid = true;
            float newHeight = calculateUIHeight();
            float newWidth = calculateMinUIWidth();

            // 检查一致性
            boolean heightConsistent = Math.abs(savedHeight - newHeight) <= CACHE_CONSISTENCY_THRESHOLD;
            boolean widthConsistent = Math.abs(savedWidth - newWidth) <= CACHE_CONSISTENCY_THRESHOLD;

            if (!heightConsistent || !widthConsistent) {
                NodeCraft.LOGGER.warn("[Force Cache Validation] Node {}: Inconsistency detected! " +
                                    "Height: cached=%.3f, calculated=%.3f (consistent=%s), " +
                                    "Width: cached=%.3f, calculated=%.3f (consistent=%s)",
                                    getId(), savedHeight, newHeight, heightConsistent,
                                    savedWidth, newWidth, widthConsistent);
                return false;
            }

            return true;
        } finally {
            // 恢复原始缓存状态
            cachedUIHeight = savedHeight;
            cachedMinWidth = savedWidth;
            cacheInvalid = savedInvalid;
            cacheVersion = savedVersion;
        }
    }

    protected final void drawDebugRect(float x, float y, float width, float height, int color) {
        if (isDebugMode()) {
            ImDrawList drawList = ImGui.getWindowDrawList();
            drawList.addRect(x, y, x + width, y + height, color, 0, 0, 1.0f);
        }
    }

    protected boolean isDebugMode() {
        return DebugManager.getInstance().isGeneralDebugEnabled();
    }

    protected boolean isCacheDebugEnabled() {
        return DebugManager.getInstance().isCacheDebugEnabled();
    }

    protected boolean isImGuiIdDebugEnabled() {
        return DebugManager.getInstance().isImGuiIdDebugEnabled();
    }

    protected boolean isLayoutDebugEnabled() {
        return DebugManager.getInstance().isLayoutDebugEnabled();
    }

    protected boolean isBoundsDebugEnabled() {
        return DebugManager.getInstance().isBoundsDebugEnabled();
    }

    protected boolean isDirectDrawDebugEnabled() {
        return DebugManager.getInstance().isDirectDrawDebugEnabled();
    }

    protected static void clearDebugModeCache() {
        DebugManager.getInstance().clearCache();
    }

    protected String getDebugModeStatus() {
        return DebugManager.getInstance().getDebugModeStatus();
    }

    protected boolean isDebugEnabledInProduction() {
        return DebugManager.getInstance().isDebugEnabledInProduction();
    }

    private String getImGuiId() {
        if (cachedImGuiId == null) {
            cachedImGuiId = generateUniqueImGuiId();
            if (isImGuiIdDebugEnabled()) {
                NodeCraft.LOGGER.debug("[ImGui ID Debug] Node {}: Generated string ID: {}", getId(), cachedImGuiId);
            }
        }
        return cachedImGuiId;
    }

    protected final void pushUniqueImGuiId() {
        try {
            String imguiId = getImGuiId();
            ImGui.pushID(imguiId);
            
            if (isImGuiIdDebugEnabled()) {
                NodeCraft.LOGGER.debug("[ImGui ID Debug] Node {}: Pushed string ID: {}", getId(), imguiId);
            }
        } catch (Exception e) {
            // 如果字符串ID失败，回退到数值ID
            if (isDebugMode()) {
                NodeCraft.LOGGER.warn("Failed to push string ImGui ID for node {}, falling back to numeric ID: {}", 
                                    getId(), e.getMessage());
            }
            pushNumericImGuiId();
        }
    }

    protected final void pushNumericImGuiId() {
        try {
            // 使用UUID的最低有效位，比hashCode()更可靠
            int numericId = (int) getId().getLeastSignificantBits();
            ImGui.pushID(numericId);
            
            if (isImGuiIdDebugEnabled()) {
                NodeCraft.LOGGER.debug("[ImGui ID Debug] Node {}: Pushed numeric ID (LSB): {}", getId(), numericId);
            }
        } catch (Exception e) {
            // 最后的回退方案：使用hashCode
            if (isDebugMode()) {
                NodeCraft.LOGGER.warn("Failed to push numeric ImGui ID for node {}, falling back to hashCode: {}", 
                                    getId(), e.getMessage());
            }
            int hashCodeId = getId().hashCode();
            ImGui.pushID(hashCodeId);
            
            if (isImGuiIdDebugEnabled()) {
                NodeCraft.LOGGER.debug("[ImGui ID Debug] Node {}: Pushed hashCode ID: {}", getId(), hashCodeId);
            }
        }
    }

    protected String generateUniqueImGuiId() {
        // 使用UUID的简化形式，去掉连字符以减少字符串长度
        // 这样既保证了唯一性，又避免了过长的字符串影响性能
        return getId().toString().replace("-", "");
    }

    protected final String getCurrentImGuiId() {
        return getImGuiId();
    }

    protected final void regenerateImGuiId() {
        cachedImGuiId = null;
        if (isImGuiIdDebugEnabled()) {
            NodeCraft.LOGGER.debug("[ImGui ID Debug] Node {}: ImGui ID cache cleared, will regenerate on next access", getId());
        }
    }
}