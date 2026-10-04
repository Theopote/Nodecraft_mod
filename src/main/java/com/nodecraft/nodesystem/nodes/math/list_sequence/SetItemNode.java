package com.nodecraft.nodesystem.nodes.math.list_sequence;

import com.nodecraft.nodesystem.api.ListElementKind;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.nodes.math.data_tree.DataTreeNodeUtils;
import com.nodecraft.nodesystem.nodes.math.data_tree.ListElementKindValidator;
import com.nodecraft.nodesystem.util.ListIndexResolver;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "math.list.set_item",
    displayName = "Set Item",
    description = "Sets an item at index (negatives from end). Invalid index → Valid=false, empty list.",
    category = "math.list"
)
public class SetItemNode extends BaseNode {

    public static final String ERROR_INVALID_INPUT = "invalid_input";
    public static final String ERROR_INVALID_INDEX = "invalid_index";

    private boolean wrapIndex = false;

    private static final String LIST_T = "T";
    private static final String INPUT_LIST_ID = "input_list";
    private static final String INPUT_INDEX_ID = "input_index";
    private static final String INPUT_VALUE_ID = "input_value";
    private static final String OUTPUT_LIST_ID = "output_list";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public SetItemNode() {
        super(UUID.randomUUID(), "math.list.set_item");

        addInputPort(new BasePort(INPUT_LIST_ID, "List", "The list to modify", NodeDataType.LIST, this)
                .bindListType(LIST_T));
        addInputPort(new BasePort(INPUT_INDEX_ID, "Index", "0-based index; negatives count from end (-1 = last). Wrap applies modulo size.",
                NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_VALUE_ID, "Value", "The new value", NodeDataType.ANY, this)
                .bindListElementType(LIST_T));
        addOutputPort(new BasePort(OUTPUT_LIST_ID, "List", "The modified list", NodeDataType.LIST, this)
                .bindListType(LIST_T));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the index and value were valid",
                NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Failure reason when Valid is false",
                NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object inputObj = inputValues.get(INPUT_LIST_ID);
        Object valueObj = inputValues.get(INPUT_VALUE_ID);
        ListElementKind kind = DataTreeNodeUtils.resolveElementKindFromListPort(this, INPUT_LIST_ID);
        ListIndexResolver.IndexResolveResult indexResult = ListIndexResolver.resolveRequiredIndex(
                resolveValue(INPUT_INDEX_ID),
                isDriven(INPUT_INDEX_ID)
        );

        if (!(inputObj instanceof List<?> inputList)) {
            writeInvalid(ERROR_INVALID_INPUT);
            return;
        }
        if (!indexResult.valid()) {
            writeInvalid(ERROR_INVALID_INDEX);
            return;
        }

        String valueError = ListElementKindValidator.validateListElement(valueObj, kind);
        if (valueError != null) {
            writeInvalid(valueError);
            return;
        }

        List<Object> result = new ArrayList<>(inputList);
        int size = result.size();
        int index = indexResult.index();
        if (index < 0) {
            index = ListIndexResolver.normalizeNegativeFromEnd(index, size);
        }
        if (wrapIndex && size > 0) {
            index = ListIndexResolver.applyWrap(index, size);
        }

        if (index < 0 || index >= size) {
            writeInvalid(ERROR_INVALID_INDEX);
            return;
        }

        result.set(index, valueObj);
        outputValues.put(OUTPUT_LIST_ID, result);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private boolean isDriven(String portId) {
        return OptionalPortDrive.isConnected(this, portId) || inputValues.containsKey(portId);
    }

    private @Nullable Object resolveValue(String portId) {
        if (OptionalPortDrive.isConnected(this, portId)) {
            return getInput(portId);
        }
        return inputValues.get(portId);
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_LIST_ID, List.of());
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? ERROR_INVALID_INPUT : error);
    }

    public boolean isWrapIndex() {
        return wrapIndex;
    }

    public void setWrapIndex(boolean wrap) {
        if (this.wrapIndex != wrap) {
            this.wrapIndex = wrap;
            markDirty();
        }
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("wrapIndex", isWrapIndex());
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> stateMap)) {
            return;
        }
        Object wrap = stateMap.get("wrapIndex");
        if (wrap instanceof Boolean value) {
            setWrapIndex(value);
        }
    }
}
