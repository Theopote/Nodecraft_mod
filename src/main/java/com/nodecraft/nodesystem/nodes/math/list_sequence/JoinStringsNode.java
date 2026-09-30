package com.nodecraft.nodesystem.nodes.math.list_sequence;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Joins a STRING_LIST with an optional separator into a single STRING.
 */
@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.list.join_strings",
    displayName = "Join Strings",
    description = "Joins STRING_LIST items with a separator into one STRING.",
    category = "math.list",
    order = 40
)
public class JoinStringsNode extends BaseNode {

    private static final String INPUT_LIST_ID = "input_list";
    private static final String INPUT_SEPARATOR_ID = "input_separator";
    private static final String OUTPUT_RESULT_ID = "output_result";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    @NodeProperty(displayName = "Separator", category = "Join", order = 0)
    private String separator = "";

    public JoinStringsNode() {
        super(UUID.randomUUID(), "math.list.join_strings");
        addInputPort(new BasePort(INPUT_LIST_ID, "List", "Strings to join", NodeDataType.STRING_LIST, this));
        addInputPort(new BasePort(INPUT_SEPARATOR_ID, "Separator", "Text inserted between items", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_RESULT_ID, "Result", "Joined string", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether preflight succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why join failed", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object listObj = inputValues.get(INPUT_LIST_ID);
        if (!(listObj instanceof List<?> list)) {
            publish("", false, "List must be a STRING_LIST.");
            return;
        }

        String sep = resolveSeparator();
        if (sep == null) {
            publish("", false, "Separator is null or invalid.");
            return;
        }

        for (int i = 0; i < list.size(); i++) {
            Object entry = list.get(i);
            if (!(entry instanceof String)) {
                publish("", false, "List element at index " + i + " is not a STRING.");
                return;
            }
        }

        String budgetError = GenerationLimits.validateJoinStringOutputBudget(list, sep);
        if (budgetError != null) {
            publish("", false, budgetError);
            return;
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                builder.append(sep);
            }
            builder.append((String) list.get(i));
        }
        publish(builder.toString(), true, "");
    }

    private @Nullable String resolveSeparator() {
        if (OptionalPortDrive.isConnected(this, INPUT_SEPARATOR_ID)) {
            Object value = inputValues.get(INPUT_SEPARATOR_ID);
            return value instanceof String text ? text : null;
        }
        Object raw = inputValues.get(INPUT_SEPARATOR_ID);
        if (raw == null) {
            return separator == null ? "" : separator;
        }
        return raw instanceof String text ? text : null;
    }

    private void publish(String result, boolean valid, String error) {
        outputValues.put(OUTPUT_RESULT_ID, result);
        outputValues.put(OUTPUT_VALID_ID, valid);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    public String getSeparator() {
        return separator;
    }

    public void setSeparator(String separator) {
        this.separator = separator == null ? "" : separator;
        markDirty();
    }
}
