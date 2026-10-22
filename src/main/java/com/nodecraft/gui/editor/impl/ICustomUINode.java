package com.nodecraft.gui.editor.impl;

import imgui.ImDrawList;
import imgui.ImGui;

/**
 * 自定义UI节点接口 - 增强版本，支持ViewPort感知渲染
 *
 * <p>这个接口为节点提供了强大的自定义UI能力，包括：</p>
 * <ul>
 *   <li>缩放感知的UI渲染</li>
 *   <li>ViewPort感知的坐标系统</li>
 *   <li>防裁剪的内容渲染</li>
 *   <li>节点编辑器风格的相对坐标</li>
 * </ul>
 */
public interface ICustomUINode {

    /**
     * 检查节点是否有自定义UI
     * @return true 如果节点有自定义UI需要渲染
     */
    boolean hasCustomUI();

    /**
     * 获取自定义UI的高度（未缩放的逻辑单位）
     * @return 自定义UI的高度，单位为像素（逻辑单位）
     */
    float getCustomUIHeight();

    /**
     * 获取自定义UI的高度（考虑缩放因子）。
     * 此方法返回的是**缩放后的实际像素高度**。
     * @param zoom 当前的缩放因子
     * @return 自定义UI的高度，单位为像素（已缩放）
     */
    default float getCustomUIHeight(float zoom) {
        // 默认实现为将未缩放的高度乘以缩放因子
        return getCustomUIHeight() * zoom;
    }

    /**
     * 获取自定义UI的最小所需宽度（未缩放的逻辑单位）。
     * 如果节点需要特定的最小宽度来正确显示其自定义UI，应该实现此方法。
     * @return 自定义UI的最小宽度，单位为像素（逻辑单位），返回0表示使用默认宽度
     */
    default float getMinRequiredUIWidth() {
        return 0; // 默认不要求特定宽度
    }

    /**
     * 获取自定义UI的最小所需宽度（考虑缩放因子）。
     * 此方法返回的是**缩放后的实际像素最小宽度**。
     * @param zoom 当前的缩放因子
     * @return 自定义UI的最小宽度，单位为像素（已缩放）
     */
    default float getMinRequiredUIWidth(float zoom) {
        // 默认实现为将未缩放的最小宽度乘以缩放因子
        return getMinRequiredUIWidth() * zoom;
    }

    /**
     * 检查是否支持直接绘制模式（避免ImGui子窗口开销）。
     * 如果返回 true，渲染器会尝试调用 {@link #renderCustomUIDirect(ImDrawList, float, float, float, float, float)}。
     * @return true 如果节点支持直接绘制模式
     */
    default boolean supportsDirectDrawing() {
        return false;
    }

    /**
     * 直接绘制模式渲染自定义UI（推荐用于性能优化）。
     * 使用 ImDrawList 直接绘制，避免 ImGui 子窗口的状态切换开销。
     *
     * ### 注意
     * - `screenX`, `screenY` 是绘制区域左上角**已缩放的屏幕坐标**。
     * - `width`, `height` 是绘制区域**已缩放的像素宽度和高度**。
     * - `zoom` 参数用于在内部**进一步**缩放 ImGui 控件的尺寸、间距等。
     *
     * @param drawList ImGui绘制列表
     * @param screenX 绘制区域左上角X坐标（已缩放像素）
     * @param screenY 绘制区域左上角Y坐标（已缩放像素）
     * @param width 可用宽度（已缩放像素）
     * @param height 可用高度（已缩放像素）
     * @param zoom 当前缩放因子
     * @return true 如果本帧数值发生了变化（legacy；优先覆写 {@link #renderCustomUIDirectInteraction}）
     */
    default boolean renderCustomUIDirect(ImDrawList drawList, float screenX, float screenY,
                                         float width, float height, float zoom) {
        // 默认实现：不支持直接绘制
        return false;
    }

    /**
     * Direct-draw interaction report: prefer this over the legacy boolean return.
     * Default maps {@link #renderCustomUIDirect} via {@link DirectDrawInteraction#fromLegacyChanged(boolean)}.
     */
    default DirectDrawInteraction renderCustomUIDirectInteraction(
            ImDrawList drawList, float screenX, float screenY,
            float width, float height, float zoom) {
        return DirectDrawInteraction.fromLegacyChanged(
                renderCustomUIDirect(drawList, screenX, screenY, width, height, zoom));
    }

