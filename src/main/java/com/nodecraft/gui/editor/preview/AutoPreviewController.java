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
import com.nodecraft.nodesystem.preview.PreviewManager;
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
    /**
     * Dirty version of the last non-cancelled preview failure. Poll retries are suppressed
     * until {@link DirtyVersionSource#getDirtyVersion()} advances past this value.
     */
    private long lastFailedPreviewVersion = -1L;
    private volatile ExecutionSession autoPreviewSession;
    /** Most recent successfully submitted preview session (may already be finished). */
    private volatile ExecutionSession lastSubmittedSession;
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
        Set<UUID> scope = IncrementalExecutionPlanner.resolveInvalidationScope(graph, node.getId());
        invalidatedNodeIds.addAll(scope);
        // Drop stale GHOST immediately for impacted nodes; debounce will rebuild after recompute.
        PreviewManager.clearEmptyInputGrace();
        for (UUID impactedId : scope) {
            PreviewManager.hideNodePreviews(impactedId.toString());
        }
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
            for (var graphNode : graph.getNodes()) {
                if (graphNode != null && graphNode.getId() != null) {
                    PreviewManager.hideNodePreviews(graphNode.getId().toString());
                }
            }
        }
        PreviewManager.clearEmptyInputGrace();
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

        // Deterministic failure: do not poll-retry the same dirty version until the graph changes.
        if (!hasPendingDirtyExecution
                && lastFailedPreviewVersion >= 0
                && currentDirtyVersion == lastFailedPreviewVersion) {
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
        lastSubmittedSession = session;
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
            // Stale completions must not overwrite the current session's fail/success ownership.
            boolean ownsFailureState = lastSubmittedSession == session;
            if (throwable != null) {
                if (session.cancellation().isCancelled()) {
                    NodeCraft.LOGGER.debug(
                            "Auto-preview cancelled: reason={}, dirtyVersion={}, stale={}",
                            triggerReason,
                            executingVersion,
                            !ownsFailureState
                    );
                    return;
                }
                if (ownsFailureState) {
                    lastFailedPreviewVersion = executingVersion;
                }
                NodeCraft.LOGGER.error(
                        "Auto-preview failed: reason={}, dirtyVersion={}, appliedFailState={}",
                        triggerReason,
                        executingVersion,
                        ownsFailureState,
                        throwable
                );
                return;
            }
            if (!ownsFailureState) {
                NodeCraft.LOGGER.debug(
                        "Auto-preview stale completion ignored: reason={}, dirtyVersion={}, success={}",
                        triggerReason,
                        executingVersion,
                        Boolean.TRUE.equals(result)
                );
                return;
            }
            if (Boolean.TRUE.equals(result)) {
                lastFailedPreviewVersion = -1L;
                NodeCraft.LOGGER.debug("Auto-preview completed: reason={}, dirtyVersion={}", triggerReason, executingVersion);
            } else {
                // Soft failure (no success flag): suppress poll until dirty advances.
                lastFailedPreviewVersion = executingVersion;
                NodeCraft.LOGGER.debug("Auto-preview ended without success: reason={}, dirtyVersion={}", triggerReason, executingVersion);
            }
        });
    }

    /** Test access: dirty version blocked from poll retry after a non-cancelled failure. */
    public long lastFailedPreviewVersion() {
        return lastFailedPreviewVersion;
    }

    public void cancel() {
        scheduler.cancelPreview();
        autoPreviewSession = null;
    }

    /**
     * In-flight preview session, or {@code null} if none is running / already cleared on completion.
     */
    @Nullable
    public ExecutionSession activeSession() {
        return autoPreviewSession;
    }

    /**
     * Last session this controller successfully submitted. Survives completion so callers can
     * observe submission without racing the worker thread that clears {@link #activeSession()}.
     */
    @Nullable
    public ExecutionSession lastSubmittedSession() {
        return lastSubmittedSession;
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
