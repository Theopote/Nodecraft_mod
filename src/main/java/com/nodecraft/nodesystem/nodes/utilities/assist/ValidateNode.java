package com.nodecraft.nodesystem.nodes.utilities.assist;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "utilities.assist.validate",
    displayName = "Validate",
    description = "Validates a boolean condition and gates a pass-through value.",
    category = "utilities.assist",
    order = 1
)
public class ValidateNode extends BaseNode {

    @NodeProperty(displayName = "Default Condition", category = "Validate", order = 1)
    private boolean defaultCondition = true;

    @NodeProperty(displayName = "Default Message", category = "Validate", order = 2)
    private String defaultMessage = "Validation failed";

    private static final String INPUT_CONDITION_ID = "input_condition";
    private static final String INPUT_VALUE_ID = "input_value";
    private static final String INPUT_MESSAGE_ID = "input_message";

    private static final String OUTPUT_VALUE_ID = "output_value";
    private static final String OUTPUT_MESSAGE_ID = "output_message";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public ValidateNode() {
        super(UUID.randomUUID(), "utilities.assist.validate");
        addInputPort(new BasePort(INPUT_CONDITION_ID, "Condition", "Validation condition", NodeDataType.BOOLEAN, this));

        BasePort valueIn = new BasePort(INPUT_VALUE_ID, "Value", "Pass-through value", NodeDataType.ANY, this);
        valueIn.bindPassthroughType("T");
        addInputPort(valueIn);

        addInputPort(new BasePort(INPUT_MESSAGE_ID, "Message", "Failure message", NodeDataType.STRING, this));

        BasePort valueOut = new BasePort(OUTPUT_VALUE_ID, "Value", "Value when valid; null when failed", NodeDataType.ANY, this);
        valueOut.bindPassthroughType("T");
        addOutputPort(valueOut);

        addOutputPort(new BasePort(OUTPUT_MESSAGE_ID, "Message", "Validation status message", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "True when condition passed", NodeDataType.BOOLEAN, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Boolean condition = OptionalPortDrive.resolveOptionalBoolean(this, INPUT_CONDITION_ID, defaultCondition);
        String message = resolveMessage();

        if (condition == null) {
            outputValues.put(OUTPUT_VALUE_ID, null);
            outputValues.put(OUTPUT_MESSAGE_ID, message);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        if (!condition) {
            outputValues.put(OUTPUT_VALUE_ID, null);
            outputValues.put(OUTPUT_MESSAGE_ID, message);
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        outputValues.put(OUTPUT_VALUE_ID, inputValues.get(INPUT_VALUE_ID));
        outputValues.put(OUTPUT_MESSAGE_ID, "ok");
        outputValues.put(OUTPUT_VALID_ID, true);
    }

    private String resolveMessage() {
        if (OptionalPortDrive.isConnected(this, INPUT_MESSAGE_ID)) {
            Object value = inputValues.get(INPUT_MESSAGE_ID);
            return value instanceof String text ? text : "";
        }
        return defaultMessage == null ? "" : defaultMessage;
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("defaultCondition", defaultCondition);
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
        if (map.get("defaultMessage") instanceof String text) {
            defaultMessage = text;
        }
    }
}
