package com.nodecraft.nodesystem.execution;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Thread-safe snapshot of the current exec frontier for editor highlighting.
 *
 * <p>Published by {@link NodeExecutor} during exec-flow runs; readers should treat instances as immutable.</p>
 */
public final class ExecFrontierSnapshot {

    public static final ExecFrontierSnapshot EMPTY = new ExecFrontierSnapshot(false, Set.of(), Set.of(), Set.of(), 0L);

    private final boolean active;
    private final Set<UUID> activeNodeIds;
    private final Set<ExecWire> activeExecWires;
    private final Set<UUID> pendingNodeIds;
    private final long step;

    public ExecFrontierSnapshot(
            boolean active,
            Set<UUID> activeNodeIds,
            Set<ExecWire> activeExecWires,
            Set<UUID> pendingNodeIds,
            long step
    ) {
        this.active = active;
        this.activeNodeIds = activeNodeIds == null ? Set.of() : Set.copyOf(activeNodeIds);
        this.activeExecWires = activeExecWires == null ? Set.of() : Set.copyOf(activeExecWires);
        this.pendingNodeIds = pendingNodeIds == null ? Set.of() : Set.copyOf(pendingNodeIds);
        this.step = step;
    }

    public boolean isActive() {
        return active;
    }

    public Set<UUID> activeNodeIds() {
        return activeNodeIds;
    }

    public Set<ExecWire> activeExecWires() {
        return activeExecWires;
    }

    public Set<UUID> pendingNodeIds() {
        return pendingNodeIds;
    }

    public long step() {
        return step;
    }

    public boolean highlightsNode(UUID nodeId) {
        return active && nodeId != null && (activeNodeIds.contains(nodeId) || pendingNodeIds.contains(nodeId));
    }

    public boolean isActiveNode(UUID nodeId) {
        return active && nodeId != null && activeNodeIds.contains(nodeId);
    }

    public boolean isPendingNode(UUID nodeId) {
        return active && nodeId != null && pendingNodeIds.contains(nodeId);
    }

    public boolean highlightsExecWire(UUID sourceNodeId, String sourcePortId, UUID targetNodeId, String targetPortId) {
        if (!active) {
            return false;
        }
        return activeExecWires.contains(new ExecWire(sourceNodeId, sourcePortId, targetNodeId, targetPortId));
    }

    public record ExecWire(UUID sourceNodeId, String sourcePortId, UUID targetNodeId, String targetPortId) {
        public ExecWire {
            Objects.requireNonNull(sourceNodeId, "sourceNodeId");
            Objects.requireNonNull(sourcePortId, "sourcePortId");
            Objects.requireNonNull(targetNodeId, "targetNodeId");
            Objects.requireNonNull(targetPortId, "targetPortId");
        }
    }
}
