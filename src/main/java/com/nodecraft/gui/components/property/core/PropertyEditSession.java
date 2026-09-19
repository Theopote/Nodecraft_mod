package com.nodecraft.gui.components.property.core;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.api.INode;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Sole owner of property-panel edit session state: temp widgets, edit locks, and error counts.
 * <p>
 * See {@code docs/architecture/property-panel-breakup.md} (Phase 1).
 */
public final class PropertyEditSession {

    private static final long EDIT_LOCK_TIMEOUT_MS = 2000;

    private final Map<String, Object> tempValues = new ConcurrentHashMap<>();
    private final Map<String, Long> propertiesBeingEdited = new ConcurrentHashMap<>();
    private final Map<String, Integer> errorCounts = new ConcurrentHashMap<>();

    public String getTempValueKey(INode node, String propName) {
        return node.getId() + "_" + propName;
    }

    @SuppressWarnings("unchecked")
    public <T> T getOrCreateTempValue(String key, Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return (T) tempValues.computeIfAbsent(key, ignored -> supplier.get());
    }

    /**
     * Returns the existing temp value when it matches {@code type}; otherwise replaces it.
     */
    public <T> T getOrReplaceTempValue(String key, Class<T> type, Supplier<T> supplier) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(supplier, "supplier");
        Object existing = tempValues.get(key);
        if (type.isInstance(existing)) {
            return type.cast(existing);
        }
        T created = supplier.get();
        tempValues.put(key, created);
        return created;
    }

    public void putTempValue(String key, Object value) {
        if (value == null) {
            tempValues.remove(key);
        } else {
            tempValues.put(key, value);
        }
    }

    public @Nullable Object getTempValue(String key) {
        return tempValues.get(key);
    }

    public void clearPropertyError(String propName) {
        if (propName != null) {
            errorCounts.remove(propName);
        }
    }

    /**
     * Increments and returns the new error count for {@code propName}.
     */
    public int recordPropertyError(String propName) {
        int next = errorCounts.getOrDefault(propName, 0) + 1;
        errorCounts.put(propName, next);
        return next;
    }

    public int getPropertyErrorCount(String propName) {
        return errorCounts.getOrDefault(propName, 0);
    }

    public boolean isPropertyDisabled(String propName, int threshold) {
        return getPropertyErrorCount(propName) >= threshold;
    }

    public void markPropertyBeingEdited(INode node, String propName) {
        String key = getTempValueKey(node, propName);
        propertiesBeingEdited.put(key, System.currentTimeMillis());
        NodeCraft.LOGGER.trace("属性 {} 标记为正在编辑", key);
    }

    public void markPropertyEditingFinished(INode node, String propName) {
        String key = getTempValueKey(node, propName);
        propertiesBeingEdited.remove(key);
        NodeCraft.LOGGER.trace("属性 {} 标记为编辑完成", key);
    }

    public boolean isPropertyBeingEdited(INode node, String propName) {
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

    public void checkAndCleanExpiredEditLocks() {
        long currentTime = System.currentTimeMillis();
        propertiesBeingEdited.entrySet().removeIf(entry -> {
            boolean expired = (currentTime - entry.getValue()) > EDIT_LOCK_TIMEOUT_MS;
            if (expired) {
                NodeCraft.LOGGER.debug("清理过期编辑锁 {}", entry.getKey());
            }
            return expired;
        });
    }

    public void clearForNode(INode node) {
        if (node == null) {
            return;
        }
        String nodeIdPrefix = node.getId() + "_";
        tempValues.entrySet().removeIf(entry -> entry.getKey().startsWith(nodeIdPrefix));
        propertiesBeingEdited.entrySet().removeIf(entry -> entry.getKey().startsWith(nodeIdPrefix));
        errorCounts.clear();
    }

    public void clearAll() {
        tempValues.clear();
        propertiesBeingEdited.clear();
        errorCounts.clear();
    }
}