    /**
     * 渲染自定义UI（推荐版本，使用ImGui子窗口）。
     *
     * ### 注意
     * - `width` 和 `height` 是可用的**已缩放像素宽度和高度**。
     * - `zoom` 参数用于在内部**进一步**缩放 ImGui 控件的尺寸、间距等。
     *
     * @param width 可用宽度（已缩放像素）
     * @param height 可用高度（已缩放像素）
     * @param zoom 当前缩放因子
     * @return true 如果用户与UI进行了交互
     */
    default boolean renderCustomUI(float width, float height, float zoom) {
        // 默认实现，子类应重写此方法或 BaseCustomUINode 的 renderCustomUIScaled
        // 这里的默认回退保留，但建议子类直接实现此方法或其基类实现
        return renderCustomUI(width, zoom); // 回退到双参数版本 (此行为可能不再需要，但为兼容性保留)
    }

    /**
     * 渲染自定义UI（向后兼容版本，仅用作回退）。
     *
     * ### 注意
     * - `width` 是可用的**已缩放像素宽度**。
     * - `zoom` 参数用于在内部**进一步**缩放 ImGui 控件的尺寸、间距等。
     *
     * @param width 可用宽度（已缩放像素）
     * @param zoom 当前缩放因子
     * @return true 如果用户与UI进行了交互
     */
    default boolean renderCustomUI(float width, float zoom) {
        // 默认回退到单参数版本 (此行为可能不再需要，但为兼容性保留)
        return renderCustomUI(width);
    }

    /**
     * 渲染自定义UI（最旧的向后兼容版本，仅用作回退）。
     *
     * ### 注意
     * - `width` 是可用的**已缩放像素宽度**。
     * - 强烈建议实现三参数或 {@link #renderCustomUIDirect(ImDrawList, float, float, float, float, float)} 方法。
     *
     * @param width 可用宽度（已缩放像素）
     * @return true 如果用户与UI进行了交互
     */
    default boolean renderCustomUI(float width) {
        // 默认实现：无自定义UI
        return false;
    }

    /**
     * 获取渲染时的ViewPort信息。
     *
     * @param canvasZoom 画布缩放因子
     * @param nodeScreenX 节点在屏幕坐标系中的X位置（已缩放像素）
     * @param nodeScreenY 节点在屏幕坐标系中的Y位置（已缩放像素）
     * @return ViewPort渲染信息
     */
    default ViewPortRenderInfo getViewPortRenderInfo(float canvasZoom, float nodeScreenX, float nodeScreenY) {
        return new ViewPortRenderInfo(canvasZoom, nodeScreenX, nodeScreenY);
    }

    /**
     * 使用ViewPort感知的渲染方式。
     * 这是解决UI元素裁剪问题的关键方法。
     *
     * ### 参数单位统一说明（重要更新）
     * 为了保持接口一致性，此方法现在接收**缩放后的像素值**，与其他渲染方法保持一致：
     * - `renderCustomUI(width, height, zoom)` - 接收缩放后像素值
     * - `renderCustomUIDirect(...)` - 接收缩放后像素值  
     * - `renderWithViewPortAwareness(...)` - **现在也接收缩放后像素值**
     * 
     * 这样可以避免在不同渲染方法间进行单位转换，减少混淆和错误。
     * 
     * ### 注意
     * - `renderInfo` 包含当前 ImGui 视口信息，其坐标通常是**屏幕像素**。
     * - `availableScaledWidth` 和 `availableScaledHeight` 是自定义UI的**已缩放像素宽度和高度**。
     *   可以直接用于 ImGui 渲染方法，无需再次缩放。
     *
     * @param renderInfo ViewPort渲染信息
     * @param availableScaledWidth 可用宽度（已缩放像素）
     * @param availableScaledHeight 可用高度（已缩放像素）
     * @return 是否有UI交互发生
     */
    default boolean renderWithViewPortAwareness(ViewPortRenderInfo renderInfo, float availableScaledWidth, float availableScaledHeight) {
        // 现在所有渲染方法都使用统一的缩放后像素值，无需进行单位转换
        // 直接调用标准的 renderCustomUI 方法
        return renderCustomUI(availableScaledWidth, availableScaledHeight, renderInfo.canvasZoom);
    }

    /**
     * 获取内容边界，用于防止裁剪。
     * 此方法应返回**已缩放的像素值**。
     *
     * @param zoom 当前缩放因子
     * @return 内容边界信息
     */
    default ContentBounds getContentBounds(float zoom) {
        // 默认返回的 minWidth/minHeight 已经是缩放后的
        return new ContentBounds(
                getMinRequiredUIWidth(zoom), // 调用 getMinRequiredUIWidth(zoom) 会返回缩放后的像素值
                getCustomUIHeight(zoom),     // 调用 getCustomUIHeight(zoom) 会返回缩放后的像素值
                0, 0, 0, 0 // 默认无额外溢出边距
        );
    }

    /**
     * ViewPort渲染信息类
     */
    class ViewPortRenderInfo {
        public final float canvasZoom;
        public final float nodeScreenX; // 节点在屏幕上的X位置（已缩放像素）
        public final float nodeScreenY; // 节点在屏幕上的Y位置（已缩放像素）

