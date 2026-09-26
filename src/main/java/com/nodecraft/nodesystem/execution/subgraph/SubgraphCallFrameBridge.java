package com.nodecraft.nodesystem.execution.subgraph;

import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Push/pop {@link SubgraphCallFrame} on {@link ExecutionContext} with thread-local fallback
 * for compute paths without a full execution context.
 */
public final class SubgraphCallFrameBridge {

    private static final ThreadLocal<SubgraphCallFrame> ACTIVE_FALLBACK_FRAME = new ThreadLocal<>();
    private static final Map<String, SubgraphCallFrame> FALLBACK_FRAMES = new ConcurrentHashMap<>();
    private static final ThreadLocal<String> ACTIVE_FALLBACK_SCOPE_ID = new ThreadLocal<>();

    private SubgraphCallFrameBridge() {
    }

    public static ScopeBinding bindFallbackScope(@Nullable String scopeId) {
        return new ScopeBinding(scopeId);
    }

    public static void clearFallbackScope(@Nullable String scopeId) {
        if (scopeId == null || scopeId.isBlank()) {
            return;
        }
        FALLBACK_FRAMES.remove(scopeId);
    }

    @Nullable
    public static SubgraphCallFrame current(@Nullable ExecutionContext context) {
        if (context != null) {
            return context.peekSubgraphCallFrame();
        }
        SubgraphCallFrame scoped = FALLBACK_FRAMES.get(resolveFallbackScopeId());
        if (scoped != null) {
            return scoped;
        }
        return ACTIVE_FALLBACK_FRAME.get();
    }

    public static FrameHandle push(
            @Nullable ExecutionContext context,
            String ref,
            Map<String, Object> inputs
    ) {
        SubgraphCallFrame parent = current(context);
        int depth = parent == null ? 0 : parent.depth() + 1;
        SubgraphCallFrame frame = new SubgraphCallFrame(ref, inputs, depth, parent);
        if (context != null) {
            context.pushSubgraphCallFrame(frame);
            return new FrameHandle(context, parent);
        }
        String scopeId = resolveFallbackScopeId();
        FALLBACK_FRAMES.put(scopeId, frame);
        ACTIVE_FALLBACK_FRAME.set(frame);
        return new FrameHandle(null, parent);
    }

    public static void restore(FrameHandle handle) {
        if (handle == null) {
            return;
        }
        if (handle.context() != null) {
            handle.context().popSubgraphCallFrame();
            return;
        }
        String scopeId = resolveFallbackScopeId();
        SubgraphCallFrame previous = handle.previous();
        if (previous == null) {
            FALLBACK_FRAMES.remove(scopeId);
            ACTIVE_FALLBACK_FRAME.remove();
        } else {
            FALLBACK_FRAMES.put(scopeId, previous);
            ACTIVE_FALLBACK_FRAME.set(previous);
        }
    }

    private static String resolveFallbackScopeId() {
        String scopeId = ACTIVE_FALLBACK_SCOPE_ID.get();
        if (scopeId == null || scopeId.isBlank()) {
            return "thread:" + Thread.currentThread().threadId();
        }
        return scopeId;
    }

    public record FrameHandle(@Nullable ExecutionContext context, @Nullable SubgraphCallFrame previous) {
    }

    public static final class ScopeBinding implements AutoCloseable {

        private final String previous;

        private ScopeBinding(@Nullable String scopeId) {
            previous = ACTIVE_FALLBACK_SCOPE_ID.get();
            if (scopeId == null || scopeId.isBlank()) {
                ACTIVE_FALLBACK_SCOPE_ID.remove();
            } else {
                ACTIVE_FALLBACK_SCOPE_ID.set(scopeId);
            }
        }

        @Override
        public void close() {
            if (previous == null || previous.isBlank()) {
                ACTIVE_FALLBACK_SCOPE_ID.remove();
            } else {
                ACTIVE_FALLBACK_SCOPE_ID.set(previous);
            }
        }
    }
}
