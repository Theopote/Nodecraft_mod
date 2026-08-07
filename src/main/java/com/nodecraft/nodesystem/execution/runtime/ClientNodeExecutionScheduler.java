package com.nodecraft.nodesystem.execution.runtime;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.graph.NodeGraph;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Client/editor singleton scheduler with a shared single worker thread.
 * <p>
 * Preview submissions supersede any in-flight preview session.
 * Manual Run cancels preview first and takes exclusive use of the worker.
 * Auto-preview is skipped while a manual session is still running.
 */
public final class ClientNodeExecutionScheduler implements NodeExecutionScheduler {

    private static final ClientNodeExecutionScheduler INSTANCE = new ClientNodeExecutionScheduler();

    private final ExecutorService sharedWorker = Executors.newSingleThreadExecutor(new SharedWorkerFactory());
    private final Object lock = new Object();
    private final AtomicLong manualGeneration = new AtomicLong(1L);
    private volatile NodeExecutorSession activePreview;
    private volatile NodeExecutorSession activeManual;

    private ClientNodeExecutionScheduler() {
    }

    public static ClientNodeExecutionScheduler getInstance() {
        return INSTANCE;
    }

    @Override
    public ExecutionSession submit(
            NodeGraph graph,
            @Nullable ExecutionContext context,
            ExecutionPlan plan,
            long generation
    ) {
        if (graph == null || plan == null) {
            throw new IllegalArgumentException("graph and plan are required");
        }

        if (plan.mode() == ExecutionMode.PREVIEW) {
            return submitPreview(graph, context, plan, generation);
        }
        if (plan.mode() == ExecutionMode.MANUAL) {
            return submitManual(graph, context, plan, generation);
        }
        // HEADLESS: run on shared worker without displacing editor sessions.
        return new NodeExecutorSession(
                graph,
                context,
                plan,
                generation,
                new SimpleCancellationToken(),
                sharedWorker
        );
    }

    private ExecutionSession submitPreview(
            NodeGraph graph,
            @Nullable ExecutionContext context,
            ExecutionPlan plan,
            long generation
    ) {
        synchronized (lock) {
            if (activeManual != null && !activeManual.result().isDone()) {
                NodeCraft.LOGGER.debug(
                        "Skipping preview generation {} while manual session {} is active",
                        generation,
                        activeManual.sessionId()
                );
                return skippedSession(ExecutionMode.PREVIEW, generation);
            }
        }

        SimpleCancellationToken token = new SimpleCancellationToken();
        NodeExecutorSession session = new NodeExecutorSession(
                graph,
                context,
                plan,
                generation,
                token,
                sharedWorker
        );

        synchronized (lock) {
            NodeExecutorSession previous = activePreview;
            activePreview = session;
            if (previous != null && previous.result() != null && !previous.result().isDone()) {
                NodeCraft.LOGGER.debug(
                        "Superseding preview session generation {} with {}",
                        previous.generation(),
                        generation
                );
                previous.requestCancel();
            }
        }
        session.result().whenComplete((ok, error) -> {
            synchronized (lock) {
                if (activePreview == session) {
                    activePreview = null;
                }
            }
        });
        return session;
    }

    private ExecutionSession submitManual(
            NodeGraph graph,
            @Nullable ExecutionContext context,
            ExecutionPlan plan,
            long generation
    ) {
        long resolvedGeneration = generation > 0L ? generation : manualGeneration.getAndIncrement();
        cancelPreview();

        SimpleCancellationToken token = new SimpleCancellationToken();
        NodeExecutorSession session = new NodeExecutorSession(
                graph,
                context,
                plan,
                resolvedGeneration,
                token,
                sharedWorker
        );

        synchronized (lock) {
            NodeExecutorSession previous = activeManual;
            activeManual = session;
            if (previous != null && previous.result() != null && !previous.result().isDone()) {
                NodeCraft.LOGGER.debug(
                        "Superseding manual session {} with {}",
                        previous.sessionId(),
                        session.sessionId()
                );
                previous.requestCancel();
            }
        }
        session.result().whenComplete((ok, error) -> {
            synchronized (lock) {
                if (activeManual == session) {
                    activeManual = null;
                }
            }
        });
        return session;
    }

    private static ExecutionSession skippedSession(ExecutionMode mode, long generation) {
        SimpleCancellationToken token = new SimpleCancellationToken();
        token.cancel();
        return new SkippedExecutionSession(mode, generation, token);
    }

    @Override
    public void cancelPreview() {
        synchronized (lock) {
            NodeExecutorSession previous = activePreview;
            activePreview = null;
            if (previous != null) {
                previous.requestCancel();
            }
        }
    }

    @Override
    public void cancelManual() {
        synchronized (lock) {
            NodeExecutorSession previous = activeManual;
            activeManual = null;
            if (previous != null) {
                previous.requestCancel();
            }
        }
    }

    @Override
    public Optional<ExecutionSession> activePreview() {
        return Optional.ofNullable(activePreview);
    }

    @Override
    public Optional<ExecutionSession> activeManual() {
        return Optional.ofNullable(activeManual);
    }

    ExecutorService sharedWorkerForTests() {
        return sharedWorker;
    }

    private static final class SharedWorkerFactory implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "nodecraft-shared-graph-worker-" + sequence.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
