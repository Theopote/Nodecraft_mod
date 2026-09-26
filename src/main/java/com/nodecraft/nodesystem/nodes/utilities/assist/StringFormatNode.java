package com.nodecraft.nodesystem.nodes.utilities.assist;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.VectorData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.StrictIntegerUtils;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "utilities.assist.string_format",
    displayName = "String Format",
    description = "Formats strings with placeholders like {0}, {1} from dynamic values.",
    category = "utilities.assist",
    order = 0
)
public class StringFormatNode extends BaseNode {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{(\\d+)}");

    @NodeProperty(displayName = "Template", category = "Format", order = 1)
    private String template = "{0}";

    @NodeProperty(displayName = "Precision", category = "Format", order = 2)
    private int precision = 3;

    private static final String INPUT_TEMPLATE_ID = "input_template";
    private static final String INPUT_VALUES_ID = "input_values";
    private static final String INPUT_VALUE_0_ID = "input_value_0";
    private static final String INPUT_VALUE_1_ID = "input_value_1";
    private static final String INPUT_VALUE_2_ID = "input_value_2";

    private static final String OUTPUT_TEXT_ID = "output_text";
    private static final String OUTPUT_LENGTH_ID = "output_length";
    private static final String OUTPUT_USED_VALUE_COUNT_ID = "output_used_value_count";
    private static final String OUTPUT_MISSING_PLACEHOLDER_COUNT_ID = "output_missing_placeholder_count";
    private static final String OUTPUT_MESSAGE_ID = "output_message";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public StringFormatNode() {
        super(UUID.randomUUID(), "utilities.assist.string_format");
        addInputPort(new BasePort(INPUT_TEMPLATE_ID, "Template", "Template string containing {0},{1}... placeholders", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_VALUES_ID, "Values", "List of values used for placeholders", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_VALUE_0_ID, "Value 0", "Single value for {0}", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_VALUE_1_ID, "Value 1", "Single value for {1}", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_VALUE_2_ID, "Value 2", "Single value for {2}", NodeDataType.ANY, this));

        addOutputPort(new BasePort(OUTPUT_TEXT_ID, "Text", "Formatted output text", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_LENGTH_ID, "Length", "Formatted text length", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_USED_VALUE_COUNT_ID, "Used Value Count", "Number of input values consumed by placeholders", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_MISSING_PLACEHOLDER_COUNT_ID, "Missing Placeholder Count", "Number of placeholders without a matching value", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_MESSAGE_ID, "Message", "Formatting warning or status message", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when formatting produced output", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        String resolvedTemplate = resolveTemplate();
        if (resolvedTemplate == null) {
            emitFail("Invalid template input.");
            return;
        }

        List<Object> values = resolveValues();
        if (values == null) {
            emitFail("Invalid values input.");
            return;
        }

        String result = resolvedTemplate;
        for (int i = 0; i < values.size(); i++) {
            result = result.replace("{" + i + "}", valueToString(values.get(i)));
        }

        PlaceholderStats stats = analyzePlaceholders(resolvedTemplate, values.size());
        String message = stats.missingCount() > 0
            ? "Missing values for " + stats.missingCount() + " placeholder(s)."
            : "ok";

        outputValues.put(OUTPUT_TEXT_ID, result);
        outputValues.put(OUTPUT_LENGTH_ID, result.length());
        outputValues.put(OUTPUT_USED_VALUE_COUNT_ID, stats.usedCount());
        outputValues.put(OUTPUT_MISSING_PLACEHOLDER_COUNT_ID, stats.missingCount());
        outputValues.put(OUTPUT_MESSAGE_ID, message);
        outputValues.put(OUTPUT_VALID_ID, stats.missingCount() == 0);
    }

    private void emitFail(String message) {
        outputValues.put(OUTPUT_TEXT_ID, "");
        outputValues.put(OUTPUT_LENGTH_ID, 0);
        outputValues.put(OUTPUT_USED_VALUE_COUNT_ID, 0);
        outputValues.put(OUTPUT_MISSING_PLACEHOLDER_COUNT_ID, 0);
        outputValues.put(OUTPUT_MESSAGE_ID, message);
        outputValues.put(OUTPUT_VALID_ID, false);
    }

    /**
     * Connected template must be String (including blank); null/non-String fails.
     * Unconnected uses the property template.
     */
    private @Nullable String resolveTemplate() {
        if (OptionalPortDrive.isConnected(this, INPUT_TEMPLATE_ID)) {
            Object value = inputValues.get(INPUT_TEMPLATE_ID);
            return value instanceof String text ? text : null;
        }
        return template == null ? "" : template;
    }

    /**
     * Connected Values must be a List (empty/null/invalid fails).
     * Unconnected uses Value0-2 via containsKey.
     *
     * @return null when connected Values is invalid
     */
    private @Nullable List<Object> resolveValues() {
        if (OptionalPortDrive.isConnected(this, INPUT_VALUES_ID)) {
            Object value = inputValues.get(INPUT_VALUES_ID);
            if (!(value instanceof List<?> list) || list.isEmpty()) {
                return null;
            }
            return new ArrayList<>(list);
        }

        List<Object> values = new ArrayList<>();
        if (inputValues.containsKey(INPUT_VALUE_0_ID)) {
            values.add(inputValues.get(INPUT_VALUE_0_ID));
        }
        if (inputValues.containsKey(INPUT_VALUE_1_ID)) {
            values.add(inputValues.get(INPUT_VALUE_1_ID));
        }
        if (inputValues.containsKey(INPUT_VALUE_2_ID)) {
            values.add(inputValues.get(INPUT_VALUE_2_ID));
        }
        return values;
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("template", template);
        state.put("precision", precision);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("template") instanceof String text) {
            template = text;
        }
        Integer exactPrecision = StrictIntegerUtils.requireExactInteger(map.get("precision"));
        if (exactPrecision != null) {
            precision = Math.max(0, Math.min(8, exactPrecision));
        }
    }

    private PlaceholderStats analyzePlaceholders(String text, int valueCount) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text == null ? "" : text);
        int used = 0;
        int missing = 0;
        boolean[] usedIndexes = new boolean[Math.max(0, valueCount)];
        while (matcher.find()) {
            int index;
            try {
                index = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (index >= 0 && index < valueCount) {
                if (!usedIndexes[index]) {
                    usedIndexes[index] = true;
                    used++;
                }
            } else {
                missing++;
            }
        }
        return new PlaceholderStats(used, missing);
    }

