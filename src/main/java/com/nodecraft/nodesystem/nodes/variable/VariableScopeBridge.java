package com.nodecraft.nodesystem.nodes.variable;

import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class VariableScopeBridge {

    static final String INTERNAL_PREFIX = "__nodecraft.";

    private static final Object NULL_VALUE = new Object();
    private static final ThreadLocal<String> ACTIVE_FALLBACK_SCOPE_ID = new ThreadLocal<>();
    private static final Map<String, Map<String, Object>> FALLBACK_SCOPES = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, Map<String, Object>>> FALLBACK_FRAME_SCOPES = new ConcurrentHashMap<>();

    private VariableScopeBridge() {
    }

    public static ScopeBinding bindFallbackScope(@Nullable String scopeId) {
        return new ScopeBinding(scopeId);
    }

    public static void clearFallbackScope(@Nullable String scopeId) {
        if (scopeId == null || scopeId.isBlank()) {
            return;
        }
        FALLBACK_SCOPES.remove(scopeId);
        FALLBACK_FRAME_SCOPES.remove(scopeId);
    }

    static Map<String, Object> getOrCreateFallbackFrameMap(String frame) {
        Map<String, Map<String, Object>> root = FALLBACK_FRAME_SCOPES.computeIfAbsent(
            resolveFallbackScopeId(),
            ignored -> new ConcurrentHashMap<>()
        );
        return getOrCreateNullFriendlyFrameMap(root, frame);
    }

    static Object get(@Nullable ExecutionContext context, String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        if (context != null) {
            return context.getVariable(key);
        }
        return decodeFallbackValue(fallbackScope().get(key));
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
        return decodeFallbackValue(fallbackScope().put(key, encodeFallbackValue(value)));
    }

    static Object remove(@Nullable ExecutionContext context, String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        if (context != null) {
            return context.removeVariable(key);
        }
        return decodeFallbackValue(fallbackScope().remove(key));
    }

    static int clear(@Nullable ExecutionContext context, boolean includeInternalVariables) {
        Map<String, Object> snapshot = snapshot(context);
        int removed = 0;
        for (String key : snapshot.keySet()) {
            if (!includeInternalVariables && isInternalVariableName(key)) {
                continue;
            }
            if (containsKey(context, key)) {
                remove(context, key);
                removed++;
            }
        }
        return removed;
    }

    static boolean containsKey(@Nullable ExecutionContext context, String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        if (context != null) {
            return context.getAllVariables().containsKey(key);
        }
        return fallbackScope().containsKey(key);
    }

    static Map<String, Object> snapshot(@Nullable ExecutionContext context) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (context != null) {
            copy.putAll(context.getAllVariables());
            return copy;
        }
        for (Map.Entry<String, Object> entry : fallbackScope().entrySet()) {
            copy.put(entry.getKey(), decodeFallbackValue(entry.getValue()));
        }
        return copy;
    }

    static String resolveName(Object inputName, String defaultName) {
        if (inputName instanceof String name && !name.isBlank()) {
            return name.trim();
        }
        if (defaultName == null || defaultName.isBlank()) {
            return null;
        }
        return defaultName.trim();
    }

    static boolean isUserVariableNameValid(String name) {
        return validationError(name) == null;
    }

    static String validationError(String name) {
        if (name == null || name.isBlank()) {
            return "Variable name is required.";
        }
        if (isInternalVariableName(name)) {
            return "Variable names starting with " + INTERNAL_PREFIX + " are reserved.";
        }
        return null;
    }

    static boolean isInternalVariableName(String name) {
        return name != null && name.startsWith(INTERNAL_PREFIX);
    }

    private static Map<String, Object> fallbackScope() {
        return FALLBACK_SCOPES.computeIfAbsent(resolveFallbackScopeId(), ignored -> new ConcurrentHashMap<>());
    }

    private static String resolveFallbackScopeId() {
        String scopeId = ACTIVE_FALLBACK_SCOPE_ID.get();
        if (scopeId == null || scopeId.isBlank()) {
            return "thread:" + Thread.currentThread().threadId();
        }
        return scopeId;
    }

    private static Map<String, Object> getOrCreateNullFriendlyFrameMap(Map<String, Map<String, Object>> root, String frame) {
        Map<String, Object> frameScope = root.get(frame);
        if (frameScope == null) {
            frameScope = newFrameMap();
            root.put(frame, frameScope);
            return frameScope;
        }
        if (frameScope instanceof ConcurrentHashMap<?, ?>) {
            Map<String, Object> migrated = newFrameMap();
            migrated.putAll(frameScope);
            root.put(frame, migrated);
            return migrated;
        }
        return frameScope;
    }

    private static Map<String, Object> newFrameMap() {
        return Collections.synchronizedMap(new LinkedHashMap<>());
    }

    private static Object encodeFallbackValue(Object value) {
        return value == null ? NULL_VALUE : value;
    }

    private static Object decodeFallbackValue(Object value) {
        return value == NULL_VALUE ? null : value;
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
