package com.nodecraft.nodesystem.nodes.utilities.assist;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.StrictIntegerUtils;
import com.nodecraft.nodesystem.util.StringFormatEngine;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "utilities.assist.string_format",
    displayName = "String Format",
    description = "Formats strings with placeholders like {0}, {1} from dynamic values.",
    category = "utilities.assist",
    order = 0
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
    private static final String OUTPUT_USED_VALUE_COUNT_ID = "output_used_value_count";
    private static final String OUTPUT_MISSING_PLACEHOLDER_COUNT_ID = "output_missing_placeholder_count";
    private static final String OUTPUT_MESSAGE_ID = "output_message";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

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
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Graph/runtime failure reason when Valid is false", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        String resolvedTemplate = resolveTemplate();
        if (resolvedTemplate == null) {
            writeGraphFailure("Template must be STRING");
            return;
        }

        List<Object> values = resolveValues();
        if (values == null) {
            writeGraphFailure(resolveValuesError());
            return;
        }

        StringFormatEngine.FormatResult result = StringFormatEngine.format(resolvedTemplate, values, precision);
        outputValues.put(OUTPUT_TEXT_ID, result.text());
        outputValues.put(OUTPUT_LENGTH_ID, result.length());
        outputValues.put(OUTPUT_USED_VALUE_COUNT_ID, result.usedValueCount());
        outputValues.put(OUTPUT_MISSING_PLACEHOLDER_COUNT_ID, result.missingPlaceholderCount());
        outputValues.put(OUTPUT_MESSAGE_ID, result.message());
        outputValues.put(OUTPUT_VALID_ID, result.valid());
        outputValues.put(OUTPUT_ERROR_ID, result.error());
    }

    private void writeGraphFailure(String error) {
        outputValues.put(OUTPUT_TEXT_ID, "");
        outputValues.put(OUTPUT_LENGTH_ID, 0);
        outputValues.put(OUTPUT_USED_VALUE_COUNT_ID, 0);
        outputValues.put(OUTPUT_MISSING_PLACEHOLDER_COUNT_ID, 0);
        outputValues.put(OUTPUT_MESSAGE_ID, "");
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
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

    private @Nullable String resolveValuesError() {
        if (OptionalPortDrive.isConnected(this, INPUT_VALUES_ID)) {
            Object value = inputValues.get(INPUT_VALUES_ID);
            if (!(value instanceof List<?> list)) {
                return "Values must be LIST";
            }
            String sizeError = GenerationLimits.validateFormatValuesSize(list.size());
            return sizeError == null ? "Invalid values input." : sizeError;
        }
        return "Invalid values input.";
    }

    /**
     * Connected Values must be a List (empty allowed).
     * Unconnected uses connected Value 0-2 ports only.
     *
     * @return null when connected Values is invalid
     */
    private @Nullable List<Object> resolveValues() {
        if (OptionalPortDrive.isConnected(this, INPUT_VALUES_ID)) {
            Object value = inputValues.get(INPUT_VALUES_ID);
            if (!(value instanceof List<?> list)) {
                return null;
            }
            if (GenerationLimits.validateFormatValuesSize(list.size()) != null) {
                return null;
            }
            return new ArrayList<>(list);
        }

        List<Object> values = new ArrayList<>(3);
        if (OptionalPortDrive.isConnected(this, INPUT_VALUE_0_ID)) {
            values.add(inputValues.get(INPUT_VALUE_0_ID));
        }
        if (OptionalPortDrive.isConnected(this, INPUT_VALUE_1_ID)) {
            values.add(inputValues.get(INPUT_VALUE_1_ID));
        }
        if (OptionalPortDrive.isConnected(this, INPUT_VALUE_2_ID)) {
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
}
