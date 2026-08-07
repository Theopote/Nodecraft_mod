package com.nodecraft.gui.editor.preview;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.execution.ExecFrontierSnapshot;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.IncrementalExecutionPlanner;
import com.nodecraft.nodesystem.execution.runtime.ExecutionPlan;
import com.nodecraft.nodesystem.execution.runtime.ExecutionSession;
import com.nodecraft.nodesystem.execution.runtime.NodeExecutionScheduler;
import com.nodecraft.nodesystem.graph.NodeGraph;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Owns auto-preview debounce, invalidation scope, and scheduler submission.
 * <p>
 * Extracted from {@code ImGuiNodeEditor} so preview policy can be unit/GameTested
 * without the full ImGui render loop.
 */
public final class AutoPreviewController {

    public static final long DEBOUNCE_MS = 250L;
    public static final long POLL_INTERVAL_MS = 750L;

    public interface DirtyVersionSource {
        long getDirtyVersion();

        void markDirty();
    }

    private final Supplier<@Nullable NodeGraph> graphSupplier;
    private final DirtyVersionSource dirtyVersionSource;
    private final Supplier<@Nullable ExecutionContext> executionContextFactory;
    private final LongSupplier clock;
    private final NodeExecutionScheduler scheduler;

    private long lastObservedDirtyVersion = -1L;
    private long pendingAutoPreviewVersion = -1L;
    private long lastAutoPreviewDirtyChangeAt = 0L;
    private long lastAutoPreviewExecutionAt = 0L;
    private volatile ExecutionSession autoPreviewSession;
    private long graphDirtyEpoch = 0L;
    private final Set<UUID> invalidatedNodeIds = new HashSet<>();

    public AutoPreviewController(
            Supplier<@Nullable NodeGraph> graphSupplier,
            DirtyVersionSource dirtyVersionSource,
            Supplier<@Nullable ExecutionContext> executionContextFactory
    ) {
        this(graphSupplier, dirtyVersionSource, executionContextFactory, System::currentTimeMillis, NodeExecutionScheduler.client());
    }

