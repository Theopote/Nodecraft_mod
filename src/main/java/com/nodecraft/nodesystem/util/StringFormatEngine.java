package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.VectorData;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Workload-safe string formatting for {@code utilities.assist.string_format}.
 * Values are streamed into a single budgeted {@link StringBuilder}; nested containers
 * are never fully materialized before output-cap checks.
 */
public final class StringFormatEngine {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{(\\d+)}");

    private StringFormatEngine() {
    }

    public record FormatResult(
        String text,
        int length,
        int usedValueCount,
        int missingPlaceholderCount,
        String message,
        boolean valid,
        String error
    ) {
        public static FormatResult graphFailure(String error) {
            return new FormatResult("", 0, 0, 0, "", false, error == null ? "" : error);
        }

        public static FormatResult semanticFailure(
            String text,
            int usedValueCount,
            int missingPlaceholderCount,
            String message
        ) {
            return new FormatResult(
                text,
                text.length(),
                usedValueCount,
                missingPlaceholderCount,
                message,
                false,
                ""
            );
        }

        public static FormatResult success(String text, int usedValueCount) {
            return new FormatResult(text, text.length(), usedValueCount, 0, "ok", true, "");
        }
    }

    public static FormatResult format(@Nullable String template, List<Object> values, int precision) {
        String templateError = GenerationLimits.validateFormatTemplateLength(template);
        if (templateError != null) {
            return FormatResult.graphFailure(templateError);
        }

        String valuesError = GenerationLimits.validateFormatValuesSize(values.size());
        if (valuesError != null) {
            return FormatResult.graphFailure(valuesError);
        }

        String resolvedTemplate = template == null ? "" : template;
        int safePrecision = Math.max(0, Math.min(8, precision));
        boolean[] usedIndexes = new boolean[Math.max(0, values.size())];
        FormatContext context = new FormatContext(new StringBuilder(resolvedTemplate.length()));
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(resolvedTemplate);
        int lastEnd = 0;

        while (matcher.find()) {
            if (!appendLiteral(context, resolvedTemplate.substring(lastEnd, matcher.start()))) {
                return FormatResult.graphFailure(context.error);
            }

            int index;
            try {
                index = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                if (!appendLiteral(context, matcher.group())) {
                    return FormatResult.graphFailure(context.error);
                }
                lastEnd = matcher.end();
                continue;
            }

            if (index >= 0 && index < values.size()) {
                if (!appendValue(context, values.get(index), safePrecision, 0)) {
                    return FormatResult.graphFailure(context.error);
                }
                if (!usedIndexes[index]) {
                    usedIndexes[index] = true;
                }
            } else if (!appendLiteral(context, matcher.group())) {
                return FormatResult.graphFailure(context.error);
            }
            lastEnd = matcher.end();
        }

        if (!appendLiteral(context, resolvedTemplate.substring(lastEnd))) {
            return FormatResult.graphFailure(context.error);
        }

        int usedCount = 0;
        for (boolean used : usedIndexes) {
            if (used) {
                usedCount++;
            }
        }

        int missingCount = countMissingPlaceholders(resolvedTemplate, values.size());
        if (missingCount > 0) {
            return FormatResult.semanticFailure(
                context.output.toString(),
                usedCount,
                missingCount,
                "Missing values for " + missingCount + " placeholder(s)."
            );
        }

        return FormatResult.success(context.output.toString(), usedCount);
    }

    private static int countMissingPlaceholders(String text, int valueCount) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        int missing = 0;
        while (matcher.find()) {
            int index;
            try {
                index = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (index < 0 || index >= valueCount) {
                missing++;
            }
        }
        return missing;
    }

    private static final class FormatContext {
        private final StringBuilder output;
        private final IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();
        private long visitedItems;
        private @Nullable String error;

        private FormatContext(StringBuilder output) {
            this.output = output;
        }
    }

    private static boolean appendLiteral(FormatContext context, String segment) {
        if (segment.isEmpty()) {
            return true;
        }
        String budgetError = GenerationLimits.validateFormatOutputBudget(context.output.length(), segment.length());
        if (budgetError != null) {
            context.error = budgetError;
            return false;
        }
        context.output.append(segment);
        return true;
    }

