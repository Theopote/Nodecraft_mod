package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.PointData;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Workload-bounded CSV/JSON encoder for {@code output.export.export_data}.
 */
public final class ExportDataEncoder {

    public record Result(String text, int count, boolean valid, String error) {
        public static Result ok(String text, int count) {
            return new Result(text, count, true, "");
        }

        public static Result invalid(String error) {
            return new Result("", 0, false, error == null ? "" : error);
        }
    }

    private ExportDataEncoder() {
    }

    public static Result encodeCsv(List<?> data) {
        if (data == null) {
            return Result.invalid("empty data");
        }
        if (data.size() > GenerationLimits.MAX_EXPORT_ROWS) {
            return Result.invalid("rows exceed MAX_EXPORT_ROWS");
        }

        EncodeContext context = new EncodeContext();
        boolean mapRows = !data.isEmpty() && data.getFirst() instanceof Map<?, ?>;
        try {
            if (mapRows) {
                List<String> headers = List.copyOf(collectHeaders(data));
                if (!append(context, String.join(",", headers))) {
                    return Result.invalid(context.error);
                }
                if (!append(context, "\n")) {
                    return Result.invalid(context.error);
                }
                for (Object item : data) {
                    if (!recordItem(context)) {
                        return Result.invalid(context.error);
                    }
                    Map<?, ?> map = item instanceof Map<?, ?> m ? m : Map.of("value", item);
                    for (int i = 0; i < headers.size(); i++) {
                        if (i > 0 && !append(context, ",")) {
                            return Result.invalid(context.error);
                        }
                        Object value = map.get(headers.get(i));
                        if (!append(context, csvEscape(stringify(value)))) {
                            return Result.invalid(context.error);
                        }
                    }
                    if (!append(context, "\n")) {
                        return Result.invalid(context.error);
                    }
                }
            } else {
                if (!append(context, "index,value\n")) {
                    return Result.invalid(context.error);
                }
                for (int i = 0; i < data.size(); i++) {
                    if (!recordItem(context)) {
                        return Result.invalid(context.error);
                    }
                    if (!append(context, i + "," + csvEscape(stringify(data.get(i))) + "\n")) {
                        return Result.invalid(context.error);
                    }
                }
            }
        } catch (Exception e) {
            return Result.invalid(e.getMessage() != null ? e.getMessage() : "csv_encode_failed");
        }
        return Result.ok(context.output.toString(), data.size());
    }

    public static Result encodeJson(Object value, boolean pretty) {
        EncodeContext context = new EncodeContext();
        try {
            if (!appendJson(context, value, pretty, 0)) {
                return Result.invalid(context.error);
            }
        } catch (Exception e) {
            return Result.invalid(e.getMessage() != null ? e.getMessage() : "json_encode_failed");
        }
        int count = value instanceof List<?> list ? list.size() : 1;
        return Result.ok(context.output.toString(), count);
    }