    public AutoPreviewController(
            Supplier<@Nullable NodeGraph> graphSupplier,
            DirtyVersionSource dirtyVersionSource,
            Supplier<@Nullable ExecutionContext> executionContextFactory,
            LongSupplier clock,
            NodeExecutionScheduler scheduler
    ) {
        this.graphSupplier = Objects.requireNonNull(graphSupplier, "graphSupplier");
        this.dirtyVersionSource = Objects.requireNonNull(dirtyVersionSource, "dirtyVersionSource");
        this.executionContextFactory = Objects.requireNonNull(executionContextFactory, "executionContextFactory");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    public void notifyNodeDirty(BaseNode node, long dirtyVersion) {
        NodeGraph graph = graphSupplier.get();
        if (node == null || graph == null) {
            return;
        }
        if (graph.getNode(node.getId()) == null) {
            return;
        }
        invalidatedNodeIds.addAll(IncrementalExecutionPlanner.resolveInvalidationScope(graph, node.getId()));
        graphDirtyEpoch++;
        dirtyVersionSource.markDirty();
        stampPendingDirty(dirtyVersionSource.getDirtyVersion());
        NodeCraft.LOGGER.debug(
                "Auto-preview dirty from node {} (nodeDirtyVersion={}). Impacted={}, graphDirtyEpoch={}",
                node.getId(),
                dirtyVersion,
                invalidatedNodeIds.size(),
                graphDirtyEpoch
        );
    }

    public void notifyStructureDirty() {
        invalidatedNodeIds.clear();
        NodeGraph graph = graphSupplier.get();
        if (graph != null) {
            graph.getExecutionCache().clear();
        }
        graphDirtyEpoch++;
        dirtyVersionSource.markDirty();
        stampPendingDirty(dirtyVersionSource.getDirtyVersion());
        NodeCraft.LOGGER.debug("Auto-preview structure dirty. graphDirtyEpoch={}", graphDirtyEpoch);
    }

    /**
     * Starts debounce from the moment dirty is notified (not only when {@link #tick()} first observes it).
     */
    private void stampPendingDirty(long dirtyVersion) {
        lastObservedDirtyVersion = dirtyVersion;
        pendingAutoPreviewVersion = dirtyVersion;
        lastAutoPreviewDirtyChangeAt = clock.getAsLong();
    }

    /**
     * Poll from the editor frame loop (or tests) to start/supersede preview sessions.
     */
    public void tick() {
        NodeGraph graph = graphSupplier.get();
        if (graph == null) {
            return;
        }

        long currentDirtyVersion = dirtyVersionSource.getDirtyVersion();
        long now = clock.getAsLong();
        if (currentDirtyVersion != lastObservedDirtyVersion) {
            lastObservedDirtyVersion = currentDirtyVersion;
            pendingAutoPreviewVersion = currentDirtyVersion;
            lastAutoPreviewDirtyChangeAt = now;
        }

        boolean hasPendingDirtyExecution = pendingAutoPreviewVersion >= 0;
        boolean shouldPollPreview = now - lastAutoPreviewExecutionAt >= POLL_INTERVAL_MS;
        if (!hasPendingDirtyExecution && !shouldPollPreview) {
            return;
        }

        if (scheduler.activeManual().map(ExecutionSession::isExecuting).orElse(false)) {
            return;
        }

        if (hasPendingDirtyExecution && now - lastAutoPreviewDirtyChangeAt < DEBOUNCE_MS) {
            return;
        }

        ExecutionContext context = executionContextFactory.get();
        if (context == null) {
            return;
        }

        final long executingVersion = hasPendingDirtyExecution ? pendingAutoPreviewVersion : currentDirtyVersion;
        final String triggerReason = hasPendingDirtyExecution ? "dirty" : "poll";
        pendingAutoPreviewVersion = -1L;
        lastAutoPreviewExecutionAt = now;

        Set<UUID> executionScope = hasPendingDirtyExecution && !invalidatedNodeIds.isEmpty()
                ? new HashSet<>(invalidatedNodeIds)
                : null;
        invalidatedNodeIds.clear();

        ExecutionPlan plan = ExecutionPlan.preview(executionScope);
        ExecutionSession session = scheduler.submit(graph, context, plan, executingVersion);
        if (session.cancellation().isCancelled() && !session.isExecuting()) {
            if (hasPendingDirtyExecution) {
                pendingAutoPreviewVersion = executingVersion;
            }
            return;
        }

        autoPreviewSession = session;
        NodeCraft.LOGGER.debug(
                "Auto-preview submit: reason={}, dirtyVersion={}, nodes={}, mode={}, scopeSize={}, session={}",
                triggerReason,
                executingVersion,
                graph.getNodes().size(),
                executionScope == null ? "full" : "partial",
                executionScope == null ? 0 : executionScope.size(),
                session.sessionId()
        );
        session.result().whenComplete((result, throwable) -> {
            if (autoPreviewSession == session) {
                autoPreviewSession = null;
            }
            if (throwable != null) {
                if (session.cancellation().isCancelled()) {
                    NodeCraft.LOGGER.debug(
                            "Auto-preview cancelled: reason={}, dirtyVersion={}",
                            triggerReason,
                            executingVersion
                    );
                    return;
                }
                NodeCraft.LOGGER.error(
                        "Auto-preview failed: reason={}, dirtyVersion={}",
                        triggerReason,
                        executingVersion,
                        throwable
                );
                return;
            }
            if (Boolean.TRUE.equals(result)) {
                NodeCraft.LOGGER.debug("Auto-preview completed: reason={}, dirtyVersion={}", triggerReason, executingVersion);
            } else {
                NodeCraft.LOGGER.debug("Auto-preview ended without success: reason={}, dirtyVersion={}", triggerReason, executingVersion);
            }
        });
    }

    public void cancel() {
        scheduler.cancelPreview();
        autoPreviewSession = null;
    }

    @Nullable
    public ExecutionSession activeSession() {
        return autoPreviewSession;
    }

    public ExecFrontierSnapshot activeExecFrontierSnapshot() {
        ExecutionSession session = autoPreviewSession;
        if (session != null && session.isExecuting()) {
            return session.execFrontierSnapshot();
        }
        return ExecFrontierSnapshot.EMPTY;
    }

    public long graphDirtyEpoch() {
        return graphDirtyEpoch;
    }

    public Set<UUID> invalidatedNodeIdsView() {
        return Set.copyOf(invalidatedNodeIds);
    }
}
