package com.nodecraft.nodesystem.nodes.utilities.assist;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "utilities.assist.string_format",
    displayName = "String Format",
    description = "Formats strings with placeholders like {0}, {1} from dynamic values.",
    category = "utilities.assist",
    order = 6
)
public class StringFormatNode extends BaseNode {

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
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when formatting produced output", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        String resolvedTemplate = inputValues.get(INPUT_TEMPLATE_ID) instanceof String text && !text.isBlank()
            ? text
            : template;
        List<Object> values = resolveValues();

        String result = resolvedTemplate;
        for (int i = 0; i < values.size(); i++) {
            result = result.replace("{" + i + "}", valueToString(values.get(i)));
        }

        outputValues.put(OUTPUT_TEXT_ID, result);
        outputValues.put(OUTPUT_LENGTH_ID, result.length());
        outputValues.put(OUTPUT_VALID_ID, true);
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
        if (map.get("precision") instanceof Number n) {
            precision = Math.max(0, Math.min(8, n.intValue()));
        }
    }

    private List<Object> resolveValues() {
        List<Object> values = new ArrayList<>();
        if (inputValues.get(INPUT_VALUES_ID) instanceof List<?> list && !list.isEmpty()) {
            values.addAll(list);
            return values;
        }
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

    private String valueToString(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number n) {
            if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
                return String.valueOf(n.longValue());
            }
            int p = Math.max(0, Math.min(8, precision));
            String format = "%." + p + "f";
            return String.format(Locale.ROOT, format, n.doubleValue());
        }
        if (value instanceof Vector3d v) {
            int p = Math.max(0, Math.min(8, precision));
            String f = "%." + p + "f";
            return "(" + String.format(Locale.ROOT, f, v.x) + ", " + String.format(Locale.ROOT, f, v.y) + ", " + String.format(Locale.ROOT, f, v.z) + ")";
        }
        if (value instanceof PointData p) {
            return valueToString(p.getPosition());
        }
        if (value instanceof BlockPos b) {
            return "(" + b.getX() + ", " + b.getY() + ", " + b.getZ() + ")";
        }
        if (value instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                out.add(valueToString(item));
            }
            return "[" + String.join(", ", out) + "]";
        }
        if (value instanceof Map<?, ?> map) {
            List<String> out = new ArrayList<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                out.add(String.valueOf(e.getKey()) + "=" + valueToString(e.getValue()));
            }
            return "{" + String.join(", ", out) + "}";
        }
        return String.valueOf(value);
    }
}

