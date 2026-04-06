package com.nodecraft.nodesystem.registry;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.spi.INodeProvider;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;

/**
 * 统一的节点注册表。
 * 负责发现、管理和实例化所有可用的节点类型。
 * 支持通过 {@link INodeProvider} SPI 进行插件化节点注册。
 */
public class NodeRegistry {

    private static NodeRegistry instance;
    private static final Map<String, String> NODE_ID_ALIASES = createNodeIdAliases();
    private static final Map<String, String> NODE_CATEGORY_OVERRIDES = createNodeCategoryOverrides();

    private final Map<String, NodeInfo> nodeInfoMap = new HashMap<>();
    private final Map<String, NodeCategory> categoryMap = new HashMap<>();
    private boolean initialized = false;

    private NodeRegistry() {
        // 私有构造函数，确保单例
    }

    /**
     * 获取 NodeRegistry 的单例实例。
     *
     * @return NodeRegistry 实例。
     */
    public static synchronized NodeRegistry getInstance() {
        if (instance == null) {
            instance = new NodeRegistry();
        }
        return instance;
    }

    private static Map<String, String> createNodeIdAliases() {
        Map<String, String> aliases = new HashMap<>();
        aliases.put("spatial.points.offsetcoordinates", "spatial.points.offset_coordinates");
        aliases.put("spatial.points.rotatecoordinates", "spatial.points.rotate_coordinates");
        aliases.put("spatial.points.scalecoordinates", "spatial.points.scale_coordinates");
        aliases.put("spatial.points.mirrorcoordinates", "spatial.points.mirror_coordinates");
        aliases.put("spatial.points.randomizecoordinates", "spatial.points.randomize_coordinates");
        return Collections.unmodifiableMap(aliases);
    }