    private String valueToString(Object value) {
        switch (value) {
            case null -> {
                return "null";
            }
            case Number n -> {
                if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
                    return String.valueOf(n.longValue());
                }
                int p = Math.max(0, Math.min(8, precision));
                String format = "%." + p + "f";
                return String.format(Locale.ROOT, format, n.doubleValue());
            }
            case Vector3d v -> {
                return formatVector3(v.x, v.y, v.z);
            }
            case VectorData v -> {
                return formatVector3(v.x(), v.y(), v.z());
            }
            case PointData p -> {
                return valueToString(p.position());
            }
            case BlockPos b -> {
                return "(" + b.getX() + ", " + b.getY() + ", " + b.getZ() + ")";
            }
            case List<?> list -> {
                List<String> out = new ArrayList<>();
                for (Object item : list) {
                    out.add(valueToString(item));
                }
                return "[" + String.join(", ", out) + "]";
            }
            case Map<?, ?> map -> {
                List<String> out = new ArrayList<>();
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    out.add(e.getKey() + "=" + valueToString(e.getValue()));
                }
                return "{" + String.join(", ", out) + "}";
            }
            default -> {
            }
        }
        return String.valueOf(value);
    }

    private String formatVector3(double x, double y, double z) {
        int p = Math.max(0, Math.min(8, precision));
        String f = "%." + p + "f";
        return "(" + String.format(Locale.ROOT, f, x) + ", "
            + String.format(Locale.ROOT, f, y) + ", "
            + String.format(Locale.ROOT, f, z) + ")";
    }

    private record PlaceholderStats(int usedCount, int missingCount) {
    }
}
