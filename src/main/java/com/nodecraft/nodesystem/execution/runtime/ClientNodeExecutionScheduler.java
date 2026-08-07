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

/**
 * Client/editor singleton scheduler with a shared single worker thread.
 * <p>
 * Preview submissions supersede any in-flight preview session (cancel + replace).
 */
public final class ClientNodeExecutionScheduler implements NodeExecutionScheduler {

    private static final ClientNodeExecutionScheduler INSTANCE = new ClientNodeExecutionScheduler();

    private final ExecutorService sharedWorker = Executors.newSingleThreadExecutor(new PreviewWorkerFactory());
    private final Object lock = new Object();
    private volatile NodeExecutorSession activePreview;

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

        SimpleCancellationToken token = new SimpleCancellationToken();
        NodeExecutorSession session = new NodeExecutorSession(
                graph,
                context,
                plan,
                generation,
                token,
                sharedWorker
        );

        if (plan.mode() == ExecutionMode.PREVIEW) {
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
        }

        return session;
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
    public Optional<ExecutionSession> activePreview() {
        return Optional.ofNullable(activePreview);
    }

    /**
     * Test helper: exposes whether the shared worker is still alive (not terminated).
     */
    ExecutorService sharedWorkerForTests() {
        return sharedWorker;
    }

    private static final class PreviewWorkerFactory implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "nodecraft-shared-graph-worker-" + sequence.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}