package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.VectorData;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.lang.reflect.Array;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Workload-bounded value stringification for the output.debug family.
 * <p>
 * Containers are walked element-by-element; container {@code toString()} is never called.
 * Soft-truncates with {@code "..."} when char / item / depth budgets are exhausted.
 */
public final class DebugValueFormatter {

    public static final FormatOptions DEFAULT_OPTIONS = new FormatOptions(
        GenerationLimits.MAX_DEBUG_TEXT_CHARS,
        GenerationLimits.MAX_DEBUG_ITEMS,
        GenerationLimits.MAX_DEBUG_DEPTH,
        false
    );

    public static final FormatOptions MONITOR_OPTIONS = new FormatOptions(512, 8, 4, true);

    public static final FormatOptions CHAT_OPTIONS = new FormatOptions(512, 16, 4, false);

    private DebugValueFormatter() {
    }

    public record FormatOptions(int maxChars, int maxItems, int maxDepth, boolean pretty) {
        public FormatOptions {
            maxChars = Math.clamp(maxChars, 10, GenerationLimits.MAX_DEBUG_TEXT_CHARS);
            maxItems = Math.clamp(maxItems, 1, GenerationLimits.MAX_DEBUG_ITEMS);
            maxDepth = Math.clamp(maxDepth, 1, GenerationLimits.MAX_DEBUG_DEPTH);
        }
    }

    public record FormatResult(String text, String typeLabel, boolean truncated, int visitedItems) {
    }

    public static FormatResult format(@Nullable Object value, @Nullable FormatOptions options) {
        FormatOptions opts = options == null ? DEFAULT_OPTIONS : options;
        FormatContext context = new FormatContext(opts);
        try {
            appendValue(context, value, 0);
        } catch (Exception e) {
            context.truncated = true;
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            context.output.setLength(0);
            context.output.append("<error: ").append(msg).append('>');
        }
        return new FormatResult(
            context.output.toString(),
            typeLabel(value),
            context.truncated,
            context.visitedItems
        );
    }

