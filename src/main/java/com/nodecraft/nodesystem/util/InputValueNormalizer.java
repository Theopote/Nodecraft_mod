package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.PortTypeResolver;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.PathData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Single graph-ingress path: canonicalize wire representations, then validate compatibility.
 * {@link #normalize} never returns the original value as a “maybe invalid” fallback.
 */
public final class InputValueNormalizer {

    private static final Object REJECTED = new Object();

    private InputValueNormalizer() {
    }

    public record Result(boolean accepted, @Nullable Object value) {
        public static Result accepted(@Nullable Object value) {
            return new Result(true, value);
        }

        public static Result rejected() {
            return new Result(false, null);
        }
    }

    /**
     * Accepted stored payload (including {@code null}), or rejected when the value must not be stored.
     */
    public static Result normalize(@Nullable IPort port, @Nullable Object value) {
        if (port == null) {
            return Result.rejected();
        }
        NodeDataType type = PortTypeResolver.resolveEffectiveType(port);
        Object canonical = canonicalize(type, value);
        if (canonical == REJECTED) {
            return Result.rejected();
        }
        if (!type.isCompatible(canonical)) {
            return Result.rejected();
        }
        if (!isCanonicalFramePayload(type, canonical)) {
            return Result.rejected();
        }
        return Result.accepted(canonical);
    }

    /**
     * PATH / PATH_LIST wrapping only. Returns {@link #REJECTED} when wrapping fails closed.
     * Other types pass through unchanged for {@link NodeDataType#isCompatible(Object)}.
     */
    private static @Nullable Object canonicalize(NodeDataType type, @Nullable Object value) {
        if (value == null) {
            return null;
        }
        if (type == NodeDataType.PATH) {
            PathData wrapped = PathData.wrap(value);
            return wrapped != null ? wrapped : REJECTED;
        }
        if (type == NodeDataType.PATH_LIST) {
            return canonicalizePathList(value);
        }
        return value;
    }

    private static Object canonicalizePathList(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return value;
        }
        List<PathData> out = new ArrayList<>(collection.size());
        for (Object item : collection) {
            if (item == null) {
                return REJECTED;
            }
            PathData wrapped = PathData.wrap(item);
            if (wrapped == null) {
                return REJECTED;
            }
            out.add(wrapped);
        }
        return List.copyOf(out);
    }

    private static boolean isCanonicalFramePayload(NodeDataType type, @Nullable Object value) {
        if (value == null) {
            return true;
        }
        if (type == NodeDataType.FRAME) {
            return value instanceof FrameData frame && frame.isCanonical();
        }
        if (type == NodeDataType.FRAME_LIST) {
            if (!(value instanceof List<?> list)) {
                return true;
            }
            for (Object item : list) {
                if (item == null) {
                    return false;
                }
                if (!(item instanceof FrameData frame) || !frame.isCanonical()) {
                    return false;
                }
            }
        }
        return true;
    }
}
