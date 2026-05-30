package com.nodecraft.nodesystem.nodes.utilities.assist;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "utilities.assist.assert",
    displayName = "Assert / Validate",
    description = "Validates a boolean condition and optionally throws to stop execution when it fails.",
    category = "utilities.assist",
    order = 7
)
public class AssertNode extends BaseNode {

    @NodeProperty(displayName = "Default Condition", category = "Assert", order = 1)
    private boolean defaultCondition = true;

    @NodeProperty(displayName = "Fail Hard", category = "Assert", order = 2)
    private boolean failHard = false;

    @NodeProperty(displayName = "Default Message", category = "Assert", order = 3)
    private String defaultMessage = "Assertion failed";

    private static final String INPUT_CONDITION_ID = "input_condition";
    private static final String INPUT_VALUE_ID = "input_value";
    private static final String INPUT_MESSAGE_ID = "input_message";

    private static final String OUTPUT_PASSED_ID = "output_passed";
    private static final String OUTPUT_VALUE_ID = "output_value";
    private static final String OUTPUT_MESSAGE_ID = "output_message";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public AssertNode() {
        super(UUID.randomUUID(), "utilities.assist.assert");
        addInputPort(new BasePort(INPUT_CONDITION_ID, "Condition", "Validation condition", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_VALUE_ID, "Value", "Pass-through value", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_MESSAGE_ID, "Message", "Failure message", NodeDataType.STRING, this));

        addOutputPort(new BasePort(OUTPUT_PASSED_ID, "Passed", "True when condition passed", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_VALUE_ID, "Value", "Value when assert passed; null when failed", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_MESSAGE_ID, "Message", "Assertion status message", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Alias of Passed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean condition = resolveCondition(inputValues.get(INPUT_CONDITION_ID));
        String message = inputValues.get(INPUT_MESSAGE_ID) instanceof String text && !text.isBlank() ? text : defaultMessage;
        Object value = inputValues.get(INPUT_VALUE_ID);

        if (!condition) {
            outputValues.put(OUTPUT_PASSED_ID, false);
            outputValues.put(OUTPUT_VALUE_ID, null);
            outputValues.put(OUTPUT_MESSAGE_ID, message);
            outputValues.put(OUTPUT_VALID_ID, false);
            if (failHard) {
                throw new IllegalStateException(message);
            }
            return;
        }

        outputValues.put(OUTPUT_PASSED_ID, true);
        outputValues.put(OUTPUT_VALUE_ID, value);
        outputValues.put(OUTPUT_MESSAGE_ID, "ok");
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private boolean resolveCondition(Object value) {
        if (value == null) {
            return defaultCondition;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0d;
        }
        if (value instanceof String text) {
            String normalized = text.trim();
            if (normalized.isEmpty()) {
                return false;
            }
            return switch (normalized.toLowerCase(Locale.ROOT)) {
                case "true", "yes", "1", "on" -> true;
                default -> false;
            };
        }
        return true;
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("defaultCondition", defaultCondition);
        state.put("failHard", failHard);
        state.put("defaultMessage", defaultMessage);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        if (map.get("defaultCondition") instanceof Boolean b) {
            defaultCondition = b;
        }
        if (map.get("failHard") instanceof Boolean b) {
            failHard = b;
        }
        if (map.get("defaultMessage") instanceof String text) {
            defaultMessage = text;
        }
    }
}

