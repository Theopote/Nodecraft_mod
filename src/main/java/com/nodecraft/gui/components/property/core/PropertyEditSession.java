package com.nodecraft.gui.components.property.core;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.api.INode;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Sole owner of property-panel edit session state: temp widgets, edit locks, and error counts.
 * <p>
 * See {@code docs/architecture/property-panel-breakup.md} (Phase 1).
 */
public final class PropertyEditSession {

    /** Orphan cleanup only — active controls refresh their lock each frame. */
    private static final long EDIT_LOCK_TIMEOUT_MS = 2000;

    private final Map<String, Object> tempValues = new ConcurrentHashMap<>();
    private final Map<String, Long> propertiesBeingEdited = new ConcurrentHashMap<>();
    private final Map<String, Long> editSessionIds = new ConcurrentHashMap<>();
    private final Map<String, Integer> errorCounts = new ConcurrentHashMap<>();
    private final AtomicLong editSessionCounter = new AtomicLong();

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

    public void clearPropertyError(INode node, String propName) {
        if (node != null && propName != null) {
            errorCounts.remove(getTempValueKey(node, propName));
        }
    }

    /**
     * Increments and returns the new error count for the node property.
     */
    public int recordPropertyError(INode node, String propName) {
        String key = getTempValueKey(node, propName);
        int next = errorCounts.getOrDefault(key, 0) + 1;
        errorCounts.put(key, next);
        return next;
    }

    public int getPropertyErrorCount(INode node, String propName) {
        return errorCounts.getOrDefault(getTempValueKey(node, propName), 0);
    }

    public boolean isPropertyDisabled(INode node, String propName, int threshold) {
        return getPropertyErrorCount(node, propName) >= threshold;
    }

    public void markPropertyBeingEdited(INode node, String propName) {
        refreshPropertyBeingEdited(node, propName);
    }

    /**
     * Refreshes the edit lock timestamp while an ImGui control stays active.
     */
    public void refreshPropertyBeingEdited(INode node, String propName) {
        String key = getTempValueKey(node, propName);
        propertiesBeingEdited.put(key, System.currentTimeMillis());
        editSessionIds.computeIfAbsent(key, ignored -> editSessionCounter.incrementAndGet());
        NodeCraft.LOGGER.trace("属性 {} 标记为正在编辑", key);
    }

    /**
     * @return active edit-session id for undo coalescing, or {@code 0} when no session is open
     */
    public long getEditSessionId(INode node, String propName) {
        if (node == null || propName == null) {
            return 0L;
        }
        return editSessionIds.getOrDefault(getTempValueKey(node, propName), 0L);
    }

    public void markPropertyEditingFinished(INode node, String propName) {
        String key = getTempValueKey(node, propName);
        propertiesBeingEdited.remove(key);
        editSessionIds.remove(key);
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
            editSessionIds.remove(key);
            NodeCraft.LOGGER.debug("属性 {} 的编辑锁已过期并移除", key);
            return false;
        }
        return true;
    }

    /**
     * Removes edit locks that were never cleared after focus loss (orphan cleanup).
     */
    public void checkAndCleanExpiredEditLocks() {
        long currentTime = System.currentTimeMillis();
        propertiesBeingEdited.entrySet().removeIf(entry -> {
            boolean expired = (currentTime - entry.getValue()) > EDIT_LOCK_TIMEOUT_MS;
            if (expired) {
                NodeCraft.LOGGER.debug("清理过期编辑锁 {}", entry.getKey());
                editSessionIds.remove(entry.getKey());
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
        editSessionIds.entrySet().removeIf(entry -> entry.getKey().startsWith(nodeIdPrefix));
        errorCounts.entrySet().removeIf(entry -> entry.getKey().startsWith(nodeIdPrefix));
    }

    public void clearAll() {
        tempValues.clear();
        propertiesBeingEdited.clear();
        editSessionIds.clear();
        errorCounts.clear();
    }
}