        // ImGui当前窗口的内容区域信息 (屏幕像素)
        public float windowContentRegionMinX;
        public float windowContentRegionMinY;
        public float windowContentRegionMaxX;
        public float windowContentRegionMaxY;

        // ImGui当前绘制列表的裁剪边界 (屏幕像素)
        public float clipRectMinX;
        public float clipRectMinY;
        public float clipRectMaxX;
        public float clipRectMaxY;

        // 是否需要特殊处理
        public boolean needsAntiClippingMode = false;

        public ViewPortRenderInfo(float canvasZoom, float nodeScreenX, float nodeScreenY) {
            this.canvasZoom = canvasZoom;
            this.nodeScreenX = nodeScreenX;
            this.nodeScreenY = nodeScreenY;
        }

        /**
         * 更新ImGui上下文信息。
         * 调用此方法时，ImGui必须处于正确的窗口上下文中。
         */
        public void updateImGuiContext() {
            // ImGui.getWindowContentRegionMin/Max() 返回的是当前窗口相对于其左上角内容的偏移量。
            // 为了得到屏幕坐标，需要加上 ImGui.getWindowPos()。
            // 但如果是在 Child Window 内部调用，它们已经是相对于 Child Window 的内容区域。
            // 这里假设它们是相对于当前 ImGui 窗口（子窗口）内部的内容区域。
            // 实际使用时，可能需要结合 ImGui.getWindowPos() 转换为绝对屏幕坐标。
            // For anti-clipping: these will be relative to the child window's top-left, so it's fine.
            windowContentRegionMinX = ImGui.getWindowContentRegionMinX();
            windowContentRegionMinY = ImGui.getWindowContentRegionMinY();
            windowContentRegionMaxX = ImGui.getWindowContentRegionMaxX();
            windowContentRegionMaxY = ImGui.getWindowContentRegionMaxY();

            // 获取当前裁剪矩形 (屏幕像素)
            imgui.ImVec2 clipMin = new imgui.ImVec2();
            imgui.ImVec2 clipMax = new imgui.ImVec2();
            ImGui.getWindowDrawList().getClipRectMin(clipMin);
            ImGui.getWindowDrawList().getClipRectMax(clipMax);

            clipRectMinX = clipMin.x;
            clipRectMinY = clipMin.y;
            clipRectMaxX = clipMax.x;
            clipRectMaxY = clipMax.y;
        }

        /**
         * 检查是否需要防裁剪模式。
         * @param requiredWidth UI内容的最小所需宽度（已缩放像素）
         * @param requiredHeight UI内容的最小所需高度（已缩放像素）
         * @return 如果UI内容超出了当前可用区域，返回true
         */
        public boolean shouldUseAntiClippingMode(float requiredWidth, float requiredHeight) {
            // 注意：windowContentRegionMax/MinX 是相对于窗口左上角的内容区域。
            // requiredWidth 和 requiredHeight 应该与这些值进行比较。
            float availableWidth = windowContentRegionMaxX - windowContentRegionMinX;
            float availableHeight = windowContentRegionMaxY - windowContentRegionMinY;

            // 如果所需的宽度或高度显著大于可用空间，则启用防裁剪模式
            needsAntiClippingMode = (requiredWidth > availableWidth * 1.05f) || // 增加一点容忍度 (5%)
                    (requiredHeight > availableHeight * 1.05f);

            return needsAntiClippingMode;
        }
    }

    /**
     * 内容边界信息类。
     * 用于描述自定义UI内容相对于其"分配"区域可能存在的溢出情况。
     * 所有尺寸和边距都应是**已缩放的像素值**。
     */
    class ContentBounds {
        public final float minWidth; // UI内容的最小宽度（已缩放像素），如果UI需要比节点分配的宽度更宽
        public final float minHeight; // UI内容的最小高度（已缩放像素），如果UI需要比节点分配的高度更高
        public final float marginLeft; // UI内容左侧溢出量（已缩放像素）
        public final float marginTop;  // UI内容顶部溢出量（已缩放像素）
        public final float marginRight; // UI内容右侧溢出量（已缩放像素） - 新增
        public final float marginBottom; // UI内容底部溢出量（已缩放像素） - 新增

        // 旧的构造函数，为了兼容性保留
        public ContentBounds(float minWidth, float minHeight, float marginLeft, float marginTop) {
            this(minWidth, minHeight, marginLeft, marginTop, 0, 0); // 默认右侧和底部不溢出
        }

        // 新的完整构造函数
        public ContentBounds(float minWidth, float minHeight,
                             float marginLeft, float marginTop,
                             float marginRight, float marginBottom) {
            this.minWidth = minWidth;
            this.minHeight = minHeight;
            this.marginLeft = marginLeft;
            this.marginTop = marginTop;
            this.marginRight = marginRight;
            this.marginBottom = marginBottom;
        }
    }
}