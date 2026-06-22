package com.nodecraft.nodesystem.preview;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.preview.elements.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 预览元素工厂接口
 */
@FunctionalInterface
interface PreviewElementFactory {
    AbstractPreviewElement create(String id, String ownerId, Object data, PreviewOptions options);
}

/**
 * NodeCraft 核心预览渲染器
 * 单例服务，负责注册、更新和渲染所有在游戏世界中显示的临时视觉元素
 * 
 * <h3>线程安全性</h3>
 * 本类设计为线程安全的，可以在多线程环境下安全使用：
 * <ul>
 *   <li>使用 {@link ConcurrentHashMap} 存储活跃元素和节点映射</li>
 *   <li>使用 {@link AtomicBoolean} 管理排序列表的脏标记</li>
 *   <li>使用 {@code volatile} 关键字确保配置字段的跨线程可见性</li>
 *   <li>使用 {@link CopyOnWriteArrayList} 存储按优先级分组的元素列表</li>
 *   <li>渲染方法通常在主线程（渲染线程）中调用</li>
 *   <li>预览管理方法可以在任意线程中安全调用</li>
 * </ul>
 * 
 * <h3>性能考虑</h3>
 * <ul>
 *   <li>排序列表采用延迟重建策略，只在需要时重建</li>
 *   <li>使用优化的重建算法减少内存分配和复制操作</li>
 *   <li>支持按优先级分组渲染以提高渲染效率</li>
 * </ul>
 */
public class PreviewRenderer {
    
    // 预览元素管理
    private final Map<String, AbstractPreviewElement> activeElements = new ConcurrentHashMap<>();
    private final Map<String, List<String>> nodeToPreviewsMap = new ConcurrentHashMap<>();
    
    // 按优先级分组的预览元素，用于提高渲染性能
    private final SortedMap<Integer, List<AbstractPreviewElement>> prioritizedElements = new ConcurrentSkipListMap<>();
    private final AtomicBoolean listDirty = new AtomicBoolean(true); // 使用AtomicBoolean确保线程安全
    
    // 预览元素类型注册表
    private final Map<String, PreviewElementFactory> elementRegistry = new ConcurrentHashMap<>();
    
    // 渲染配置 - 使用volatile确保跨线程可见性
    private volatile boolean globalPreviewEnabled = true;
    private volatile float globalOpacity = 1.0f;
    private volatile VertexConsumerProvider activeVertexConsumers;
    private final PreviewRenderSettings settings = new PreviewRenderSettings();

    // ID生成器 - 使用原子计数器确保线程安全的唯一ID生成
    private static final AtomicLong idCounter = new AtomicLong(0);
    
    private PreviewRenderer() {
        // 私有构造函数，单例模式
        initializeElementTypes();
    }
    
    /**
     * 初始化预览元素类型注册表
     */
    private void initializeElementTypes() {
        registerElementType("block_highlight", BlockHighlightElement::new);
        registerElementType("wireframe", BlockHighlightElement::new);
        registerElementType("ghost_block", GhostBlockElement::new);
        registerElementType("semi_transparent_block", GhostBlockElement::new);
        registerElementType("region_box", RegionBoxElement::new);
        registerElementType("spatial_shape", RegionBoxElement::new);
        registerElementType("plane_grid", PlaneGridElement::new);
        registerElementType("frame_axes", FrameAxesElement::new);
        registerElementType("points", PointsElement::new);
        registerElementType("vectors", VectorsElement::new);
        registerElementType("arrows", VectorsElement::new);
        registerElementType("lines", LinesElement::new);
        registerElementType("paths", LinesElement::new);
        registerElementType("geometry_surface", GeometrySurfaceElement::new);
        registerElementType("transformation_gizmo", TransformationGizmoElement::new);
        registerElementType("field_visualization", FieldVisualizationElement::new);
        registerElementType("text_labels", TextLabelsElement::new);
    }
    
