package com.nodecraft.nodesystem.nodes.variable;

import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class VariableScopeBridge {

    private static final Map<String, Object> FALLBACK_SCOPE = new ConcurrentHashMap<>();

    private VariableScopeBridge() {
    }

    static Object get(@Nullable ExecutionContext context, String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        if (context != null) {
            return context.getVariable(key);
        }
        return FALLBACK_SCOPE.get(key);
    }

    static Object put(@Nullable ExecutionContext context, String key, Object value) {
        if (key == null || key.isBlank()) {
            return null;
        }
        if (context != null) {
            Object previous = context.getVariable(key);
            context.setVariable(key, value);
            return previous;
        }
        return FALLBACK_SCOPE.put(key, value);
    }

    static boolean containsKey(@Nullable ExecutionContext context, String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        if (context != null) {
            return context.getAllVariables().containsKey(key);
        }
        return FALLBACK_SCOPE.containsKey(key);
    }

    static Map<String, Object> snapshot(@Nullable ExecutionContext context) {
        if (context != null) {
            return context.getAllVariables();
        }
        return Collections.unmodifiableMap(FALLBACK_SCOPE);
    }
}

