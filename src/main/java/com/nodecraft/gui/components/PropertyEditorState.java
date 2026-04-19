package com.nodecraft.gui.components;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.api.INode;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class PropertyEditorState {
    private static final long EDIT_LOCK_TIMEOUT_MS = 2000;

    private final Map<String, Object> tempValues = new ConcurrentHashMap<>();
    private final Map<String, Long> propertiesBeingEdited = new ConcurrentHashMap<>();
    private final Map<String, Integer> errorCounts = new ConcurrentHashMap<>();

    Map<String, Object> getTempValues() {
        return tempValues;
    }

    Map<String, Long> getPropertiesBeingEdited() {
        return propertiesBeingEdited;
    }

    Map<String, Integer> getErrorCounts() {
        return errorCounts;
    }

    void markPropertyBeingEdited(INode node, String propName) {
        String key = getTempValueKey(node, propName);
        propertiesBeingEdited.put(key, System.currentTimeMillis());
        NodeCraft.LOGGER.trace("属性 {} 标记为正在编辑", key);
    }

    void markPropertyEditingFinished(INode node, String propName) {
        String key = getTempValueKey(node, propName);
        propertiesBeingEdited.remove(key);
        NodeCraft.LOGGER.trace("属性 {} 标记为编辑完成", key);
    }

    boolean isPropertyBeingEdited(INode node, String propName) {
        String key = getTempValueKey(node, propName);
        Long timestamp = propertiesBeingEdited.get(key);
        if (timestamp == null) {
            return false;
        }
        if (System.currentTimeMillis() - timestamp > EDIT_LOCK_TIMEOUT_MS) {
            propertiesBeingEdited.remove(key);
            NodeCraft.LOGGER.debug("属性 {} 的编辑锁已过期并移除", key);
            return false;
        }
        return true;
    }

    void checkAndCleanExpiredEditLocks() {
        long currentTime = System.currentTimeMillis();
        propertiesBeingEdited.entrySet().removeIf(entry -> {
            boolean expired = (currentTime - entry.getValue()) > EDIT_LOCK_TIMEOUT_MS;
            if (expired) {
                NodeCraft.LOGGER.debug("清理过期编辑锁 {}", entry.getKey());
            }
            return expired;
        });
    }

    String getTempValueKey(INode node, String propName) {
        return node.getId() + "_" + propName;
    }

    void clearForNode(INode node) {
        if (node == null) {
            return;
        }

        String nodeIdPrefix = node.getId() + "_";
        tempValues.entrySet().removeIf(entry -> entry.getKey().startsWith(nodeIdPrefix));
        propertiesBeingEdited.entrySet().removeIf(entry -> entry.getKey().startsWith(nodeIdPrefix));
        errorCounts.clear();
    }

    void clearAll() {
        tempValues.clear();
        propertiesBeingEdited.clear();
        errorCounts.clear();
    }
}
