package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.VectorData;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Workload-safe string formatting for {@code utilities.assist.string_format}.
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
        StringBuilder output = new StringBuilder(resolvedTemplate.length());
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(resolvedTemplate);
        int lastEnd = 0;

        while (matcher.find()) {
            String literal = resolvedTemplate.substring(lastEnd, matcher.start());
            String literalError = appendSegment(output, literal);
            if (literalError != null) {
                return FormatResult.graphFailure(literalError);
            }

            int index;
            try {
                index = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                String placeholder = matcher.group();
                String placeholderError = appendSegment(output, placeholder);
                if (placeholderError != null) {
                    return FormatResult.graphFailure(placeholderError);
                }
                lastEnd = matcher.end();
                continue;
            }

            if (index >= 0 && index < values.size()) {
                FormatContext context = new FormatContext();
                String formatted = formatValue(values.get(index), safePrecision, 0, new IdentityHashMap<>(), context);
                if (formatted == null) {
                    return FormatResult.graphFailure(
                        context.error == null ? "Value formatting failed" : context.error
                    );
                }
                String appendError = appendSegment(output, formatted);
                if (appendError != null) {
                    return FormatResult.graphFailure(appendError);
                }
                if (!usedIndexes[index]) {
                    usedIndexes[index] = true;
                }
            } else {
                String placeholder = matcher.group();
                String placeholderError = appendSegment(output, placeholder);
                if (placeholderError != null) {
                    return FormatResult.graphFailure(placeholderError);
                }
            }
            lastEnd = matcher.end();
        }

        String tailError = appendSegment(output, resolvedTemplate.substring(lastEnd));
        if (tailError != null) {
            return FormatResult.graphFailure(tailError);
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
                output.toString(),
                usedCount,
                missingCount,
                "Missing values for " + missingCount + " placeholder(s)."
            );
        }

        return FormatResult.success(output.toString(), usedCount);
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

    private static @Nullable String appendSegment(StringBuilder output, String segment) {
        if (segment.isEmpty()) {
            return null;
        }
        return GenerationLimits.validateFormatOutputBudget(output.length(), segment.length()) == null
            ? appendChecked(output, segment)
            : "Formatted output exceeds MAX_FORMAT_OUTPUT_CHARS";
    }

    private static @Nullable String appendChecked(StringBuilder output, String segment) {
        output.append(segment);
        return null;
    }

    private static final class FormatContext {
        private @Nullable String error;
    }

    private static @Nullable String formatValue(
        Object value,
        int precision,
        int depth,
        IdentityHashMap<Object, Boolean> visited,
        FormatContext context
    ) {
        if (depth > GenerationLimits.MAX_FORMAT_DEPTH) {
            context.error = "Format depth exceeds MAX_FORMAT_DEPTH";
            return null;
        }

        switch (value) {
            case null -> {
                return "null";
            }
            case Number number -> {
                if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
                    return String.valueOf(number.longValue());
                }
                String format = "%." + precision + "f";
                return String.format(Locale.ROOT, format, number.doubleValue());
            }
            case Vector3d vector -> {
                return formatVector3(vector.x, vector.y, vector.z, precision);
            }
            case VectorData vectorData -> {
                return formatVector3(vectorData.x(), vectorData.y(), vectorData.z(), precision);
            }
            case PointData pointData -> {
                return formatValue(pointData.position(), precision, depth, visited, context);
            }
            case BlockPos blockPos -> {
                return "(" + blockPos.getX() + ", " + blockPos.getY() + ", " + blockPos.getZ() + ")";
            }
            case List<?> list -> {
                if (visited.put(list, Boolean.TRUE) != null) {
                    context.error = "Cyclic list detected during formatting";
                    return null;
                }
                List<String> parts = new ArrayList<>(list.size());
                for (Object item : list) {
                    String formatted = formatValue(item, precision, depth + 1, visited, context);
                    if (formatted == null) {
                        visited.remove(list);
                        return null;
                    }
                    parts.add(formatted);
                }
                visited.remove(list);
                return "[" + String.join(", ", parts) + "]";
            }
            case Map<?, ?> map -> {
                if (visited.put(map, Boolean.TRUE) != null) {
                    context.error = "Cyclic map detected during formatting";
                    return null;
                }
                List<String> parts = new ArrayList<>(map.size());
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    String key = formatValue(entry.getKey(), precision, depth + 1, visited, context);
                    if (key == null) {
                        visited.remove(map);
                        return null;
                    }
                    String mapValue = formatValue(entry.getValue(), precision, depth + 1, visited, context);
                    if (mapValue == null) {
                        visited.remove(map);
                        return null;
                    }
                    parts.add(key + "=" + mapValue);
                }
                visited.remove(map);
                return "{" + String.join(", ", parts) + "}";
            }
            default -> {
            }
        }
        return String.valueOf(value);
    }

    private static String formatVector3(double x, double y, double z, int precision) {
        String format = "%." + precision + "f";
        return "(" + String.format(Locale.ROOT, format, x) + ", "
            + String.format(Locale.ROOT, format, y) + ", "
            + String.format(Locale.ROOT, format, z) + ")";
    }
}