    /**
     * 注册预览元素类型
     * @param type 类型标识符
     * @param factory 元素工厂
     */
    public void registerElementType(String type, PreviewElementFactory factory) {
        elementRegistry.put(type.toLowerCase(), factory);
    }
    
    /**
     * 单例持有者，利用JVM类加载机制保证线程安全
     */
    private static class SingletonHolder {
        private static final PreviewRenderer INSTANCE = new PreviewRenderer();
    }
    
    /**
     * 获取单例实例
     */
    public static PreviewRenderer getInstance() {
        return SingletonHolder.INSTANCE;
    }
    
    // ================= 核心 API =================
    
    /**
     * 注册并显示一个预览
     * @param ownerNodeId 拥有者节点ID
     * @param previewType 预览类型标识符
     * @param data 预览数据
     * @param options 预览选项
     * @return 唯一的预览ID
     */
    public String showPreview(String ownerNodeId, String previewType, Object data, PreviewOptions options) {
        try {
            NodeCraft.LOGGER.debug("PreviewRenderer.showPreview 调用: 节点={}, 类型={}, 数据={}, 全局预览启用={}",
                ownerNodeId, previewType, data != null ? data.getClass().getSimpleName() : "null", globalPreviewEnabled);

            if (!globalPreviewEnabled) {
                NodeCraft.LOGGER.warn("全局预览已禁用，跳过预览创建");
                return null;
            }

            String previewId = generatePreviewId(ownerNodeId, previewType);

            AbstractPreviewElement element = createPreviewElement(previewType, previewId, ownerNodeId, data, options);
            if (element != null) {
                enforceActivePreviewLimit();
                enforceMemoryBudget(null, element.estimateMemoryWeight());
                activeElements.put(previewId, element);

                // 维护节点到预览的映射，使用线程安全的CopyOnWriteArrayList
                nodeToPreviewsMap.computeIfAbsent(ownerNodeId, k -> new CopyOnWriteArrayList<>()).add(previewId);

                // 标记需要更新排序列表
                listDirty.set(true);

                NodeCraft.LOGGER.debug("预览元素创建成功: ID={}, 类型={}, 活跃元素数量={}",
                    previewId, element.getClass().getSimpleName(), activeElements.size());
                if ("ghost_block".equalsIgnoreCase(previewType) || "geometry_surface".equalsIgnoreCase(previewType)) {
                    NodeCraft.LOGGER.info("PreviewRenderer created preview: node={}, type={}, id={}, element={}, activeCount={}",
                        ownerNodeId, previewType, previewId, element.getClass().getSimpleName(), activeElements.size());
                }

                return previewId;
            } else {
                NodeCraft.LOGGER.error("无法创建预览元素，类型: {}, 检查元素注册表", previewType);
            }

            return null;
        } catch (Exception e) {
            NodeCraft.LOGGER.error("PreviewRenderer.showPreview 异常: 节点={}, 类型={}", ownerNodeId, previewType, e);
            return null;
        }
    }
    
    /**
     * 更新现有预览的数据或选项
     */
    public void updatePreview(String previewId, Object newData, PreviewOptions options) {
        AbstractPreviewElement element = activeElements.get(previewId);
        if (element != null) {
            int oldPriority = element.getRenderPriority();

            // Allow option-only updates without wiping existing element data.
            if (newData != null) {
                element.updateData(newData);
            }
            if (options != null) {
                element.updateOptions(options);
            }

            enforceMemoryBudget(previewId, element.estimateMemoryWeight());
            
            // 如果渲染优先级改变，需要更新排序列表
            if (oldPriority != element.getRenderPriority()) {
                listDirty.set(true);
            }
        }
    }
    
    /**
     * 隐藏并清除指定预览
     */
    public void hidePreview(String previewId) {
        AbstractPreviewElement element = activeElements.remove(previewId);
        if (element != null) {
            // 从节点映射中移除
            String nodeId = element.getOwnerNodeId();
            List<String> nodePreviews = nodeToPreviewsMap.get(nodeId);
            if (nodePreviews != null) {
                nodePreviews.remove(previewId);
                if (nodePreviews.isEmpty()) {
                    nodeToPreviewsMap.remove(nodeId);
                }
            }
            
            element.cleanup();
            
            // 标记需要更新排序列表
            listDirty.set(true);
        }
    }
    
