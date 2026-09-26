package com.nodecraft.nodesystem.nodes.variable;

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
    effect = NodeEffect.CONTEXT_WRITE,
    id = "variable.set",
    displayName = "Set Variable",
    description = "Stores a typed value under a user variable name. Slot type is fixed on first write; mismatched overwrites fail closed.",
    category = "variable",
    order = 0
)
public class SetVariableNode extends BaseNode {

    @NodeProperty(displayName = "Default Name", category = "Variable", order = 1)
    private String defaultName = "";

    private static final String INPUT_NAME_ID = "input_name";
    private static final String INPUT_VALUE_ID = "input_value";

    private static final String OUTPUT_VALUE_ID = "output_value";
    private static final String OUTPUT_PREVIOUS_ID = "output_previous";
    private static final String OUTPUT_NAME_ID = "output_name";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_EXISTS_BEFORE_ID = "output_exists_before";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public SetVariableNode() {
        super(UUID.randomUUID(), "variable.set");

        addInputPort(new BasePort(INPUT_NAME_ID, "Name", "Variable name", NodeDataType.STRING, this));
        BasePort valueIn = new BasePort(INPUT_VALUE_ID, "Value", "Value to store", NodeDataType.ANY, this);
        valueIn.bindPassthroughType("T");
        addInputPort(valueIn);

        BasePort valueOut = new BasePort(OUTPUT_VALUE_ID, "Value", "Stored value", NodeDataType.ANY, this);
        valueOut.bindPassthroughType("T");
        addOutputPort(valueOut);
        BasePort previousOut = new BasePort(OUTPUT_PREVIOUS_ID, "Previous", "Previous value at this name", NodeDataType.ANY, this);
        previousOut.bindPassthroughType("T");
        addOutputPort(previousOut);
        addOutputPort(new BasePort(OUTPUT_NAME_ID, "Name", "Resolved variable name", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether write succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_EXISTS_BEFORE_ID, "Exists Before", "Whether variable existed before write", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when write fails", NodeDataType.STRING, this));
    }

    @Override
    public String getDisplayName() {
        return "Set Variable";
    }

    @Override
    public String getDescription() {
        return "Stores a typed value under a user variable name. Slot type is fixed on first write; mismatched overwrites fail closed.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        String name = VariableScopeBridge.resolveName(this, INPUT_NAME_ID, defaultName);
        Object value = inputValues.get(INPUT_VALUE_ID);
        String error = nameError(name);

        if (error != null) {
            writeFailure(name, value, false, error);
            return;
        }

        NodeDataType writeType = VariableTypeOps.resolveWriteType(this, INPUT_VALUE_ID, value);
        VariableScopeBridge.PutResult result = VariableScopeBridge.putTyped(context, name, writeType, value);
        if (!result.success()) {
            writeFailure(name, value, result.existedBefore(), result.error());
            return;
        }

        outputValues.put(OUTPUT_VALUE_ID, value);
        outputValues.put(OUTPUT_PREVIOUS_ID, result.previous());
        outputValues.put(OUTPUT_NAME_ID, name);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_EXISTS_BEFORE_ID, result.existedBefore());
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private @Nullable String nameError(@Nullable String name) {
        if (name == null) {
            if (OptionalPortDrive.isConnected(this, INPUT_NAME_ID)) {
                return "Name is connected but null or invalid.";
            }
            return VariableScopeBridge.validationError(null);
        }
        return VariableScopeBridge.validationError(name);
    }

    private void writeFailure(@Nullable String name, Object value, boolean existsBefore, String error) {
        outputValues.put(OUTPUT_VALUE_ID, value);
        outputValues.put(OUTPUT_PREVIOUS_ID, null);
        outputValues.put(OUTPUT_NAME_ID, name == null ? "" : name);
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_EXISTS_BEFORE_ID, existsBefore);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("defaultName", defaultName);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        Object nameObj = map.get("defaultName");
        if (nameObj instanceof String name) {
            defaultName = name;
        }
    }
}
