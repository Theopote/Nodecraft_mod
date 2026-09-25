package com.nodecraft.nodesystem.execution;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.core.BaseNode;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks the last successful execution version for each node output cache.
 */
public final class NodeExecutionCache {

    private final Map<UUID, Long> lastExecutedDirtyVersion = new HashMap<>();

    public void record(INode node) {
        if (node instanceof BaseNode baseNode) {
            lastExecutedDirtyVersion.put(node.getId(), baseNode.getDirtyVersion());
        }
    }

    public boolean hasValidCachedOutput(INode node) {
        if (!(node instanceof BaseNode baseNode) || baseNode.isDirty()) {
            return false;
        }
        Long lastExecuted = lastExecutedDirtyVersion.get(node.getId());
        return lastExecuted != null && lastExecuted == baseNode.getDirtyVersion();
    }

    public void invalidate(UUID nodeId) {
        if (nodeId != null) {
            lastExecutedDirtyVersion.remove(nodeId);
        }
    }

    public void invalidateAll(Iterable<UUID> nodeIds) {
        if (nodeIds == null) {
            return;
        }
        for (UUID nodeId : nodeIds) {
            invalidate(nodeId);
        }
    }

    public void clear() {
        lastExecutedDirtyVersion.clear();
    }

    public int size() {
        return lastExecutedDirtyVersion.size();
    }
}
