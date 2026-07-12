package com.nodecraft.nodesystem.nodes.utilities.organization;

import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Stores subgraph call-stack state on {@link ExecutionContext} when present, otherwise on a
 * scoped fallback map for preview/UI compute paths that run without an execution context.
 */
public final class SubgraphCallStackBridge {

    private static final ThreadLocal<String> ACTIVE_FALLBACK_SCOPE_ID = new ThreadLocal<>();
    private static final Map<String, List<String>> FALLBACK_STACKS = new ConcurrentHashMap<>();

    private SubgraphCallStackBridge() {
    }

    public static ScopeBinding bindFallbackScope(@Nullable String scopeId) {
        return new ScopeBinding(scopeId);
    }

    public static void clearFallbackScope(@Nullable String scopeId) {
        if (scopeId == null || scopeId.isBlank()) {
            return;
        }
        FALLBACK_STACKS.remove(scopeId);
    }

    public static int depth(@Nullable ExecutionContext context) {
        return normalizeStack(readRawStack(context)).size();
    }

    public static boolean contains(@Nullable ExecutionContext context, String ref) {
        return normalizeStack(readRawStack(context)).contains(ref);
    }

    public static StackFrame push(@Nullable ExecutionContext context, String ref) {
        Object previous = readRawStack(context);
        List<String> next = normalizeStack(previous);
        next.add(ref);
        writeStack(context, next);
        return new StackFrame(context, previous);
    }

    public static void restore(StackFrame frame) {
        writeStack(frame.context(), frame.previous());
    }

    private static Object readRawStack(@Nullable ExecutionContext context) {
        if (context != null) {
            return context.getVariable(GraphIOKeys.SUBGRAPH_CALL_STACK_KEY);
        }
        return fallbackStack();
    }

    private static void writeStack(@Nullable ExecutionContext context, List<String> stack) {
        if (context != null) {
            context.setVariable(GraphIOKeys.SUBGRAPH_CALL_STACK_KEY, stack);
            return;
        }
        FALLBACK_STACKS.put(resolveFallbackScopeId(), new ArrayList<>(stack));
    }

    private static void writeStack(@Nullable ExecutionContext context, Object rawStack) {
        if (rawStack instanceof List<?> list) {
            writeStack(context, normalizeStack(list));
            return;
        }
        writeStack(context, new ArrayList<>());
    }

    private static List<String> fallbackStack() {
        return FALLBACK_STACKS.computeIfAbsent(resolveFallbackScopeId(), ignored -> new ArrayList<>());
    }

    private static String resolveFallbackScopeId() {
        String scopeId = ACTIVE_FALLBACK_SCOPE_ID.get();
        if (scopeId == null || scopeId.isBlank()) {
            return "thread:" + Thread.currentThread().threadId();
        }
        return scopeId;
    }

    static List<String> normalizeStack(Object raw) {
        if (raw instanceof List<?> list) {
            List<String> normalized = new ArrayList<>();
            for (Object entry : list) {
                if (entry == null) {
                    continue;
                }
                normalized.add(String.valueOf(entry));
            }
            return normalized;
        }
        return new ArrayList<>();
    }

    public record StackFrame(@Nullable ExecutionContext context, @Nullable Object previous) {
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