    public static String typeLabel(@Nullable Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof BlockPosList list) {
            return "BlockPosList (size=" + list.size() + ")";
        }
        if (value instanceof List<?> list) {
            return "List (size=" + list.size() + ")";
        }
        if (value instanceof Map<?, ?> map) {
            return "Map (size=" + map.size() + ")";
        }
        if (value.getClass().isArray()) {
            return value.getClass().getComponentType().getSimpleName() + "[] (length=" + Array.getLength(value) + ")";
        }
        if (value instanceof Iterable<?> && !(value instanceof String)) {
            return "Iterable";
        }
        if (value instanceof String) {
            return "String";
        }
        if (value instanceof Integer) {
            return "Integer";
        }
        if (value instanceof Long) {
            return "Long";
        }
        if (value instanceof Float) {
            return "Float";
        }
        if (value instanceof Double) {
            return "Double";
        }
        if (value instanceof Boolean) {
            return "Boolean";
        }
        if (value instanceof Number) {
            return "Number";
        }
        if (value instanceof BlockPos) {
            return "BlockPos";
        }
        if (value instanceof PointData) {
            return "Point";
        }
        if (value instanceof VectorData || value instanceof Vector3d) {
            return "Vector";
        }
        return value.getClass().getSimpleName();
    }

    private static void appendValue(FormatContext context, @Nullable Object value, int depth) {
        if (context.truncated) {
            return;
        }
        if (depth > context.options.maxDepth()) {
            context.truncated = true;
            appendRaw(context, "...");
            return;
        }

        switch (value) {
            case null -> appendRaw(context, "null");
            case String s -> appendQuotedString(context, s);
            case Boolean b -> appendRaw(context, b ? "true" : "false");
            case Integer i -> appendRaw(context, String.valueOf(i.longValue()));
            case Long l -> appendRaw(context, String.valueOf(l));
            case Short s -> appendRaw(context, String.valueOf(s.longValue()));
            case Byte b -> appendRaw(context, String.valueOf(b.longValue()));
            case Number number -> appendRaw(context, String.format(Locale.ROOT, "%s", number));
            case BlockPos pos -> appendRaw(context, "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")");
            case Vector3d vector -> appendVector(context, vector.x, vector.y, vector.z);
            case VectorData vectorData -> appendVector(context, vectorData.x(), vectorData.y(), vectorData.z());
            case PointData pointData -> appendValue(context, pointData.position(), depth);
            case BlockPosList list -> appendIterable(context, list, list.size(), depth, '[' , ']');
            case List<?> list -> appendIterable(context, list, list.size(), depth, '[', ']');
            case Map<?, ?> map -> appendMap(context, map, depth);
            default -> {
                if (value.getClass().isArray()) {
                    appendArray(context, value, depth);
                } else if (value instanceof Iterable<?> iterable) {
                    appendIterable(context, iterable, -1, depth, '[', ']');
                } else {
                    appendLeafObject(context, value);
                }
            }
        }
    }

    private static void appendVector(FormatContext context, double x, double y, double z) {
        appendRaw(context, String.format(Locale.ROOT, "(%.4f, %.4f, %.4f)", x, y, z));
    }

    private static void appendQuotedString(FormatContext context, String s) {
        appendRaw(context, "\"");
        int remaining = context.remaining() - 1; // reserve closing quote when possible
        if (remaining <= 0) {
            context.truncated = true;
            return;
        }
        if (s.length() > remaining) {
            appendRaw(context, s.substring(0, Math.max(0, remaining - 3)));
            appendRaw(context, "...");
            context.truncated = true;
        } else {
            appendRaw(context, s);
        }
        appendRaw(context, "\"");
    }

    private static void appendLeafObject(FormatContext context, Object value) {
        String text;
        try {
            text = String.valueOf(value);
        } catch (Exception e) {
            text = "<error: " + e.getClass().getSimpleName() + ">";
            context.truncated = true;
        }
        if (text.length() > context.remaining()) {
            int take = Math.max(0, context.remaining() - 3);
            appendRaw(context, text.substring(0, take));
            appendRaw(context, "...");
            context.truncated = true;
        } else {
            appendRaw(context, text);
        }
    }

    private static void appendArray(FormatContext context, Object array, int depth) {
        int length = Array.getLength(array);
        if (!beginContainer(context, array, '[')) {
            return;
        }
        int shown = 0;
        for (int i = 0; i < length; i++) {
            if (shown >= context.options.maxItems() || !recordVisit(context)) {
                context.truncated = true;
                appendSeparator(context, depth, shown > 0);
                appendRaw(context, "...");
                break;
            }
            appendSeparator(context, depth, shown > 0);
            if (context.options.pretty()) {
                appendIndent(context, depth + 1);
            }
            appendValue(context, Array.get(array, i), depth + 1);
            shown++;
            if (context.truncated) {
                break;
            }
        }
        endContainer(context, depth, ']');
        context.visited.remove(array);
    }

    private static void appendIterable(
        FormatContext context,
        Iterable<?> iterable,
        int knownSize,
        int depth,
        char open,
        char close
    ) {
        if (!beginContainer(context, iterable, open)) {
            return;
        }
        int shown = 0;
        Iterator<?> iterator = iterable.iterator();
        while (iterator.hasNext()) {
            Object item = iterator.next();
            if (shown >= context.options.maxItems() || !recordVisit(context)) {
                context.truncated = true;
                appendSeparator(context, depth, shown > 0);
                appendRaw(context, "...");
                break;
            }
            appendSeparator(context, depth, shown > 0);
            if (context.options.pretty()) {
                appendIndent(context, depth + 1);
            }
            appendValue(context, item, depth + 1);
            shown++;
            if (context.truncated) {
                break;
            }
        }
        if (knownSize >= 0 && knownSize > shown && !context.truncated) {
            // no-op: all items shown
        }
        endContainer(context, depth, close);
        context.visited.remove(iterable);
    }

    private static void appendMap(FormatContext context, Map<?, ?> map, int depth) {
        if (!beginContainer(context, map, '{')) {
            return;
        }
        int shown = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (shown >= context.options.maxItems() || !recordVisit(context)) {
                context.truncated = true;
                appendSeparator(context, depth, shown > 0);
                appendRaw(context, "...");
                break;
            }
            appendSeparator(context, depth, shown > 0);
            if (context.options.pretty()) {
                appendIndent(context, depth + 1);
            }
            appendValue(context, entry.getKey(), depth + 1);
            appendRaw(context, context.options.pretty() ? ": " : "=");
            appendValue(context, entry.getValue(), depth + 1);
            shown++;
            if (context.truncated) {
                break;
            }
        }
        endContainer(context, depth, '}');
        context.visited.remove(map);
    }

    private static boolean beginContainer(FormatContext context, Object container, char open) {
        if (context.visited.put(container, Boolean.TRUE) != null) {
            appendRaw(context, "<cycle>");
            context.truncated = true;
            return false;
        }
        appendRaw(context, String.valueOf(open));
        return true;
    }

    private static void endContainer(FormatContext context, int depth, char close) {
        if (context.options.pretty() && context.output.length() > 0
                && context.output.charAt(context.output.length() - 1) != '['
                && context.output.charAt(context.output.length() - 1) != '{') {
            appendRaw(context, "\n");
            appendIndent(context, depth);
        }
        appendRaw(context, String.valueOf(close));
    }

    private static void appendSeparator(FormatContext context, int depth, boolean needed) {
        if (!needed) {
            return;
        }
        if (context.options.pretty()) {
            appendRaw(context, ",\n");
        } else {
            appendRaw(context, ", ");
        }
    }

    private static void appendIndent(FormatContext context, int depth) {
        for (int i = 0; i < depth; i++) {
            if (!appendRaw(context, "  ")) {
                return;
            }
        }
    }

    private static boolean recordVisit(FormatContext context) {
        context.visitedItems++;
        if (context.visitedItems > GenerationLimits.MAX_FORMAT_VISITED_ITEMS) {
            context.truncated = true;
            return false;
        }
        return true;
    }

    private static boolean appendRaw(FormatContext context, String segment) {
        if (segment == null || segment.isEmpty()) {
            return true;
        }
        if (context.truncated && context.remaining() <= 0) {
            return false;
        }
        int remaining = context.remaining();
        if (segment.length() <= remaining) {
            context.output.append(segment);
            return true;
        }
        if (remaining > 3) {
            context.output.append(segment, 0, remaining - 3);
            context.output.append("...");
        } else if (remaining > 0) {
            context.output.append("...");
        }
        context.truncated = true;
        return false;
    }

    private static final class FormatContext {
        private final FormatOptions options;
        private final StringBuilder output = new StringBuilder(64);
        private final IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();
        private int visitedItems;
        private boolean truncated;

        private FormatContext(FormatOptions options) {
            this.options = options;
        }

        private int remaining() {
            return Math.max(0, options.maxChars() - output.length());
        }
    }
}
