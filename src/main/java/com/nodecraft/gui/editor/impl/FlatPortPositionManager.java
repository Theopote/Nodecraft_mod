package com.nodecraft.gui.editor.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import imgui.ImVec2;
import org.jspecify.annotations.NonNull;

/**
 * 扁平化端口位置管理器
 * 使用高效的数据结构替代嵌套Map，提升查找和更新性能
 */
public class FlatPortPositionManager {

    /**
         * 端口位置数据结构
         * 使用扁平化设计，避免嵌套Map的开销
         */
        public record PortPosition(UUID nodeId, String portId, float x, float y, long frameCreated) {

        public ImVec2 toImVec2() {
                return new ImVec2(x, y);
            }

        @Override
            public boolean equals(Object obj) {
                if (this == obj) return true;
                if (obj == null || getClass() != obj.getClass()) return false;
                PortPosition that = (PortPosition) obj;
                return nodeId.equals(that.nodeId) && portId.equals(that.portId);
            }

        @Override
            public int hashCode() {
                return nodeId.hashCode() * 31 + portId.hashCode();
            }

        @Override
            public @NonNull String toString() {
                return String.format("PortPosition{node=%s, port=%s, pos=(%.1f,%.1f)}",
                        nodeId.toString().substring(0, 8), portId, x, y);
            }
        }
    
    /**
     * 复合键，用于快速查找端口位置
     */
    private static class PortKey {
        final UUID nodeId;
        final String portId;
        final int hashCode;
        
        PortKey(UUID nodeId, String portId) {
            this.nodeId = nodeId;
            this.portId = portId;
            this.hashCode = nodeId.hashCode() * 31 + portId.hashCode();
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            PortKey that = (PortKey) obj;
            return nodeId.equals(that.nodeId) && portId.equals(that.portId);
        }
        
        @Override
        public int hashCode() {
            return hashCode;
        }
    }
    
    /**
     * 节点端口集合，用于快速获取节点的所有端口
     */
    private static class NodePortCollection {
        final UUID nodeId;
        final List<PortPosition> ports;
        final Map<String, PortPosition> portMap;
        long lastUpdateFrame;
        
        NodePortCollection(UUID nodeId) {
            this.nodeId = nodeId;
            this.ports = new ArrayList<>();
            this.portMap = new HashMap<>();
            this.lastUpdateFrame = 0;
        }
        
        void clear() {
            ports.clear();
            portMap.clear();
        }
        
        void addPort(PortPosition port) {
            ports.add(port);
            portMap.put(port.portId, port);
        }
        
        PortPosition getPort(String portId) {
            return portMap.get(portId);
        }
        
        boolean isEmpty() {
            return ports.isEmpty();
        }
        
        int size() {
            return ports.size();
        }
        
        /**
         * 转换为传统的Map格式（用于兼容性）
         */
        Map<String, ImVec2> toTraditionalMap() {
            Map<String, ImVec2> result = new HashMap<>(portMap.size());
            for (PortPosition port : ports) {
                result.put(port.portId, port.toImVec2());
            }
            return result;
        }
    }
    
    // 主要数据存储
    private final Map<PortKey, PortPosition> portPositions = new ConcurrentHashMap<>();
    private final Map<UUID, NodePortCollection> nodeCollections = new ConcurrentHashMap<>();
    
    // 性能统计
    private long currentFrame = 0;
    private int totalLookups = 0;
    private int cacheHits = 0;
    private int totalUpdates = 0;
    
    // 缓存清理配置
    private static final int MAX_CACHE_AGE_FRAMES = 300; // 5秒@60fps
    private static final int CLEANUP_INTERVAL_FRAMES = 60; // 每秒清理一次
    private int framesSinceLastCleanup = 0;
    
    /**
     * 更新帧计数器
     */
    public void nextFrame() {
        currentFrame++;
        framesSinceLastCleanup++;
        
        if (framesSinceLastCleanup >= CLEANUP_INTERVAL_FRAMES) {
            cleanupOldEntries();
            framesSinceLastCleanup = 0;
        }
    }
    
    /**
     * 设置端口位置
     */
    public void setPortPosition(UUID nodeId, String portId, float x, float y) {
        PortKey key = new PortKey(nodeId, portId);
        PortPosition position = new PortPosition(nodeId, portId, x, y, currentFrame);
        
        // 更新主索引
        portPositions.put(key, position);
        
        // 更新节点集合
        NodePortCollection collection = nodeCollections.computeIfAbsent(nodeId, NodePortCollection::new);
        collection.portMap.put(portId, position);
        
        // 如果是新端口，添加到列表中
        boolean found = false;
        for (int i = 0; i < collection.ports.size(); i++) {
            if (collection.ports.get(i).portId.equals(portId)) {
                collection.ports.set(i, position);
                found = true;
                break;
            }
        }
        if (!found) {
            collection.ports.add(position);
        }
        
        collection.lastUpdateFrame = currentFrame;
        totalUpdates++;
    }
    
    /**
     * 获取端口位置
     */
    public ImVec2 getPortPosition(UUID nodeId, String portId) {
        totalLookups++;
        PortKey key = new PortKey(nodeId, portId);
        PortPosition position = portPositions.get(key);
        
        if (position != null) {
            cacheHits++;
            return position.toImVec2();
        }
        return null;
    }
    
    /**
     * 获取端口位置对象（避免创建ImVec2）
     */
    public PortPosition getPortPositionDirect(UUID nodeId, String portId) {
        totalLookups++;
        PortKey key = new PortKey(nodeId, portId);
        PortPosition position = portPositions.get(key);
        
        if (position != null) {
            cacheHits++;
        }
        return position;
    }
    