    private static boolean appendValue(FormatContext context, @Nullable Object value, int precision, int depth) {
        if (depth > GenerationLimits.MAX_FORMAT_DEPTH) {
            context.error = "Format depth exceeds MAX_FORMAT_DEPTH";
            return false;
        }

        return switch (value) {
            case null -> appendLiteral(context, "null");
            case Integer i -> appendLiteral(context, String.valueOf(i.longValue()));
            case Long l -> appendLiteral(context, String.valueOf(l));
            case Short s -> appendLiteral(context, String.valueOf(s.longValue()));
            case Byte b -> appendLiteral(context, String.valueOf(b.longValue()));
            case Number number -> {
                String format = "%." + precision + "f";
                yield appendLiteral(context, String.format(Locale.ROOT, format, number.doubleValue()));
            }
            case Vector3d vector -> appendVector3(context, vector.x, vector.y, vector.z, precision);
            case VectorData vectorData -> appendVector3(context, vectorData.x(), vectorData.y(), vectorData.z(), precision);
            case PointData pointData -> appendValue(context, pointData.position(), precision, depth);
            case BlockPos blockPos -> appendLiteral(context,
                "(" + blockPos.getX() + ", " + blockPos.getY() + ", " + blockPos.getZ() + ")");
            case List<?> list -> appendList(context, list, precision, depth);
            case Map<?, ?> map -> appendMap(context, map, precision, depth);
            default -> appendLiteral(context, String.valueOf(value));
        };
    }

    private static boolean appendVector3(FormatContext context, double x, double y, double z, int precision) {
        String format = "%." + precision + "f";
        return appendLiteral(context, "(" + String.format(Locale.ROOT, format, x) + ", "
            + String.format(Locale.ROOT, format, y) + ", "
            + String.format(Locale.ROOT, format, z) + ")");
    }

    private static boolean appendList(FormatContext context, List<?> list, int precision, int depth) {
        String containerError = GenerationLimits.validateFormatContainerSize(list.size());
        if (containerError != null) {
            context.error = containerError;
            return false;
        }

        if (context.visited.put(list, Boolean.TRUE) != null) {
            context.error = "Cyclic list detected during formatting";
            return false;
        }

        if (!appendLiteral(context, "[")) {
            context.visited.remove(list);
            return false;
        }

        boolean first = true;
        for (Object item : list) {
            if (!recordVisitedItem(context)) {
                context.visited.remove(list);
                return false;
            }
            if (!first && !appendLiteral(context, ", ")) {
                context.visited.remove(list);
                return false;
            }
            first = false;
            if (!appendValue(context, item, precision, depth + 1)) {
                context.visited.remove(list);
                return false;
            }
        }

        context.visited.remove(list);
        return appendLiteral(context, "]");
    }

    private static boolean appendMap(FormatContext context, Map<?, ?> map, int precision, int depth) {
        String containerError = GenerationLimits.validateFormatContainerSize(map.size());
        if (containerError != null) {
            context.error = containerError;
            return false;
        }

        if (context.visited.put(map, Boolean.TRUE) != null) {
            context.error = "Cyclic map detected during formatting";
            return false;
        }

        if (!appendLiteral(context, "{")) {
            context.visited.remove(map);
            return false;
        }

        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!recordVisitedItem(context)) {
                context.visited.remove(map);
                return false;
            }
            if (!first && !appendLiteral(context, ", ")) {
                context.visited.remove(map);
                return false;
            }
            first = false;
            if (!appendValue(context, entry.getKey(), precision, depth + 1)) {
                context.visited.remove(map);
                return false;
            }
            if (!appendLiteral(context, "=")) {
                context.visited.remove(map);
                return false;
            }
            if (!appendValue(context, entry.getValue(), precision, depth + 1)) {
                context.visited.remove(map);
                return false;
            }
        }

        context.visited.remove(map);
        return appendLiteral(context, "}");
    }

    private static boolean recordVisitedItem(FormatContext context) {
        long next = context.visitedItems + 1L;
        String budgetError = GenerationLimits.validateFormatVisitedItems(next);
        if (budgetError != null) {
            context.error = budgetError;
            return false;
        }
        context.visitedItems = next;
        return true;
    }
}