    /**
     * 隐藏并清除某个节点生成的所有预览
     */
    public void hidePreviewsByNode(String ownerNodeId) {
        List<String> previews = nodeToPreviewsMap.remove(ownerNodeId);
        if (previews != null) {
            for (String previewId : previews) {
                AbstractPreviewElement element = activeElements.remove(previewId);
                if (element != null) {
                    element.cleanup();
                }
            }
            
            // 标记需要更新排序列表
            listDirty.set(true);
        }
    }

    /**
     * Hide previews for a node but only for a given preview type.
     */
    public void hidePreviewsByNodeAndType(String ownerNodeId, String previewType) {
        if (ownerNodeId == null || ownerNodeId.isEmpty() || previewType == null || previewType.isEmpty()) {
            return;
        }
        List<String> previews = nodeToPreviewsMap.get(ownerNodeId);
        if (previews == null || previews.isEmpty()) {
            return;
        }

        List<String> removed = new ArrayList<>();
        for (String previewId : previews) {
            if (!isPreviewIdOfType(previewId, ownerNodeId, previewType)) {
                continue;
            }
            AbstractPreviewElement element = activeElements.remove(previewId);
            if (element != null) {
                element.cleanup();
            }
            removed.add(previewId);
        }

        if (!removed.isEmpty()) {
            previews.removeAll(removed);
            if (previews.isEmpty()) {
                nodeToPreviewsMap.remove(ownerNodeId);
            }
            listDirty.set(true);
        }
    }

    /**
     * Find active previews for a node and preview type.
     */
    public List<String> getPreviewIdsByNodeAndType(String ownerNodeId, String previewType) {
        if (ownerNodeId == null || ownerNodeId.isEmpty() || previewType == null || previewType.isEmpty()) {
            return List.of();
        }
        List<String> previews = nodeToPreviewsMap.get(ownerNodeId);
        if (previews == null || previews.isEmpty()) {
            return List.of();
        }
        List<String> filtered = new ArrayList<>();
        for (String previewId : previews) {
            if (!isPreviewIdOfType(previewId, ownerNodeId, previewType)) {
                continue;
            }
            if (activeElements.containsKey(previewId)) {
                filtered.add(previewId);
            }
        }
        return filtered;
    }

    /**
     * Update existing preview of same node/type if present, otherwise create one.
     */
    public String upsertPreview(String ownerNodeId, String previewType, Object data, PreviewOptions options) {
        List<String> existing = getPreviewIdsByNodeAndType(ownerNodeId, previewType);
        if (!existing.isEmpty()) {
            String previewId = existing.getFirst();
            updatePreview(previewId, data, options);
            // If duplicates exist for same node/type, remove extras to keep render stable.
            for (int i = 1; i < existing.size(); i++) {
                hidePreview(existing.get(i));
            }
            return previewId;
        }
        return showPreview(ownerNodeId, previewType, data, options);
    }

    /**
     * Upsert a batch of previews for same node/type: reuse existing IDs by index, create missing, remove extras.
     */
    public List<String> upsertPreviews(String ownerNodeId, String previewType, List<?> dataList, PreviewOptions options) {
        if (dataList == null || dataList.isEmpty()) {
            hidePreviewsByNodeAndType(ownerNodeId, previewType);
            return List.of();
        }

        List<String> existing = getPreviewIdsByNodeAndType(ownerNodeId, previewType);
        List<String> result = new ArrayList<>(dataList.size());

        int i = 0;
        for (Object data : dataList) {
            if (data == null) {
                continue;
            }
            if (i < existing.size()) {
                String previewId = existing.get(i);
                updatePreview(previewId, data, options);
                result.add(previewId);
            } else {
                String previewId = showPreview(ownerNodeId, previewType, data, options);
                if (previewId != null) {
                    result.add(previewId);
                }
            }
            i++;
        }

        for (int j = i; j < existing.size(); j++) {
            hidePreview(existing.get(j));
        }

        return result;
    }
    