    /**
     * 获取节点的所有端口位置
     */
    public List<PortPosition> getNodePortPositions(UUID nodeId) {
        NodePortCollection collection = nodeCollections.get(nodeId);
        return collection != null ? new ArrayList<>(collection.ports) : new ArrayList<>();
    }
    
    /**
     * 获取节点的所有端口位置（传统Map格式，用于兼容性）
     */
    public Map<String, ImVec2> getNodePortPositionsAsMap(UUID nodeId) {
        NodePortCollection collection = nodeCollections.get(nodeId);
        return collection != null ? collection.toTraditionalMap() : new HashMap<>();
    }
    
    /**
     * 批量设置节点的端口位置
     */
    public void setNodePortPositions(UUID nodeId, List<PortPosition> positions) {
        NodePortCollection collection = nodeCollections.computeIfAbsent(nodeId, NodePortCollection::new);
        collection.clear();
        
        for (PortPosition position : positions) {
            PortKey key = new PortKey(nodeId, position.portId);
            portPositions.put(key, position);
            collection.addPort(position);
        }
        
        collection.lastUpdateFrame = currentFrame;
        totalUpdates += positions.size();
    }
    
    /**
     * 清除节点的所有端口位置
     */
    public void clearNodePortPositions(UUID nodeId) {
        NodePortCollection collection = nodeCollections.remove(nodeId);
        if (collection != null) {
            for (PortPosition port : collection.ports) {
                PortKey key = new PortKey(nodeId, port.portId);
                portPositions.remove(key);
            }
        }
    }
    
    /**
     * 检查节点是否有端口位置数据
     */
    public boolean hasNodePortPositions(UUID nodeId) {
        NodePortCollection collection = nodeCollections.get(nodeId);
        return collection != null && !collection.isEmpty();
    }
    
    /**
     * 获取节点的端口数量
     */
    public int getNodePortCount(UUID nodeId) {
        NodePortCollection collection = nodeCollections.get(nodeId);
        return collection != null ? collection.size() : 0;
    }
    
    /**
     * 清理过期的端口位置数据
     */
    private void cleanupOldEntries() {
        long cutoffFrame = currentFrame - MAX_CACHE_AGE_FRAMES;
        List<UUID> nodesToRemove = new ArrayList<>();
        
        for (Map.Entry<UUID, NodePortCollection> entry : nodeCollections.entrySet()) {
            NodePortCollection collection = entry.getValue();
            if (collection.lastUpdateFrame < cutoffFrame) {
                nodesToRemove.add(entry.getKey());
            }
        }
        
        for (UUID nodeId : nodesToRemove) {
            clearNodePortPositions(nodeId);
        }
    }
    
    /**
     * 清除所有数据
     */
    public void clearAll() {
        portPositions.clear();
        nodeCollections.clear();
        totalLookups = 0;
        cacheHits = 0;
        totalUpdates = 0;
    }
    
    /**
     * 获取性能统计信息
     */
    public String getPerformanceStats() {
        double hitRate = totalLookups > 0 ? (cacheHits * 100.0 / totalLookups) : 0.0;
        return String.format("FlatPortManager: %d positions, %d nodes, %.1f%% hit rate, %d updates", 
                           portPositions.size(), nodeCollections.size(), hitRate, totalUpdates);
    }
    
    /**
     * 获取详细统计信息
     */
    public String getDetailedStats() {
        return String.format(
                """
                        FlatPortPositionManager Stats:
                          Total Positions: %d
                          Active Nodes: %d
                          Total Lookups: %d
                          Cache Hits: %d
                          Hit Rate: %.2f%%
                          Total Updates: %d
                          Current Frame: %d""",
            portPositions.size(),
            nodeCollections.size(),
            totalLookups,
            cacheHits,
            totalLookups > 0 ? (cacheHits * 100.0 / totalLookups) : 0.0,
            totalUpdates,
            currentFrame
        );
    }
    
    /**
     * 转换为传统的嵌套Map格式（用于兼容性）
     */
    public Map<UUID, Map<String, ImVec2>> toTraditionalFormat() {
        Map<UUID, Map<String, ImVec2>> result = new HashMap<>();
        for (Map.Entry<UUID, NodePortCollection> entry : nodeCollections.entrySet()) {
            result.put(entry.getKey(), entry.getValue().toTraditionalMap());
        }
        return result;
    }
    
    /**
     * 从传统格式导入数据
     */
    public void importFromTraditionalFormat(Map<UUID, Map<String, ImVec2>> traditionalFormat) {
        clearAll();
        for (Map.Entry<UUID, Map<String, ImVec2>> nodeEntry : traditionalFormat.entrySet()) {
            UUID nodeId = nodeEntry.getKey();
            for (Map.Entry<String, ImVec2> portEntry : nodeEntry.getValue().entrySet()) {
                String portId = portEntry.getKey();
                ImVec2 pos = portEntry.getValue();
                setPortPosition(nodeId, portId, pos.x, pos.y);
            }
        }
    }
    
    /**
     * 批量查找端口位置（优化版本）
     * 减少Map查找次数，提升批量操作性能
     */
    public void batchGetPortPositions(List<PortKey> keys, List<ImVec2> results) {
        results.clear();
        for (PortKey key : keys) {
            PortPosition position = portPositions.get(key);
            results.add(position != null ? position.toImVec2() : null);
        }
        totalLookups += keys.size();
        // 注意：这里没有更新cacheHits，因为批量操作的命中率计算更复杂
    }
    
    /**
     * 创建端口键
     */
    public static PortKey createPortKey(UUID nodeId, String portId) {
        return new PortKey(nodeId, portId);
    }
    
    /**
     * 创建端口位置
     */
    public PortPosition createPortPosition(UUID nodeId, String portId, float x, float y) {
        return new PortPosition(nodeId, portId, x, y, currentFrame);
    }
} 