    private static Map<String, String> createNodeCategoryOverrides() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("spatial.generators.box_center_size", "spatial.construct");
        overrides.put("spatial.generators.box_corners", "spatial.construct");
        overrides.put("spatial.generators.box_corner_size", "spatial.construct");
        overrides.put("spatial.generators.sphere_by_center_radius", "spatial.construct");
        overrides.put("spatial.generators.sphere_by_diameter", "spatial.construct");
        overrides.put("spatial.generators.push_pull_box_face", "spatial.modeling");
        overrides.put("spatial.generators.extrude_box_face", "spatial.modeling");
        return Collections.unmodifiableMap(overrides);
    }

    private String normalizeNodeId(String nodeId) {
        if (nodeId == null) {
            return null;
        }
        String normalizedId = nodeId.toLowerCase();
        return NODE_ID_ALIASES.getOrDefault(normalizedId, normalizedId);
    }

    private String remapCategory(String normalizedNodeId, String categoryId) {
        String normalizedCategoryId = categoryId == null ? "" : categoryId.toLowerCase();
        String explicitOverride = NODE_CATEGORY_OVERRIDES.get(normalizedNodeId);
        if (explicitOverride != null) {
            return explicitOverride;
        }
        if ("spatial.generators".equals(normalizedCategoryId) && isLegacyDirectOutputNode(normalizedNodeId)) {
            return "spatial.legacy";
        }
        return normalizedCategoryId;
    }

    private boolean isLegacyDirectOutputNode(String normalizedNodeId) {
        return normalizedNodeId.endsWith("_blocks")
            || normalizedNodeId.endsWith("blocks")
            || normalizedNodeId.contains(".circle_sphere_blocks")
            || normalizedNodeId.contains(".line_blocks")
            || normalizedNodeId.contains(".polyline_blocks")
            || normalizedNodeId.contains(".curve_blocks");
    }

    private String remapDescription(String targetCategoryId, String description) {
        String safeDescription = description == null ? "" : description;
        if (!"spatial.legacy".equals(targetCategoryId)) {
            return safeDescription;
        }
        String prefix = "Legacy direct block output node. ";
        return safeDescription.startsWith(prefix) ? safeDescription : prefix + safeDescription;
    }

    /**
     * 初始化节点注册表。
     * 此方法会清空当前注册信息，并使用 {@link ServiceLoader} 重新加载所有 {@link INodeProvider}。
     * 应该在应用程序启动时调用一次。
     */
    public synchronized void initialize() {
        if (initialized) {
            NodeCraft.LOGGER.warn("NodeRegistry 已初始化，跳过重复初始化。如需重新加载，请先调用 clear()。");
            return;
        }

        NodeCraft.LOGGER.debug("开始初始化 NodeRegistry...");
        this.clearInternal(); // 确保在初始化前清空内部状态

        // 加载所有节点提供者
        ServiceLoader<INodeProvider> loader = ServiceLoader.load(INodeProvider.class);
        int providerCount = 0;
        int initialNodes; // 用于计算每个提供者注册的节点数量
        int initialCategories; // 用于计算每个提供者注册的分类数量

        for (INodeProvider provider : loader) {
            try {
                NodeCraft.LOGGER.debug("加载节点提供者: {}", provider.getClass().getName());
                initialNodes = nodeInfoMap.size(); // 记录调用前数量
                initialCategories = categoryMap.size(); // 记录调用前数量

                provider.registerNodes(this);
                providerCount++;

                NodeCraft.LOGGER.debug("提供者 {} 注册了 {} 个节点和 {} 个分类",
                        provider.getClass().getSimpleName(),
                        nodeInfoMap.size() - initialNodes,
                        categoryMap.size() - initialCategories);

            } catch (Exception e) {
                NodeCraft.LOGGER.error("加载节点提供者 {} 时出错: {}",
                        provider.getClass().getName(), e.getMessage(), e);
            }
        }

        if (providerCount == 0) {
            NodeCraft.LOGGER.error("警告：没有找到任何节点提供者！请检查 META-INF/services/com.nodecraft.nodesystem.spi.INodeProvider 文件。");
        }

        initialized = true;
        NodeCraft.LOGGER.info("NodeRegistry 初始化完成。加载了 {} 个节点提供者，共注册了 {} 个类别和 {} 个节点。",
                providerCount, categoryMap.size(), nodeInfoMap.size());
    }

    /**
     * 注册一个节点类别。
     * 如果类别ID已存在，则此操作无效。
     *
     * @param category 要注册的节点类别。
     * @return 如果成功注册或类别已存在，则返回 true；如果 category 为 null 或 categoryId 为 null，则返回 false。
     */
    public boolean registerCategory(NodeCategory category) {
        if (category == null || category.getId() == null) {
            NodeCraft.LOGGER.warn("尝试注册无效的节点类别 (null 或 null ID)。");
            return false;
        }

        // 标准化分类ID和显示名称
        String normalizedId = category.getId().toLowerCase();
        String displayName = category.getDisplayName();
        if (displayName == null || displayName.trim().isEmpty()) {
            displayName = formatCategoryName(normalizedId); // 使用新的格式化方法
        }

        // 检查是否为子分类，并自动创建父分类
        if (normalizedId.contains(".") && !normalizedId.endsWith(".")) {
            String parentId = normalizedId.substring(0, normalizedId.lastIndexOf('.'));

            // 递归注册父分类，确保层级关系
            // 避免无限循环，只注册直接父级，更上级由其父级的注册处理
            if (!categoryMap.containsKey(parentId)) {
                NodeCategory parentCategory = new NodeCategory(parentId, formatCategoryName(parentId));
                // 递归调用 registerCategory 确保父分类被完整处理
                registerCategory(parentCategory); // 确保父分类也经过标准化和日志
            }
        }

        // 检查是否已存在
        if (categoryMap.containsKey(normalizedId)) {
            NodeCraft.LOGGER.debug("节点类别 {} 已存在，无需重复注册。", normalizedId);
            return true;
        }

        // 创建新的分类对象
        NodeCategory newCategory = new NodeCategory(normalizedId, displayName);
        categoryMap.put(normalizedId, newCategory);
        NodeCraft.LOGGER.debug("注册节点类别: {} (ID: {})", displayName, normalizedId);
        return true;
    }

    /**
     * 注册一个节点。
     * 如果节点ID已存在，则会警告并且不进行覆盖。
     * 如果节点所属的类别不存在，会自动创建一个默认类别。
     *
     * @param nodeInfo 要注册的节点信息。
     * @return 如果成功注册，则返回 true；如果节点信息无效或已存在，则返回 false。
     */
    public boolean registerNode(NodeInfo nodeInfo) {
        if (nodeInfo == null || nodeInfo.getId() == null || nodeInfo.getNodeClass() == null) {
            NodeCraft.LOGGER.warn("尝试注册无效的节点信息 (null, null ID, 或 null NodeClass)。");
            return false;
        }

        // 标准化节点ID
        String normalizedId = nodeInfo.getId().toLowerCase();
        String categoryId = remapCategory(normalizedId, nodeInfo.getCategoryId());
        String description = remapDescription(categoryId, nodeInfo.getDescription());
        NodeInfo normalizedNodeInfo = new NodeInfo(
                normalizedId,
                nodeInfo.getDisplayName(),
                description,
                categoryId,
                nodeInfo.getNodeClass());
        normalizedNodeInfo.setIcon(nodeInfo.getIcon());

        // 检查是否已存在
        if (nodeInfoMap.containsKey(normalizedId)) {
            NodeCraft.LOGGER.warn("尝试重复注册节点 ID: {} (标题: {}). 跳过。",
                    normalizedId, nodeInfo.getDisplayName());
            return false;
        }

        // 注册节点
        nodeInfoMap.put(normalizedId, normalizedNodeInfo);

        // 确保分类存在 (调用 registerCategory 确保其父分类链也被处理)
        if (!categoryMap.containsKey(categoryId)) {
            // 如果分类不存在，则注册它，这将自动处理父分类链
            NodeCategory newCategoryForNode = new NodeCategory(categoryId, formatCategoryName(categoryId));
            registerCategory(newCategoryForNode); // 确保这个分类及其父类被正确注册
        }

        // 将节点添加到分类中
        NodeCategory category = categoryMap.get(categoryId);
        if (category != null) { // 确保分类确实存在（理论上应存在，因为上面已注册）
            category.addNode(normalizedNodeInfo);
            NodeCraft.LOGGER.debug("注册节点: {} (ID: {}) 到类别 [{}]",
                    normalizedNodeInfo.getDisplayName(), normalizedId, category.getDisplayName());
            return true;
        } else {
            NodeCraft.LOGGER.error("节点 {} (ID: {}) 注册失败：无法找到或创建分类 {}",
                    normalizedNodeInfo.getDisplayName(), normalizedId, categoryId);
            nodeInfoMap.remove(normalizedId); // 回滚注册
            return false;
        }
    }

    /**
     * 根据节点类型 ID 创建一个新的节点实例。
     *
     * @param nodeId 要创建实例的节点类型 ID。
     * @return 新创建的 {@link INode} 实例。
     * @throws IllegalArgumentException 如果节点 ID 未注册或节点信息缺少实现类。
     * @throws RuntimeException         如果实例化节点时发生错误 (如构造函数问题)。
     */
    public INode createNodeInstance(String nodeId) {
        String resolvedNodeId = normalizeNodeId(nodeId);
        NodeInfo nodeInfo = nodeInfoMap.get(resolvedNodeId);
        if (nodeInfo == null) {
            throw new IllegalArgumentException("未注册的节点类型 ID: " + nodeId);
        }

        Class<? extends INode> nodeClass = nodeInfo.getNodeClass();
        if (nodeClass == null) {
            // 此情况理论上不应发生，因为 registerNode 会检查 nodeClass
            throw new IllegalArgumentException("节点类型 '" + nodeId + "' (" + nodeInfo.getDisplayName() + ") 没有关联的实现类，无法实例化。");
        }

        try {
            return nodeClass.getDeclaredConstructor().newInstance();
        } catch (NoSuchMethodException e) {
            NodeCraft.LOGGER.error("节点类 {} 缺少无参构造函数。", nodeClass.getName(), e);
            throw new RuntimeException("节点类 " + nodeClass.getName() + " 缺少无参构造函数。", e);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            NodeCraft.LOGGER.error("实例化节点 {} ({}) 时出错。", nodeId, nodeClass.getName(), e);
            throw new RuntimeException("无法实例化节点 " + nodeId + ": " + e.getMessage(), e);
        } catch (Exception e) {
            NodeCraft.LOGGER.error("实例化节点 {} ({}) 时发生未知错误。", nodeId, nodeClass.getName(), e);
            throw new RuntimeException("实例化节点 " + nodeId + " 时发生未知错误: " + e.getMessage(), e);
        }
    }

    /**
     * 根据 ID 获取节点类别。
     *
     * @param categoryId 类别 ID。
     * @return {@link NodeCategory}，如果不存在则返回 null。
     */
    public NodeCategory getCategory(String categoryId) {
        return categoryMap.get(categoryId);
    }

    /**
     * 获取所有已注册的节点类别。
     * 列表按类别显示名称排序。
     *
     * @return 已排序的节点类别列表。
     */
    public List<NodeCategory> getAllCategories() {
        List<NodeCategory> sortedCategories = new ArrayList<>(categoryMap.values());
        // 按显示名称排序
        sortedCategories.sort((c1, c2) -> c1.getDisplayName().compareToIgnoreCase(c2.getDisplayName()));
        return Collections.unmodifiableList(sortedCategories); // 返回不可修改列表
    }

    /**
     * 获取所有已注册节点ID的列表。
     * @return 已注册节点ID的不可修改列表。
     */
    public List<String> getAllNodeIds() {
        return List.copyOf(nodeInfoMap.keySet());
    }

    /**
     * 根据节点ID获取节点信息。
     * @param nodeId 节点ID。
     * @return 对应的 NodeInfo 对象，如果不存在则返回 null。
     */
    public NodeInfo getNodeInfo(String nodeId) {
        return nodeInfoMap.get(normalizeNodeId(nodeId));
    }

    /**
     * 获取已注册的类别数量。
     * @return 类别数量。
     */
    public int getCategoryCount() {
        return categoryMap.size();
    }

    /**
     * 获取已注册的节点数量。
     * @return 节点数量。
     */
    public int getNodeCount() {
        return nodeInfoMap.size();
    }

    /**
     * 检查注册表是否已初始化。
     *
     * @return 如果已调用 {@link #initialize()} 并且未被 {@link #clear()}，则返回 true。
     */
    public boolean isInitialized() {
        return initialized;
    }

    private void clearInternal() {
        nodeInfoMap.clear();
        categoryMap.clear();
        // initialized 标志由 initialize 和 clear 控制
    }

    /**
     * 清空注册表并重置初始化状态。
     * 在重新加载配置或插件时可能有用。
     */
    public void clear() {
        clearInternal();
        initialized = false;
        NodeCraft.LOGGER.info("NodeRegistry 已清空并重置。");
    }

    /**
     * 代表一个节点类别，包含该类别下的多个节点。
     */
    public static class NodeCategory {
        private final String id;
        private final String displayName;
        private final List<NodeInfo> nodes = new ArrayList<>();

        public NodeCategory(String id, String displayName) {
            this.id = Objects.requireNonNull(id, "类别 ID 不能为空");
            this.displayName = Objects.requireNonNull(displayName, "类别显示名称不能为空");
        }

        public String getId() {
            return id;
        }

        public String getDisplayName() {
            return displayName;
        }

        void addNode(NodeInfo nodeInfo) {
            if (nodeInfo != null && !nodes.contains(nodeInfo)) {
                nodes.add(nodeInfo);
                // 按显示名称排序节点
                nodes.sort((n1, n2) -> n1.getDisplayName().compareToIgnoreCase(n2.getDisplayName()));
            }
        }

        public List<NodeInfo> getNodes() {
            return Collections.unmodifiableList(nodes);
        }

        public boolean isEmpty() {
            return nodes.isEmpty();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            NodeCategory that = (NodeCategory) o;
            return id.equals(that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

        @Override
        public String toString() {
            return "NodeCategory{id='" + id + "', displayName='" + displayName + "', nodeCount=" + nodes.size() + "}";
        }
    }

    /**
     * 将分类ID的一部分格式化为更好的显示名称。
     * 支持点分式ID，例如 "inputs.basic" -> "Inputs / Basic"。
     * @param categoryId 分类ID。
     * @return 格式化后的显示名称。
     */
    private String formatCategoryName(String categoryId) {
        if (categoryId == null || categoryId.isEmpty()) {
            return "General"; // 或其他默认值
        }
        String[] parts = categoryId.split("\\.");
        StringBuilder formatted = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (!formatted.isEmpty()) {
                    formatted.append(" / ");
                }
                // 首字母大写
                formatted.append(part.substring(0, 1).toUpperCase()).append(part.substring(1));
            }
        }
        return formatted.toString();
    }
}
