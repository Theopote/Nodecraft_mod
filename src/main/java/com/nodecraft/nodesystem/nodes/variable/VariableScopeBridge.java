package com.nodecraft.nodesystem.nodes.variable;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.VariableEntry;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
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

    static @Nullable Object get(@Nullable ExecutionContext context, String key) {
        VariableEntry entry = getEntry(context, key);
        return entry == null ? null : entry.value();
    }

    static @Nullable VariableEntry getEntry(@Nullable ExecutionContext context, String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        Object raw;
        if (context != null) {
            if (!context.getAllVariables().containsKey(key)) {
                return null;
            }
            raw = context.getVariableStorage(key);
        } else {
            Map<String, Object> scope = fallbackScope();
            if (!scope.containsKey(key)) {
                return null;
            }
            raw = decodeFallbackValue(scope.get(key));
        }
        return decodeEntry(raw);
    }

    /**
     * Typed write with slot type lock. First concrete write establishes the type;
     * later writes with a different concrete type fail closed.
     */
    static PutResult putTyped(
            @Nullable ExecutionContext context,
            String key,
            NodeDataType writeType,
            @Nullable Object value
    ) {
        if (key == null || key.isBlank()) {
            return PutResult.failure("Variable name is required.");
        }
        NodeDataType candidate = writeType == null ? NodeDataType.ANY : writeType;
        VariableEntry existing = getEntry(context, key);
        if (existing != null) {
            String mismatch = VariableTypeOps.writeTypeMismatchError(key, existing.type(), candidate);
            if (mismatch != null) {
                return PutResult.failure(mismatch, existing.value(), existing.type(), true);
            }
            if (candidate == NodeDataType.ANY && value != null && !existing.type().isCompatible(value)
                    && existing.type() != NodeDataType.ANY) {
                return PutResult.failure(
                        VariableTypeOps.writeTypeMismatchError(key, existing.type(), VariableTypeOps.inferFromValue(value)),
                        existing.value(),
                        existing.type(),
                        true
                );
            }
        }

        NodeDataType slotType;
        if (existing != null) {
            slotType = existing.type() == NodeDataType.ANY && candidate != NodeDataType.ANY
                    ? candidate
                    : existing.type();
        } else if (candidate != NodeDataType.ANY) {
            slotType = candidate;
        } else {
            slotType = VariableTypeOps.inferFromValue(value);
        }

        Object previous = existing == null ? null : existing.value();
        NodeDataType previousType = existing == null ? null : existing.type();
        storeEntry(context, key, VariableEntry.of(slotType, value));
        return PutResult.success(previous, previousType, existing != null);
    }

    static Object put(@Nullable ExecutionContext context, String key, Object value) {
        PutResult result = putTyped(context, key, VariableTypeOps.inferFromValue(value), value);
        return result.previous();
    }

    static Object remove(@Nullable ExecutionContext context, String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        VariableEntry existing = getEntry(context, key);
        if (context != null) {
            context.removeVariable(key);
        } else {
            fallbackScope().remove(key);
        }
        return existing == null ? null : existing.value();
    }

    static int clear(@Nullable ExecutionContext context) {
        Map<String, Object> snapshot = snapshot(context);
        int removed = 0;
        for (String key : snapshot.keySet()) {
            if (isInternalVariableName(key)) {
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
            for (Map.Entry<String, Object> entry : context.getAllVariables().entrySet()) {
                copy.put(entry.getKey(), unwrapStored(entry.getValue()));
            }
            return copy;
        }
        for (Map.Entry<String, Object> entry : fallbackScope().entrySet()) {
            copy.put(entry.getKey(), unwrapStored(decodeFallbackValue(entry.getValue())));
        }
        return copy;
    }

    static @Nullable String resolveName(BaseNode node, String portId, @Nullable String defaultName) {
        return OptionalPortDrive.resolveOptionalString(node, portId, defaultName);
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

    // --- Frame-local typed map helpers ---

    static @Nullable VariableEntry getFrameEntry(Map<String, Object> frameScope, String key) {
        if (frameScope == null || key == null || !frameScope.containsKey(key)) {
            return null;
        }
        return decodeEntry(frameScope.get(key));
    }

    static PutResult putFrameTyped(Map<String, Object> frameScope, String key, NodeDataType writeType, @Nullable Object value) {
        if (frameScope == null || key == null || key.isBlank()) {
            return PutResult.failure("Variable name is required.");
        }
        NodeDataType candidate = writeType == null ? NodeDataType.ANY : writeType;
        VariableEntry existing = getFrameEntry(frameScope, key);
        if (existing != null) {
            String mismatch = VariableTypeOps.writeTypeMismatchError(key, existing.type(), candidate);
            if (mismatch != null) {
                return PutResult.failure(mismatch, existing.value(), existing.type(), true);
            }
            if (candidate == NodeDataType.ANY && value != null && !existing.type().isCompatible(value)
                    && existing.type() != NodeDataType.ANY) {
                return PutResult.failure(
                        VariableTypeOps.writeTypeMismatchError(key, existing.type(), VariableTypeOps.inferFromValue(value)),
                        existing.value(),
                        existing.type(),
                        true
                );
            }
        }

        NodeDataType slotType;
        if (existing != null) {
            slotType = existing.type() == NodeDataType.ANY && candidate != NodeDataType.ANY
                    ? candidate
                    : existing.type();
        } else if (candidate != NodeDataType.ANY) {
            slotType = candidate;
        } else {
            slotType = VariableTypeOps.inferFromValue(value);
        }

        Object previous = existing == null ? null : existing.value();
        NodeDataType previousType = existing == null ? null : existing.type();
        frameScope.put(key, VariableEntry.of(slotType, value));
        return PutResult.success(previous, previousType, existing != null);
    }

    private static void storeEntry(@Nullable ExecutionContext context, String key, VariableEntry entry) {
        if (context != null) {
            context.setVariable(key, entry);
            return;
        }
        fallbackScope().put(key, encodeFallbackValue(entry));
    }

    private static VariableEntry decodeEntry(@Nullable Object raw) {
        if (raw instanceof VariableEntry entry) {
            return entry;
        }
        // Internal keys / legacy untyped values: treat as untyped slot for user-facing reads.
        return VariableEntry.of(VariableTypeOps.inferFromValue(raw), raw);
    }

    private static @Nullable Object unwrapStored(@Nullable Object raw) {
        if (raw instanceof VariableEntry entry) {
            return entry.value();
        }
        return raw;
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

    public record PutResult(
            boolean success,
            @Nullable Object previous,
            @Nullable NodeDataType previousType,
            boolean existedBefore,
            @Nullable String error
    ) {
        static PutResult success(@Nullable Object previous, @Nullable NodeDataType previousType, boolean existedBefore) {
            return new PutResult(true, previous, previousType, existedBefore, null);
        }

        static PutResult failure(String error) {
            return new PutResult(false, null, null, false, error);
        }

        static PutResult failure(String error, @Nullable Object previous, @Nullable NodeDataType previousType, boolean existedBefore) {
            return new PutResult(false, previous, previousType, existedBefore, error);
        }
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