    /**
     * 清除所有活跃预览
     */
    public void clearAllPreviews() {
        for (AbstractPreviewElement element : activeElements.values()) {
            element.cleanup();
        }
        activeElements.clear();
        nodeToPreviewsMap.clear();
        
        // 清空并标记排序列表
        prioritizedElements.clear();
        listDirty.set(false); // 已经清空了，不需要重建
    }
    
    /**
     * 检查某个节点是否有活跃预览
     */
    public int getActivePreviewCount() {
        return activeElements.size();
    }

    /**
     * 在 Minecraft 的世界渲染事件中被调用，绘制所有注册的预览
     */
    public void renderAll(MatrixStack matrices, Camera camera, float partialTicks) {
        if (!globalPreviewEnabled || activeElements.isEmpty()) {
            return;
        }

        purgeExpiredPreviews();
        if (activeElements.isEmpty()) {
            return;
        }
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            return;
        }
        
        // 设置渲染状态
        setupRenderState();
        
        try {
            // 分层渲染不同类型的预览元素
            renderElementsByType(matrices, camera, partialTicks);
        } finally {
            // 恢复渲染状态
            restoreRenderState();
        }
    }

    public void setActiveVertexConsumers(VertexConsumerProvider consumers) {
        this.activeVertexConsumers = consumers;
    }

    public VertexConsumerProvider getActiveVertexConsumers() {
        return activeVertexConsumers;
    }
    
    // ================= 预览元素创建 =================
    
    private AbstractPreviewElement createPreviewElement(String type, String id, String ownerId, Object data, PreviewOptions options) {
        try {
            String lowerType = type.toLowerCase();
            
            // 检查注册表是否为空（只在错误情况下记录）
            if (elementRegistry.isEmpty()) {
                NodeCraft.LOGGER.error("预览元素注册表为空！这可能是初始化问题");
                return null;
            }

            PreviewElementFactory factory = elementRegistry.get(lowerType);
            if (factory != null) {
                AbstractPreviewElement element = factory.create(id, ownerId, data, options);
                if (element == null) {
                    NodeCraft.LOGGER.error("工厂返回null元素: 类型={}", type);
                    return null;
                }

                // Avoid virtual method dispatch from base constructor: initialize element data after construction.
                element.updateData(data);
                return element;
            } else {
                NodeCraft.LOGGER.error("未找到预览元素工厂: 类型={}, 可用类型={}", lowerType, elementRegistry.keySet());
                return null;
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.error("创建预览元素时异常: 类型={}, ID={}", type, id, e);
            return null;
        }
    }
    
    // ================= 渲染管理 =================
    
    private void setupRenderState() {
        // 1.21.11 渲染状态由管线管理，避免调用已移除的全局状态方法
    }
    
    private void restoreRenderState() {
        // 1.21.11 渲染状态由管线管理，避免调用已移除的全局状态方法
    }
    
    private void renderElementsByType(MatrixStack matrices, Camera camera, float partialTicks) {
        // 使用 getAndSet 原子性地获取当前值并设为 false
        if (listDirty.getAndSet(false)) {
            // 如果重建期间有并发更新再次将 listDirty 置为 true，继续重建直到稳定。
            do {
                rebuildSortedListOptimized();
            } while (listDirty.getAndSet(false));
        }

        // 直接按优先级顺序渲染元素
        int renderedThisFrame = 0;
        int renderBudget = settings.maxElementsPerFrame;
        outer:
        for (List<AbstractPreviewElement> priorityGroup : prioritizedElements.values()) {
            for (AbstractPreviewElement element : priorityGroup) {
                if (renderedThisFrame >= renderBudget) {
                    break outer;
                }
                // 避免并发删除后旧快照元素被渲染（可能已执行 cleanup）。
                if (activeElements.get(element.getId()) != element) {
                    continue;
                }
                if (element.isVisible() && element.shouldRender(camera)) {
                    try {
                        element.render(matrices, camera, partialTicks, globalOpacity);
                        renderedThisFrame++;
                    } catch (Exception e) {
                        NodeCraft.LOGGER.error("Error rendering preview element " + element.getId() + ": " + e.getMessage(), e);
                    }
                }
            }
        }
    }

    /**
     * Removes previews whose {@code duration} option has elapsed since the last update.
     *
     * @return number of previews removed
     */
    public int purgeExpiredPreviews() {
        List<String> expired = new ArrayList<>();
        for (Map.Entry<String, AbstractPreviewElement> entry : activeElements.entrySet()) {
            if (entry.getValue().hasExpired()) {
                expired.add(entry.getKey());
            }
        }
        for (String previewId : expired) {
            hidePreview(previewId);
        }
        return expired.size();
    }

    private void enforceActivePreviewLimit() {
        int limit = settings.maxActivePreviews;
        while (activeElements.size() >= limit) {
            String oldestId = findOldestPreviewId(null);
            if (oldestId == null) {
                break;
            }
            hidePreview(oldestId);
            NodeCraft.LOGGER.warn(
                    "Active preview limit {} reached; evicted least-recently-updated preview (remaining={})",
                    limit,
                    activeElements.size()
            );
        }
    }

    private void enforceMemoryBudget(String excludePreviewId, int reservedWeight) {
        long limit = settings.maxPreviewMemoryWeight;
        if (limit <= 0L) {
            return;
        }
        long totalWeight = reservedWeight;
        for (Map.Entry<String, AbstractPreviewElement> entry : activeElements.entrySet()) {
            if (Objects.equals(entry.getKey(), excludePreviewId)) {
                continue;
            }
            totalWeight += entry.getValue().estimateMemoryWeight();
        }
        while (totalWeight > limit && !activeElements.isEmpty()) {
            String oldestId = findOldestPreviewId(excludePreviewId);
            if (oldestId == null) {
                break;
            }
            AbstractPreviewElement evicted = activeElements.get(oldestId);
            if (evicted == null) {
                break;
            }
            totalWeight -= evicted.estimateMemoryWeight();
            hidePreview(oldestId);
            NodeCraft.LOGGER.warn(
                    "Preview memory budget {} exceeded; evicted least-recently-updated preview (remainingWeight={}, active={})",
                    limit,
                    totalWeight,
                    activeElements.size()
            );
        }
    }

    private String findOldestPreviewId(String excludePreviewId) {
        String oldestId = null;
        long oldestTime = Long.MAX_VALUE;
        for (Map.Entry<String, AbstractPreviewElement> entry : activeElements.entrySet()) {
            if (Objects.equals(entry.getKey(), excludePreviewId)) {
                continue;
            }
            long updatedAt = entry.getValue().getLastUpdatedTime();
            if (updatedAt < oldestTime) {
                oldestTime = updatedAt;
                oldestId = entry.getKey();
            }
        }
        return oldestId;
    }
    
    /**
     * 优化的重建按优先级排序的元素列表
     * 这个版本只从 activeElements 读取一次，性能更好
     */
    private void rebuildSortedListOptimized() {
        // 创建临时映射来收集元素
        Map<Integer, List<AbstractPreviewElement>> newPrioritizedElements = new HashMap<>();
        for (AbstractPreviewElement element : activeElements.values()) {
            newPrioritizedElements.computeIfAbsent(element.getRenderPriority(), k -> new ArrayList<>()).add(element);
        }
        
        // 清理旧的
        prioritizedElements.clear();
        
        // 填充新的，并将 ArrayList 转换为 CopyOnWriteArrayList 以确保线程安全
        for (Map.Entry<Integer, List<AbstractPreviewElement>> entry : newPrioritizedElements.entrySet()) {
            prioritizedElements.put(entry.getKey(), new CopyOnWriteArrayList<>(entry.getValue()));
        }
    }
    
    /**
     * 重建按优先级排序的元素列表
     * @deprecated 使用 rebuildSortedListOptimized() 替代
     */
    @Deprecated
    private void rebuildSortedList() {
        prioritizedElements.clear();
        
        for (AbstractPreviewElement element : activeElements.values()) {
            int priority = element.getRenderPriority();
            prioritizedElements.computeIfAbsent(priority, k -> new CopyOnWriteArrayList<>()).add(element);
        }
    }
    
    // ================= 辅助方法 =================
    
    /**
     * 生成线程安全的唯一预览ID
     * 使用原子计数器和ThreadLocalRandom确保高性能和唯一性
     */
    private String generatePreviewId(String nodeId, String type) {
        long counter = idCounter.incrementAndGet();
        int randomSuffix = ThreadLocalRandom.current().nextInt(1000, 9999);
        return nodeId + "_" + type + "_" + counter + "_" + randomSuffix;
    }

    private boolean isPreviewIdOfType(String previewId, String nodeId, String previewType) {
        if (previewId == null || nodeId == null || previewType == null) {
            return false;
        }
        return previewId.startsWith(nodeId + "_" + previewType + "_");
    }
    
    // ================= 配置管理 =================
    
    public void setGlobalPreviewEnabled(boolean enabled) {
        this.globalPreviewEnabled = enabled;
    }
    
    public boolean isGlobalPreviewEnabled() {
        return globalPreviewEnabled;
    }
    
    public void setGlobalOpacity(float opacity) {
        this.globalOpacity = Math.max(0.0f, Math.min(1.0f, opacity));
    }
    
    public float getGlobalOpacity() {
        return globalOpacity;
    }
    
    public PreviewRenderSettings getSettings() {
        return settings;
    }
    
    // ================= 交互支持 =================
    
    /**
     * 处理鼠标拾取，用于 Gizmo 交互
     */
    public AbstractPreviewElement pickElement(Vec3d rayStart, Vec3d rayDirection, double maxDistance) {
        for (AbstractPreviewElement element : activeElements.values()) {
            if (element instanceof InteractivePreviewElement interactive) {
                if (interactive.intersectsRay(rayStart, rayDirection, maxDistance)) {
                    return element;
                }
            }
        }
        return null;
    }

    /**
     * 处理鼠标拖拽事件
     */
    public boolean handleMouseDrag(Vec3d rayStart, Vec3d rayDirection, double maxDistance, Vec3d deltaMovement) {
        // 实现拖拽逻辑，主要用于 Transformation Gizmo
        for (AbstractPreviewElement element : activeElements.values()) {
            if (element instanceof InteractivePreviewElement && ((InteractivePreviewElement) element).isBeingDragged()) {
                return ((InteractivePreviewElement) element).onMouseDrag(rayStart, rayDirection, deltaMovement);
            }
        }
        return false;
    }
    
    // ================= 调试和统计信息 =================
    
    public int getActiveElementCount() {
        return activeElements.size();
    }

    /**
     * Snapshot of currently active preview elements (for interaction routing).
     */
    public Collection<AbstractPreviewElement> getActiveElements() {
        return List.copyOf(activeElements.values());
    }

    public boolean hasActivePreview(String previewId) {
        if (previewId == null || previewId.isEmpty()) {
            return false;
        }
        return activeElements.containsKey(previewId);
    }
    
    public Map<String, Integer> getElementCountByType() {
        Map<String, Integer> counts = new ConcurrentHashMap<>();
        for (AbstractPreviewElement element : activeElements.values()) {
            String type = element.getClass().getSimpleName();
            counts.put(type, counts.getOrDefault(type, 0) + 1);
        }
        return counts;
    }
    
    /**
     * 渲染设置类
     * 使用volatile字段确保多线程环境下的可见性
     */
    public static class PreviewRenderSettings {
        // 基础渲染设置
        public volatile float defaultLineWidth = 2.0f;
        public volatile float defaultPointSize = 0.1f;
        private volatile Vector3f defaultColor = new Vector3f(0.2f, 0.7f, 1.0f); // 天蓝色
        public volatile float defaultOpacity = 0.7f;
        public volatile int maxRenderDistance = 256;
        
        // Gizmo 设置
        public volatile float gizmoSize = 1.0f;
        public volatile float gizmoInteractionRadius = 0.5f;
        
        // 性能设置
        public volatile int maxElementsPerFrame = 1000;
        public volatile int maxActivePreviews = 2048;
        /** Soft cap on summed {@link AbstractPreviewElement#estimateMemoryWeight()} across active previews. */
        public volatile long maxPreviewMemoryWeight = 500_000L;
        public volatile float lodDistance = 64.0f;
        
        /**
         * 线程安全地更新默认颜色
         * @param r 红色分量 (0.0-1.0)
         * @param g 绿色分量 (0.0-1.0)
         * @param b 蓝色分量 (0.0-1.0)
         */
        public synchronized void setDefaultColor(float r, float g, float b) {
            this.defaultColor = new Vector3f(
                Math.max(0.0f, Math.min(1.0f, r)),
                Math.max(0.0f, Math.min(1.0f, g)),
                Math.max(0.0f, Math.min(1.0f, b))
            );
        }
        
        /**
         * 线程安全地获取默认颜色的副本
         * @return 默认颜色的副本
         */
        public synchronized Vector3f getDefaultColor() {
            return new Vector3f(defaultColor);
        }
        
        /**
         * 设置默认透明度，自动限制在有效范围内
         * @param opacity 透明度值 (0.0-1.0)
         */
        public void setDefaultOpacity(float opacity) {
            this.defaultOpacity = Math.max(0.0f, Math.min(1.0f, opacity));
        }
        
        /**
         * 设置默认线宽，自动限制在有效范围内
         * @param lineWidth 线宽值 (>0)
         */
        public void setDefaultLineWidth(float lineWidth) {
            this.defaultLineWidth = Math.max(0.1f, lineWidth);
        }
        
        /**
         * 设置默认点大小，自动限制在有效范围内
         * @param pointSize 点大小值 (>0)
         */
        public void setDefaultPointSize(float pointSize) {
            this.defaultPointSize = Math.max(0.01f, pointSize);
        }
        
        /**
         * 设置最大渲染距离，自动限制在有效范围内
         * @param distance 渲染距离 (>0)
         */
        public void setMaxRenderDistance(int distance) {
            this.maxRenderDistance = Math.max(1, distance);
        }
        
        /**
         * 设置每帧最大元素数量，自动限制在有效范围内
         * @param maxElements 最大元素数量 (>0)
         */
        public void setMaxElementsPerFrame(int maxElements) {
            this.maxElementsPerFrame = Math.max(1, maxElements);
        }

        /**
         * Sets the maximum number of concurrently registered preview elements.
         * @param maxPreviews maximum active previews (&gt;0)
         */
        public void setMaxActivePreviews(int maxPreviews) {
            this.maxActivePreviews = Math.max(1, maxPreviews);
        }

        /**
         * Sets the soft memory budget for active preview payloads.
         * @param maxWeight summed weight units (&gt;0); use 0 to disable enforcement
         */
        public void setMaxPreviewMemoryWeight(long maxWeight) {
            this.maxPreviewMemoryWeight = Math.max(0L, maxWeight);
        }
        
        /**
         * 设置LOD距离，自动限制在有效范围内
         * @param distance LOD距离 (>0)
         */
        public void setLodDistance(float distance) {
            this.lodDistance = Math.max(1.0f, distance);
        }
        
        /**
         * 设置Gizmo大小，自动限制在有效范围内
         * @param size Gizmo大小 (>0)
         */
        public void setGizmoSize(float size) {
            this.gizmoSize = Math.max(0.1f, size);
        }
        
        /**
         * 设置Gizmo交互半径，自动限制在有效范围内
         * @param radius 交互半径 (>0)
         */
        public void setGizmoInteractionRadius(float radius) {
            this.gizmoInteractionRadius = Math.max(0.1f, radius);
        }
    }
} 