    private static Set<String> collectHeaders(List<?> data) {
        LinkedHashSet<String> headers = new LinkedHashSet<>();
        for (Object item : data) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            for (Object key : map.keySet()) {
                headers.add(String.valueOf(key));
            }
        }
        if (headers.isEmpty()) {
            headers.add("value");
        }
        return headers;
    }

    private static boolean appendJson(EncodeContext context, @Nullable Object value, boolean pretty, int depth) {
        if (depth > GenerationLimits.MAX_EXPORT_DEPTH) {
            context.error = "depth exceeds MAX_EXPORT_DEPTH";
            return false;
        }
        if (value == null) {
            return append(context, "null");
        }
        if (value instanceof Boolean || value instanceof Integer || value instanceof Long
            || value instanceof Short || value instanceof Byte) {
            return append(context, String.valueOf(value));
        }
        if (value instanceof Number number) {
            double d = number.doubleValue();
            return append(context, Double.isFinite(d) ? String.valueOf(d) : "null");
        }
        if (value instanceof String text) {
            return append(context, "\"" + escapeJson(text) + "\"");
        }
        if (value instanceof BlockPos b) {
            return append(context, "{\"x\":" + b.getX() + ",\"y\":" + b.getY() + ",\"z\":" + b.getZ() + "}");
        }
        if (value instanceof Vector3d v) {
            return append(context, String.format(Locale.ROOT, "{\"x\":%s,\"y\":%s,\"z\":%s}", v.x, v.y, v.z));
        }
        if (value instanceof PointData p) {
            return appendJson(context, p.position(), pretty, depth);
        }
        if (value instanceof Map<?, ?> map) {
            return appendMap(context, map, pretty, depth);
        }
        if (value instanceof Iterable<?> iterable) {
            return appendIterable(context, iterable, pretty, depth);
        }
        return append(context, "\"" + escapeJson(String.valueOf(value)) + "\"");
    }

    private static boolean appendMap(EncodeContext context, Map<?, ?> map, boolean pretty, int depth) {
        if (context.visited.put(map, Boolean.TRUE) != null) {
            context.error = "cyclic_reference";
            return false;
        }
        if (!append(context, "{")) {
            context.visited.remove(map);
            return false;
        }
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!recordItem(context)) {
                context.visited.remove(map);
                return false;
            }
            if (!first) {
                if (!append(context, pretty ? ",\n" : ",")) {
                    context.visited.remove(map);
                    return false;
                }
            } else if (pretty) {
                if (!append(context, "\n")) {
                    context.visited.remove(map);
                    return false;
                }
            }
            first = false;
            if (pretty && !appendIndent(context, depth + 1)) {
                context.visited.remove(map);
                return false;
            }
            String key = "\"" + escapeJson(String.valueOf(entry.getKey())) + "\"";
            if (!append(context, key + ":" + (pretty ? " " : ""))) {
                context.visited.remove(map);
                return false;
            }
            if (!appendJson(context, entry.getValue(), pretty, depth + 1)) {
                context.visited.remove(map);
                return false;
            }
        }
        if (pretty && !first) {
            if (!append(context, "\n") || !appendIndent(context, depth)) {
                context.visited.remove(map);
                return false;
            }
        }
        context.visited.remove(map);
        return append(context, "}");
    }

    private static boolean appendIterable(EncodeContext context, Iterable<?> iterable, boolean pretty, int depth) {
        if (context.visited.put(iterable, Boolean.TRUE) != null) {
            context.error = "cyclic_reference";
            return false;
        }
        if (!append(context, "[")) {
            context.visited.remove(iterable);
            return false;
        }
        boolean first = true;
        Iterator<?> iterator = iterable.iterator();
        while (iterator.hasNext()) {
            Object item = iterator.next();
            if (!recordItem(context)) {
                context.visited.remove(iterable);
                return false;
            }
            if (!first) {
                if (!append(context, pretty ? ",\n" : ",")) {
                    context.visited.remove(iterable);
                    return false;
                }
            } else if (pretty) {
                if (!append(context, "\n")) {
                    context.visited.remove(iterable);
                    return false;
                }
            }
            first = false;
            if (pretty && !appendIndent(context, depth + 1)) {
                context.visited.remove(iterable);
                return false;
            }
            if (!appendJson(context, item, pretty, depth + 1)) {
                context.visited.remove(iterable);
                return false;
            }
        }
        if (pretty && !first) {
            if (!append(context, "\n") || !appendIndent(context, depth)) {
                context.visited.remove(iterable);
                return false;
            }
        }
        context.visited.remove(iterable);
        return append(context, "]");
    }

    private static boolean appendIndent(EncodeContext context, int depth) {
        for (int i = 0; i < depth; i++) {
            if (!append(context, "  ")) {
                return false;
            }
        }
        return true;
    }

    private static boolean recordItem(EncodeContext context) {
        context.visitedItems++;
        if (context.visitedItems > GenerationLimits.MAX_EXPORT_ITEMS) {
            context.error = "items exceed MAX_EXPORT_ITEMS";
            return false;
        }
        return true;
    }

    private static boolean append(EncodeContext context, String segment) {
        if (segment == null || segment.isEmpty()) {
            return true;
        }
        if (context.output.length() + segment.length() > GenerationLimits.MAX_EXPORT_TEXT_CHARS) {
            context.error = "text exceeds MAX_EXPORT_TEXT_CHARS";
            return false;
        }
        context.output.append(segment);
        return true;
    }

    private static String csvEscape(String text) {
        if (text == null) {
            return "";
        }
        boolean quote = text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r");
        if (!quote) {
            return text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static String stringify(@Nullable Object value) {
        return switch (value) {
            case null -> "";
            case BlockPos b -> b.getX() + "," + b.getY() + "," + b.getZ();
            case Vector3d v -> v.x + "," + v.y + "," + v.z;
            case PointData p -> stringify(p.position());
            default -> String.valueOf(value);
        };
    }

    private static String escapeJson(String text) {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\f", "\\f")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private static final class EncodeContext {
        private final StringBuilder output = new StringBuilder(256);
        private final IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();
        private int visitedItems;
        private String error = "";
    }
}
